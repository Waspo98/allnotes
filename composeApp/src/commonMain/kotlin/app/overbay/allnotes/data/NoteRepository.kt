package app.overbay.allnotes.data

import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DeletedEntity
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.EntityKind
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspacePreferences
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class NoteRepository(
    private val storage: NoteStorageBackend = PlatformNoteStore(),
    private var syncProvider: SyncProvider = LocalLoopbackSyncProvider(),
    private val typingDebounceMs: Long = 0L,
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val persistOnBackground: Boolean = false,
    private val allowAutoHttpSync: Boolean = storage is PlatformNoteStore
) {
    private val initialLoadedSnapshot = storage.loadSnapshot()
    private var isUntouchedStarterSeed: Boolean = initialLoadedSnapshot == null

    private val _snapshot = MutableStateFlow(
        initialLoadedSnapshot ?: SampleWorkspaceFactory.createStarterSnapshot().also {
            storage.saveSnapshot(it)
        }
    )
    val snapshot: StateFlow<WorkspaceSnapshot> = _snapshot.asStateFlow()

    private val _preferences = MutableStateFlow(storage.loadPreferences())
    val preferences: StateFlow<WorkspacePreferences> = _preferences.asStateFlow()

    private val _storageErrors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val storageErrors: SharedFlow<String> = _storageErrors.asSharedFlow()

    private val stateLock = Any()
    private val persistence = WorkspacePersistenceCoordinator(
        storage = storage,
        typingDebounceMs = typingDebounceMs,
        scope = repositoryScope,
        persistOnBackground = persistOnBackground,
        initialRevision = _snapshot.value.revision,
        snapshotProvider = { _snapshot.value },
        preferencesProvider = { _preferences.value }
    )

    private var idCounter = 1000L
    private var maxKnownEntityEpochMs = computeMaxEpochMs(_snapshot.value)
    private var lastEpochMs = maxKnownEntityEpochMs

    init {
        storage.storageErrorHandler = { msg -> _storageErrors.tryEmit(msg) }
        val prefs = _preferences.value
        if (allowAutoHttpSync && prefs.useRemoteHttpSync && syncProvider is LocalLoopbackSyncProvider) {
            syncProvider = HttpSyncProvider(
                endpointUrl = prefs.syncEndpointUrl,
                authTokenProvider = { _preferences.value.syncAuthToken }
            )
        }
    }

    val isRemoteSyncEnabled: Boolean
        get() = (allowAutoHttpSync && _preferences.value.useRemoteHttpSync) || syncProvider !is LocalLoopbackSyncProvider

    private fun computeMaxEpochMs(snap: WorkspaceSnapshot): Long {
        val maxPage = snap.pages.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
        val maxSec = snap.sections.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
        val maxNb = snap.notebooks.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
        val maxTomb = snap.tombstones.maxOfOrNull { it.deletedAtEpochMs } ?: 0L
        return maxOf(maxPage, maxSec, maxNb, maxTomb)
    }

    private fun refreshMaxEpochLocked(snap: WorkspaceSnapshot) {
        val snapMax = computeMaxEpochMs(snap)
        if (snapMax > maxKnownEntityEpochMs) maxKnownEntityEpochMs = snapMax
        if (maxKnownEntityEpochMs > lastEpochMs) lastEpochMs = maxKnownEntityEpochMs
    }

    @OptIn(ExperimentalUuidApi::class)
    fun nextId(prefix: String): String {
        val (rev, seq) = platformSynchronized(stateLock) {
            idCounter += 1
            _snapshot.value.revision to idCounter
        }
        val shortUuid = Uuid.random().toHexString().take(8)
        return "$prefix-$rev-$seq-$shortUuid"
    }

    fun nextTimestampMs(): Long = platformSynchronized(stateLock) {
        val wallNow = currentEpochMillis()
        val candidate = maxOf(wallNow, maxKnownEntityEpochMs + 1L, lastEpochMs + 1L)
        lastEpochMs = candidate
        maxKnownEntityEpochMs = candidate
        candidate
    }

    val currentSyncEndpoint: String
        get() = platformSynchronized(stateLock) { syncProvider.endpointUrl }

    fun configureSyncProvider(provider: SyncProvider) {
        platformSynchronized(stateLock) { syncProvider = provider }
    }

    fun updatePreferences(transform: (WorkspacePreferences) -> WorkspacePreferences): WorkspacePreferences {
        val updated = platformSynchronized(stateLock) {
            val next = transform(_preferences.value)
            _preferences.value = next
            next
        }
        persistence.recordPreferencesChanged(updated)
        return updated
    }

    /**
     * Immediately commits any debounced or queued in-memory edits to SQLite / durable storage.
     */
    fun flushPendingWrites() = persistence.flush()

    private fun commitFullSnapshot(transform: (WorkspaceSnapshot) -> WorkspaceSnapshot) {
        if (!persistOnBackground) {
            persistence.flush()
        }
        val updated = platformSynchronized(stateLock) {
            isUntouchedStarterSeed = false
            val next = transform(_snapshot.value).let { it.copy(revision = it.revision + 1) }
            _snapshot.value = next
            refreshMaxEpochLocked(next)
            next
        }
        persistence.recordFullSnapshot(updated)
    }

    private fun commitPageMutation(
        pageId: String,
        debounced: Boolean = false,
        mutatePage: (NotePage) -> NotePage
    ) {
        val mutated = platformSynchronized(stateLock) {
            val current = _snapshot.value
            var mutatedTarget: NotePage? = null
            val updatedPages = current.pages.map { page ->
                if (page.id == pageId) mutatePage(page).also { mutatedTarget = it } else page
            }
            val target = mutatedTarget ?: return@platformSynchronized null
            isUntouchedStarterSeed = false
            val nextRev = current.revision + 1
            val cleanedTombstones = current.tombstones.filterNot {
                it.id == pageId && it.kind == EntityKind.PAGE
            }
            _snapshot.value = current.copy(pages = updatedPages, revision = nextRev, tombstones = cleanedTombstones)
            if (target.updatedAtEpochMs > maxKnownEntityEpochMs) {
                maxKnownEntityEpochMs = target.updatedAtEpochMs
            }
            target
        } ?: return
        persistence.recordPageWrite(mutated.id, debounced = debounced)
    }

    fun createNotebook(title: String, subtitle: String, accent: SectionAccentKey): Notebook {
        val now = nextTimestampMs()
        lateinit var created: Notebook
        commitFullSnapshot { current ->
            val (newNotebook, nextSnapshot) = WorkspaceHierarchyMutator.createNotebook(
                current = current,
                title = title,
                subtitle = subtitle,
                accent = accent,
                now = now,
                nextId = ::nextId
            )
            created = newNotebook
            nextSnapshot
        }
        return created
    }

    fun updateNotebook(notebookId: String, title: String, subtitle: String, accent: SectionAccentKey) {
        val now = nextTimestampMs()
        commitFullSnapshot { WorkspaceHierarchyMutator.updateNotebook(it, notebookId, title, subtitle, accent, now) }
    }

    fun reorderNotebook(notebookId: String, delta: Int) {
        val now = nextTimestampMs()
        commitFullSnapshot { WorkspaceHierarchyMutator.reorderNotebook(it, notebookId, delta, now) }
    }

    fun deleteNotebook(notebookId: String) {
        val now = nextTimestampMs()
        commitFullSnapshot { WorkspaceHierarchyMutator.deleteNotebook(it, notebookId, now) }
        if (_snapshot.value.notebooks.isEmpty()) {
            createNotebook("AllNotes Workspace", "Default notebook", SectionAccentKey.INDIGO)
        }
    }

    fun createSection(notebookId: String, title: String, accent: SectionAccentKey): NoteSection {
        val now = nextTimestampMs()
        lateinit var created: NoteSection
        commitFullSnapshot { current ->
            val (newSection, nextSnapshot) = WorkspaceHierarchyMutator.createSection(
                current = current,
                notebookId = notebookId,
                title = title,
                accent = accent,
                now = now,
                nextId = ::nextId
            )
            created = newSection
            nextSnapshot
        }
        return created
    }

    fun updateSection(
        sectionId: String,
        title: String,
        accent: SectionAccentKey,
        isPinned: Boolean? = null
    ) {
        val now = nextTimestampMs()
        commitFullSnapshot { WorkspaceHierarchyMutator.updateSection(it, sectionId, title, accent, isPinned, now) }
    }

    fun toggleSectionPin(sectionId: String) {
        val now = nextTimestampMs()
        commitFullSnapshot { WorkspaceHierarchyMutator.toggleSectionPin(it, sectionId, now) }
    }

    fun reorderSection(sectionId: String, delta: Int) {
        val now = nextTimestampMs()
        commitFullSnapshot { WorkspaceHierarchyMutator.reorderSection(it, sectionId, delta, now) }
    }

    fun deleteSection(sectionId: String) {
        val now = nextTimestampMs()
        commitFullSnapshot { WorkspaceHierarchyMutator.deleteSection(it, sectionId, now) }
    }

    fun createPage(sectionId: String, title: String = "Untitled Page", parentPageId: String? = null, allowBlankTitle: Boolean = false): NotePage {
        flushPendingWrites()
        val now = nextTimestampMs()
        val resolvedTitle = if (allowBlankTitle) title.trim() else title.trim().ifEmpty { "Untitled Page" }
        return insertConstructedPage(
            NotePage(
                id = nextId("page"),
                sectionId = sectionId,
                parentPageId = parentPageId,
                title = resolvedTitle,
                blocks = listOf(DocumentBlock.Paragraph(id = nextId("blk"), text = "")),
                updatedAtEpochMs = now
            )
        )
    }

    fun insertConstructedPage(newPage: NotePage): NotePage {
        if (!persistOnBackground) {
            persistence.flush()
        }
        platformSynchronized(stateLock) {
            isUntouchedStarterSeed = false
            val current = _snapshot.value
            val rev = current.revision + 1
            val cleanedTombstones = current.tombstones.filterNot {
                it.id == newPage.id && it.kind == EntityKind.PAGE
            }
            _snapshot.value = current.copy(
                pages = listOf(newPage) + current.pages.filterNot { it.id == newPage.id },
                revision = rev,
                tombstones = cleanedTombstones
            )
            if (newPage.updatedAtEpochMs > maxKnownEntityEpochMs) {
                maxKnownEntityEpochMs = newPage.updatedAtEpochMs
            }
        }
        persistence.recordPageWrite(newPage.id, debounced = false)
        return newPage
    }

    fun movePageToSection(pageId: String, targetSectionId: String) {
        flushPendingWrites()
        val now = nextTimestampMs()
        commitFullSnapshot { current ->
            if (current.sections.none { it.id == targetSectionId }) return@commitFullSnapshot current
            current.copy(
                pages = current.pages.map { page ->
                    when {
                        page.id == pageId -> page.copy(
                            sectionId = targetSectionId,
                            parentPageId = null,
                            updatedAtEpochMs = now
                        )
                        page.parentPageId == pageId -> page.copy(
                            parentPageId = null,
                            updatedAtEpochMs = now
                        )
                        else -> page
                    }
                }
            )
        }
    }

    fun deletePage(pageId: String) {
        if (!persistOnBackground) {
            persistence.flush()
        }
        val now = nextTimestampMs()
        val outcome = platformSynchronized(stateLock) {
            val current = _snapshot.value
            if (current.pages.none { it.id == pageId }) return@platformSynchronized null
            isUntouchedStarterSeed = false
            val nextRev = current.revision + 1
            val hasOrphanedChildren = current.pages.any { it.parentPageId == pageId }
            val updatedPages = current.pages
                .filterNot { it.id == pageId }
                .map { page ->
                    if (page.parentPageId == pageId) page.copy(parentPageId = null, updatedAtEpochMs = now) else page
                }
            val updatedTombstones = WorkspaceHierarchyMutator.upsertTombstones(
                current.tombstones,
                listOf(DeletedEntity(pageId, EntityKind.PAGE, now))
            )
            val updated = current.copy(pages = updatedPages, revision = nextRev, tombstones = updatedTombstones)
            _snapshot.value = updated
            updated to hasOrphanedChildren
        } ?: return

        val (updated, hasOrphanedChildren) = outcome
        persistence.recordPageDelete(
            pageId = pageId,
            deletedAtEpochMs = now,
            hasOrphanedChildren = hasOrphanedChildren,
            snapshot = updated
        )
    }

    fun updatePageParent(pageId: String, parentPageId: String?) {
        if (pageId == parentPageId) return
        val now = nextTimestampMs()
        commitPageMutation(pageId) { page ->
            page.copy(parentPageId = parentPageId, updatedAtEpochMs = now)
        }
    }

    fun togglePagePin(pageId: String) {
        val now = nextTimestampMs()
        commitPageMutation(pageId) { page ->
            page.copy(isPinned = !page.isPinned, updatedAtEpochMs = now)
        }
    }

    fun updatePageTitle(pageId: String, newTitle: String, debounced: Boolean = false) {
        val now = nextTimestampMs()
        commitPageMutation(pageId, debounced = debounced) { page ->
            page.copy(title = newTitle, updatedAtEpochMs = now)
        }
    }

    fun updatePageBlocks(pageId: String, blocks: List<DocumentBlock>, debounced: Boolean = false) {
        val now = nextTimestampMs()
        commitPageMutation(pageId, debounced = debounced) { page ->
            page.copy(blocks = blocks, updatedAtEpochMs = now)
        }
    }

    fun appendBlock(pageId: String, block: DocumentBlock) {
        val now = nextTimestampMs()
        commitPageMutation(pageId) { page ->
            page.copy(blocks = page.blocks + block, updatedAtEpochMs = now)
        }
    }

    fun toggleChecklistItem(pageId: String, blockId: String, itemId: String) {
        val now = nextTimestampMs()
        commitPageMutation(pageId) { page ->
            page.copy(
                updatedAtEpochMs = now,
                blocks = page.blocks.map { block ->
                    if (block.id == blockId && block is DocumentBlock.Checklist) {
                        block.copy(items = block.items.map { item -> if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item })
                    } else block
                }
            )
        }
    }

    fun addChecklistItem(pageId: String, blockId: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val newItem = ChecklistItem(id = nextId("chk"), text = cleanText, isChecked = false)
        val now = nextTimestampMs()
        commitPageMutation(pageId) { page ->
            page.copy(
                updatedAtEpochMs = now,
                blocks = page.blocks.map { block ->
                    if (block.id == blockId && block is DocumentBlock.Checklist) block.copy(items = block.items + newItem) else block
                }
            )
        }
    }

    suspend fun syncWithServer(): Result<WorkspaceSnapshot> {
        flushPendingWrites()
        val (provider, pushed, untouchedSeed) = platformSynchronized(stateLock) {
            Triple(syncProvider, _snapshot.value, isUntouchedStarterSeed)
        }
        if (untouchedSeed) {
            val pullOutcome = pullFromServerInternal()
            if (pullOutcome.isFailure) return pullOutcome.map { it.first }
            val (pulledSnapshot, needsPush) = pullOutcome.getOrThrow()
            if (!needsPush) {
                updatePreferences { it.copy(lastSyncedEpochMs = currentEpochMillis()) }
                return Result.success(pulledSnapshot)
            }
        }
        val latestPushed = platformSynchronized(stateLock) { _snapshot.value }
        val result = provider.pushSnapshot(if (untouchedSeed) latestPushed else pushed)
        return result.map { acknowledged ->
            val committed = platformSynchronized(stateLock) {
                isUntouchedStarterSeed = false
                val currentLive = _snapshot.value
                val resolved = if (currentLive.revision > latestPushed.revision) {
                    WorkspaceMerger.merge(acknowledged, currentLive)
                } else {
                    acknowledged
                }
                _snapshot.value = resolved
                refreshMaxEpochLocked(resolved)
                resolved
            }
            persistence.recordFullSnapshot(committed)
            updatePreferences { it.copy(lastSyncedEpochMs = currentEpochMillis()) }
            committed
        }
    }

    suspend fun pullFromServer(): Result<WorkspaceSnapshot> = pullFromServerInternal().map { it.first }

    internal suspend fun syncOnLaunch(pushLocalIfAhead: Boolean): Result<WorkspaceSnapshot> {
        val outcome = pullFromServerInternal()
        if (outcome.isFailure) return outcome.map { it.first }
        val (committed, needsPush) = outcome.getOrThrow()
        return if (pushLocalIfAhead && needsPush) {
            syncWithServer()
        } else {
            updatePreferences { it.copy(lastSyncedEpochMs = currentEpochMillis()) }
            Result.success(committed)
        }
    }

    private suspend fun pullFromServerInternal(): Result<Pair<WorkspaceSnapshot, Boolean>> {
        flushPendingWrites()
        val provider = platformSynchronized(stateLock) { syncProvider }
        val result = provider.pullSnapshot()
        return result.map { remote ->
            var didMutate = false
            var needsPush = false
            val committed = platformSynchronized(stateLock) {
                val current = _snapshot.value
                val wasUntouchedSeed = isUntouchedStarterSeed || current == SampleWorkspaceFactory.createStarterSnapshot()
                val remoteHasContent = remote.notebooks.isNotEmpty() || remote.tombstones.isNotEmpty()
                when {
                    wasUntouchedSeed && remoteHasContent -> {
                        didMutate = true
                        isUntouchedStarterSeed = false
                        _snapshot.value = remote
                        refreshMaxEpochLocked(remote)
                        remote
                    }
                    remote.revision <= current.revision && WorkspaceMerger.isSecondSubsumedByFirst(current, remote) -> {
                        isUntouchedStarterSeed = false
                        needsPush = wasUntouchedSeed || !WorkspaceMerger.isSecondSubsumedByFirst(remote, current)
                        current
                    }
                    remote.revision > current.revision && WorkspaceMerger.isSecondSubsumedByFirst(remote, current) -> {
                        didMutate = true
                        isUntouchedStarterSeed = false
                        _snapshot.value = remote
                        refreshMaxEpochLocked(remote)
                        remote
                    }
                    else -> {
                        didMutate = true
                        isUntouchedStarterSeed = false
                        val merged = WorkspaceMerger.merge(current, remote)
                        _snapshot.value = merged
                        refreshMaxEpochLocked(merged)
                        needsPush = !WorkspaceMerger.isSecondSubsumedByFirst(remote, merged)
                        merged
                    }
                }
            }
            if (didMutate) {
                persistence.recordFullSnapshot(committed)
            }
            committed to needsPush
        }
    }
}
