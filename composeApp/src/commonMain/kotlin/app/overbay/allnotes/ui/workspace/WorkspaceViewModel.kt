package app.overbay.allnotes.ui.workspace

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
    private val undoRedo = UndoRedoController()
    private val seqLock = Any()
    private var blockSeq = 5000L
    private var focusTokenSeq = 1L

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()
    val preferencesFlow: StateFlow<app.overbay.allnotes.model.WorkspacePreferences> = repository.preferences

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
        syncController.syncOnLaunchIfEnabled()
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
        syncController.scheduleAutoSyncIfEnabled(_uiState.value.preferences)
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
            isPageMenuOpen = false,
            isMobileHierarchySheetOpen = false,
            isMobileInsertMenuOpen = false
        )
    }

    fun dismissDialog() = _uiState.update {
        it.copy(activeDialog = ActiveDialog.NONE, pendingDeletePageId = null)
    }

    fun setActiveBlockId(blockId: String?) = _uiState.update { it.copy(activeBlockId = blockId) }

    internal fun focusOrAppendTrailingParagraph() {
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

    fun createPageInstant(asSubpageOfActive: Boolean = false) {
        val secId = _uiState.value.activeSection?.id ?: return
        val parentId = if (asSubpageOfActive) {
            val active = _uiState.value.activePage
            active?.parentPageId ?: active?.id
        } else null
        val created = repository.createPage(secId, "", parentId)
        val blankTitlePage = created.copy(title = "")
        repository.updatePageTitle(created.id, "", debounced = false)
        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.NONE,
                isMobileHierarchySheetOpen = false,
                isPageMenuOpen = false,
                isMobileInsertMenuOpen = false,
                isBlockInsertSheetOpen = false,
                collapsedParentPageIds = if (parentId != null) it.collapsedParentPageIds - parentId else it.collapsedParentPageIds
            )
        }
        selectPage(blankTitlePage.id)
        _uiState.update {
            it.copy(
                activeBlockId = null,
                titleFocusRequestToken = nextFocusToken()
            )
        }
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
        val activeChildParent = _uiState.value.activePage?.parentPageId
        _uiState.update { state ->
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
                isMobileHierarchySheetOpen = false,
                isMobileInsertMenuOpen = false
            )
        }
    }

    fun deleteActivePage() {
        val pageId = _uiState.value.activePage?.id ?: return
        deletePage(pageId)
    }

    fun deletePage(pageId: String) {
        undoRedo.clearPage(pageId)
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

    internal fun performUndo() {
        val page = _uiState.value.activePage ?: return
        val restored = undoRedo.undo(page.id, page.blocks) ?: return
        repository.updatePageBlocks(page.id, restored, debounced = false)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    internal fun performRedo() {
        val page = _uiState.value.activePage ?: return
        val restored = undoRedo.redo(page.id, page.blocks) ?: return
        repository.updatePageBlocks(page.id, restored, debounced = false)
        syncStateFromRepository()
        scheduleAutoSyncIfEnabled()
    }

    internal fun applyEditorResult(debounced: Boolean, compute: (List<DocumentBlock>) -> EditorMutationResult) {
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

    internal fun toggleSelectionSpan(kind: RichSpanKind, url: String?, explicitBlockId: String?, explicitStart: Int?, explicitEnd: Int?) {
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
    internal fun updateAttachmentCaption(blockId: String, caption: String) = mutateActivePageBlocks(blockId, true) { BlockMutationEngine.updateAttachmentCaption(it, blockId, caption) }
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

    fun clearActiveParagraphFormatting() {
        val targetId = _uiState.value.activeBlockId ?: return
        mutateActivePageBlocks(targetId) { BlockMutationEngine.clearParagraphFormatting(it, targetId) }
    }

    fun convertActiveBlock(command: SlashCommandKind) {
        val blockId = _uiState.value.activeBlockId ?: return
        applyEditorResult(debounced = false) {
            EditorInputController.convertBlockWithFocus(it, blockId, command, ::nextBlockId, ::nextFocusToken)
        }
    }

    fun deleteBlock(blockId: String) {
        val page = _uiState.value.activePage ?: return
        if (page.blocks.none { it.id == blockId }) return
        undoRedo.recordBeforeMutation(page.id, page.blocks)
        repository.updatePageBlocks(page.id, page.blocks.filterNot { it.id == blockId })
        syncStateFromRepository()
        _uiState.update {
            it.copy(
                activeBlockId = if (it.activeBlockId == blockId) null else it.activeBlockId,
                statusBannerMessage = "Block removed",
                statusBannerActionLabel = "Undo",
                statusBannerAction = WorkspaceAction.Undo
            )
        }
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
        _uiState.update {
            it.copy(
                activeBlockId = block.id,
                focusRequest = req ?: it.focusRequest,
                isMobileInsertMenuOpen = false,
                isBlockInsertSheetOpen = false
            )
        }
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

    fun configureSyncServer(endpointUrl: String, useRemoteHttp: Boolean) {
        val prefs = repository.preferences.value
        syncController.saveSettingsAndSync(endpointUrl, useRemoteHttp, prefs.syncAuthToken, prefs.autoSyncOnEdit)
    }

    fun triggerManualSync() = syncController.triggerManualSync()
}
