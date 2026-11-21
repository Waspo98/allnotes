package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine

data class EditorMutationResult(
    val blocks: List<DocumentBlock>,
    val activeBlockId: String?,
    val focusRequest: EditorFocusRequest? = null,
    val isStructuralConversion: Boolean = false
)

enum class SlashCommandKind(
    val command: String,
    val title: String,
    val subtitle: String
) {
    PARAGRAPH("/text", "Body Text", "Standard rich text paragraph"),
    HEADING_1("/h1", "Heading 1", "Large document section title"),
    HEADING_2("/h2", "Heading 2", "Medium subsection heading"),
    HEADING_3("/h3", "Heading 3", "Compact accent heading"),
    CHECKLIST("/todo", "Checklist", "Interactive task items with checkboxes"),
    BULLET_LIST("/bullet", "Bulleted List", "Unordered bullet points"),
    NUMBERED_LIST("/numbered", "Numbered List", "Ordered sequential steps"),
    TABLE("/table", "Responsive Table", "Structured rows & columns"),
    CALLOUT("/callout", "Callout Box", "Highlighted note, tip, or key takeaway"),
    CODE("/code", "Code Block", "Monospace snippet with copy button"),
    DIVIDER("/divider", "Horizontal Divider", "Visual section separator"),
    ATTACHMENT("/image", "Image or Attachment", "Embed photo or file attachment")
}

/**
 * Stateless Word-style keyboard & auto-formatting controller handling:
 * - Line-aware Markdown live prefix triggers (`# `, `- `, `1. `, `[] `, `> `, ```` ``` ````, `---`)
 * - Line-aware Slash `/` inline command insertions and Escape dismissal
 * - Flowing multi-line paragraphs with smart splitting, coalescing, and Backspace merging
 */
object EditorInputController {

    fun matchingSlashCommands(
        paragraphText: String,
        cursorOffset: Int = paragraphText.length
    ): List<SlashCommandKind> {
        val line = ParagraphFlowEngine.lineBoundsAt(paragraphText, cursorOffset).text
        if (!line.startsWith("/")) return emptyList()
        if (line.contains(' ') || line.length > 18) {
            return emptyList()
        }
        val query = line.removePrefix("/").lowercase()
        val available = SlashCommandKind.entries.filter { it != SlashCommandKind.PARAGRAPH }
        if (query.isEmpty()) return available
        return available.filter {
            it.command.removePrefix("/").contains(query) ||
                it.title.lowercase().contains(query)
        }
    }

    fun editParagraphWithTriggers(
        blocks: List<DocumentBlock>,
        blockId: String,
        newText: String,
        cursorOffset: Int,
        nextId: () -> String,
        nextToken: () -> Long
    ): EditorMutationResult {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return EditorMutationResult(blocks, blockId)
        val current = blocks[idx] as? DocumentBlock.Paragraph ?: return EditorMutationResult(blocks, blockId)

        val normalizedText: String
        val normalizedCursor: Int
        if (newText.contains('\r')) {
            val clampedCur = cursorOffset.coerceIn(0, newText.length)
            normalizedText = newText.replace("\r\n", "\n").replace('\r', '\n')
            normalizedCursor = newText.substring(0, clampedCur)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .length
        } else {
            normalizedText = newText
            normalizedCursor = cursorOffset.coerceIn(0, normalizedText.length)
        }

        val adjustedSpans = RichSpanEngine.adjustSpansAfterEdit(
            oldText = current.text,
            newText = normalizedText,
            existingSpans = current.spans
        )
        val updatedParagraph = current.copy(text = normalizedText, spans = adjustedSpans)

        val diff = EditorMarkdownTriggers.computeEditDiff(current.text, normalizedText)
        val line = ParagraphFlowEngine.lineBoundsAt(normalizedText, normalizedCursor)
        val editTouchesLine = normalizedText != current.text && diff.newSuffix >= line.start && diff.prefix <= line.end

        if (!diff.isMultilinePaste && editTouchesLine) {
            val triggered = EditorMarkdownTriggers.tryApplyLineTrigger(
                blocks = blocks,
                blockIndex = idx,
                updatedParagraph = updatedParagraph,
                line = line,
                normalizedCursor = normalizedCursor,
                nextId = nextId,
                nextToken = nextToken
            )
            if (triggered != null) return triggered
        }

        val mutable = blocks.toMutableList()
        mutable[idx] = updatedParagraph
        return EditorMutationResult(
            blocks = mutable,
            activeBlockId = blockId
        )
    }

