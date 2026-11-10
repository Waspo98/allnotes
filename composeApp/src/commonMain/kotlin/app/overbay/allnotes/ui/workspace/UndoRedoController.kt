package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.platformSynchronized
import app.overbay.allnotes.model.DocumentBlock

/**
 * Bounded per-page undo/redo history controller with typing-burst coalescing.
 */
class UndoRedoController(
    private val maxHistoryPerPage: Int = 50,
    private val typingCoalesceWindowMs: Long = 650L
) {
    private val lock = Any()
    private val undoStacks = LinkedHashMap<String, ArrayDeque<List<DocumentBlock>>>()
    private val redoStacks = LinkedHashMap<String, ArrayDeque<List<DocumentBlock>>>()
    private val lastTypingCheckpointMs = LinkedHashMap<String, Long>()
    private val lastTypingBlockId = LinkedHashMap<String, String?>()

    fun recordBeforeMutation(
        pageId: String,
        currentBlocks: List<DocumentBlock>,
        coalesceTyping: Boolean = false,
        activeBlockId: String? = null,
        nowEpochMs: Long = 0L
    ) = platformSynchronized(lock) {
        val undoStack = undoStacks.getOrPut(pageId) { ArrayDeque() }
        val redoStack = redoStacks.getOrPut(pageId) { ArrayDeque() }
        redoStack.clear()

        if (coalesceTyping) {
            val prevMs = lastTypingCheckpointMs[pageId] ?: -10_000L
            val prevBlock = lastTypingBlockId[pageId]
            val withinWindow = prevBlock == activeBlockId && (nowEpochMs - prevMs) in 0 until typingCoalesceWindowMs
            lastTypingCheckpointMs[pageId] = nowEpochMs
            lastTypingBlockId[pageId] = activeBlockId
            if (withinWindow && undoStack.isNotEmpty()) {
                return@platformSynchronized
            }
        } else {
            lastTypingCheckpointMs.remove(pageId)
            lastTypingBlockId.remove(pageId)
        }

        if (undoStack.lastOrNull() == currentBlocks) return@platformSynchronized
        if (undoStack.size >= maxHistoryPerPage) {
            undoStack.removeFirst()
        }
        undoStack.addLast(currentBlocks)
    }

    fun undo(pageId: String, currentBlocks: List<DocumentBlock>): List<DocumentBlock>? =
        platformSynchronized(lock) {
            val undoStack = undoStacks[pageId] ?: return@platformSynchronized null
            if (undoStack.isEmpty()) return@platformSynchronized null
            val previous = undoStack.removeLast()
            val redoStack = redoStacks.getOrPut(pageId) { ArrayDeque() }
            if (redoStack.size >= maxHistoryPerPage) {
                redoStack.removeFirst()
            }
            redoStack.addLast(currentBlocks)
            lastTypingCheckpointMs.remove(pageId)
            previous
        }

    fun redo(pageId: String, currentBlocks: List<DocumentBlock>): List<DocumentBlock>? =
        platformSynchronized(lock) {
            val redoStack = redoStacks[pageId] ?: return@platformSynchronized null
            if (redoStack.isEmpty()) return@platformSynchronized null
            val restored = redoStack.removeLast()
            val undoStack = undoStacks.getOrPut(pageId) { ArrayDeque() }
            if (undoStack.size >= maxHistoryPerPage) {
                undoStack.removeFirst()
            }
            undoStack.addLast(currentBlocks)
            lastTypingCheckpointMs.remove(pageId)
            restored
        }

    fun canUndo(pageId: String?): Boolean = platformSynchronized(lock) {
        if (pageId.isNullOrEmpty()) false
        else undoStacks[pageId]?.isNotEmpty() == true
    }

    fun canRedo(pageId: String?): Boolean = platformSynchronized(lock) {
        if (pageId.isNullOrEmpty()) false
        else redoStacks[pageId]?.isNotEmpty() == true
    }

    fun clearPage(pageId: String) = platformSynchronized(lock) {
        undoStacks.remove(pageId)
        redoStacks.remove(pageId)
        lastTypingCheckpointMs.remove(pageId)
        lastTypingBlockId.remove(pageId)
    }
}
