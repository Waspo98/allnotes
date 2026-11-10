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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoteRepository(
    private val storage: NoteStorageBackend = PlatformNoteStore(),
    private var syncProvider: SyncProvider = LocalLoopbackSyncProvider(),
    private val typingDebounceMs: Long = 0L,
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _snapshot = MutableStateFlow(
        storage.loadSnapshot() ?: SampleWorkspaceFactory.createStarterSnapshot().also {
            storage.saveSnapshot(it)
        }
    )
    val snapshot: StateFlow<WorkspaceSnapshot> = _snapshot.asStateFlow()

    private val _preferences = MutableStateFlow(storage.loadPreferences())
    val preferences: StateFlow<WorkspacePreferences> = _preferences.asStateFlow()

    private val pendingLock = Any()
    private val dirtyPageIds = LinkedHashSet<String>()
    private val pendingDebounceJobs = LinkedHashMap<String, Job>()

    private var idCounter = 1000L
    private var lastEpochMs = 0L

    init {
        val prefs = _preferences.value
        if (prefs.useRemoteHttpSync && syncProvider is LocalLoopbackSyncProvider) {
            syncProvider = HttpSyncProvider(
                endpointUrl = prefs.syncEndpointUrl,
                authTokenProvider = { _preferences.value.syncAuthToken }
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun nextId(prefix: String): String {
        idCounter += 1
        val shortUuid = Uuid.random().toHexString().take(8)
        return "$prefix-${_snapshot.value.revision}-$idCounter-$shortUuid"
    }

    fun nextTimestampMs(): Long {
        val snap = _snapshot.value
        val maxPage = snap.pages.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
        val maxSec = snap.sections.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
        val maxNb = snap.notebooks.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
        val maxTomb = snap.tombstones.maxOfOrNull { it.deletedAtEpochMs } ?: 0L
        val currentMax = maxOf(maxPage, maxSec, maxNb, maxTomb)
        val wallNow = currentEpochMillis()
        val candidate = maxOf(wallNow, currentMax + 1L, lastEpochMs + 1L)
        lastEpochMs = candidate
        return candidate
    }

    val currentSyncEndpoint: String
        get() = syncProvider.endpointUrl

    fun configureSyncProvider(provider: SyncProvider) {
        syncProvider = provider
    }

    fun updatePreferences(transform: (WorkspacePreferences) -> WorkspacePreferences): WorkspacePreferences {
        val updated = transform(_preferences.value)
        _preferences.value = updated
        storage.savePreferences(updated)
        return updated
    }

    /**
     * Immediately commits any debounced in-memory page edits to SQLite / durable storage.
     */
    fun flushPendingWrites() {
        val pagesToFlush = platformSynchronized(pendingLock) {
            pendingDebounceJobs.values.forEach { it.cancel() }
            pendingDebounceJobs.clear()
            dirtyPageIds.toList().also { dirtyPageIds.clear() }
        }
        if (pagesToFlush.isEmpty()) return
        val current = _snapshot.value
        for (pageId in pagesToFlush) {
            val page = current.pages.firstOrNull { it.id == pageId }
            if (page != null) {
                storage.savePage(page, current.revision)
            }
        }
    }

    private fun commitFullSnapshot(transform: (WorkspaceSnapshot) -> WorkspaceSnapshot) {
        flushPendingWrites()
        val updated = transform(_snapshot.value).let {
            it.copy(revision = it.revision + 1)
        }
        _snapshot.value = updated
        storage.saveSnapshot(updated)
    }

    private fun commitPageMutation(
        pageId: String,
        debounced: Boolean = false,
        mutatePage: (NotePage) -> NotePage
    ) {
        val current = _snapshot.value
        var mutatedTarget: NotePage? = null
        val updatedPages = current.pages.map { page ->
            if (page.id == pageId) {
                mutatePage(page).also { mutatedTarget = it }
            } else {
                page
            }
        }
        val target = mutatedTarget ?: return
        val nextRev = current.revision + 1
        val cleanedTombstones = current.tombstones.filterNot {
            it.id == pageId && it.kind == EntityKind.PAGE
        }
        val updated = current.copy(pages = updatedPages, revision = nextRev, tombstones = cleanedTombstones)
        _snapshot.value = updated

        if (debounced && typingDebounceMs > 0L) {
            platformSynchronized(pendingLock) {
                dirtyPageIds.add(pageId)
                pendingDebounceJobs.remove(pageId)?.cancel()
                pendingDebounceJobs[pageId] = repositoryScope.launch {
                    delay(typingDebounceMs)
                    val shouldWrite = platformSynchronized(pendingLock) {
                        pendingDebounceJobs.remove(pageId)
                        dirtyPageIds.remove(pageId)
                    }
                    if (shouldWrite) {
                        val latestSnap = _snapshot.value
                        val latestPage = latestSnap.pages.firstOrNull { it.id == pageId }
                        if (latestPage != null) {
                            storage.savePage(latestPage, latestSnap.revision)
                        }
                    }
                }
            }
        } else {
            platformSynchronized(pendingLock) {
                pendingDebounceJobs.remove(pageId)?.cancel()
                dirtyPageIds.remove(pageId)
            }
            storage.savePage(target, nextRev)
        }
    }

    private fun upsertTombstones(
        existing: List<DeletedEntity>,
        additions: List<DeletedEntity>
    ): List<DeletedEntity> {
        if (additions.isEmpty()) return existing
        val map = LinkedHashMap<Pair<String, EntityKind>, DeletedEntity>()
        for (item in existing + additions) {
            val key = item.id to item.kind
            val prev = map[key]
            if (prev == null || item.deletedAtEpochMs >= prev.deletedAtEpochMs) {
                map[key] = item
            }
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
                DocumentBlock.Paragraph(
                    id = nextId("blk"),
                    text = "Start typing or add structured blocks below."
                )
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
            blocks = listOf(
                DocumentBlock.Paragraph(
                    id = nextId("blk"),
                    text = ""
                )
            ),
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

    fun updateSection(sectionId: String, title: String, accent: SectionAccentKey) {
        val cleanTitle = title.trim().ifEmpty { "Untitled Section" }
        val now = nextTimestampMs()
        commitFullSnapshot { current ->
            current.copy(
                sections = current.sections.map { sec ->
                    if (sec.id == sectionId) {
                        sec.copy(title = cleanTitle, accent = accent, updatedAtEpochMs = now)
                    } else sec
                }
            )
        }
    }

    fun reorderSection(sectionId: String, delta: Int) {
        val now = nextTimestampMs()
        commitFullSnapshot { current ->
            val targetSec = current.sections.firstOrNull { it.id == sectionId } ?: return@commitFullSnapshot current
            val siblings = current.sections.filter { it.notebookId == targetSec.notebookId }
                .sortedBy { it.sortOrder }
                .toMutableList()
            val idx = siblings.indexOfFirst { it.id == sectionId }
            if (idx < 0) return@commitFullSnapshot current
            val targetIdx = (idx + delta).coerceIn(0, siblings.lastIndex)
            if (targetIdx == idx) return@commitFullSnapshot current
            val moved = siblings.removeAt(idx)
            siblings.add(targetIdx, moved)
            val reindexedById = siblings.mapIndexed { i, sec ->
                sec.id to sec.copy(sortOrder = i, updatedAtEpochMs = now)
            }.toMap()
            current.copy(sections = current.sections.map { reindexedById[it.id] ?: it })
        }
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

    fun createPage(sectionId: String, title: String = "Untitled Page", parentPageId: String? = null): NotePage {
        flushPendingWrites()
        val now = nextTimestampMs()
        val newPage = NotePage(
            id = nextId("page"),
            sectionId = sectionId,
            parentPageId = parentPageId,
            title = title.trim().ifEmpty { "Untitled Page" },
            blocks = listOf(
                DocumentBlock.Paragraph(
                    id = nextId("blk"),
                    text = ""
                )
            ),
            updatedAtEpochMs = now
        )
        return insertConstructedPage(newPage)
    }

    fun insertConstructedPage(newPage: NotePage): NotePage {
        flushPendingWrites()
        val current = _snapshot.value
        val nextRev = current.revision + 1
        val cleanedTombstones = current.tombstones.filterNot {
            it.id == newPage.id && it.kind == EntityKind.PAGE
        }
        val updated = current.copy(
            pages = listOf(newPage) + current.pages.filterNot { it.id == newPage.id },
            revision = nextRev,
            tombstones = cleanedTombstones
        )
        _snapshot.value = updated
        storage.savePage(newPage, nextRev)
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
        flushPendingWrites()
        val current = _snapshot.value
        if (current.pages.none { it.id == pageId }) return
        val now = nextTimestampMs()
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
        if (hasOrphanedChildren) {
            storage.saveSnapshot(updated)
        } else {
            storage.deletePage(pageId, nextRev, now)
        }
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
                        block.copy(
                            items = block.items.map { item ->
                                if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
                            }
                        )
                    } else {
                        block
                    }
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
                    if (block.id == blockId && block is DocumentBlock.Checklist) {
                        block.copy(items = block.items + newItem)
                    } else {
                        block
                    }
                }
            )
        }
    }

    suspend fun syncWithServer(): Result<WorkspaceSnapshot> {
        flushPendingWrites()
        val pushed = _snapshot.value
        val result = syncProvider.pushSnapshot(pushed)
        return result.map { acknowledged ->
            val currentLive = _snapshot.value
            val committed = if (currentLive.revision > pushed.revision) {
                WorkspaceMerger.merge(acknowledged, currentLive)
            } else {
                acknowledged
            }
            _snapshot.value = committed
            storage.saveSnapshot(committed)
            updatePreferences { it.copy(lastSyncedEpochMs = currentEpochMillis()) }
            committed
        }
    }

    suspend fun pullFromServer(): Result<WorkspaceSnapshot> {
        flushPendingWrites()
        val result = syncProvider.pullSnapshot()
        return result.map { remote ->
            val current = _snapshot.value
            val committed = when {
                remote.revision <= current.revision && WorkspaceMerger.isSecondSubsumedByFirst(current, remote) -> {
                    current
                }
                remote.revision > current.revision && WorkspaceMerger.isSecondSubsumedByFirst(remote, current) -> {
                    _snapshot.value = remote
                    storage.saveSnapshot(remote)
                    remote
                }
                else -> {
                    val merged = WorkspaceMerger.merge(current, remote)
                    _snapshot.value = merged
                    storage.saveSnapshot(merged)
                    merged
                }
            }
            committed
        }
    }
}
