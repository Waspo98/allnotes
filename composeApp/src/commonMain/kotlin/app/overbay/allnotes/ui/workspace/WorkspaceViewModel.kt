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
    val activeBlockId: String? = null,
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
            repository.snapshot.collect {
                syncStateFromRepository()
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

    fun syncStateFromRepository(
        targetNotebookId: String? = null,
        targetSectionId: String? = null,
        targetPageId: String? = null
    ) {
        val snap = repository.snapshot.value
        _uiState.update { current ->
            var nbId = targetNotebookId ?: current.selectedNotebookId
            var secId = targetSectionId ?: current.selectedSectionId
            var pageId = targetPageId ?: current.selectedPageId

            if (targetPageId != null) {
                val matchedPage = snap.pages.firstOrNull { it.id == targetPageId }
                if (matchedPage != null) {
                    pageId = matchedPage.id
                    secId = matchedPage.sectionId
                    val matchedSec = snap.sections.firstOrNull { it.id == secId }
                    if (matchedSec != null) {
                        nbId = matchedSec.notebookId
                    }
                }
            } else if (targetSectionId != null) {
                val matchedSec = snap.sections.firstOrNull { it.id == targetSectionId }
                if (matchedSec != null) {
                    secId = matchedSec.id
                    nbId = matchedSec.notebookId
                    pageId = snap.pages.firstOrNull { it.sectionId == secId }?.id.orEmpty()
                }
            } else if (targetNotebookId != null) {
                val matchedNb = snap.notebooks.firstOrNull { it.id == targetNotebookId }
                if (matchedNb != null) {
                    nbId = matchedNb.id
                    secId = snap.sections.firstOrNull { it.notebookId == nbId }?.id.orEmpty()
                    pageId = snap.pages.firstOrNull { it.sectionId == secId }?.id.orEmpty()
                }
            }

            if (snap.notebooks.none { it.id == nbId }) {
                nbId = snap.notebooks.firstOrNull()?.id.orEmpty()
            }
            if (snap.sections.none { it.id == secId && it.notebookId == nbId }) {
                secId = snap.sections.firstOrNull { it.notebookId == nbId }?.id.orEmpty()
            }
            if (snap.pages.none { it.id == pageId && it.sectionId == secId }) {
                pageId = snap.pages.firstOrNull { it.sectionId == secId }?.id.orEmpty()
            }

            val retainedActiveBlockId = if (pageId != current.selectedPageId) {
                null
            } else {
                val activePageBlocks = snap.pages.firstOrNull { it.id == pageId }?.blocks.orEmpty()
                if (activePageBlocks.any { it.id == current.activeBlockId }) {
                    current.activeBlockId
                } else {
                    null
                }
            }

            current.copy(
                notebooks = snap.notebooks,
                sections = snap.sections,
                pages = snap.pages,
                selectedNotebookId = nbId,
                selectedSectionId = secId,
                selectedPageId = pageId,
                activeBlockId = retainedActiveBlockId,
                revision = snap.revision
            )
        }
    }

    private fun nextBlockId(): String {
        blockSeq += 1
        return "blk-${_uiState.value.revision}-$blockSeq"
    }

    fun selectNotebook(notebookId: String) {
        val snap = repository.snapshot.value
        val firstSec = snap.sections.firstOrNull { it.notebookId == notebookId }?.id.orEmpty()
        val firstPage = snap.pages.firstOrNull { it.sectionId == firstSec }?.id.orEmpty()
        syncStateFromRepository(
            targetNotebookId = notebookId,
            targetSectionId = firstSec,
            targetPageId = firstPage
        )
    }

    fun selectSection(sectionId: String) {
        val snap = repository.snapshot.value
        val firstPage = snap.pages.firstOrNull { it.sectionId == sectionId }?.id.orEmpty()
        syncStateFromRepository(
            targetSectionId = sectionId,
            targetPageId = firstPage
        )
    }

    fun selectPage(pageId: String) {
        syncStateFromRepository(targetPageId = pageId)
        _uiState.update { it.copy(isMobileHierarchySheetOpen = false) }
    }

    fun setSearchQuery(query: String) {
        syncStateFromRepository()
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
                activeDialog = ActiveDialog.NONE,
                isMobileHierarchySheetOpen = false
            )
        }
        selectPage(created.id)
    }

    fun setActiveBlockId(blockId: String?) {
        _uiState.update { it.copy(activeBlockId = blockId) }
    }

    fun deleteActivePage() {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.deletePage(pageId)
        syncStateFromRepository()
    }

    fun toggleActivePagePin() {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.togglePagePin(pageId)
        syncStateFromRepository()
    }

    fun updateActivePageTitle(newTitle: String) {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.updatePageTitle(pageId, newTitle)
        syncStateFromRepository()
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
        syncStateFromRepository()
        _uiState.update { it.copy(activeBlockId = blockId) }
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
        syncStateFromRepository()
        _uiState.update { it.copy(activeBlockId = blockId) }
    }

    fun deleteBlock(blockId: String) {
        val page = _uiState.value.activePage ?: return
        repository.updatePageBlocks(page.id, page.blocks.filterNot { it.id == blockId })
        syncStateFromRepository()
        if (_uiState.value.activeBlockId == blockId) {
            _uiState.update { it.copy(activeBlockId = null) }
        }
    }

    fun toggleChecklistItem(blockId: String, itemId: String) {
        val page = _uiState.value.activePage ?: return
        repository.toggleChecklistItem(page.id, blockId, itemId)
        syncStateFromRepository()
    }

    fun addChecklistItem(blockId: String, text: String) {
        val page = _uiState.value.activePage ?: return
        repository.addChecklistItem(page.id, blockId, text)
        syncStateFromRepository()
    }

    fun updateHeadingLevel(blockId: String, level: Int) {
        val page = _uiState.value.activePage ?: return
        val clamped = level.coerceIn(1, 3)
        val updatedBlocks = page.blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Heading) {
                block.copy(level = clamped)
            } else {
                block
            }
        }
        repository.updatePageBlocks(page.id, updatedBlocks)
        syncStateFromRepository()
    }

    fun toggleActiveParagraphStyle(style: String) {
        val page = _uiState.value.activePage ?: return
        val activeId = _uiState.value.activeBlockId
        val targetedParagraph = if (activeId != null) {
            page.blocks.firstOrNull { it.id == activeId } as? DocumentBlock.Paragraph
        } else null

        val paragraph = targetedParagraph
            ?: page.blocks.filterIsInstance<DocumentBlock.Paragraph>().lastOrNull()
            ?: return

        toggleParagraphStyle(paragraph.id, style)
    }

    fun insertHeadingBlock(level: Int = 2) {
        val page = _uiState.value.activePage ?: return
        repository.appendBlock(page.id, DocumentBlock.Heading(id = nextBlockId(), text = "New Section Heading", level = level))
        syncStateFromRepository()
    }

    fun insertParagraphBlock() {
        val page = _uiState.value.activePage ?: return
        repository.appendBlock(page.id, DocumentBlock.Paragraph(id = nextBlockId(), text = ""))
        syncStateFromRepository()
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
        syncStateFromRepository()
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
        syncStateFromRepository()
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
        syncStateFromRepository()
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
        syncStateFromRepository()
    }

    fun insertBulletListBlock(isNumbered: Boolean = false) {
        val page = _uiState.value.activePage ?: return
        repository.appendBlock(
            page.id,
            DocumentBlock.BulletList(
                id = nextBlockId(),
                isNumbered = isNumbered,
                items = listOf("First item", "Second item")
            )
        )
        syncStateFromRepository()
    }

    fun addBulletListItem(blockId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val page = _uiState.value.activePage ?: return
        val updatedBlocks = page.blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.BulletList) {
                block.copy(items = block.items + clean)
            } else {
                block
            }
        }
        repository.updatePageBlocks(page.id, updatedBlocks)
        syncStateFromRepository()
    }

    fun updateTableCell(blockId: String, rowIndex: Int, colIndex: Int, text: String) {
        val page = _uiState.value.activePage ?: return
        val updatedBlocks = page.blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Table) {
                val updatedRows = block.rows.mapIndexed { rIdx, row ->
                    if (rIdx == rowIndex) {
                        row.mapIndexed { cIdx, cell ->
                            if (cIdx == colIndex) text else cell
                        }
                    } else {
                        row
                    }
                }
                block.copy(rows = updatedRows)
            } else {
                block
            }
        }
        repository.updatePageBlocks(page.id, updatedBlocks)
        syncStateFromRepository()
    }

    fun addTableRow(blockId: String, rowData: List<String> = emptyList()) {
        val page = _uiState.value.activePage ?: return
        val updatedBlocks = page.blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Table) {
                val newRow = if (rowData.isNotEmpty()) {
                    rowData
                } else {
                    List(block.headers.size) { "" }
                }
                block.copy(rows = block.rows + listOf(newRow))
            } else {
                block
            }
        }
        repository.updatePageBlocks(page.id, updatedBlocks)
        syncStateFromRepository()
    }

    fun addTableColumn(blockId: String, headerTitle: String, defaultCellText: String = "") {
        val page = _uiState.value.activePage ?: return
        val updatedBlocks = page.blocks.map { block ->
            if (block.id == blockId && block is DocumentBlock.Table) {
                val newHeaders = block.headers + headerTitle
                val newRows = block.rows.map { row -> row + defaultCellText }
                block.copy(headers = newHeaders, rows = newRows)
            } else {
                block
            }
        }
        repository.updatePageBlocks(page.id, updatedBlocks)
        syncStateFromRepository()
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
