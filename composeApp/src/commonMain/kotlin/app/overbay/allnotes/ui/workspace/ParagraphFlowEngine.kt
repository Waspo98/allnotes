package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine

/**
 * Pure, stateless helpers for continuous multi-line [DocumentBlock.Paragraph] regions:
 * line boundary resolution, line-level splitting, automatic adjacent-paragraph coalescing,
 * cursor-aware block insertion, and cross-block keyboard traversal.
 */
object ParagraphFlowEngine {

    data class LineBounds(
        val start: Int,
        val end: Int,
        val text: String
    )

    data class LineSpliceResult(
        val blocks: List<DocumentBlock>,
        val primaryBlockId: String,
        val insertedBlockIndex: Int,
        val afterBlock: DocumentBlock.Paragraph?
    )

    fun lineBoundsAt(text: String, cursorOffset: Int): LineBounds {
        val clamped = cursorOffset.coerceIn(0, text.length)
        val start = if (clamped == 0) 0 else text.lastIndexOf('\n', clamped - 1) + 1
        val nlAfter = text.indexOf('\n', clamped)
        val end = if (nlAfter < 0) text.length else nlAfter
        return LineBounds(start = start, end = end, text = text.substring(start, end))
    }

    fun lineRangeForSelection(text: String, rawStart: Int, rawEnd: Int): LineBounds {
        val minSel = minOf(rawStart, rawEnd).coerceIn(0, text.length)
        val maxSel = maxOf(rawStart, rawEnd).coerceIn(0, text.length)
        val startLine = lineBoundsAt(text, minSel)
        val endProbe = if (maxSel > minSel && text.getOrNull(maxSel - 1) == '\n') maxSel - 1 else maxSel
        val endLine = lineBoundsAt(text, endProbe)
        return LineBounds(
            start = startLine.start,
            end = endLine.end,
            text = text.substring(startLine.start, endLine.end)
        )
    }

    fun replaceLineRangeWithBlocks(
        blocks: List<DocumentBlock>,
        blockIndex: Int,
        paragraph: DocumentBlock.Paragraph,
        rangeStart: Int,
        rangeEnd: Int,
        nextId: () -> String,
        buildReplacement: (preferredId: String) -> List<DocumentBlock>
    ): LineSpliceResult {
        val clampedStart = rangeStart.coerceIn(0, paragraph.text.length)
        val clampedEnd = rangeEnd.coerceIn(clampedStart, paragraph.text.length)
        val rawBeforeEnd = if (clampedStart > 0) clampedStart - 1 else 0
        val rawAfterStart = if (clampedEnd < paragraph.text.length) clampedEnd + 1 else paragraph.text.length
        return spliceParagraphWithSeparatorTrim(
            blocks = blocks,
            blockIndex = blockIndex,
            paragraph = paragraph,
            rawBeforeEnd = rawBeforeEnd,
            rawAfterStart = rawAfterStart,
            nextId = nextId,
            buildReplacement = buildReplacement
        )
    }

    private fun splitAtNewline(
        blocks: List<DocumentBlock>,
        blockIndex: Int,
        paragraph: DocumentBlock.Paragraph,
        newlineIdx: Int,
        nextId: () -> String,
        newBlock: DocumentBlock
    ): LineSpliceResult = spliceParagraphWithSeparatorTrim(
        blocks = blocks,
        blockIndex = blockIndex,
        paragraph = paragraph,
        rawBeforeEnd = newlineIdx,
        rawAfterStart = newlineIdx + 1,
        nextId = nextId,
        buildReplacement = { listOf(newBlock) }
    )

