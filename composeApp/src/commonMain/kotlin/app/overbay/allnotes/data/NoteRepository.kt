package app.overbay.allnotes.data

import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoteRepository(
    private val storage: NoteStorageBackend = PlatformNoteStore(),
    private var syncProvider: SyncProvider = LocalLoopbackSyncProvider()
) {
    private val _snapshot = MutableStateFlow(
        storage.loadSnapshot() ?: SampleWorkspaceFactory.createStarterSnapshot().also {
            storage.saveSnapshot(it)
        }
    )
    val snapshot: StateFlow<WorkspaceSnapshot> = _snapshot.asStateFlow()

    private val monotonicStart = TimeSource.Monotonic.markNow()
    private var idCounter = 1000L
    private var lastEpochMs = 1762425000000L

    @OptIn(ExperimentalUuidApi::class)
    fun nextId(prefix: String): String {
        idCounter += 1
        val shortUuid = Uuid.random().toHexString().take(8)
        return "$prefix-${_snapshot.value.revision}-$idCounter-$shortUuid"
    }

    fun nextTimestampMs(): Long {
        val currentMax = _snapshot.value.pages.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
        val elapsed = monotonicStart.elapsedNow().inWholeMilliseconds
        val candidate = maxOf(1762425000000L + elapsed, currentMax + 1L, lastEpochMs + 1L)
        lastEpochMs = candidate
        return candidate
    }

    val currentSyncEndpoint: String
        get() = syncProvider.endpointUrl

    fun configureSyncProvider(provider: SyncProvider) {
        syncProvider = provider
    }

    private fun commitFullSnapshot(transform: (WorkspaceSnapshot) -> WorkspaceSnapshot) {
        val updated = transform(_snapshot.value).let {
            it.copy(revision = it.revision + 1)
        }
        _snapshot.value = updated
        storage.saveSnapshot(updated)
    }

    private fun commitPageMutation(pageId: String, mutatePage: (NotePage) -> NotePage) {
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
        val updated = current.copy(pages = updatedPages, revision = nextRev)
        _snapshot.value = updated
        storage.savePage(target, nextRev)
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

    fun deleteNotebook(notebookId: String) {
        commitFullSnapshot { current ->
            val remainingNotebooks = current.notebooks.filterNot { it.id == notebookId }
            val removedSectionIds = current.sections.filter { it.notebookId == notebookId }.map { it.id }.toSet()
            val remainingSections = current.sections.filterNot { it.notebookId == notebookId }
            val remainingPages = current.pages.filterNot { it.sectionId in removedSectionIds }
            current.copy(
                notebooks = remainingNotebooks,
                sections = remainingSections,
                pages = remainingPages
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

    fun deleteSection(sectionId: String) {
        commitFullSnapshot { current ->
            current.copy(
                sections = current.sections.filterNot { it.id == sectionId },
                pages = current.pages.filterNot { it.sectionId == sectionId }
            )
        }
    }

    fun createPage(sectionId: String, title: String = "Untitled Page", parentPageId: String? = null): NotePage {
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
        val current = _snapshot.value
        val nextRev = current.revision + 1
        val updated = current.copy(pages = listOf(newPage) + current.pages, revision = nextRev)
        _snapshot.value = updated
        storage.savePage(newPage, nextRev)
        return newPage
    }

    fun deletePage(pageId: String) {
        val current = _snapshot.value
        if (current.pages.none { it.id == pageId }) return
        val nextRev = current.revision + 1
        val updatedPages = current.pages
            .filterNot { it.id == pageId }
            .map { page ->
                if (page.parentPageId == pageId) page.copy(parentPageId = null) else page
            }
        val updated = current.copy(pages = updatedPages, revision = nextRev)
        _snapshot.value = updated
        storage.deletePage(pageId, nextRev)
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

    fun updatePageTitle(pageId: String, newTitle: String) {
        val now = nextTimestampMs()
        commitPageMutation(pageId) { page ->
            page.copy(title = newTitle, updatedAtEpochMs = now)
        }
    }

    fun updatePageBlocks(pageId: String, blocks: List<DocumentBlock>) {
        val now = nextTimestampMs()
        commitPageMutation(pageId) { page ->
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
        val result = syncProvider.pushSnapshot(_snapshot.value)
        result.onSuccess { acknowledged ->
            _snapshot.value = acknowledged
            storage.saveSnapshot(acknowledged)
        }
        return result
    }

    suspend fun pullFromServer(): Result<WorkspaceSnapshot> {
        val result = syncProvider.pullSnapshot()
        result.onSuccess { remote ->
            if (remote.revision > _snapshot.value.revision) {
                _snapshot.value = remote
                storage.saveSnapshot(remote)
            }
        }
        return result
    }
}
