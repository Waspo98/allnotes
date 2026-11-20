package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.currentEpochMillis
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Coordinates block-level editing, rich-span toggling, block insertion, and undo/redo history
 * for [WorkspaceViewModel].
 */
internal class WorkspaceEditorCoordinator(
    private val repository: NoteRepository,
    private val uiState: MutableStateFlow<WorkspaceUiState>,
    private val nextBlockId: () -> String,
    private val nextFocusToken: () -> Long,
    private val syncState: () -> Unit,
    private val scheduleAutoSync: () -> Unit
) {
    private val undoRedo = UndoRedoController()

    fun canUndo(pageId: String?): Boolean = undoRedo.canUndo(pageId)
    fun canRedo(pageId: String?): Boolean = undoRedo.canRedo(pageId)
    fun clearPage(pageId: String) = undoRedo.clearPage(pageId)

    fun recordBeforeMutation(pageId: String, blocks: List<DocumentBlock>) {
        undoRedo.recordBeforeMutation(pageId, blocks)
    }

    fun focusOrAppendTrailingParagraph() {
        val page = uiState.value.activePage ?: return
        val last = page.blocks.lastOrNull()
        if (last is DocumentBlock.Paragraph) {
            val req = EditorFocusRequest(last.id, cursorOffset = last.text.length, requestToken = nextFocusToken())
            uiState.update { it.copy(activeBlockId = last.id, focusRequest = req) }
        } else {
            val newBlock = DocumentBlock.Paragraph(id = nextBlockId(), text = "")
            undoRedo.recordBeforeMutation(page.id, page.blocks)
            repository.appendBlock(page.id, newBlock)
            syncState()
            val req = EditorFocusRequest(newBlock.id, cursorOffset = 0, requestToken = nextFocusToken())
            uiState.update { it.copy(activeBlockId = newBlock.id, focusRequest = req) }
            scheduleAutoSync()
        }
    }

    fun performUndo() {
        val page = uiState.value.activePage ?: return
        val restored = undoRedo.undo(page.id, page.blocks) ?: return
        repository.updatePageBlocks(page.id, restored, debounced = false)
        syncState()
        scheduleAutoSync()
    }

    fun performRedo() {
        val page = uiState.value.activePage ?: return
        val restored = undoRedo.redo(page.id, page.blocks) ?: return
        repository.updatePageBlocks(page.id, restored, debounced = false)
        syncState()
        scheduleAutoSync()
    }

    fun applyEditorResult(debounced: Boolean, compute: (List<DocumentBlock>) -> EditorMutationResult) {
        val page = uiState.value.activePage ?: return
        val res = compute(page.blocks)
        if (res.blocks == page.blocks && res.focusRequest == null) return
        if (res.blocks != page.blocks) {
            val coalesce = debounced && !res.isStructuralConversion
            undoRedo.recordBeforeMutation(page.id, page.blocks, coalesce, res.activeBlockId, currentEpochMillis())
            repository.updatePageBlocks(page.id, res.blocks, debounced = coalesce)
            syncState()
            scheduleAutoSync()
        }
        uiState.update {
            it.copy(activeBlockId = res.activeBlockId ?: it.activeBlockId, focusRequest = res.focusRequest ?: it.focusRequest)
        }
    }

    fun mutateActivePageBlocks(
        keepBlockActive: String? = null,
        debounced: Boolean = false,
        transform: (List<DocumentBlock>) -> List<DocumentBlock>
    ) {
        val page = uiState.value.activePage ?: return
        val updatedBlocks = transform(page.blocks)
        if (updatedBlocks == page.blocks) return
        undoRedo.recordBeforeMutation(page.id, page.blocks, debounced, keepBlockActive ?: uiState.value.activeBlockId, currentEpochMillis())
        repository.updatePageBlocks(page.id, updatedBlocks, debounced = debounced)
        syncState()
        if (keepBlockActive != null) {
            uiState.update { it.copy(activeBlockId = keepBlockActive, canUndo = undoRedo.canUndo(page.id), canRedo = undoRedo.canRedo(page.id)) }
        }
        scheduleAutoSync()
    }

    fun toggleSelectionSpan(kind: RichSpanKind, url: String?, explicitBlockId: String?, explicitStart: Int?, explicitEnd: Int?) {
        val state = uiState.value
        val page = state.activePage ?: return
        val targetId = explicitBlockId ?: state.activeBlockId
        val paragraph = (if (targetId != null) page.blocks.firstOrNull { it.id == targetId } as? DocumentBlock.Paragraph else null)
            ?: page.blocks.filterIsInstance<DocumentBlock.Paragraph>().lastOrNull() ?: return
        val s = explicitStart ?: if (state.activeBlockId == paragraph.id) state.selectionStart else 0
        val e = explicitEnd ?: if (state.activeBlockId == paragraph.id) state.selectionEnd else paragraph.text.length
        mutateActivePageBlocks(paragraph.id) { blocks ->
            blocks.map { if (it.id == paragraph.id && it is DocumentBlock.Paragraph) RichSpanEngine.toggleSelectionSpan(it, s, e, kind, url) else it }
        }
        uiState.update { it.copy(activeDialog = ActiveDialog.NONE) }
    }

    fun insertBlock(block: DocumentBlock, afterBlockId: String? = null, focusInserted: Boolean = true) {
        val page = uiState.value.activePage ?: return
        undoRedo.recordBeforeMutation(page.id, page.blocks)
        if (afterBlockId == null) repository.appendBlock(page.id, block)
        else repository.updatePageBlocks(page.id, BlockMutationEngine.insertBlock(page.blocks, block, afterBlockId))
        syncState()
        val req = if (focusInserted) {
            val subIdx = if (block is DocumentBlock.Checklist || block is DocumentBlock.BulletList) 0 else -1
            EditorFocusRequest(block.id, subItemIndex = subIdx, cursorOffset = Int.MAX_VALUE, requestToken = nextFocusToken())
        } else null
        uiState.update {
            it.copy(
                activeBlockId = block.id,
                focusRequest = req ?: it.focusRequest,
                isBlockInsertSheetOpen = false
            )
        }
        scheduleAutoSync()
    }
}