    private fun spliceParagraphWithSeparatorTrim(
        blocks: List<DocumentBlock>,
        blockIndex: Int,
        paragraph: DocumentBlock.Paragraph,
        rawBeforeEnd: Int,
        rawAfterStart: Int,
        nextId: () -> String,
        buildReplacement: (preferredId: String) -> List<DocumentBlock>
    ): LineSpliceResult {
        var beforeEnd = rawBeforeEnd.coerceIn(0, paragraph.text.length)
        if (beforeEnd > 0 && paragraph.text[beforeEnd - 1] == '\n') {
            beforeEnd--
        }
        var afterStart = rawAfterStart.coerceIn(0, paragraph.text.length)
        if (afterStart < paragraph.text.length && paragraph.text[afterStart] == '\n') {
            afterStart++
        }

        val hasBefore = beforeEnd > 0
        val hasAfter = afterStart < paragraph.text.length

        val beforeBlock = if (hasBefore) {
            RichSpanEngine.sliceParagraph(paragraph, 0, beforeEnd, newId = paragraph.id)
        } else null
        val preferredReplacementId = if (!hasBefore && !hasAfter) paragraph.id else nextId()
        val afterId = if (!hasBefore && hasAfter) paragraph.id else nextId()
        val afterBlock = if (hasAfter) {
            RichSpanEngine.sliceParagraph(paragraph, afterStart, paragraph.text.length, newId = afterId)
        } else null

        val replacementBlocks = buildReplacement(preferredReplacementId)
        val mutable = blocks.toMutableList()
        val spliced = buildList {
            if (beforeBlock != null) add(beforeBlock)
            addAll(replacementBlocks)
            if (afterBlock != null) add(afterBlock)
        }
        mutable.removeAt(blockIndex)
        mutable.addAll(blockIndex, spliced)

        val insertedIndex = blockIndex + if (beforeBlock != null) 1 else 0
        val primaryId = replacementBlocks.firstOrNull()?.id
            ?: afterBlock?.id
            ?: beforeBlock?.id
            ?: paragraph.id
        return LineSpliceResult(
            blocks = mutable,
            primaryBlockId = primaryId,
            insertedBlockIndex = insertedIndex,
            afterBlock = afterBlock
        )
    }

    fun coalesceParagraphAt(
        blocks: List<DocumentBlock>,
        index: Int,
        cursorInBlock: Int = 0,
        separator: String = "\n"
    ): Triple<List<DocumentBlock>, String, Int> {
        if (index !in blocks.indices) return Triple(blocks, "", 0)
        val initial = blocks[index] as? DocumentBlock.Paragraph
            ?: return Triple(blocks, blocks[index].id, cursorInBlock)

        val mutable = blocks.toMutableList()
        var workingIndex = index
        var surviving = initial
        var adjustedCursor = cursorInBlock.coerceIn(0, initial.text.length)

        val prev = mutable.getOrNull(workingIndex - 1) as? DocumentBlock.Paragraph
        if (prev != null) {
            val (mergedPrev, joinOffset) = RichSpanEngine.mergeParagraphsWithSeparator(prev, surviving, separator)
            adjustedCursor = joinOffset + adjustedCursor
            surviving = mergedPrev
            mutable[workingIndex - 1] = mergedPrev
            mutable.removeAt(workingIndex)
            workingIndex--
        }

        val next = mutable.getOrNull(workingIndex + 1) as? DocumentBlock.Paragraph
        if (next != null) {
            val (mergedNext, _) = RichSpanEngine.mergeParagraphsWithSeparator(surviving, next, separator)
            surviving = mergedNext
            mutable[workingIndex] = mergedNext
            mutable.removeAt(workingIndex + 1)
        }

        return Triple(mutable, surviving.id, adjustedCursor)
    }

    fun coalesceAcrossRemovedIndex(
        blocks: List<DocumentBlock>,
        removedIndex: Int
    ): List<DocumentBlock> {
        if (removedIndex !in blocks.indices) return blocks
        val mutable = blocks.toMutableList()
        mutable.removeAt(removedIndex)
        val prev = mutable.getOrNull(removedIndex - 1) as? DocumentBlock.Paragraph
        val next = mutable.getOrNull(removedIndex) as? DocumentBlock.Paragraph
        if (prev != null && next != null) {
            val (merged, _) = RichSpanEngine.mergeParagraphsWithSeparator(prev, next, "\n")
            mutable[removedIndex - 1] = merged
            mutable.removeAt(removedIndex)
        }
        return mutable
    }

