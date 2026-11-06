package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.SyncStatus
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveDialog {
    NONE,
    NEW_NOTEBOOK,
    NEW_SECTION,
    NEW_PAGE
}

data class WorkspaceUiState(
    val notebooks: List<Notebook> = emptyList(),
    val sections: List<NoteSection> = emptyList(),
    val pages: List<NotePage> = emptyList(),
    val selectedNotebookId: String = "",
    val selectedSectionId: String = "",
    val selectedPageId: String = "",
    val searchQuery: String = "",
    val isSidebarOpen: Boolean = true,
    val isMobileHierarchySheetOpen: Boolean = false,
    val activeDialog: ActiveDialog = ActiveDialog.NONE,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_READY,
    val revision: Long = 1L
) {
    val activeNotebook: Notebook?
        get() = notebooks.firstOrNull { it.id == selectedNotebookId } ?: notebooks.firstOrNull()

    val sectionsForNotebook: List<NoteSection>
        get() {
            val nbId = activeNotebook?.id ?: return emptyList()
            return sections.filter { it.notebookId == nbId }.sortedBy { it.sortOrder }
        }

    val activeSection: NoteSection?
        get() = sectionsForNotebook.firstOrNull { it.id == selectedSectionId }
            ?: sectionsForNotebook.firstOrNull()

    val pagesForSection: List<NotePage>
        get() {
            val secId = activeSection?.id ?: return emptyList()
            val q = searchQuery.trim().lowercase()
            return pages
                .filter { it.sectionId == secId }
                .filter { page ->
                    if (q.isEmpty()) true
                    else page.title.lowercase().contains(q) ||
                        page.blocks.any { blockContainsQuery(it, q) }
                }
                .sortedWith(compareByDescending<NotePage> { it.isPinned }.thenByDescending { it.updatedAtEpochMs })
        }

    val activePage: NotePage?
        get() = pagesForSection.firstOrNull { it.id == selectedPageId }
            ?: pagesForSection.firstOrNull()
}

private fun blockContainsQuery(block: DocumentBlock, query: String): Boolean = when (block) {
    is DocumentBlock.Heading -> block.text.lowercase().contains(query)
    is DocumentBlock.Paragraph -> block.text.lowercase().contains(query)
    is DocumentBlock.Checklist -> block.title.lowercase().contains(query) ||
        block.items.any { it.text.lowercase().contains(query) }
    is DocumentBlock.BulletList -> block.items.any { it.lowercase().contains(query) }
    is DocumentBlock.Table -> block.headers.any { it.lowercase().contains(query) } ||
        block.rows.any { row -> row.any { it.lowercase().contains(query) } }
    is DocumentBlock.Callout -> block.title.lowercase().contains(query) ||
        block.text.lowercase().contains(query)
    is DocumentBlock.CodeSnippet -> block.code.lowercase().contains(query)
}