    fun executeSlashCommand(
        blocks: List<DocumentBlock>,
        blockId: String,
        kind: SlashCommandKind,
        nextId: () -> String,
        nextToken: () -> Long,
        cursorOffset: Int? = null
    ): EditorMutationResult {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return EditorMutationResult(blocks, blockId)
        val current = blocks[idx] as? DocumentBlock.Paragraph
            ?: return EditorMarkdownTriggers.executeSlashCommandOnWholeBlock(blocks, idx, blockId, kind, nextId, nextToken)

        val line = ParagraphFlowEngine.lineBoundsAt(current.text, cursorOffset ?: current.text.length)
        if (kind == SlashCommandKind.PARAGRAPH || kind == SlashCommandKind.ATTACHMENT) {
            val mutable = blocks.toMutableList()
            mutable[idx] = EditorMarkdownTriggers.removeRangePreservingSpans(current, line.start, line.end)
            return EditorMutationResult(
                blocks = mutable,
                activeBlockId = blockId,
                focusRequest = EditorFocusRequest(blockId, cursorOffset = line.start, requestToken = nextToken()),
                isStructuralConversion = true
            )
        }

        if (kind == SlashCommandKind.DIVIDER) {
            val splice = ParagraphFlowEngine.replaceLineRangeWithBlocks(
                blocks = blocks,
                blockIndex = idx,
                paragraph = current,
                rangeStart = line.start,
                rangeEnd = line.end,
                nextId = nextId
            ) { preferredId -> listOf(DocumentBlock.Divider(id = preferredId)) }
            val mutable = splice.blocks.toMutableList()
            val (targetId, focusReq) = if (splice.afterBlock != null) {
                splice.afterBlock.id to EditorFocusRequest(splice.afterBlock.id, cursorOffset = 0, requestToken = nextToken())
            } else {
                ParagraphFlowEngine.focusOrPrependNextParagraph(mutable, splice.insertedBlockIndex, "", nextId, nextToken)
            }
            return EditorMutationResult(mutable, targetId, focusReq, isStructuralConversion = true)
        }

        val splice = ParagraphFlowEngine.replaceLineRangeWithBlocks(
            blocks = blocks,
            blockIndex = idx,
            paragraph = current,
            rangeStart = line.start,
            rangeEnd = line.end,
            nextId = nextId
        ) { preferredId -> listOf(EditorMarkdownTriggers.buildEmptySlashBlock(preferredId, kind, nextId)) }

        val targetId = splice.primaryBlockId
        val focusReq = if (kind == SlashCommandKind.TABLE) {
            null
        } else {
            EditorFocusRequest(targetId, subItemIndex = listSubItemIndex(kind), cursorOffset = 0, requestToken = nextToken())
        }
        return EditorMutationResult(splice.blocks, targetId, focusReq, isStructuralConversion = true)
    }

    fun dismissSlashCommand(
        blocks: List<DocumentBlock>,
        blockId: String,
        cursorOffset: Int,
        nextToken: () -> Long
    ): EditorMutationResult {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return EditorMutationResult(blocks, blockId)
        val current = blocks[idx] as? DocumentBlock.Paragraph ?: return EditorMutationResult(blocks, blockId)
        val line = ParagraphFlowEngine.lineBoundsAt(current.text, cursorOffset)
        if (!line.text.startsWith("/")) return EditorMutationResult(blocks, blockId)

        val mutable = blocks.toMutableList()
        mutable[idx] = EditorMarkdownTriggers.removeRangePreservingSpans(current, line.start, line.end)
        return EditorMutationResult(
            blocks = mutable,
            activeBlockId = blockId,
            focusRequest = EditorFocusRequest(blockId, cursorOffset = line.start, requestToken = nextToken()),
            isStructuralConversion = true
        )
    }

    private fun listSubItemIndex(kind: SlashCommandKind): Int = if (
        kind == SlashCommandKind.CHECKLIST ||
        kind == SlashCommandKind.BULLET_LIST ||
        kind == SlashCommandKind.NUMBERED_LIST
    ) 0 else -1