    fun insertBlockIntoParagraphAtCursor(
        blocks: List<DocumentBlock>,
        paragraphIndex: Int,
        newBlock: DocumentBlock,
        cursorOffset: Int,
        nextId: () -> String,
        nextToken: () -> Long,
        focusInserted: Boolean
    ): EditorMutationResult {
        val paragraph = blocks.getOrNull(paragraphIndex) as? DocumentBlock.Paragraph
            ?: return EditorMutationResult(blocks, blocks.getOrNull(paragraphIndex)?.id)

        val mutable: MutableList<DocumentBlock>
        val insertedIndex: Int
        val afterBlock: DocumentBlock.Paragraph?

        if (paragraph.text.isEmpty()) {
            mutable = blocks.toMutableList()
            mutable[paragraphIndex] = newBlock
            insertedIndex = paragraphIndex
            afterBlock = null
        } else {
            val line = lineBoundsAt(paragraph.text, cursorOffset)
            when {
                line.text.isEmpty() -> {
                    val splice = replaceLineRangeWithBlocks(
                        blocks = blocks,
                        blockIndex = paragraphIndex,
                        paragraph = paragraph,
                        rangeStart = line.start,
                        rangeEnd = line.end,
                        nextId = nextId
                    ) { listOf(newBlock) }
                    mutable = splice.blocks.toMutableList()
                    insertedIndex = splice.insertedBlockIndex
                    afterBlock = splice.afterBlock
                }
                cursorOffset <= line.start && line.start > 0 -> {
                    val splice = splitAtNewline(
                        blocks = blocks,
                        blockIndex = paragraphIndex,
                        paragraph = paragraph,
                        newlineIdx = line.start - 1,
                        nextId = nextId,
                        newBlock = newBlock
                    )
                    mutable = splice.blocks.toMutableList()
                    insertedIndex = splice.insertedBlockIndex
                    afterBlock = splice.afterBlock
                }
                line.end >= paragraph.text.length -> {
                    mutable = blocks.toMutableList()
                    mutable.add(paragraphIndex + 1, newBlock)
                    insertedIndex = paragraphIndex + 1
                    afterBlock = null
                }
                else -> {
                    val splice = splitAtNewline(
                        blocks = blocks,
                        blockIndex = paragraphIndex,
                        paragraph = paragraph,
                        newlineIdx = line.end,
                        nextId = nextId,
                        newBlock = newBlock
                    )
                    mutable = splice.blocks.toMutableList()
                    insertedIndex = splice.insertedBlockIndex
                    afterBlock = splice.afterBlock
                }
            }
        }

        if (newBlock is DocumentBlock.Divider) {
            if (afterBlock != null) {
                return EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = afterBlock.id,
                    focusRequest = EditorFocusRequest(afterBlock.id, cursorOffset = 0, requestToken = nextToken()),
                    isStructuralConversion = true
                )
            }
            val (nextParaId, focusReq) = focusOrPrependNextParagraph(mutable, insertedIndex, "", nextId, nextToken)
            return EditorMutationResult(
                blocks = mutable,
                activeBlockId = nextParaId,
                focusRequest = focusReq,
                isStructuralConversion = true
            )
        }

        val focusReq = if (focusInserted) {
            val subIdx = if (newBlock is DocumentBlock.Checklist || newBlock is DocumentBlock.BulletList) 0 else -1
            EditorFocusRequest(
                blockId = newBlock.id,
                subItemIndex = subIdx,
                cursorOffset = Int.MAX_VALUE,
                requestToken = nextToken()
            )
        } else null

