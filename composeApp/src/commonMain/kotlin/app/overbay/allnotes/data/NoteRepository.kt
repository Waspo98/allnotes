package app.overbay.allnotes.data

import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoteRepository(
    private val storage: NoteStorageBackend = PlatformNoteStore(),
    private val syncProvider: SyncProvider = LocalLoopbackSyncProvider()
) {
    private val _snapshot = MutableStateFlow(
        storage.loadSnapshot() ?: SampleWorkspaceFactory.createStarterSnapshot().also {
            storage.saveSnapshot(it)
        }
    )
    val snapshot: StateFlow<WorkspaceSnapshot> = _snapshot.asStateFlow()

    private var idCounter = 1000L

    private fun nextId(prefix: String): String {
        idCounter += 1
        return "$prefix-${_snapshot.value.revision}-$idCounter"
    }

    private fun commit(transform: (WorkspaceSnapshot) -> WorkspaceSnapshot) {
        val updated = transform(_snapshot.value).let {
            it.copy(revision = it.revision + 1)
        }
        _snapshot.value = updated
        storage.saveSnapshot(updated)
    }

    fun createNotebook(title: String, subtitle: String, accent: SectionAccentKey): Notebook {
        val cleanTitle = title.trim().ifEmpty { "Untitled Notebook" }
        val newNotebook = Notebook(
            id = nextId("nb"),
            title = cleanTitle,
            subtitle = subtitle.trim(),
            accent = accent,
            sortOrder = _snapshot.value.notebooks.size
        )
        val defaultSection = NoteSection(
            id = nextId("sec"),
            notebookId = newNotebook.id,
            title = "General Notes",
            accent = accent,
            sortOrder = 0
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
            )
        )
        commit { current ->
            current.copy(
                notebooks = current.notebooks + newNotebook,
                sections = current.sections + defaultSection,
                pages = current.pages + starterPage
            )
        }
        return newNotebook
    }

    fun createSection(notebookId: String, title: String, accent: SectionAccentKey): NoteSection {
        val cleanTitle = title.trim().ifEmpty { "New Section" }
        val newSection = NoteSection(
            id = nextId("sec"),
            notebookId = notebookId,
            title = cleanTitle,
            accent = accent,
            sortOrder = _snapshot.value.sections.count { it.notebookId == notebookId }
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
            )
        )
        commit { current ->
            current.copy(
                sections = current.sections + newSection,
                pages = current.pages + firstPage
            )
        }
        return newSection
    }

    fun createPage(sectionId: String, title: String = "Untitled Page"): NotePage {
        val newPage = NotePage(
            id = nextId("page"),
            sectionId = sectionId,
            title = title.trim().ifEmpty { "Untitled Page" },
            blocks = listOf(
                DocumentBlock.Paragraph(
                    id = nextId("blk"),
                    text = ""
                )
            )
        )
        commit { current ->
            current.copy(pages = listOf(newPage) + current.pages)
        }
        return newPage
    }

    fun deletePage(pageId: String) {
        commit { current ->
            current.copy(pages = current.pages.filterNot { it.id == pageId })
        }
    }

    fun togglePagePin(pageId: String) {
        commit { current ->
            current.copy(
                pages = current.pages.map { page ->
                    if (page.id == pageId) page.copy(isPinned = !page.isPinned) else page
                }
            )
        }
    }

    fun updatePageTitle(pageId: String, newTitle: String) {
        commit { current ->
            current.copy(
                pages = current.pages.map { page ->
                    if (page.id == pageId) page.copy(title = newTitle) else page
                }
            )
        }
    }

    fun updatePageBlocks(pageId: String, blocks: List<DocumentBlock>) {
        commit { current ->
            current.copy(
                pages = current.pages.map { page ->
                    if (page.id == pageId) page.copy(blocks = blocks) else page
                }
            )
        }
    }

    fun appendBlock(pageId: String, block: DocumentBlock) {
        commit { current ->
            current.copy(
                pages = current.pages.map { page ->
                    if (page.id == pageId) page.copy(blocks = page.blocks + block) else page
                }
            )
        }
    }

    fun toggleChecklistItem(pageId: String, blockId: String, itemId: String) {
        commit { current ->
            current.copy(
                pages = current.pages.map { page ->
                    if (page.id != pageId) return@map page
                    page.copy(
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
            )
        }
    }

    fun addChecklistItem(pageId: String, blockId: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val newItem = ChecklistItem(id = nextId("chk"), text = cleanText, isChecked = false)
        commit { current ->
            current.copy(
                pages = current.pages.map { page ->
                    if (page.id != pageId) return@map page
                    page.copy(
                        blocks = page.blocks.map { block ->
                            if (block.id == blockId && block is DocumentBlock.Checklist) {
                                block.copy(items = block.items + newItem)
                            } else {
                                block
                            }
                        }
                    )
                }
            )
        }
    }

    suspend fun syncWithServer(): Result<WorkspaceSnapshot> {
        return syncProvider.pushSnapshot(_snapshot.value)
    }
}