class WorkspaceViewModel(
    private val repository: NoteRepository = NoteRepository(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow( buildInitialState() )
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var blockSeq = 5000L

    init {
        scope.launch {
            repository.snapshot.collect { snap ->
                _uiState.update { current ->
                    val nbId = current.selectedNotebookId.takeIf { id -> snap.notebooks.any { it.id == id } }
                        ?: snap.notebooks.firstOrNull()?.id.orEmpty()
                    val secId = current.selectedSectionId.takeIf { id ->
                        snap.sections.any { it.id == id && it.notebookId == nbId }
                    } ?: snap.sections.firstOrNull { it.notebookId == nbId }?.id.orEmpty()
                    val pageId = current.selectedPageId.takeIf { id ->
                        snap.pages.any { it.id == id && it.sectionId == secId }
                    } ?: snap.pages.firstOrNull { it.sectionId == secId }?.id.orEmpty()

                    current.copy(
                        notebooks = snap.notebooks,
                        sections = snap.sections,
                        pages = snap.pages,
                        selectedNotebookId = nbId,
                        selectedSectionId = secId,
                        selectedPageId = pageId,
                        revision = snap.revision
                    )
                }
            }
        }
    }

    private fun buildInitialState(): WorkspaceUiState {
        val snap = repository.snapshot.value
        val nbId = snap.notebooks.firstOrNull()?.id.orEmpty()
        val secId = snap.sections.firstOrNull { it.notebookId == nbId }?.id.orEmpty()
        val pageId = snap.pages.firstOrNull { it.sectionId == secId }?.id.orEmpty()
        return WorkspaceUiState(
            notebooks = snap.notebooks,
            sections = snap.sections,
            pages = snap.pages,
            selectedNotebookId = nbId,
            selectedSectionId = secId,
            selectedPageId = pageId,
            revision = snap.revision
        )
    }

    private fun nextBlockId(): String {
        blockSeq += 1
        return "blk-${_uiState.value.revision}-$blockSeq"
    }

    fun selectNotebook(notebookId: String) {
        val snap = repository.snapshot.value
        _uiState.update { state ->
            val firstSec = snap.sections.firstOrNull { it.notebookId == notebookId }?.id.orEmpty()
            val firstPage = snap.pages.firstOrNull { it.sectionId == firstSec }?.id.orEmpty()
            state.copy(
                notebooks = snap.notebooks,
                sections = snap.sections,
                pages = snap.pages,
                revision = snap.revision,
                selectedNotebookId = notebookId,
                selectedSectionId = firstSec,
                selectedPageId = firstPage
            )
        }
    }

    fun selectSection(sectionId: String) {
        val snap = repository.snapshot.value
        _uiState.update { state ->
            val firstPage = snap.pages.firstOrNull { it.sectionId == sectionId }?.id.orEmpty()
            state.copy(
                notebooks = snap.notebooks,
                sections = snap.sections,
                pages = snap.pages,
                revision = snap.revision,
                selectedSectionId = sectionId,
                selectedPageId = firstPage
            )
        }
    }

    fun selectPage(pageId: String) {
        val snap = repository.snapshot.value
        _uiState.update {
            it.copy(
                notebooks = snap.notebooks,
                sections = snap.sections,
                pages = snap.pages,
                revision = snap.revision,
                selectedPageId = pageId,
                isMobileHierarchySheetOpen = false
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSidebar() {
        _uiState.update { it.copy(isSidebarOpen = !it.isSidebarOpen) }
    }

    fun setMobileHierarchySheetOpen(open: Boolean) {
        _uiState.update { it.copy(isMobileHierarchySheetOpen = open) }
    }

    fun openDialog(dialog: ActiveDialog) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
    }

    fun createNotebook(title: String, subtitle: String, accent: SectionAccentKey) {
        val created = repository.createNotebook(title, subtitle, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        selectNotebook(created.id)
    }

    fun createSection(title: String, accent: SectionAccentKey) {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        val created = repository.createSection(nbId, title, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        selectSection(created.id)
    }

    fun createPage(title: String) {
        val secId = _uiState.value.activeSection?.id ?: return
        val created = repository.createPage(secId, title)
        _uiState.update {
            it.copy(
                selectedPageId = created.id,
                activeDialog = ActiveDialog.NONE,
                isMobileHierarchySheetOpen = false
            )
        }
    }

    fun deleteActivePage() {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.deletePage(pageId)
    }

    fun toggleActivePagePin() {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.togglePagePin(pageId)
    }

    fun updateActivePageTitle(newTitle: String) {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.updatePageTitle(pageId, newTitle)
    }

    fun updateBlockText(blockId: String, newText: String) {
        val page = _uiState.value.activePage ?: return
        val updatedBlocks = page.blocks.map { block ->
            when (block) {
                is DocumentBlock.Heading -> if (block.id == blockId) block.copy(text = newText) else block
                is DocumentBlock.Paragraph -> if (block.id == blockId) block.copy(text = newText) else block
                is DocumentBlock.Callout -> if (block.id == blockId) block.copy(text = newText) else block
                is DocumentBlock.CodeSnippet -> if (block.id == blockId) block.copy(code = newText) else block
                else -> block
            }
        }
        repository.updatePageBlocks(page.id, updatedBlocks)
    }

    fun toggleParagraphStyle(blockId: String, style: String) {
        val page = _uiState.value.activePage ?: return
        val updatedBlocks = page.blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Paragraph) {
                when (style) {
                    "bold" -> block.copy(isBold = !block.isBold)
                    "italic" -> block.copy(isItalic = !block.isItalic)
                    "underline" -> block.copy(isUnderlined = !block.isUnderlined)
                    "highlight" -> block.copy(isHighlighted = !block.isHighlighted)
                    else -> block
                }
            } else {
                block
            }
        }
        repository.updatePageBlocks(page.id, updatedBlocks)
    }

    fun deleteBlock(blockId: String) {
        val page = _uiState.value.activePage ?: return
        repository.updatePageBlocks(page.id, page.blocks.filterNot { it.id == blockId })
    }

    fun toggleChecklistItem(blockId: String, itemId: String) {
        val page = _uiState.value.activePage ?: return
        repository.toggleChecklistItem(page.id, blockId, itemId)
    }

    fun addChecklistItem(blockId: String, text: String) {
        val page = _uiState.value.activePage ?: return
        repository.addChecklistItem(page.id, blockId, text)
    }

    fun insertHeadingBlock(level: Int = 2) {
        val page = _uiState.value.activePage ?: return
        repository.appendBlock(page.id, DocumentBlock.Heading(id = nextBlockId(), text = "New Section Heading", level = level))
    }

    fun insertParagraphBlock() {
        val page = _uiState.value.activePage ?: return
        repository.appendBlock(page.id, DocumentBlock.Paragraph(id = nextBlockId(), text = ""))
    }

    fun insertChecklistBlock() {
        val page = _uiState.value.activePage ?: return
        repository.appendBlock(
            page.id,
            DocumentBlock.Checklist(
                id = nextBlockId(),
                title = "Action Items",
                items = listOf(ChecklistItem(id = nextBlockId(), text = "First task", isChecked = false))
            )
        )
    }

    fun insertTableBlock() {
        val page = _uiState.value.activePage ?: return
        repository.appendBlock(
            page.id,
            DocumentBlock.Table(
                id = nextBlockId(),
                headers = listOf("Column 1", "Column 2", "Status"),
                rows = listOf(
                    listOf("Item A", "Details", "Ready"),
                    listOf("Item B", "Notes", "In Progress")
                )
            )
        )
    }

    fun insertCalloutBlock() {
        val page = _uiState.value.activePage ?: return
        repository.appendBlock(
            page.id,
            DocumentBlock.Callout(
                id = nextBlockId(),
                title = "Key Takeaway",
                text = "Summarize important decisions or reminders here.",
                tone = CalloutTone.TIP
            )
        )
    }

    fun insertCodeBlock() {
        val page = _uiState.value.activePage ?: return
        repository.appendBlock(
            page.id,
            DocumentBlock.CodeSnippet(
                id = nextBlockId(),
                language = "kotlin",
                code = "// Add snippet or command here"
            )
        )
    }

    fun triggerManualSync() {
        scope.launch {
            _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING) }
            val result = repository.syncWithServer()
            _uiState.update {
                it.copy(syncStatus = if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE)
            }
        }
    }
}