        return EditorMutationResult(
            blocks = mutable,
            activeBlockId = newBlock.id,
            focusRequest = focusReq,
            isStructuralConversion = true
        )
    }

    fun focusOrPrependNextParagraph(
        blocks: MutableList<DocumentBlock>,
        afterIndex: Int,
        carryText: String,
        nextId: () -> String,
        nextToken: () -> Long
    ): Pair<String, EditorFocusRequest> {
        val next = blocks.getOrNull(afterIndex + 1) as? DocumentBlock.Paragraph
        if (next != null) {
            val prefix = when {
                carryText.isEmpty() && next.text.isEmpty() -> ""
                next.text.isEmpty() -> carryText
                else -> "$carryText\n"
            }
            if (prefix.isNotEmpty()) {
                val shiftedSpans = next.effectiveSpans().map { span ->
                    span.copy(start = span.start + prefix.length, end = span.end + prefix.length)
                }
                blocks[afterIndex + 1] = DocumentBlock.Paragraph(
                    id = next.id,
                    text = prefix + next.text,
                    spans = RichSpanEngine.normalizeAndCoalesce(prefix.length + next.text.length, shiftedSpans)
                )
            }
            return next.id to EditorFocusRequest(next.id, cursorOffset = 0, requestToken = nextToken())
        }

        val createdId = nextId()
        blocks.add(afterIndex + 1, DocumentBlock.Paragraph(id = createdId, text = carryText))
        return createdId to EditorFocusRequest(createdId, cursorOffset = 0, requestToken = nextToken())
    }

    fun focusAdjacentBlock(
        blocks: List<DocumentBlock>,
        fromBlockId: String,
        direction: Int,
        nextToken: () -> Long,
        fromSubItemIndex: Int = -1
    ): EditorMutationResult {
        if (direction == 0) return EditorMutationResult(blocks, fromBlockId)
        val step = if (direction < 0) -1 else 1
        val fromIdx = blocks.indexOfFirst { it.id == fromBlockId }
        if (fromIdx < 0) return EditorMutationResult(blocks, fromBlockId)

        if (fromSubItemIndex >= 0) {
            val itemCount = when (val current = blocks[fromIdx]) {
                is DocumentBlock.Checklist -> current.items.size
                is DocumentBlock.BulletList -> current.items.size
                else -> 0
            }
            val nextSub = fromSubItemIndex + step
            if (nextSub in 0 until itemCount) {
                return EditorMutationResult(
                    blocks = blocks,
                    activeBlockId = fromBlockId,
                    focusRequest = EditorFocusRequest(
                        blockId = fromBlockId,
                        subItemIndex = nextSub,
                        cursorOffset = if (step > 0) 0 else Int.MAX_VALUE,
                        requestToken = nextToken()
                    ),
                    isStructuralConversion = false
                )
            }
        }

        var idx = fromIdx + step
        while (idx in blocks.indices) {
            val candidate = blocks[idx]
            val focusReq = focusRequestForBlockEntry(candidate, enteringFromAbove = step > 0, nextToken = nextToken)
            if (focusReq != null) {
                return EditorMutationResult(
                    blocks = blocks,
                    activeBlockId = candidate.id,
                    focusRequest = focusReq,
                    isStructuralConversion = false
                )
            }
            idx += step
        }
        return EditorMutationResult(blocks, fromBlockId)
    }

    private fun focusRequestForBlockEntry(
        block: DocumentBlock,
        enteringFromAbove: Boolean,
        nextToken: () -> Long
    ): EditorFocusRequest? = when (block) {
        is DocumentBlock.Paragraph,
        is DocumentBlock.Heading,
        is DocumentBlock.Callout,
        is DocumentBlock.CodeSnippet -> EditorFocusRequest(
            blockId = block.id,
            subItemIndex = -1,
            cursorOffset = if (enteringFromAbove) 0 else Int.MAX_VALUE,
            requestToken = nextToken()
        )
        is DocumentBlock.Checklist -> if (block.items.isNotEmpty()) {
            EditorFocusRequest(
                blockId = block.id,
                subItemIndex = if (enteringFromAbove) 0 else block.items.lastIndex,
                cursorOffset = if (enteringFromAbove) 0 else Int.MAX_VALUE,
                requestToken = nextToken()
            )
        } else null
        is DocumentBlock.BulletList -> if (block.items.isNotEmpty()) {
            EditorFocusRequest(
                blockId = block.id,
                subItemIndex = if (enteringFromAbove) 0 else block.items.lastIndex,
                cursorOffset = if (enteringFromAbove) 0 else Int.MAX_VALUE,
                requestToken = nextToken()
            )
        } else null
        is DocumentBlock.Divider,
        is DocumentBlock.Table,
        is DocumentBlock.Attachment -> null
    }
}
