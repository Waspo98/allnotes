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
        uiState.update { it.copy(structureRevision = it.structureRevision + 1L) }
        scheduleAutoSync()
    }

    fun performRedo() {
        val page = uiState.value.activePage ?: return
        val restored = undoRedo.redo(page.id, page.blocks) ?: return
        repository.updatePageBlocks(page.id, restored, debounced = false)
        syncState()
        uiState.update { it.copy(structureRevision = it.structureRevision + 1L) }
        scheduleAutoSync()
    }

    fun applyEditorResult(
        debounced: Boolean,
        updatedSelection: Pair<Int, Int>? = null,
        compute: (List<DocumentBlock>) -> EditorMutationResult
    ) {
        val page = uiState.value.activePage ?: return
        val res = compute(page.blocks)
        if (res.blocks == page.blocks && res.focusRequest == null) {
            if (updatedSelection != null && !res.isStructuralConversion) {
                uiState.update {
                    it.copy(
                        activeBlockId = res.activeBlockId ?: it.activeBlockId,
                        selectionStart = minOf(updatedSelection.first, updatedSelection.second),
                        selectionEnd = maxOf(updatedSelection.first, updatedSelection.second)
                    )
                }
            }
            return
        }
        if (res.blocks != page.blocks) {
            val coalesce = debounced && !res.isStructuralConversion
            undoRedo.recordBeforeMutation(page.id, page.blocks, coalesce, res.activeBlockId, currentEpochMillis())
            repository.updatePageBlocks(page.id, res.blocks, debounced = coalesce)
            syncState()
            scheduleAutoSync()
        }
        uiState.update {
            val nextStructureRev = if (res.isStructuralConversion) it.structureRevision + 1L else it.structureRevision
            val targetPara = res.focusRequest?.let { req ->
                res.blocks.firstOrNull { b -> b.id == req.blockId } as? DocumentBlock.Paragraph
            }
            val (selStart, selEnd) = when {
                updatedSelection != null && !res.isStructuralConversion -> {
                    minOf(updatedSelection.first, updatedSelection.second) to
                        maxOf(updatedSelection.first, updatedSelection.second)
                }
                res.focusRequest != null && targetPara != null -> {
                    val clamped = res.focusRequest.cursorOffset.coerceIn(0, targetPara.text.length)
                    clamped to clamped
                }
                else -> it.selectionStart to it.selectionEnd
            }
            it.copy(
                activeBlockId = res.activeBlockId ?: it.activeBlockId,
                focusRequest = res.focusRequest ?: it.focusRequest,
                structureRevision = nextStructureRev,
                selectionStart = selStart,
                selectionEnd = selEnd
            )
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
        uiState.update {
            it.copy(
                activeBlockId = keepBlockActive ?: it.activeBlockId,
                structureRevision = if (!debounced) it.structureRevision + 1L else it.structureRevision,
                canUndo = undoRedo.canUndo(page.id),
                canRedo = undoRedo.canRedo(page.id)
            )
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
        val state = uiState.value
        val page = state.activePage ?: return
        if (afterBlockId != null && afterBlockId == state.activeBlockId) {
            val idx = page.blocks.indexOfFirst { it.id == afterBlockId }
            val activePara = page.blocks.getOrNull(idx) as? DocumentBlock.Paragraph
            if (activePara != null) {
                if (block is DocumentBlock.Paragraph) {
                    insertNewlineIntoParagraph(page.id, page.blocks, idx, activePara, state.selectionEnd)
                } else {
                    val res = ParagraphFlowEngine.insertBlockIntoParagraphAtCursor(
                        blocks = page.blocks,
                        paragraphIndex = idx,
                        newBlock = block,
                        cursorOffset = state.selectionEnd,
                        nextId = nextBlockId,
                        nextToken = nextFocusToken,
                        focusInserted = focusInserted
                    )
                    undoRedo.recordBeforeMutation(page.id, page.blocks)
                    repository.updatePageBlocks(page.id, res.blocks, debounced = false)
                    syncState()
                    uiState.update {
                        it.copy(
                            activeBlockId = res.activeBlockId ?: block.id,
                            focusRequest = res.focusRequest ?: it.focusRequest,
                            structureRevision = it.structureRevision + 1L,
                            isBlockInsertSheetOpen = false
                        )
                    }
                    scheduleAutoSync()
                }
                return
            }
        }

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
                structureRevision = it.structureRevision + 1L,
                isBlockInsertSheetOpen = false
            )
        }
        scheduleAutoSync()
    }

    private fun insertNewlineIntoParagraph(
        pageId: String,
        blocks: List<DocumentBlock>,
        idx: Int,
        activePara: DocumentBlock.Paragraph,
        selectionEnd: Int
    ) {
        if (activePara.text.isEmpty()) {
            val req = EditorFocusRequest(activePara.id, cursorOffset = 0, requestToken = nextFocusToken())
            uiState.update {
                it.copy(
                    activeBlockId = activePara.id,
                    focusRequest = req,
                    isBlockInsertSheetOpen = false
                )
            }
            return
        }
        val caret = selectionEnd.coerceIn(0, activePara.text.length)
        val nextText = activePara.text.substring(0, caret) + "\n" + activePara.text.substring(caret)
        val nextCaret = caret + 1
        val updatedPara = activePara.copy(
            text = nextText,
            spans = RichSpanEngine.adjustSpansAfterEdit(activePara.text, nextText, activePara.spans)
        )
        val updatedBlocks = blocks.toMutableList().apply { this[idx] = updatedPara }
        undoRedo.recordBeforeMutation(pageId, blocks)
        repository.updatePageBlocks(pageId, updatedBlocks, debounced = false)
        syncState()
        val req = EditorFocusRequest(activePara.id, cursorOffset = nextCaret, requestToken = nextFocusToken())
        uiState.update {
            it.copy(
                activeBlockId = activePara.id,
                selectionStart = nextCaret,
                selectionEnd = nextCaret,
                focusRequest = req,
                structureRevision = it.structureRevision + 1L,
                isBlockInsertSheetOpen = false
            )
        }
        scheduleAutoSync()
    }
}
