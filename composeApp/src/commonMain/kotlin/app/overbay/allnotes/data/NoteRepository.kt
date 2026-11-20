package app.overbay.allnotes.data

import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DeletedEntity
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.EntityKind
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.NoteSectionDisplayComparator
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

    private fun upsertTombstones(existing: List<DeletedEntity>, additions: List<DeletedEntity>): List<DeletedEntity> {
        if (additions.isEmpty()) return existing
        val map = LinkedHashMap<Pair<String, EntityKind>, DeletedEntity>(existing.size + additions.size)
        for (item in existing + additions) {
            val key = item.id to item.kind
            val prev = map[key]
            if (prev == null || item.deletedAtEpochMs >= prev.deletedAtEpochMs) map[key] = item
        }
        return map.values.toList()
    }

    fun createNotebook(title: String, subtitle: String, accent: SectionAccentKey): Notebook {
        val cleanTitle = title.trim().ifEmpty { "Untitled Notebook" }
        val now = nextTimestampMs()
        val newNotebook = Notebook(
            id = nextId("nb"),
            title = cleanTitle,
            subtitle = subtitle.trim(),
            accent = accent,
            sortOrder = _snapshot.value.notebooks.size,
            updatedAtEpochMs = now
        )
        val defaultSection = NoteSection(
            id = nextId("sec"),
            notebookId = newNotebook.id,
            title = "General Notes",
            accent = accent,
            sortOrder = 0,
            updatedAtEpochMs = now
        )
        val starterPage = NotePage(
            id = nextId("page"),
            sectionId = defaultSection.id,
            title = "Welcome to $cleanTitle",
            blocks = listOf(
                DocumentBlock.Heading(id = nextId("blk"), text = cleanTitle, level = 2),
                DocumentBlock.Paragraph(id = nextId("blk"), text = "Start typing or add structured blocks below.")
            ),
            updatedAtEpochMs = now
        )
        commitFullSnapshot { current ->
            current.copy(
                notebooks = current.notebooks + newNotebook,
                sections = current.sections + defaultSection,
                pages = current.pages + starterPage
            )
        }
        return newNotebook
    }

    fun updateNotebook(notebookId: String, title: String, subtitle: String, accent: SectionAccentKey) {
        val cleanTitle = title.trim().ifEmpty { "Untitled Notebook" }
        val now = nextTimestampMs()
        commitFullSnapshot { current ->
            current.copy(
                notebooks = current.notebooks.map { nb ->
                    if (nb.id == notebookId) {
                        nb.copy(
                            title = cleanTitle,
                            subtitle = subtitle.trim(),
                            accent = accent,
                            updatedAtEpochMs = now
                        )
                    } else nb
                }
            )
        }
    }

    fun reorderNotebook(notebookId: String, delta: Int) {
        val now = nextTimestampMs()
        commitFullSnapshot { current ->
            val ordered = current.notebooks.sortedBy { it.sortOrder }.toMutableList()
            val idx = ordered.indexOfFirst { it.id == notebookId }
            if (idx < 0) return@commitFullSnapshot current
            val targetIdx = (idx + delta).coerceIn(0, ordered.lastIndex)
            if (targetIdx == idx) return@commitFullSnapshot current
            val moved = ordered.removeAt(idx)
            ordered.add(targetIdx, moved)
            val reindexed = ordered.mapIndexed { i, nb ->
                if (nb.sortOrder != i || nb.id == notebookId) {
                    nb.copy(sortOrder = i, updatedAtEpochMs = now)
                } else nb
            }
            current.copy(notebooks = reindexed)
        }
    }

    fun deleteNotebook(notebookId: String) {
        val now = nextTimestampMs()
        commitFullSnapshot { current ->
            val remainingNotebooks = current.notebooks.filterNot { it.id == notebookId }
            val removedSections = current.sections.filter { it.notebookId == notebookId }
            val removedSectionIds = removedSections.map { it.id }.toSet()
            val removedPages = current.pages.filter { it.sectionId in removedSectionIds }
            val remainingSections = current.sections.filterNot { it.notebookId == notebookId }
            val remainingPages = current.pages.filterNot { it.sectionId in removedSectionIds }

            val newTombstones = buildList {
                if (current.notebooks.any { it.id == notebookId }) {
                    add(DeletedEntity(notebookId, EntityKind.NOTEBOOK, now))
                }
                removedSections.forEach { add(DeletedEntity(it.id, EntityKind.SECTION, now)) }
                removedPages.forEach { add(DeletedEntity(it.id, EntityKind.PAGE, now)) }
            }

            current.copy(
                notebooks = remainingNotebooks,
                sections = remainingSections,
                pages = remainingPages,
                tombstones = upsertTombstones(current.tombstones, newTombstones)
            )
        }
        if (_snapshot.value.notebooks.isEmpty()) {
            createNotebook("AllNotes Workspace", "Default notebook", SectionAccentKey.INDIGO)
        }
    }

    fun createSection(notebookId: String, title: String, accent: SectionAccentKey): NoteSection {
        val cleanTitle = title.trim().ifEmpty { "New Section" }
        val now = nextTimestampMs()
        val newSection = NoteSection(
            id = nextId("sec"),
            notebookId = notebookId,
            title = cleanTitle,
            accent = accent,
            sortOrder = _snapshot.value.sections.count { it.notebookId == notebookId },
            updatedAtEpochMs = now
        )
        val firstPage = NotePage(
            id = nextId("page"),
            sectionId = newSection.id,
            title = "Untitled Page",
            blocks = listOf(DocumentBlock.Paragraph(id = nextId("blk"), text = "")),
            updatedAtEpochMs = now
        )
        commitFullSnapshot { current ->
            current.copy(
                sections = current.sections + newSection,
                pages = current.pages + firstPage
            )
        }
        return newSection
    }

    fun updateSection(
        sectionId: String,
        title: String,
        accent: SectionAccentKey,
        isPinned: Boolean? = null
    ) {
        val cleanTitle = title.trim().ifEmpty { "Untitled Section" }
        val now = nextTimestampMs()
        commitFullSnapshot { current ->
            val updated = current.sections.map { sec ->
                if (sec.id == sectionId) {
                    sec.copy(
                        title = cleanTitle,
                        accent = accent,
                        isPinned = isPinned ?: sec.isPinned,
                        updatedAtEpochMs = now
                    )
                } else sec
            }
            val target = updated.firstOrNull { it.id == sectionId } ?: return@commitFullSnapshot current
            val reindexed = reindexNotebookSections(updated, target.notebookId, now, touchedId = sectionId)
            current.copy(sections = reindexed)
        }
    }

    fun toggleSectionPin(sectionId: String) {
        mutateNotebookSectionPartitions(sectionId) { target, pinned, unpinned, now ->
            val updated = target.copy(isPinned = !target.isPinned, updatedAtEpochMs = now)
            pinned.removeAll { it.id == sectionId }
            unpinned.removeAll { it.id == sectionId }
            if (updated.isPinned) pinned.add(updated) else unpinned.add(0, updated)
            true
        }
    }

    fun reorderSection(sectionId: String, delta: Int) {
        mutateNotebookSectionPartitions(sectionId) { target, pinned, unpinned, _ ->
            val partition = if (target.isPinned) pinned else unpinned
            val idx = partition.indexOfFirst { it.id == sectionId }
            val targetIdx = if (idx >= 0) (idx + delta).coerceIn(0, partition.lastIndex) else -1
            if (idx < 0 || targetIdx == idx) return@mutateNotebookSectionPartitions false
            partition.add(targetIdx, partition.removeAt(idx))
            true
        }
    }

    private inline fun mutateNotebookSectionPartitions(
        sectionId: String,
        crossinline transform: (
            target: NoteSection,
            pinned: MutableList<NoteSection>,
            unpinned: MutableList<NoteSection>,
            now: Long
        ) -> Boolean
    ) {
        val now = nextTimestampMs()
        commitFullSnapshot { current ->
            val target = current.sections.firstOrNull { it.id == sectionId } ?: return@commitFullSnapshot current
            val siblings = current.sections.filter { it.notebookId == target.notebookId }
            val pinned = siblings.filter { it.isPinned }.sortedWith(NoteSectionDisplayComparator).toMutableList()
            val unpinned = siblings.filter { !it.isPinned }.sortedWith(NoteSectionDisplayComparator).toMutableList()
            if (!transform(target, pinned, unpinned, now)) return@commitFullSnapshot current
            val reindexedById = (pinned + unpinned).mapIndexed { i, sec ->
                val changed = sec.id == sectionId || sec.sortOrder != i
                sec.id to if (changed) sec.copy(sortOrder = i, updatedAtEpochMs = now) else sec
            }.toMap()
            current.copy(sections = current.sections.map { reindexedById[it.id] ?: it })
        }
    }

    private fun reindexNotebookSections(
        sections: List<NoteSection>,
        notebookId: String,
        now: Long,
        touchedId: String
    ): List<NoteSection> {
        val ordered = sections.filter { it.notebookId == notebookId }.sortedWith(NoteSectionDisplayComparator)
        val reindexedById = ordered.mapIndexed { i, sec ->
            sec.id to if (sec.sortOrder != i || sec.id == touchedId) {
                sec.copy(sortOrder = i, updatedAtEpochMs = now)
            } else sec
        }.toMap()
        return sections.map { reindexedById[it.id] ?: it }
    }

    fun deleteSection(sectionId: String) {
        val now = nextTimestampMs()
        commitFullSnapshot { current ->
            val removedPages = current.pages.filter { it.sectionId == sectionId }
            val newTombstones = buildList {
                if (current.sections.any { it.id == sectionId }) {
                    add(DeletedEntity(sectionId, EntityKind.SECTION, now))
                }
                removedPages.forEach { add(DeletedEntity(it.id, EntityKind.PAGE, now)) }
            }
            current.copy(
                sections = current.sections.filterNot { it.id == sectionId },
                pages = current.pages.filterNot { it.sectionId == sectionId },
                tombstones = upsertTombstones(current.tombstones, newTombstones)
            )
        }
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
            val updatedTombstones = upsertTombstones(
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
                val wasUntouchedSeed = isUntouchedStarterSeed
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
