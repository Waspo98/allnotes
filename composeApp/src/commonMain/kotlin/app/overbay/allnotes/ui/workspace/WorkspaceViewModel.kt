package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.SyncStatus
import app.overbay.allnotes.data.markdown.MarkdownCodec
import app.overbay.allnotes.data.platformSynchronized
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NoteSectionDisplayComparator
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val repository: NoteRepository = NoteRepository(typingDebounceMs = 300L, persistOnBackground = true),
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)
) {
    private val seqLock = Any()
    private var blockSeq = 5000L
    private var focusTokenSeq = 1L

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()
    val preferencesFlow: StateFlow<app.overbay.allnotes.model.WorkspacePreferences> = repository.preferences

    private val editorCoordinator = WorkspaceEditorCoordinator(
        repository = repository,
        uiState = _uiState,
        nextBlockId = ::nextBlockId,
        nextFocusToken = ::nextFocusToken,
        syncState = ::syncStateFromRepository,
        scheduleAutoSync = ::scheduleAutoSyncIfEnabled
    )

    private val syncController = WorkspaceSyncController(
        repository = repository,
        scope = scope,
        onStatusChange = { status -> _uiState.update { it.copy(syncStatus = status) } },
        onSettingsSaved = { endpoint, remote ->
            _uiState.update {
                it.copy(syncEndpointUrl = endpoint, useRemoteHttpSync = remote, activeDialog = ActiveDialog.NONE)
            }
        }
    )

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
        scope.launch {
            repository.storageErrors.collect { errMsg ->
                _uiState.update {
                    it.copy(statusBannerMessage = errMsg, statusBannerActionLabel = null, statusBannerAction = null)
                }
            }
        }
        syncController.syncOnLaunchIfEnabled()
    }

    private fun buildInitialState(): WorkspaceUiState {
        val snap = repository.snapshot.value
        val prefs = repository.preferences.value
        val nbId = snap.notebooks.firstOrNull()?.id.orEmpty()
        val secId = snap.sections
            .filter { it.notebookId == nbId }
            .sortedWith(NoteSectionDisplayComparator)
            .firstOrNull()?.id.orEmpty()
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
    internal fun updateUiState(transform: (WorkspaceUiState) -> WorkspaceUiState) = _uiState.update(transform)
    internal fun nextFocusToken(): Long = platformSynchronized(seqLock) { ++focusTokenSeq }

    @OptIn(ExperimentalUuidApi::class)
    internal fun nextBlockId(): String {
        val seq = platformSynchronized(seqLock) { ++blockSeq }
        return "blk-${_uiState.value.revision}-$seq-${Uuid.random().toHexString().take(6)}"
    }

    fun dispatch(action: WorkspaceAction) = WorkspaceActionDispatcher.dispatch(this, action)

    internal fun updateThemeSettings(themeMode: ThemeMode, useDynamicColor: Boolean) {
        repository.updatePreferences {
            it.copy(themeMode = themeMode, useDynamicColor = useDynamicColor)
        }
    }

    internal fun setShowSectionGradient(enabled: Boolean) {
        repository.updatePreferences {
            it.copy(showSectionGradient = enabled)
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
            resolved.copy(
                canUndo = editorCoordinator.canUndo(activePageId),
                canRedo = editorCoordinator.canRedo(activePageId)
            )
        }
    }

    private fun scheduleAutoSyncIfEnabled() {
        syncController.scheduleAutoSyncIfEnabled(_uiState.value.preferences)
    }

    fun selectNotebook(notebookId: String) {
        val snap = repository.snapshot.value
        val firstSec = snap.sections
            .filter { it.notebookId == notebookId }
            .sortedWith(NoteSectionDisplayComparator)
            .firstOrNull()?.id.orEmpty()
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
                mobileAccessoryMenu = MobileAccessoryMenu.NONE,
                collapsedParentPageIds = if (parentToUncollapse != null) it.collapsedParentPageIds - parentToUncollapse else it.collapsedParentPageIds
            )
        }
    }

    fun setSearchQuery(query: String) {
        if (repository.snapshot.value.revision > _uiState.value.revision) {
            syncStateFromRepository()
        }
        _uiState.update { it.copy(searchQuery = query) }
    }

    internal fun requestSearchFocus() = _uiState.update {
        it.copy(
            isSidebarOpen = true,
            isMobileHierarchySheetOpen = true,
            searchFocusRequestToken = nextFocusToken()
        )
    }

    fun toggleSidebar() = _uiState.update { it.copy(isSidebarOpen = !it.isSidebarOpen) }
    fun setMobileHierarchySheetOpen(open: Boolean) = _uiState.update { it.copy(isMobileHierarchySheetOpen = open) }
    fun openDialog(dialog: ActiveDialog) = _uiState.update {
        it.copy(
            activeDialog = dialog,
            editingSectionId = if (dialog == ActiveDialog.CONFIRM_DELETE_SECTION) it.editingSectionId else null,
            isPageMenuOpen = false,
            mobileAccessoryMenu = MobileAccessoryMenu.NONE,
            isMobileHierarchySheetOpen = false
        )
    }

    internal fun openEditSectionDialog(sectionId: String) = _uiState.update {
        it.copy(
            activeDialog = ActiveDialog.EDIT_SECTION,
            editingSectionId = sectionId,
            isPageMenuOpen = false,
            mobileAccessoryMenu = MobileAccessoryMenu.NONE,
            isMobileHierarchySheetOpen = false
        )
    }

    fun dismissDialog() = _uiState.update {
        it.copy(activeDialog = ActiveDialog.NONE, pendingDeletePageId = null, editingSectionId = null)
    }

    internal fun setMobileAccessoryMenu(menu: MobileAccessoryMenu) = _uiState.update {
        it.copy(
            mobileAccessoryMenu = if (it.activeBlockId == null) MobileAccessoryMenu.NONE else menu
        )
    }

    fun setActiveBlockId(blockId: String?) = _uiState.update {
        it.copy(
            activeBlockId = blockId,
            mobileAccessoryMenu = if (blockId == null || blockId != it.activeBlockId) {
                MobileAccessoryMenu.NONE
            } else {
                it.mobileAccessoryMenu
            }
        )
    }

    internal fun focusOrAppendTrailingParagraph() = editorCoordinator.focusOrAppendTrailingParagraph()

    private inline fun commitHierarchyMutation(closeDialog: Boolean = false, mutate: () -> Unit) {
        mutate()
        if (closeDialog) _uiState.update { it.copy(activeDialog = ActiveDialog.NONE, editingSectionId = null) }
        syncStateFromRepository(); scheduleAutoSyncIfEnabled()
    }

    fun createNotebook(title: String, subtitle: String, accent: SectionAccentKey) {
        val created = repository.createNotebook(title, subtitle, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        selectNotebook(created.id); scheduleAutoSyncIfEnabled()
    }

    fun updateActiveNotebook(title: String, subtitle: String, accent: SectionAccentKey) {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        commitHierarchyMutation(closeDialog = true) { repository.updateNotebook(nbId, title, subtitle, accent) }
    }

    fun reorderActiveNotebook(delta: Int) {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        commitHierarchyMutation { repository.reorderNotebook(nbId, delta) }
    }

    fun deleteActiveNotebook() {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        commitHierarchyMutation(closeDialog = true) { repository.deleteNotebook(nbId) }
    }

    fun createSection(title: String, accent: SectionAccentKey) {
        val nbId = _uiState.value.activeNotebook?.id ?: return
        val created = repository.createSection(nbId, title, accent)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
        selectSection(created.id); scheduleAutoSyncIfEnabled()
    }

    fun updateSection(sectionId: String, title: String, accent: SectionAccentKey, isPinned: Boolean? = null) =
        commitHierarchyMutation(closeDialog = true) { repository.updateSection(sectionId, title, accent, isPinned) }

    fun toggleSectionPin(sectionId: String) =
        commitHierarchyMutation { repository.toggleSectionPin(sectionId) }

    fun reorderSection(sectionId: String, delta: Int) =
        commitHierarchyMutation { repository.reorderSection(sectionId, delta) }

    fun deleteSection(sectionId: String) =
        commitHierarchyMutation(closeDialog = true) { repository.deleteSection(sectionId) }

    fun createPageInstant(asSubpageOfActive: Boolean = false) {
        val secId = _uiState.value.activeSection?.id ?: return
        val parentId = if (asSubpageOfActive) _uiState.value.activePage?.let { it.parentPageId ?: it.id } else null
        val created = repository.createPage(secId, "", parentId, allowBlankTitle = true)
        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.NONE, isMobileHierarchySheetOpen = false,
                isPageMenuOpen = false, isBlockInsertSheetOpen = false,
                collapsedParentPageIds = if (parentId != null) it.collapsedParentPageIds - parentId else it.collapsedParentPageIds
            )
        }
        selectPage(created.id)
        _uiState.update { it.copy(activeBlockId = null, titleFocusRequestToken = nextFocusToken()) }
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

    internal fun toggleSubpageCollapse(parentPageId: String) {
        _uiState.update { state ->
            val activeChildParent = state.activePage?.parentPageId
            val nextSet = if (parentPageId in state.collapsedParentPageIds) state.collapsedParentPageIds - parentPageId
            else state.collapsedParentPageIds + parentPageId
            val nextPageId = if (parentPageId in nextSet && activeChildParent == parentPageId) parentPageId else state.selectedPageId
            state.copy(collapsedParentPageIds = nextSet, selectedPageId = nextPageId)
        }
    }

    internal fun moveActivePageToSection(targetSectionId: String) {
        val pageId = _uiState.value.activePage?.id ?: return
        repository.movePageToSection(pageId, targetSectionId)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE, isPageMenuOpen = false) }
        syncStateFromRepository(targetPageId = pageId)
        scheduleAutoSyncIfEnabled()
    }

    internal fun importMarkdownPage(markdown: String) {
        val secId = _uiState.value.activeSection?.id ?: return
        if (markdown.isBlank()) return
        val imported = MarkdownCodec.importMarkdownToPage(markdown, secId, repository::nextId, repository.nextTimestampMs())
        repository.insertConstructedPage(imported)
        _uiState.update { it.copy(activeDialog = ActiveDialog.NONE, statusBannerMessage = "Imported \"${imported.title}\"") }
        selectPage(imported.id)
        scheduleAutoSyncIfEnabled()
    }

    internal fun requestDeletePage(pageId: String) {
        if (_uiState.value.pages.none { it.id == pageId }) return
        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.CONFIRM_DELETE_PAGE,
                pendingDeletePageId = pageId,
                isPageMenuOpen = false,
                mobileAccessoryMenu = MobileAccessoryMenu.NONE,
                isMobileHierarchySheetOpen = false
            )
        }
    }

    fun deleteActivePage() {
        val pageId = _uiState.value.activePage?.id ?: return
        deletePage(pageId)
    }

    fun deletePage(pageId: String) {
        editorCoordinator.clearPage(pageId)
        repository.deletePage(pageId)
        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.NONE,
                pendingDeletePageId = null,
                isPageMenuOpen = false
            )
        }
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun toggleActivePagePin() {
        val pageId = _uiState.value.activePage?.id ?: return
        togglePinPage(pageId)
    }

    fun togglePinPage(pageId: String) {
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

    internal fun performUndo() = editorCoordinator.performUndo()
    internal fun performRedo() = editorCoordinator.performRedo()
    internal fun applyEditorResult(
        debounced: Boolean,
        updatedSelection: Pair<Int, Int>? = null,
        compute: (List<DocumentBlock>) -> EditorMutationResult
    ) = editorCoordinator.applyEditorResult(debounced, updatedSelection, compute)
    private fun mutateActivePageBlocks(
        keepBlockActive: String? = null,
        debounced: Boolean = false,
        transform: (List<DocumentBlock>) -> List<DocumentBlock>
    ) = editorCoordinator.mutateActivePageBlocks(keepBlockActive, debounced, transform)
    internal fun toggleSelectionSpan(kind: RichSpanKind, url: String?, explicitBlockId: String?, explicitStart: Int?, explicitEnd: Int?) =
        editorCoordinator.toggleSelectionSpan(kind, url, explicitBlockId, explicitStart, explicitEnd)

    fun moveBlock(blockId: String, delta: Int) = mutateActivePageBlocks(blockId) { BlockMutationEngine.moveBlock(it, blockId, delta) }
    fun updateBlockText(blockId: String, newText: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateBlockText(it, blockId, newText) }
    internal fun updateAttachmentCaption(blockId: String, caption: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateAttachmentCaption(it, blockId, caption) }
    fun toggleParagraphStyle(blockId: String, style: String) = mutateActivePageBlocks(blockId) { BlockMutationEngine.toggleParagraphStyle(it, blockId, style) }
    fun updateHeadingLevel(blockId: String, level: Int) = mutateActivePageBlocks(blockId) { BlockMutationEngine.updateHeadingLevel(it, blockId, level) }
    fun updateChecklistTitle(blockId: String, title: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateChecklistTitle(it, blockId, title) }
    fun updateChecklistItemText(blockId: String, itemId: String, text: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateChecklistItemText(it, blockId, itemId, text) }
    fun deleteChecklistItem(blockId: String, itemId: String) = mutateActivePageBlocks(blockId) { BlockMutationEngine.deleteChecklistItem(it, blockId, itemId) }
    fun insertChecklistItemAfter(blockId: String, afterItemId: String? = null) = applyEditorResult(debounced = false) { blocks ->
        val newItem = ChecklistItem(nextBlockId(), "", false)
        val updated = BlockMutationEngine.insertChecklistItemAfter(blocks, blockId, newItem, afterItemId)
        val insertedIdx = (updated.firstOrNull { it.id == blockId } as? DocumentBlock.Checklist)
            ?.items?.indexOfFirst { it.id == newItem.id } ?: -1
        val focusReq = if (insertedIdx >= 0) {
            EditorFocusRequest(blockId = blockId, subItemIndex = insertedIdx, cursorOffset = 0, requestToken = nextFocusToken())
        } else null
        EditorMutationResult(blocks = updated, activeBlockId = blockId, focusRequest = focusReq)
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

    fun clearActiveParagraphFormatting() {
        val state = _uiState.value
        val targetId = state.activeBlockId ?: return
        val activePara = state.activePage?.blocks?.firstOrNull { it.id == targetId } as? DocumentBlock.Paragraph
        if (activePara == null) {
            mutateActivePageBlocks(targetId) { BlockMutationEngine.clearParagraphFormatting(it, targetId) }
            return
        }
        mutateActivePageBlocks(targetId) { blocks ->
            blocks.map { block ->
                if (block.id != targetId || block !is DocumentBlock.Paragraph) return@map block
                if (state.selectionStart != state.selectionEnd) {
                    RichSpanEngine.clearSpansInRange(block, state.selectionStart, state.selectionEnd)
                } else {
                    val line = ParagraphFlowEngine.lineBoundsAt(block.text, state.selectionStart)
                    val clearedLine = if (line.start < line.end) {
                        RichSpanEngine.clearSpansInRange(block, line.start, line.end)
                    } else block
                    if (clearedLine != block) {
                        clearedLine
                    } else {
                        BlockMutationEngine.clearParagraphFormatting(listOf(block), targetId).single()
                    }
                }
            }
        }
    }

    fun convertActiveBlock(command: SlashCommandKind) {
        val state = _uiState.value
        val blockId = state.activeBlockId ?: return
        applyEditorResult(debounced = false) {
            EditorInputController.convertBlockWithFocus(
                blocks = it,
                blockId = blockId,
                target = command,
                nextId = ::nextBlockId,
                nextToken = ::nextFocusToken,
                selectionStart = state.selectionStart,
                selectionEnd = state.selectionEnd
            )
        }
    }

    fun deleteBlock(blockId: String) {
        val page = _uiState.value.activePage ?: return
        val idx = page.blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return
        editorCoordinator.recordBeforeMutation(page.id, page.blocks)
        val updated = ParagraphFlowEngine.coalesceAcrossRemovedIndex(page.blocks, idx)
        repository.updatePageBlocks(page.id, updated)
        syncStateFromRepository()
        _uiState.update {
            it.copy(
                activeBlockId = if (it.activeBlockId == blockId || updated.none { b -> b.id == it.activeBlockId }) null else it.activeBlockId,
                structureRevision = it.structureRevision + 1L,
                statusBannerMessage = "Block removed", statusBannerActionLabel = "Undo", statusBannerAction = WorkspaceAction.Undo
            )
        }
        scheduleAutoSyncIfEnabled()
    }

    fun toggleChecklistItem(blockId: String, itemId: String) {
        val page = _uiState.value.activePage ?: return
        editorCoordinator.recordBeforeMutation(page.id, page.blocks)
        repository.toggleChecklistItem(page.id, blockId, itemId)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun addChecklistItem(blockId: String, text: String) {
        val page = _uiState.value.activePage ?: return
        if (text.trim().isEmpty()) return
        editorCoordinator.recordBeforeMutation(page.id, page.blocks)
        repository.addChecklistItem(page.id, blockId, text)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    fun addBulletListItem(blockId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        mutateActivePageBlocks(blockId) { BlockMutationEngine.addBulletListItem(it, blockId, clean) }
    }

    private fun insertBlock(block: DocumentBlock, afterBlockId: String? = null, focusInserted: Boolean = true) =
        editorCoordinator.insertBlock(block, afterBlockId, focusInserted)

    fun insertHeadingBlock(level: Int = 2, afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultHeading(nextBlockId(), level), afterBlockId)
    fun insertParagraphBlock(afterBlockId: String? = null) = insertBlock(DocumentBlock.Paragraph(nextBlockId(), ""), afterBlockId)
    fun insertChecklistBlock(afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultChecklist(nextBlockId(), nextBlockId()), afterBlockId)
    fun insertTableBlock(afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultTable(nextBlockId()), afterBlockId, focusInserted = false)
    fun insertCalloutBlock(afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultCallout(nextBlockId()), afterBlockId)
    fun insertCodeBlock(afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultCode(nextBlockId()), afterBlockId)
    fun insertBulletListBlock(isNumbered: Boolean = false, afterBlockId: String? = null) = insertBlock(BlockMutationEngine.defaultBulletList(nextBlockId(), isNumbered), afterBlockId)
    fun insertDividerBlock(afterBlockId: String? = null) = insertBlock(DocumentBlock.Divider(nextBlockId()), afterBlockId, focusInserted = false)
    fun insertAttachmentBlock(
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        dataBase64: String,
        afterBlockId: String? = null,
        caption: String = ""
    ) = insertBlock(
        block = DocumentBlock.Attachment(
            id = nextBlockId(),
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            caption = caption,
            dataBase64 = dataBase64
        ),
        afterBlockId = afterBlockId,
        focusInserted = false
    )

    internal fun saveSyncAndAuthSettings(endpointUrl: String, useRemoteHttp: Boolean, authToken: String, autoSync: Boolean) {
        syncController.saveSettingsAndSync(endpointUrl, useRemoteHttp, authToken, autoSync)
    }

    fun triggerManualSync() = syncController.triggerManualSync()
}