    fun splitBlockAtCursor(
        blocks: List<DocumentBlock>,
        blockId: String,
        cursorOffset: Int,
        nextId: () -> String,
        nextToken: () -> Long
    ): EditorMutationResult {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return EditorMutationResult(blocks, blockId)
        val mutable = blocks.toMutableList()

        return when (val target = blocks[idx]) {
            is DocumentBlock.Paragraph -> {
                val newBlockId = nextId()
                val (first, second) = RichSpanEngine.splitParagraphAt(target, cursorOffset, newBlockId)
                mutable[idx] = first
                mutable.add(idx + 1, second)
                EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = newBlockId,
                    focusRequest = EditorFocusRequest(newBlockId, cursorOffset = 0, requestToken = nextToken()),
                    isStructuralConversion = true
                )
            }
            is DocumentBlock.Heading -> {
                val split = cursorOffset.coerceIn(0, target.text.length)
                mutable[idx] = target.copy(text = target.text.substring(0, split))
                val (targetId, focusReq) = ParagraphFlowEngine.focusOrPrependNextParagraph(
                    mutable, idx, target.text.substring(split), nextId, nextToken
                )
                EditorMutationResult(mutable, targetId, focusReq, isStructuralConversion = true)
            }
            else -> EditorMutationResult(blocks, blockId)
        }
    }

    fun backspaceAtBlockStart(
        blocks: List<DocumentBlock>,
        blockId: String,
        nextToken: () -> Long
    ): EditorMutationResult {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return EditorMutationResult(blocks, blockId)
        val mutable = blocks.toMutableList()

        return when (val current = blocks[idx]) {
            is DocumentBlock.Heading -> {
                mutable[idx] = DocumentBlock.Paragraph(id = current.id, text = current.text)
                coalesceParagraphResult(mutable, idx, cursorInBlock = 0, nextToken = nextToken)
            }
            is DocumentBlock.Callout -> {
                if (current.text.isEmpty() && current.title.isEmpty()) {
                    mutable[idx] = DocumentBlock.Paragraph(id = current.id, text = "")
                    coalesceParagraphResult(mutable, idx, cursorInBlock = 0, nextToken = nextToken)
                } else EditorMutationResult(blocks, blockId)
            }
            is DocumentBlock.Paragraph -> backspaceParagraphAtStart(mutable, idx, current, nextToken)
            else -> EditorMutationResult(blocks, blockId)
        }
    }

    private fun backspaceParagraphAtStart(
        mutable: MutableList<DocumentBlock>,
        idx: Int,
        current: DocumentBlock.Paragraph,
        nextToken: () -> Long
    ): EditorMutationResult {
        if (idx == 0) {
            if (current.text.isEmpty() && mutable.size > 1) {
                mutable.removeAt(0)
                val nextActive = mutable.first().id
                return EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = nextActive,
                    focusRequest = EditorFocusRequest(nextActive, cursorOffset = 0, requestToken = nextToken()),
                    isStructuralConversion = true
                )
            }
            return EditorMutationResult(mutable, current.id)
        }

        return when (val prev = mutable[idx - 1]) {
            is DocumentBlock.Paragraph -> {
                val joinOffset = prev.text.length
                mutable[idx - 1] = RichSpanEngine.mergeParagraphs(prev, current)
                mutable.removeAt(idx)
                EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = prev.id,
                    focusRequest = EditorFocusRequest(prev.id, cursorOffset = joinOffset, requestToken = nextToken()),
                    isStructuralConversion = true
                )
            }
            is DocumentBlock.Heading -> {
                val joinOffset = prev.text.length
                val firstNl = current.text.indexOf('\n')
                if (firstNl < 0) {
                    mutable[idx - 1] = prev.copy(text = prev.text + current.text)
                    mutable.removeAt(idx)
                } else {
                    mutable[idx - 1] = prev.copy(text = prev.text + current.text.substring(0, firstNl))
                    mutable[idx] = RichSpanEngine.sliceParagraph(current, firstNl + 1, current.text.length, current.id)
                }
                EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = prev.id,
                    focusRequest = EditorFocusRequest(prev.id, cursorOffset = joinOffset, requestToken = nextToken()),
                    isStructuralConversion = true
                )
            }
            is DocumentBlock.Divider -> {
                mutable.removeAt(idx - 1)
                coalesceParagraphResult(mutable, idx - 1, cursorInBlock = 0, nextToken = nextToken)
            }
            else -> {
                if (current.text.isEmpty()) {
                    mutable.removeAt(idx)
                    EditorMutationResult(
                        blocks = mutable,
                        activeBlockId = prev.id,
                        focusRequest = EditorFocusRequest(prev.id, cursorOffset = Int.MAX_VALUE, requestToken = nextToken()),
                        isStructuralConversion = true
                    )
                } else {
                    EditorMutationResult(mutable, current.id)
                }
            }
        }
    }

    fun handleListEnter(
        blocks: List<DocumentBlock>,
        blockId: String,
        itemIndex: Int,
        nextId: () -> String,
        nextToken: () -> Long
    ): EditorMutationResult {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return EditorMutationResult(blocks, blockId)
        val mutable = blocks.toMutableList()

        return when (val current = blocks[idx]) {
            is DocumentBlock.Checklist -> {
                if (itemIndex !in current.items.indices) return EditorMutationResult(blocks, blockId)
                if (current.items[itemIndex].text.isEmpty()) {
                    exitEmptyListItem(mutable, idx, blockId, current.items.size <= 1,
                        trimBlock = { current.copy(items = current.items.filterIndexed { i, _ -> i != itemIndex }) },
                        nextId = nextId, nextToken = nextToken
                    )
                } else {
                    val newItems = current.items.toMutableList().apply {
                        add(itemIndex + 1, ChecklistItem(id = nextId(), text = "", isChecked = false))
                    }
                    mutable[idx] = current.copy(items = newItems)
                    EditorMutationResult(
                        mutable, blockId,
                        EditorFocusRequest(blockId, subItemIndex = itemIndex + 1, cursorOffset = 0, requestToken = nextToken()),
                        true
                    )
                }
            }
            is DocumentBlock.BulletList -> {
                if (itemIndex !in current.items.indices) return EditorMutationResult(blocks, blockId)
                if (current.items[itemIndex].isEmpty()) {
                    exitEmptyListItem(mutable, idx, blockId, current.items.size <= 1,
                        trimBlock = { current.copy(items = current.items.filterIndexed { i, _ -> i != itemIndex }) },
                        nextId = nextId, nextToken = nextToken
                    )
                } else {
                    val newItems = current.items.toMutableList().apply { add(itemIndex + 1, "") }
                    mutable[idx] = current.copy(items = newItems)
                    EditorMutationResult(
                        mutable, blockId,
                        EditorFocusRequest(blockId, subItemIndex = itemIndex + 1, cursorOffset = 0, requestToken = nextToken()),
                        true
                    )
                }
            }
            else -> EditorMutationResult(blocks, blockId)
        }
    }

    private inline fun exitEmptyListItem(
        mutable: MutableList<DocumentBlock>,
        idx: Int,
        blockId: String,
        isSingleItem: Boolean,
        trimBlock: () -> DocumentBlock,
        noinline nextId: () -> String,
        noinline nextToken: () -> Long
    ): EditorMutationResult = if (isSingleItem) {
        mutable[idx] = DocumentBlock.Paragraph(id = blockId, text = "")
        coalesceParagraphResult(mutable, idx, cursorInBlock = 0, nextToken = nextToken)
    } else {
        mutable[idx] = trimBlock()
        val (targetId, focusReq) = ParagraphFlowEngine.focusOrPrependNextParagraph(mutable, idx, "", nextId, nextToken)
        EditorMutationResult(mutable, targetId, focusReq, true)
    }

    fun handleListBackspaceOnEmpty(
        blocks: List<DocumentBlock>,
        blockId: String,
        itemIndex: Int,
        nextToken: () -> Long
    ): EditorMutationResult {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return EditorMutationResult(blocks, blockId)
        val mutable = blocks.toMutableList()

        return when (val current = blocks[idx]) {
            is DocumentBlock.Checklist -> {
                if (itemIndex !in current.items.indices) return EditorMutationResult(blocks, blockId)
                if (current.items.size <= 1) {
                    mutable[idx] = DocumentBlock.Paragraph(id = blockId, text = current.items.first().text)
                    coalesceParagraphResult(mutable, idx, cursorInBlock = 0, nextToken = nextToken)
                } else {
                    val prevIdx = (itemIndex - 1).coerceAtLeast(0)
                    mutable[idx] = current.copy(items = current.items.filterIndexed { i, _ -> i != itemIndex })
                    EditorMutationResult(
                        mutable, blockId,
                        EditorFocusRequest(blockId, subItemIndex = prevIdx, cursorOffset = current.items[prevIdx].text.length, requestToken = nextToken()),
                        true
                    )
                }
            }
            is DocumentBlock.BulletList -> {
                if (itemIndex !in current.items.indices) return EditorMutationResult(blocks, blockId)
                if (current.items.size <= 1) {
                    mutable[idx] = DocumentBlock.Paragraph(id = blockId, text = current.items.first())
                    coalesceParagraphResult(mutable, idx, cursorInBlock = 0, nextToken = nextToken)
                } else {
                    val prevIdx = (itemIndex - 1).coerceAtLeast(0)
                    mutable[idx] = current.copy(items = current.items.filterIndexed { i, _ -> i != itemIndex })
                    EditorMutationResult(
                        mutable, blockId,
                        EditorFocusRequest(blockId, subItemIndex = prevIdx, cursorOffset = current.items[prevIdx].length, requestToken = nextToken()),
                        true
                    )
                }
            }
            else -> EditorMutationResult(blocks, blockId)
        }
    }

    fun convertBlockWithFocus(
        blocks: List<DocumentBlock>,
        blockId: String,
        target: SlashCommandKind,
        nextId: () -> String,
        nextToken: () -> Long,
        selectionStart: Int? = null,
        selectionEnd: Int? = null
    ): EditorMutationResult {
        if (target == SlashCommandKind.ATTACHMENT) return EditorMutationResult(blocks, blockId)
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return EditorMutationResult(blocks, blockId)
        val current = blocks[idx]

        if (current is DocumentBlock.Paragraph && target != SlashCommandKind.PARAGRAPH) {
            val lineRange = if (selectionStart != null && selectionEnd != null) {
                ParagraphFlowEngine.lineRangeForSelection(current.text, selectionStart, selectionEnd)
            } else {
                ParagraphFlowEngine.LineBounds(0, current.text.length, current.text)
            }
            if ((target == SlashCommandKind.TABLE || target == SlashCommandKind.DIVIDER) && lineRange.text.isNotBlank()) {
                return EditorMutationResult(blocks, blockId)
            }
            val splice = ParagraphFlowEngine.replaceLineRangeWithBlocks(
                blocks = blocks,
                blockIndex = idx,
                paragraph = current,
                rangeStart = lineRange.start,
                rangeEnd = lineRange.end,
                nextId = nextId
            ) { preferredId ->
                listOfNotNull(
                    BlockMutationEngine.convertTextToBlock(
                        blockId = preferredId,
                        text = lineRange.text,
                        target = target,
                        nextId = nextId
                    )
                )
            }
            val targetId = splice.primaryBlockId
            return EditorMutationResult(
                blocks = splice.blocks,
                activeBlockId = targetId,
                focusRequest = EditorFocusRequest(
                    blockId = targetId,
                    subItemIndex = listSubItemIndex(target),
                    cursorOffset = Int.MAX_VALUE,
                    requestToken = nextToken()
                ),
                isStructuralConversion = true
            )
        }

        val converted = BlockMutationEngine.convertBlock(blocks, blockId, target, nextId)
        if (converted == blocks) return EditorMutationResult(blocks, blockId)

        if (current !is DocumentBlock.Paragraph && target == SlashCommandKind.PARAGRAPH) {
            val convertedLen = (converted[idx] as? DocumentBlock.Paragraph)?.text?.length ?: 0
            return coalesceParagraphResult(converted, idx, cursorInBlock = convertedLen, nextToken = nextToken)
        }

        return EditorMutationResult(
            blocks = converted,
            activeBlockId = blockId,
            focusRequest = EditorFocusRequest(
                blockId = blockId,
                subItemIndex = listSubItemIndex(target),
                cursorOffset = Int.MAX_VALUE,
                requestToken = nextToken()
            ),
            isStructuralConversion = true
        )
    }

    private fun coalesceParagraphResult(
        blocks: List<DocumentBlock>,
        index: Int,
        cursorInBlock: Int,
        nextToken: () -> Long
    ): EditorMutationResult {
        val (coalesced, survivingId, adjustedCursor) = ParagraphFlowEngine.coalesceParagraphAt(
            blocks = blocks,
            index = index,
            cursorInBlock = cursorInBlock,
            separator = "\n"
        )
        return EditorMutationResult(
            blocks = coalesced,
            activeBlockId = survivingId,
            focusRequest = EditorFocusRequest(
                blockId = survivingId,
                cursorOffset = adjustedCursor,
                requestToken = nextToken()
            ),
            isStructuralConversion = true
        )
    }
}
