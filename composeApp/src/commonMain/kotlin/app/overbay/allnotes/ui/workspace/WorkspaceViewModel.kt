package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.HttpSyncProvider
import app.overbay.allnotes.data.LocalLoopbackSyncProvider
import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.SyncStatus
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val repository: NoteRepository = NoteRepository(),
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)
) {
    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var blockSeq = 5000L

    init {
        scope.launch {
            repository.snapshot.collect { incoming ->
                syncStateFromRepository(incomingSnapshot = incoming)
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
            syncEndpointUrl = repository.currentSyncEndpoint,
            revision = snap.revision
        )
    }

    fun dispatch(action: WorkspaceAction) {
        when (action) {
            is WorkspaceAction.SelectNotebook -> selectNotebook(action.notebookId)
            is WorkspaceAction.SelectSection -> selectSection(action.sectionId)
            is WorkspaceAction.SelectPage -> selectPage(action.pageId)
            is WorkspaceAction.SetSearchQuery -> setSearchQuery(action.query)
            WorkspaceAction.ToggleSidebar -> toggleSidebar()
            is WorkspaceAction.SetMobileHierarchySheetOpen -> setMobileHierarchySheetOpen(action.open)
            is WorkspaceAction.OpenDialog -> openDialog(action.dialog)
            WorkspaceAction.DismissDialog -> dismissDialog()
            WorkspaceAction.TriggerSync -> triggerManualSync()
            is WorkspaceAction.ConfigureSyncServer -> configureSyncServer(action.endpointUrl, action.useRemoteHttp)

            is WorkspaceAction.CreateNotebook -> createNotebook(action.title, action.subtitle, action.accent)
            is WorkspaceAction.UpdateActiveNotebook -> updateActiveNotebook(action.title, action.subtitle, action.accent)
            WorkspaceAction.DeleteActiveNotebook -> deleteActiveNotebook()
            is WorkspaceAction.CreateSection -> createSection(action.title, action.accent)
            is WorkspaceAction.UpdateActiveSection -> updateActiveSection(action.title, action.accent)
            WorkspaceAction.DeleteActiveSection -> deleteActiveSection()
            is WorkspaceAction.CreatePage -> createPage(action.title, action.asSubpageOfActive)
            is WorkspaceAction.TogglePageSubpageIndent -> togglePageSubpageIndent(action.pageId)

            is WorkspaceAction.UpdateActivePageTitle -> updateActivePageTitle(action.newTitle)
            WorkspaceAction.ToggleActivePagePin -> toggleActivePagePin()
            WorkspaceAction.DeleteActivePage -> deleteActivePage()

            is WorkspaceAction.SetActiveBlock -> setActiveBlockId(action.blockId)
            is WorkspaceAction.MoveBlock -> moveBlock(action.blockId, action.delta)
            is WorkspaceAction.DeleteBlock -> deleteBlock(action.blockId)

            is WorkspaceAction.InsertHeading -> insertHeadingBlock(action.level, action.afterBlockId)
            is WorkspaceAction.InsertParagraph -> insertParagraphBlock(action.afterBlockId)
            is WorkspaceAction.InsertChecklist -> insertChecklistBlock(action.afterBlockId)
            is WorkspaceAction.InsertBulletList -> insertBulletListBlock(action.isNumbered, action.afterBlockId)
            is WorkspaceAction.InsertTable -> insertTableBlock(action.afterBlockId)
            is WorkspaceAction.InsertCallout -> insertCalloutBlock(action.afterBlockId)
            is WorkspaceAction.InsertCode -> insertCodeBlock(action.afterBlockId)

            is WorkspaceAction.UpdateBlockText -> updateBlockText(action.blockId, action.newText)
            is WorkspaceAction.ToggleParagraphStyle -> toggleParagraphStyle(action.blockId, action.style)
            is WorkspaceAction.ToggleActiveParagraphStyle -> toggleActiveParagraphStyle(action.style)
            is WorkspaceAction.UpdateHeadingLevel -> updateHeadingLevel(action.blockId, action.level)

            is WorkspaceAction.UpdateChecklistTitle -> updateChecklistTitle(action.blockId, action.title)
            is WorkspaceAction.ToggleChecklistItem -> toggleChecklistItem(action.blockId, action.itemId)
            is WorkspaceAction.AddChecklistItem -> addChecklistItem(action.blockId, action.text)
            is WorkspaceAction.InsertChecklistItemAfter -> insertChecklistItemAfter(action.blockId, action.afterItemId)
            is WorkspaceAction.UpdateChecklistItemText -> updateChecklistItemText(action.blockId, action.itemId, action.text)
            is WorkspaceAction.DeleteChecklistItem -> deleteChecklistItem(action.blockId, action.itemId)

            is WorkspaceAction.ToggleBulletListKind -> toggleBulletListKind(action.blockId)
            is WorkspaceAction.AddBulletListItem -> addBulletListItem(action.blockId, action.text)
            is WorkspaceAction.InsertBulletListItemAfter -> insertBulletListItemAfter(action.blockId, action.afterItemIndex)
            is WorkspaceAction.UpdateBulletListItem -> updateBulletListItem(action.blockId, action.itemIndex, action.text)
            is WorkspaceAction.DeleteBulletListItem -> deleteBulletListItem(action.blockId, action.itemIndex)

            is WorkspaceAction.UpdateTableHeader -> updateTableHeader(action.blockId, action.colIndex, action.title)
            is WorkspaceAction.UpdateTableCell -> updateTableCell(action.blockId, action.rowIndex, action.colIndex, action.text)
            is WorkspaceAction.AddTableRow -> addTableRow(action.blockId)
            is WorkspaceAction.DeleteTableRow -> deleteTableRow(action.blockId, action.rowIndex)
            is WorkspaceAction.AddTableColumn -> addTableColumn(action.blockId, action.headerTitle)
            is WorkspaceAction.DeleteTableColumn -> deleteTableColumn(action.blockId, action.colIndex)

            is WorkspaceAction.UpdateCalloutTitle -> updateCalloutTitle(action.blockId, action.title)
            is WorkspaceAction.UpdateCalloutTone -> updateCalloutTone(action.blockId, action.tone)
            is WorkspaceAction.UpdateCodeLanguage -> updateCodeLanguage(action.blockId, action.language)
        }
    }

    fun syncStateFromRepository(
        targetNotebookId: String? = null,
        targetSectionId: String? = null,
        targetPageId: String? = null,
        incomingSnapshot: WorkspaceSnapshot? = null
    ) {
        val snap = incomingSnapshot ?: repository.snapshot.value
        _uiState.update { current ->
            if (snap.revision < current.revision && targetNotebookId == null && targetSectionId == null && targetPageId == null) {
                return@update current
            }
            val effectiveSnap = if (snap.revision < current.revision) repository.snapshot.value else snap
            HierarchySelectionResolver.resolveSyncedState(
                current = current,
                effectiveSnap = effectiveSnap,
                targetNotebookId = targetNotebookId,
                targetSectionId = targetSectionId,
                targetPageId = targetPageId
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun nextBlockId(): String {
        blockSeq += 1
        val shortUuid = Uuid.random().toHexString().take(6)
        return "blk-${_uiState.value.revision}-$blockSeq-$shortUuid"
    }

    fun selectNotebook(notebookId: String) {
        val snap = repository.snapshot.value
        val firstSec = snap.sections.firstOrNull { it.notebookId == notebookId }?.id.orEmpty()
        val firstPage = snap.pages.firstOrNull { it.sectionId == firstSec }?.id.orEmpty()
        syncStateFromRepository(targetNotebookId = notebookId, targetSectionId = firstSec, targetPageId = firstPage)
    }

    fun selectSection(sectionId: String) {
        val snap = repository.snapshot.value
        val firstPage = snap.pages.firstOrNull { it.sectionId == sectionId }?.id.orEmpty()
        syncStateFromRepository(targetSectionId = sectionId, targetPageId = firstPage)
    }

    fun selectPage(pageId: String) {
        syncStateFromRepository(targetPageId = pageId)
        _uiState.update { it.copy(isMobileHierarchySheetOpen = false) }
    }

    fun setSearchQuery(query: String) {
        syncStateFromRepository()
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSidebar() = _uiState.update { it.copy(isSidebarOpen = !it.isSidebarOpen) }
    fun setMobileHierarchySheetOpen(open: Boolean) = _uiState.update { it.copy(isMobileHierarchySheetOpen = open) }
    fun openDialog(dialog: ActiveDialog) = _uiState.update { it.copy(activeDialog = dialog) }
    fun dismissDialog() = _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
    fun setActiveBlockId(blockId: String?) = _uiState.update { it.copy(activeBlockId = blockId) }

    fun createNotebook(title: String, subtitle: String, accent: SectionAccentKey) {
        val created = repository.createNotebook(title, subtitle, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        selectNotebook(created.id)
    }

    fun updateActiveNotebook(title: String, subtitle: String, accent: SectionAccentKey) {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        repository.updateNotebook(nbId, title, subtitle, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        syncStateFromRepository()
    }

    fun deleteActiveNotebook() {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        repository.deleteNotebook(nbId)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        syncStateFromRepository()
    }

    fun createSection(title: String, accent: SectionAccentKey) {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        val created = repository.createSection(nbId, title, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        selectSection(created.id)
    }

    fun updateActiveSection(title: String, accent: SectionAccentKey) {
        val secId = _uiState.value.activeSection?.id ?: return
        repository.updateSection(secId, title, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        syncStateFromRepository()
    }

    fun deleteActiveSection() {
        val secId = _uiState.value.activeSection?.id ?: return
        repository.deleteSection(secId)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        syncStateFromRepository()
    }

    fun createPage(title: String, asSubpageOfActive: Boolean = false) {
        val secId = _uiState.value.activeSection?.id ?: return
        val parentId = if (asSubpageOfActive) {
            val active = _uiState.value.activePage
            active?.parentPageId ?: active?.id
        } else null
        val created = repository.createPage(secId, title, parentId)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE, isMobileHierarchySheetOpen = false) }
        selectPage(created.id)
    }

    fun togglePageSubpageIndent(pageId: String) {
        val sectionPages = _uiState.value.pagesForSection
        val targetIdx = sectionPages.indexOfFirst { it.id == pageId }
        if (targetIdx < 0) return
        val target = sectionPages[targetIdx]
        if (target.parentPageId != null) {
            repository.updatePageParent(pageId, null)
        } else {
            val precedingRoot = sectionPages.subList(0, targetIdx).lastOrNull { it.parentPageId == null }
            if (precedingRoot != null && precedingRoot.id != pageId) {
                repository.updatePageParent(pageId, precedingRoot.id)
            }
        }
        syncStateFromRepository()
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

    private fun mutateActivePageBlocks(keepBlockActive: String? = null, transform: (List<DocumentBlock>) -> List<DocumentBlock>) {
        val page = _uiState.value.activePage ?: return
        val updatedBlocks = transform(page.blocks)
        repository.updatePageBlocks(page.id, updatedBlocks)
        syncStateFromRepository()
        if (keepBlockActive != null) {
            _uiState.update { it.copy(activeBlockId = keepBlockActive) }
        }
    }

    fun moveBlock(blockId: String, delta: Int) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.moveBlock(it, blockId, delta)
    }

    fun updateBlockText(blockId: String, newText: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateBlockText(it, blockId, newText)
    }

    fun toggleParagraphStyle(blockId: String, style: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.toggleParagraphStyle(it, blockId, style)
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

    fun insertChecklistItemAfter(blockId: String, afterItemId: String? = null) {
        val newItem = ChecklistItem(id = nextBlockId(), text = "", isChecked = false)
        mutateActivePageBlocks(blockId) {
            BlockMutationEngine.insertChecklistItemAfter(it, blockId, newItem, afterItemId)
        }
    }

    fun updateChecklistTitle(blockId: String, title: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateChecklistTitle(it, blockId, title)
    }

    fun updateChecklistItemText(blockId: String, itemId: String, text: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateChecklistItemText(it, blockId, itemId, text)
    }

    fun deleteChecklistItem(blockId: String, itemId: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.deleteChecklistItem(it, blockId, itemId)
    }

    fun updateHeadingLevel(blockId: String, level: Int) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateHeadingLevel(it, blockId, level)
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

    private fun insertBlock(block: DocumentBlock, afterBlockId: String? = null) {
        val page = _uiState.value.activePage ?: return
        if (afterBlockId == null) {
            repository.appendBlock(page.id, block)
        } else {
            val updated = BlockMutationEngine.insertBlock(page.blocks, block, afterBlockId)
            repository.updatePageBlocks(page.id, updated)
        }
        syncStateFromRepository()
        _uiState.update { it.copy(activeBlockId = block.id) }
    }

    fun insertHeadingBlock(level: Int = 2, afterBlockId: String? = null) =
        insertBlock(DocumentBlock.Heading(id = nextBlockId(), text = "New Section Heading", level = level.coerceIn(1, 3)), afterBlockId)

    fun insertParagraphBlock(afterBlockId: String? = null) =
        insertBlock(DocumentBlock.Paragraph(id = nextBlockId(), text = ""), afterBlockId)

    fun insertChecklistBlock(afterBlockId: String? = null) = insertBlock(
        DocumentBlock.Checklist(
            id = nextBlockId(),
            title = "Action Items",
            items = listOf(ChecklistItem(id = nextBlockId(), text = "First task", isChecked = false))
        ),
        afterBlockId
    )

    fun insertTableBlock(afterBlockId: String? = null) = insertBlock(
        DocumentBlock.Table(
            id = nextBlockId(),
            headers = listOf("Column 1", "Column 2", "Status"),
            rows = listOf(
                listOf("Item A", "Details", "Ready"),
                listOf("Item B", "Notes", "In Progress")
            )
        ),
        afterBlockId
    )

    fun insertCalloutBlock(afterBlockId: String? = null) = insertBlock(
        DocumentBlock.Callout(
            id = nextBlockId(),
            title = "Key Takeaway",
            text = "Summarize important decisions or reminders here.",
            tone = CalloutTone.TIP
        ),
        afterBlockId
    )

    fun insertCodeBlock(afterBlockId: String? = null) = insertBlock(
        DocumentBlock.CodeSnippet(
            id = nextBlockId(),
            language = "kotlin",
            code = "// Add snippet or command here"
        ),
        afterBlockId
    )

    fun insertBulletListBlock(isNumbered: Boolean = false, afterBlockId: String? = null) = insertBlock(
        DocumentBlock.BulletList(
            id = nextBlockId(),
            isNumbered = isNumbered,
            items = listOf("First item", "Second item")
        ),
        afterBlockId
    )

    fun toggleBulletListKind(blockId: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.toggleBulletListKind(it, blockId)
    }

    fun addBulletListItem(blockId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        mutateActivePageBlocks(blockId) {
            BlockMutationEngine.addBulletListItem(it, blockId, clean)
        }
    }

    fun insertBulletListItemAfter(blockId: String, afterItemIndex: Int? = null) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.insertBulletListItemAfter(it, blockId, afterItemIndex)
    }

    fun updateBulletListItem(blockId: String, itemIndex: Int, text: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateBulletListItem(it, blockId, itemIndex, text)
    }

    fun deleteBulletListItem(blockId: String, itemIndex: Int) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.deleteBulletListItem(it, blockId, itemIndex)
    }

    fun updateTableHeader(blockId: String, colIndex: Int, title: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateTableHeader(it, blockId, colIndex, title)
    }

    fun updateTableCell(blockId: String, rowIndex: Int, colIndex: Int, text: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateTableCell(it, blockId, rowIndex, colIndex, text)
    }

    fun addTableRow(blockId: String, rowData: List<String> = emptyList()) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.addTableRow(it, blockId, rowData)
    }

    fun deleteTableRow(blockId: String, rowIndex: Int) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.deleteTableRow(it, blockId, rowIndex)
    }

    fun addTableColumn(blockId: String, headerTitle: String, defaultCellText: String = "") = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.addTableColumn(it, blockId, headerTitle, defaultCellText)
    }

    fun deleteTableColumn(blockId: String, colIndex: Int) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.deleteTableColumn(it, blockId, colIndex)
    }

    fun updateCalloutTitle(blockId: String, title: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateCalloutTitle(it, blockId, title)
    }

    fun updateCalloutTone(blockId: String, tone: CalloutTone) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateCalloutTone(it, blockId, tone)
    }

    fun updateCodeLanguage(blockId: String, language: String) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.updateCodeLanguage(it, blockId, language)
    }

    fun configureSyncServer(endpointUrl: String, useRemoteHttp: Boolean) {
        val cleanUrl = endpointUrl.trim().ifEmpty { "https://notes.overbay.app/api/v1/sync" }
        val provider = if (useRemoteHttp) {
            HttpSyncProvider(endpointUrl = cleanUrl)
        } else {
            LocalLoopbackSyncProvider(endpointUrl = cleanUrl)
        }
        repository.configureSyncProvider(provider)
        _uiState.update {
            it.copy(
                syncEndpointUrl = cleanUrl,
                useRemoteHttpSync = useRemoteHttp,
                activeDialog = ActiveDialog.NONE
            )
        }
        triggerManualSync()
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
