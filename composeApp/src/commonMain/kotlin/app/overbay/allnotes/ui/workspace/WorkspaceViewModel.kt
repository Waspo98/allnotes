package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.HttpSyncProvider
import app.overbay.allnotes.data.LocalLoopbackSyncProvider
import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.SyncStatus
import app.overbay.allnotes.data.currentEpochMillis
import app.overbay.allnotes.data.markdown.MarkdownCodec
import app.overbay.allnotes.data.platformSynchronized
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.ThemeMode
import app.overbay.allnotes.model.WorkspaceSnapshot
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val repository: NoteRepository = NoteRepository(typingDebounceMs = 300L),
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)
) {
    private val undoRedo = UndoRedoController()
    private val seqLock = Any()
    private var blockSeq = 5000L
    private var focusTokenSeq = 1L
    private var autoSyncJob: Job? = null

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        scope.launch { repository.snapshot.collect { syncStateFromRepository(incomingSnapshot = it) } }
        scope.launch {
            repository.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        preferences = prefs,
                        syncEndpointUrl = prefs.syncEndpointUrl,
                        useRemoteHttpSync = prefs.useRemoteHttpSync
                    )
                }
            }
        }
        if (repository.preferences.value.useRemoteHttpSync) {
            scope.launch {
                val res = repository.pullFromServer()
                _uiState.update { it.copy(syncStatus = if (res.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE) }
            }
        }
    }

    private fun buildInitialState(): WorkspaceUiState {
        val snap = repository.snapshot.value
        val prefs = repository.preferences.value
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
            syncEndpointUrl = prefs.syncEndpointUrl.ifBlank { repository.currentSyncEndpoint },
            useRemoteHttpSync = prefs.useRemoteHttpSync,
            preferences = prefs,
            revision = snap.revision
        )
    }

    fun flushPendingWrites() = repository.flushPendingWrites()
    private fun nextFocusToken(): Long = platformSynchronized(seqLock) { ++focusTokenSeq }

    @OptIn(ExperimentalUuidApi::class)
    private fun nextBlockId(): String {
        val seq = platformSynchronized(seqLock) { ++blockSeq }
        return "blk-${_uiState.value.revision}-$seq-${Uuid.random().toHexString().take(6)}"
    }

    fun dispatch(action: WorkspaceAction) {
        when (action) {
            is WorkspaceAction.SelectNotebook -> selectNotebook(action.notebookId)
            is WorkspaceAction.SelectSection -> selectSection(action.sectionId)
            is WorkspaceAction.SelectPage -> selectPage(action.pageId)
            is WorkspaceAction.SetSearchQuery -> setSearchQuery(action.query)
            WorkspaceAction.ToggleSidebar -> toggleSidebar()
            is WorkspaceAction.SetMobileHierarchySheetOpen -> setMobileHierarchySheetOpen(action.open)
            is WorkspaceAction.SetPageMenuOpen -> _uiState.update { it.copy(isPageMenuOpen = action.open) }
            is WorkspaceAction.SetMobileInsertMenuOpen -> _uiState.update { it.copy(isMobileInsertMenuOpen = action.open) }
            is WorkspaceAction.OpenDialog -> openDialog(action.dialog)
            WorkspaceAction.DismissDialog -> dismissDialog()
            is WorkspaceAction.ShowStatusBanner -> _uiState.update { it.copy(statusBannerMessage = action.message) }
            WorkspaceAction.DismissStatusBanner -> _uiState.update { it.copy(statusBannerMessage = null) }
            WorkspaceAction.TriggerSync -> triggerManualSync()
            is WorkspaceAction.ConfigureSyncServer -> configureSyncServer(action.endpointUrl, action.useRemoteHttp)
            is WorkspaceAction.SaveSyncAndAuthSettings -> saveSyncAndAuthSettings(
                action.endpointUrl, action.useRemoteHttp, action.authToken, action.autoSync
            )
            is WorkspaceAction.UpdateThemeSettings -> repository.updatePreferences {
                it.copy(themeMode = action.themeMode, useDynamicColor = action.useDynamicColor)
            }

            is WorkspaceAction.CreateNotebook -> createNotebook(action.title, action.subtitle, action.accent)
            is WorkspaceAction.UpdateActiveNotebook -> updateActiveNotebook(action.title, action.subtitle, action.accent)
            is WorkspaceAction.ReorderActiveNotebook -> reorderActiveNotebook(action.delta)
            WorkspaceAction.DeleteActiveNotebook -> deleteActiveNotebook()
            is WorkspaceAction.CreateSection -> createSection(action.title, action.accent)
            is WorkspaceAction.UpdateActiveSection -> updateActiveSection(action.title, action.accent)
            is WorkspaceAction.ReorderActiveSection -> reorderActiveSection(action.delta)
            WorkspaceAction.DeleteActiveSection -> deleteActiveSection()
            is WorkspaceAction.CreatePage -> createPage(action.title, action.asSubpageOfActive)
            is WorkspaceAction.TogglePageSubpageIndent -> togglePageSubpageIndent(action.pageId)
            is WorkspaceAction.ToggleSubpageCollapse -> toggleSubpageCollapse(action.parentPageId)
            is WorkspaceAction.MoveActivePageToSection -> moveActivePageToSection(action.targetSectionId)
            is WorkspaceAction.ImportMarkdownPage -> importMarkdownPage(action.markdown)

            is WorkspaceAction.UpdateActivePageTitle -> updateActivePageTitle(action.newTitle)
            WorkspaceAction.ToggleActivePagePin -> toggleActivePagePin()
            WorkspaceAction.DeleteActivePage -> deleteActivePage()
            WorkspaceAction.Undo -> performUndo()
            WorkspaceAction.Redo -> performRedo()

            is WorkspaceAction.SetActiveBlock -> setActiveBlockId(action.blockId)
            is WorkspaceAction.UpdateActiveSelection -> _uiState.update {
                it.copy(activeBlockId = action.blockId, selectionStart = action.selectionStart, selectionEnd = action.selectionEnd)
            }
            is WorkspaceAction.ConsumeFocusRequest -> _uiState.update {
                if (it.focusRequest?.requestToken == action.requestToken) it.copy(focusRequest = null) else it
            }
            WorkspaceAction.FocusOrAppendTrailingParagraph -> focusOrAppendTrailingParagraph()
            is WorkspaceAction.MoveBlock -> moveBlock(action.blockId, action.delta)
            is WorkspaceAction.DeleteBlock -> deleteBlock(action.blockId)

            is WorkspaceAction.EditParagraphText -> applyEditorResult(true) {
                EditorInputController.editParagraphWithTriggers(it, action.blockId, action.newText, action.cursorOffset, ::nextBlockId, ::nextFocusToken)
            }
            is WorkspaceAction.SplitBlockAtCursor -> applyEditorResult(false) {
                EditorInputController.splitBlockAtCursor(it, action.blockId, action.cursorOffset, ::nextBlockId, ::nextFocusToken)
            }
            is WorkspaceAction.BackspaceAtBlockStart -> applyEditorResult(false) {
                EditorInputController.backspaceAtBlockStart(it, action.blockId, ::nextFocusToken)
            }
            is WorkspaceAction.ListEnterAtItem -> applyEditorResult(false) {
                EditorInputController.handleListEnter(it, action.blockId, action.itemIndex, ::nextBlockId, ::nextFocusToken)
            }
            is WorkspaceAction.ListBackspaceOnEmptyItem -> applyEditorResult(false) {
                EditorInputController.handleListBackspaceOnEmpty(it, action.blockId, action.itemIndex, ::nextFocusToken)
            }
            is WorkspaceAction.ExecuteSlashCommand -> applyEditorResult(false) {
                EditorInputController.executeSlashCommand(it, action.blockId, action.command, ::nextBlockId, ::nextFocusToken)
            }

            is WorkspaceAction.InsertHeading -> insertHeadingBlock(action.level, action.afterBlockId)
            is WorkspaceAction.InsertParagraph -> insertParagraphBlock(action.afterBlockId)
            is WorkspaceAction.InsertChecklist -> insertChecklistBlock(action.afterBlockId)
            is WorkspaceAction.InsertBulletList -> insertBulletListBlock(action.isNumbered, action.afterBlockId)
            is WorkspaceAction.InsertTable -> insertTableBlock(action.afterBlockId)
            is WorkspaceAction.InsertCallout -> insertCalloutBlock(action.afterBlockId)
            is WorkspaceAction.InsertCode -> insertCodeBlock(action.afterBlockId)
            is WorkspaceAction.InsertDivider -> insertDividerBlock(action.afterBlockId)
            is WorkspaceAction.InsertAttachment -> insertAttachmentBlock(
                action.fileName, action.mimeType, action.sizeBytes, action.dataBase64, action.afterBlockId
            )

            is WorkspaceAction.UpdateBlockText -> updateBlockText(action.blockId, action.newText)
            is WorkspaceAction.UpdateAttachmentCaption -> updateAttachmentCaption(action.blockId, action.caption)
            is WorkspaceAction.ToggleSelectionSpan -> toggleSelectionSpan(
                action.kind, action.url, action.blockId, action.selStart, action.selEnd
            )
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
            val resolved = HierarchySelectionResolver.resolveSyncedState(
                current, effectiveSnap, targetNotebookId, targetSectionId, targetPageId
            )
            val activePageId = resolved.activePage?.id
            resolved.copy(canUndo = undoRedo.canUndo(activePageId), canRedo = undoRedo.canRedo(activePageId))
        }
    }

    private fun scheduleAutoSyncIfEnabled() {
        val prefs = _uiState.value.preferences
        if (!prefs.autoSyncOnEdit || !prefs.useRemoteHttpSync) return
        autoSyncJob?.cancel()
        autoSyncJob = scope.launch {
            delay(1500L)
            _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING) }
            val result = repository.syncWithServer()
            _uiState.update { it.copy(syncStatus = if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE) }
        }
    }

    fun selectNotebook(notebookId: String) {
        val snap = repository.snapshot.value
        val firstSec = snap.sections.filter { it.notebookId == notebookId }.minByOrNull { it.sortOrder }?.id.orEmpty()
        val firstPage = snap.pages.firstOrNull { it.sectionId == firstSec }?.id.orEmpty()
        syncStateFromRepository(targetNotebookId = notebookId, targetSectionId = firstSec, targetPageId = firstPage)
    }

    fun selectSection(sectionId: String) {
        val firstPage = repository.snapshot.value.pages.firstOrNull { it.sectionId == sectionId }?.id.orEmpty()
        syncStateFromRepository(targetSectionId = sectionId, targetPageId = firstPage)
    }

    fun selectPage(pageId: String) {
        val parentToUncollapse = repository.snapshot.value.pages.firstOrNull { it.id == pageId }?.parentPageId
        syncStateFromRepository(targetPageId = pageId)
        _uiState.update {
            it.copy(
                isMobileHierarchySheetOpen = false,
                isPageMenuOpen = false,
                collapsedParentPageIds = if (parentToUncollapse != null) it.collapsedParentPageIds - parentToUncollapse else it.collapsedParentPageIds
            )
        }
    }

    fun setSearchQuery(query: String) {
        syncStateFromRepository()
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSidebar() = _uiState.update { it.copy(isSidebarOpen = !it.isSidebarOpen) }
    fun setMobileHierarchySheetOpen(open: Boolean) = _uiState.update { it.copy(isMobileHierarchySheetOpen = open) }
    fun openDialog(dialog: ActiveDialog) = _uiState.update { it.copy(activeDialog = dialog, isPageMenuOpen = false) }
    fun dismissDialog() = _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
    fun setActiveBlockId(blockId: String?) = _uiState.update { it.copy(activeBlockId = blockId) }

    private fun focusOrAppendTrailingParagraph() {
        val page = _uiState.value.activePage ?: return
        val last = page.blocks.lastOrNull()
        if (last is DocumentBlock.Paragraph) {
            val req = EditorFocusRequest(last.id, cursorOffset = last.text.length, requestToken = nextFocusToken())
            _uiState.update { it.copy(activeBlockId = last.id, focusRequest = req) }
        } else {
            val newBlock = DocumentBlock.Paragraph(id = nextBlockId(), text = "")
            undoRedo.recordBeforeMutation(page.id, page.blocks)
            repository.appendBlock(page.id, newBlock)
            syncStateFromRepository()
            val req = EditorFocusRequest(newBlock.id, cursorOffset = 0, requestToken = nextFocusToken())
            _uiState.update { it.copy(activeBlockId = newBlock.id, focusRequest = req) }
            scheduleAutoSyncIfEnabled()
        }
    }

    fun createNotebook(title: String, subtitle: String, accent: SectionAccentKey) {
        val created = repository.createNotebook(title, subtitle, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        selectNotebook(created.id)
        scheduleAutoSyncIfEnabled()
    }

    fun updateActiveNotebook(title: String, subtitle: String, accent: SectionAccentKey) {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        repository.updateNotebook(nbId, title, subtitle, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun reorderActiveNotebook(delta: Int) {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        repository.reorderNotebook(nbId, delta)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun deleteActiveNotebook() {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        repository.deleteNotebook(nbId)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun createSection(title: String, accent: SectionAccentKey) {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        val created = repository.createSection(nbId, title, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        selectSection(created.id)
        scheduleAutoSyncIfEnabled()
    }

    fun updateActiveSection(title: String, accent: SectionAccentKey) {
        val secId = _uiState.value.activeSection?.id ?: return
        repository.updateSection(secId, title, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun reorderActiveSection(delta: Int) {
        val secId = _uiState.value.activeSection?.id ?: return
        repository.reorderSection(secId, delta)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun deleteActiveSection() {
        val secId = _uiState.value.activeSection?.id ?: return
        repository.deleteSection(secId)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun createPage(title: String, asSubpageOfActive: Boolean = false) {
        val secId = _uiState.value.activeSection?.id ?: return
        val parentId = if (asSubpageOfActive) {
            val active = _uiState.value.activePage
            active?.parentPageId ?: active?.id
        } else null
        val created = repository.createPage(secId, title, parentId)
        val firstBlkId = created.blocks.firstOrNull()?.id
        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.NONE,
                isMobileHierarchySheetOpen = false,
                collapsedParentPageIds = if (parentId != null) it.collapsedParentPageIds - parentId else it.collapsedParentPageIds
            )
        }
        selectPage(created.id)
        if (firstBlkId != null) {
            _uiState.update {
                it.copy(activeBlockId = firstBlkId, focusRequest = EditorFocusRequest(firstBlkId, cursorOffset = 0, requestToken = nextFocusToken()))
            }
        }
        scheduleAutoSyncIfEnabled()
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
        scheduleAutoSyncIfEnabled()
    }

    private fun toggleSubpageCollapse(parentPageId: String) {
        val activeChildParent = _uiState.value.activePage?.parentPageId
        _uiState.update { state ->
            val nextSet = if (parentPageId in state.collapsedParentPageIds) state.collapsedParentPageIds - parentPageId
            else state.collapsedParentPageIds + parentPageId
            val nextPageId = if (parentPageId in nextSet && activeChildParent == parentPageId) parentPageId else state.selectedPageId
            state.copy(collapsedParentPageIds = nextSet, selectedPageId = nextPageId)
        }
    }

    private fun moveActivePageToSection(targetSectionId: String) {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.movePageToSection(pageId, targetSectionId)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE, isPageMenuOpen = false) }
        syncStateFromRepository(targetPageId = pageId)
        scheduleAutoSyncIfEnabled()
    }

    private fun importMarkdownPage(markdown: String) {
        val secId = _uiState.value.activeSection?.id ?: return
        if (markdown.isBlank()) return
        val imported = MarkdownCodec.importMarkdownToPage(markdown, secId, repository::nextId, repository.nextTimestampMs())
        repository.insertConstructedPage(imported)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE, statusBannerMessage = "Imported \"${imported.title}\"") }
        selectPage(imported.id)
        scheduleAutoSyncIfEnabled()
    }

    fun deleteActivePage() {
        val pageId = _uiState.value.activePage?.id ?: return
        undoRedo.clearPage(pageId)
        repository.deletePage(pageId)
        _uiState.update { it.copy(isPageMenuOpen = false) }
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun toggleActivePagePin() {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.togglePagePin(pageId)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun updateActivePageTitle(newTitle: String) {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.updatePageTitle(pageId, newTitle, debounced = true)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    private fun performUndo() {
        val page = _uiState.value.activePage ?: return
        val restored = undoRedo.undo(page.id, page.blocks) ?: return
        repository.updatePageBlocks(page.id, restored, debounced = false)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    private fun performRedo() {
        val page = _uiState.value.activePage ?: return
        val restored = undoRedo.redo(page.id, page.blocks) ?: return
        repository.updatePageBlocks(page.id, restored, debounced = false)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    private fun applyEditorResult(debounced: Boolean, compute: (List<DocumentBlock>) -> EditorMutationResult) {
        val page = _uiState.value.activePage ?: return
        val res = compute(page.blocks)
        if (res.blocks == page.blocks && res.focusRequest == null) return
        if (res.blocks != page.blocks) {
            val coalesce = debounced && !res.isStructuralConversion
            undoRedo.recordBeforeMutation(page.id, page.blocks, coalesce, res.activeBlockId, currentEpochMillis())
            repository.updatePageBlocks(page.id, res.blocks, debounced = coalesce)
            syncStateFromRepository()
            scheduleAutoSyncIfEnabled()
        }
        _uiState.update {
            it.copy(activeBlockId = res.activeBlockId ?: it.activeBlockId, focusRequest = res.focusRequest ?: it.focusRequest)
        }
    }

    private fun mutateActivePageBlocks(
        keepBlockActive: String? = null,
        debounced: Boolean = false,
        transform: (List<DocumentBlock>) -> List<DocumentBlock>
    ) {
        val page = _uiState.value.activePage ?: return
        val updatedBlocks = transform(page.blocks)
        if (updatedBlocks != page.blocks) {
            undoRedo.recordBeforeMutation(page.id, page.blocks, debounced, keepBlockActive ?: _uiState.value.activeBlockId, currentEpochMillis())
        }
        repository.updatePageBlocks(page.id, updatedBlocks, debounced = debounced)
        syncStateFromRepository()
        if (keepBlockActive != null) {
            _uiState.update { it.copy(activeBlockId = keepBlockActive, canUndo = undoRedo.canUndo(page.id), canRedo = undoRedo.canRedo(page.id)) }
        }
        scheduleAutoSyncIfEnabled()
    }

    private fun toggleSelectionSpan(kind: RichSpanKind, url: String?, explicitBlockId: String?, explicitStart: Int?, explicitEnd: Int?) {
        val state = _uiState.value
        val page = state.activePage ?: return
        val targetId = explicitBlockId ?: state.activeBlockId
        val paragraph = (if (targetId != null) page.blocks.firstOrNull { it.id == targetId } as? DocumentBlock.Paragraph else null)
            ?: page.blocks.filterIsInstance<DocumentBlock.Paragraph>().lastOrNull() ?: return
        val s = explicitStart ?: if (state.activeBlockId == paragraph.id) state.selectionStart else 0
        val e = explicitEnd ?: if (state.activeBlockId == paragraph.id) state.selectionEnd else paragraph.text.length
        mutateActivePageBlocks(paragraph.id) { blocks ->
            blocks.map { if (it.id == paragraph.id && it is DocumentBlock.Paragraph) RichSpanEngine.toggleSelectionSpan(it, s, e, kind, url) else it }
        }
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
    }

    fun moveBlock(blockId: String, delta: Int) = mutateActivePageBlocks(blockId) { BlockMutationEngine.moveBlock(it, blockId, delta) }
    fun updateBlockText(blockId: String, newText: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateBlockText(it, blockId, newText) }
    private fun updateAttachmentCaption(blockId: String, caption: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateAttachmentCaption(it, blockId, caption) }
    fun toggleParagraphStyle(blockId: String, style: String) = mutateActivePageBlocks(blockId) { BlockMutationEngine.toggleParagraphStyle(it, blockId, style) }
    fun updateHeadingLevel(blockId: String, level: Int) = mutateActivePageBlocks(blockId) { BlockMutationEngine.updateHeadingLevel(it, blockId, level) }
    fun updateChecklistTitle(blockId: String, title: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateChecklistTitle(it, blockId, title) }
    fun updateChecklistItemText(blockId: String, itemId: String, text: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateChecklistItemText(it, blockId, itemId, text) }
    fun deleteChecklistItem(blockId: String, itemId: String) = mutateActivePageBlocks(blockId) { BlockMutationEngine.deleteChecklistItem(it, blockId, itemId) }
    fun insertChecklistItemAfter(blockId: String, afterItemId: String? = null) = mutateActivePageBlocks(blockId) {
        BlockMutationEngine.insertChecklistItemAfter(it, blockId, ChecklistItem(nextBlockId(), "", false), afterItemId)
    }
    fun toggleBulletListKind(blockId: String) = mutateActivePageBlocks(blockId) { BlockMutationEngine.toggleBulletListKind(it, blockId) }
    fun insertBulletListItemAfter(blockId: String, afterItemIndex: Int? = null) = mutateActivePageBlocks(blockId) { BlockMutationEngine.insertBulletListItemAfter(it, blockId, afterItemIndex) }
    fun updateBulletListItem(blockId: String, itemIndex: Int, text: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateBulletListItem(it, blockId, itemIndex, text) }
    fun deleteBulletListItem(blockId: String, itemIndex: Int) = mutateActivePageBlocks(blockId) { BlockMutationEngine.deleteBulletListItem(it, blockId, itemIndex) }
    fun updateTableHeader(blockId: String, colIndex: Int, title: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateTableHeader(it, blockId, colIndex, title) }
    fun updateTableCell(blockId: String, rowIndex: Int, colIndex: Int, text: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateTableCell(it, blockId, rowIndex, colIndex, text) }
    fun addTableRow(blockId: String, rowData: List<String> = emptyList()) = mutateActivePageBlocks(blockId) { BlockMutationEngine.addTableRow(it, blockId, rowData) }
    fun deleteTableRow(blockId: String, rowIndex: Int) = mutateActivePageBlocks(blockId) { BlockMutationEngine.deleteTableRow(it, blockId, rowIndex) }
    fun addTableColumn(blockId: String, headerTitle: String, defaultCellText: String = "") = mutateActivePageBlocks(blockId) { BlockMutationEngine.addTableColumn(it, blockId, headerTitle, defaultCellText) }
    fun deleteTableColumn(blockId: String, colIndex: Int) = mutateActivePageBlocks(blockId) { BlockMutationEngine.deleteTableColumn(it, blockId, colIndex) }
    fun updateCalloutTitle(blockId: String, title: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateCalloutTitle(it, blockId, title) }
    fun updateCalloutTone(blockId: String, tone: CalloutTone) = mutateActivePageBlocks(blockId) { BlockMutationEngine.updateCalloutTone(it, blockId, tone) }
    fun updateCodeLanguage(blockId: String, language: String) = mutateActivePageBlocks(blockId) { BlockMutationEngine.updateCodeLanguage(it, blockId, language) }

    fun toggleActiveParagraphStyle(style: String) {
        val kind = when (style.lowercase()) {
            "bold" -> RichSpanKind.BOLD
            "italic" -> RichSpanKind.ITALIC
            "underline" -> RichSpanKind.UNDERLINE
            "strikethrough", "strike" -> RichSpanKind.STRIKETHROUGH
            "highlight" -> RichSpanKind.HIGHLIGHT
            "code" -> RichSpanKind.INLINE_CODE
            "accent" -> RichSpanKind.COLOR_ACCENT
            else -> return
        }
        toggleSelectionSpan(kind, null, null, null, null)
    }

    fun deleteBlock(blockId: String) {
        val page = _uiState.value.activePage ?: return
        undoRedo.recordBeforeMutation(page.id, page.blocks)
        repository.updatePageBlocks(page.id, page.blocks.filterNot { it.id == blockId })
        syncStateFromRepository()
        if (_uiState.value.activeBlockId == blockId) _uiState.update { it.copy(activeBlockId = null) }
        scheduleAutoSyncIfEnabled()
    }

    fun toggleChecklistItem(blockId: String, itemId: String) {
        val page = _uiState.value.activePage ?: return
        undoRedo.recordBeforeMutation(page.id, page.blocks)
        repository.toggleChecklistItem(page.id, blockId, itemId)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun addChecklistItem(blockId: String, text: String) {
        val page = _uiState.value.activePage ?: return
        if (text.trim().isEmpty()) return
        undoRedo.recordBeforeMutation(page.id, page.blocks)
        repository.addChecklistItem(page.id, blockId, text)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun addBulletListItem(blockId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        mutateActivePageBlocks(blockId) { BlockMutationEngine.addBulletListItem(it, blockId, clean) }
    }

    private fun insertBlock(block: DocumentBlock, afterBlockId: String? = null, focusInserted: Boolean = true) {
        val page = _uiState.value.activePage ?: return
        undoRedo.recordBeforeMutation(page.id, page.blocks)
        if (afterBlockId == null) repository.appendBlock(page.id, block)
        else repository.updatePageBlocks(page.id, BlockMutationEngine.insertBlock(page.blocks, block, afterBlockId))
        syncStateFromRepository()
        val req = if (focusInserted) {
            val subIdx = if (block is DocumentBlock.Checklist || block is DocumentBlock.BulletList) 0 else -1
            EditorFocusRequest(block.id, subItemIndex = subIdx, cursorOffset = Int.MAX_VALUE, requestToken = nextFocusToken())
        } else null
        _uiState.update { it.copy(activeBlockId = block.id, focusRequest = req ?: it.focusRequest, isMobileInsertMenuOpen = false) }
        scheduleAutoSyncIfEnabled()
    }

    fun insertHeadingBlock(level: Int = 2, afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultHeading(nextBlockId(), level), afterBlockId)
    fun insertParagraphBlock(afterBlockId: String? = null) = insertBlock(DocumentBlock.Paragraph(nextBlockId(), ""), afterBlockId)
    fun insertChecklistBlock(afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultChecklist(nextBlockId(), nextBlockId()), afterBlockId)
    fun insertTableBlock(afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultTable(nextBlockId()), afterBlockId, focusInserted = false)
    fun insertCalloutBlock(afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultCallout(nextBlockId()), afterBlockId)
    fun insertCodeBlock(afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultCode(nextBlockId()), afterBlockId)
    fun insertBulletListBlock(isNumbered: Boolean = false, afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultBulletList(nextBlockId(), isNumbered), afterBlockId)
    fun insertDividerBlock(afterBlockId: String? = null) = insertBlock(DocumentBlock.Divider(nextBlockId()), afterBlockId, focusInserted = false)
    fun insertAttachmentBlock(fileName: String, mimeType: String, sizeBytes: Long, dataBase64: String, afterBlockId: String? = null) =
        insertBlock(DocumentBlock.Attachment(nextBlockId(), fileName, mimeType, sizeBytes, dataBase64), afterBlockId, focusInserted = false)

    private fun saveSyncAndAuthSettings(endpointUrl: String, useRemoteHttp: Boolean, authToken: String, autoSync: Boolean) {
        val cleanUrl = endpointUrl.trim().ifEmpty { "https://notes.overbay.app/api/v1/sync" }
        val cleanToken = authToken.trim()
        repository.updatePreferences {
            it.copy(syncEndpointUrl = cleanUrl, useRemoteHttpSync = useRemoteHttp, syncAuthToken = cleanToken, autoSyncOnEdit = autoSync)
        }
        val provider = if (useRemoteHttp) HttpSyncProvider(cleanUrl, authTokenProvider = { repository.preferences.value.syncAuthToken })
        else LocalLoopbackSyncProvider(cleanUrl)
        repository.configureSyncProvider(provider)
        _uiState.update { it.copy(syncEndpointUrl = cleanUrl, useRemoteHttpSync = useRemoteHttp, activeDialog = ActiveDialog.NONE) }
        triggerManualSync()
    }

    fun configureSyncServer(endpointUrl: String, useRemoteHttp: Boolean) {
        val prefs = repository.preferences.value
        saveSyncAndAuthSettings(endpointUrl, useRemoteHttp, prefs.syncAuthToken, prefs.autoSyncOnEdit)
    }

    fun triggerManualSync() {
        scope.launch {
            _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING) }
            val result = repository.syncWithServer()
            _uiState.update { it.copy(syncStatus = if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE) }
        }
    }
}
