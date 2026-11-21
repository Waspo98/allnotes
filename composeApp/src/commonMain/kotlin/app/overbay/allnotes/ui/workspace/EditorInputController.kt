package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.model.CalloutTone
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
        if (query.isEmpty()) return SlashCommandKind.entries
        return SlashCommandKind.entries.filter {
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

        var prefix = 0
        val maxPrefix = minOf(current.text.length, normalizedText.length)
        while (prefix < maxPrefix && current.text[prefix] == normalizedText[prefix]) {
            prefix++
        }
        var oldSuffix = current.text.length
        var newSuffix = normalizedText.length
        while (oldSuffix > prefix && newSuffix > prefix && current.text[oldSuffix - 1] == normalizedText[newSuffix - 1]) {
            oldSuffix--
            newSuffix--
        }
        val insertedSegment = normalizedText.substring(prefix, newSuffix)
        val isMultilinePaste = insertedSegment.contains('\n') && insertedSegment.length > 1

        val line = ParagraphFlowEngine.lineBoundsAt(normalizedText, normalizedCursor)
        val editTouchesLine = normalizedText != current.text && newSuffix >= line.start && prefix <= line.end

        if (!isMultilinePaste && editTouchesLine) {
            val lineText = line.text
            if (lineText == "--- " || lineText == "---") {
                val splice = ParagraphFlowEngine.replaceLineRangeWithBlocks(
                    blocks = blocks,
                    blockIndex = idx,
                    paragraph = updatedParagraph,
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
                return EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = targetId,
                    focusRequest = focusReq,
                    isStructuralConversion = true
                )
            }

            val cursorInLine = (normalizedCursor - line.start).coerceIn(0, lineText.length)
            fun shiftedCursor(prefixLen: Int, rest: String): Int =
                (cursorInLine - prefixLen).coerceIn(0, rest.length)

            val triggerConversion: Pair<(String) -> DocumentBlock, (String) -> EditorFocusRequest>? = when {
                lineText.startsWith("### ") -> {
                    val rest = lineText.removePrefix("### ")
                    val offset = shiftedCursor(4, rest)
                    ({ id: String -> DocumentBlock.Heading(id = id, text = rest, level = 3) }) to
                        ({ id: String -> EditorFocusRequest(id, cursorOffset = offset, requestToken = nextToken()) })
                }
                lineText.startsWith("## ") -> {
                    val rest = lineText.removePrefix("## ")
                    val offset = shiftedCursor(3, rest)
                    ({ id: String -> DocumentBlock.Heading(id = id, text = rest, level = 2) }) to
                        ({ id: String -> EditorFocusRequest(id, cursorOffset = offset, requestToken = nextToken()) })
                }
                lineText.startsWith("# ") -> {
                    val rest = lineText.removePrefix("# ")
                    val offset = shiftedCursor(2, rest)
                    ({ id: String -> DocumentBlock.Heading(id = id, text = rest, level = 1) }) to
                        ({ id: String -> EditorFocusRequest(id, cursorOffset = offset, requestToken = nextToken()) })
                }
                lineText.startsWith("- [x] ") || lineText.startsWith("- [X] ") ||
                    lineText.startsWith("[x] ") || lineText.startsWith("[X] ") -> {
                    val rest = lineText.substringAfter("] ")
                    val prefixLen = lineText.length - rest.length
                    val offset = shiftedCursor(prefixLen, rest)
                    ({ id: String ->
                        DocumentBlock.Checklist(
                            id = id,
                            title = "",
                            items = listOf(ChecklistItem(id = nextId(), text = rest, isChecked = true))
                        )
                    }) to ({ id: String ->
                        EditorFocusRequest(id, subItemIndex = 0, cursorOffset = offset, requestToken = nextToken())
                    })
                }
                lineText.startsWith("- [ ] ") || lineText.startsWith("[] ") || lineText.startsWith("[ ] ") -> {
                    val rest = lineText.substringAfter("] ")
                    val prefixLen = lineText.length - rest.length
                    val offset = shiftedCursor(prefixLen, rest)
                    ({ id: String ->
                        DocumentBlock.Checklist(
                            id = id,
                            title = "",
                            items = listOf(ChecklistItem(id = nextId(), text = rest, isChecked = false))
                        )
                    }) to ({ id: String ->
                        EditorFocusRequest(id, subItemIndex = 0, cursorOffset = offset, requestToken = nextToken())
                    })
                }
                lineText.startsWith("- ") || lineText.startsWith("* ") -> {
                    val rest = lineText.substring(2)
                    val offset = shiftedCursor(2, rest)
                    ({ id: String -> DocumentBlock.BulletList(id = id, isNumbered = false, items = listOf(rest)) }) to
                        ({ id: String ->
                            EditorFocusRequest(id, subItemIndex = 0, cursorOffset = offset, requestToken = nextToken())
                        })
                }
                lineText.startsWith("1. ") -> {
                    val rest = lineText.removePrefix("1. ")
                    val offset = shiftedCursor(3, rest)
                    ({ id: String -> DocumentBlock.BulletList(id = id, isNumbered = true, items = listOf(rest)) }) to
                        ({ id: String ->
                            EditorFocusRequest(id, subItemIndex = 0, cursorOffset = offset, requestToken = nextToken())
                        })
                }
                lineText.startsWith("> ") -> {
                    val rest = lineText.removePrefix("> ")
                    val offset = shiftedCursor(2, rest)
                    ({ id: String -> DocumentBlock.Callout(id = id, title = "Note", text = rest, tone = CalloutTone.INFO) }) to
                        ({ id: String -> EditorFocusRequest(id, cursorOffset = offset, requestToken = nextToken()) })
                }
                lineText.startsWith("```") && lineText.endsWith(" ") && !lineText.removePrefix("```").dropLast(1).contains(' ') -> {
                    val lang = lineText.removePrefix("```").trim().ifEmpty { "kotlin" }
                    ({ id: String -> DocumentBlock.CodeSnippet(id = id, language = lang, code = "") }) to
                        ({ id: String -> EditorFocusRequest(id, cursorOffset = 0, requestToken = nextToken()) })
                }
                else -> null
            }

            if (triggerConversion != null) {
                val (buildBlock, buildFocus) = triggerConversion
                val splice = ParagraphFlowEngine.replaceLineRangeWithBlocks(
                    blocks = blocks,
                    blockIndex = idx,
                    paragraph = updatedParagraph,
                    rangeStart = line.start,
                    rangeEnd = line.end,
                    nextId = nextId
                ) { preferredId -> listOf(buildBlock(preferredId)) }
                val targetId = splice.primaryBlockId
                return EditorMutationResult(
                    blocks = splice.blocks,
                    activeBlockId = targetId,
                    focusRequest = buildFocus(targetId),
                    isStructuralConversion = true
                )
            }
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
        if (current == null) {
            return executeSlashCommandOnWholeBlock(blocks, idx, blockId, kind, nextId, nextToken)
        }

        val line = ParagraphFlowEngine.lineBoundsAt(current.text, cursorOffset ?: current.text.length)
        if (kind == SlashCommandKind.PARAGRAPH || kind == SlashCommandKind.ATTACHMENT) {
            val mutable = blocks.toMutableList()
            mutable[idx] = removeRangePreservingSpans(current, line.start, line.end)
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
            return EditorMutationResult(
                blocks = mutable,
                activeBlockId = targetId,
                focusRequest = focusReq,
                isStructuralConversion = true
            )
        }

        val splice = ParagraphFlowEngine.replaceLineRangeWithBlocks(
            blocks = blocks,
            blockIndex = idx,
            paragraph = current,
            rangeStart = line.start,
            rangeEnd = line.end,
            nextId = nextId
        ) { preferredId -> listOf(buildEmptySlashBlock(preferredId, kind, nextId)) }

        val targetId = splice.primaryBlockId
        val focusReq = if (kind == SlashCommandKind.TABLE) {
            null
        } else {
            val subIdx = listSubItemIndex(kind)
            EditorFocusRequest(targetId, subItemIndex = subIdx, cursorOffset = 0, requestToken = nextToken())
        }
        return EditorMutationResult(
            blocks = splice.blocks,
            activeBlockId = targetId,
            focusRequest = focusReq,
            isStructuralConversion = true
        )
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
        mutable[idx] = removeRangePreservingSpans(current, line.start, line.end)
        return EditorMutationResult(
            blocks = mutable,
            activeBlockId = blockId,
            focusRequest = EditorFocusRequest(blockId, cursorOffset = line.start, requestToken = nextToken()),
            isStructuralConversion = true
        )
    }

    private fun removeRangePreservingSpans(
        paragraph: DocumentBlock.Paragraph,
        start: Int,
        end: Int
    ): DocumentBlock.Paragraph {
        val s = start.coerceIn(0, paragraph.text.length)
        val e = end.coerceIn(s, paragraph.text.length)
        val updatedText = paragraph.text.removeRange(s, e)
        val updatedSpans = RichSpanEngine.adjustSpansAfterEdit(
            oldText = paragraph.text,
            newText = updatedText,
            existingSpans = paragraph.effectiveSpans()
        )
        return DocumentBlock.Paragraph(
            id = paragraph.id,
            text = updatedText,
            spans = updatedSpans
        )
    }

    private fun executeSlashCommandOnWholeBlock(
        blocks: List<DocumentBlock>,
        idx: Int,
        blockId: String,
        kind: SlashCommandKind,
        nextId: () -> String,
        nextToken: () -> Long
    ): EditorMutationResult {
        val mutable = blocks.toMutableList()
        return when (kind) {
            SlashCommandKind.DIVIDER -> {
                mutable[idx] = DocumentBlock.Divider(id = blockId)
                val (targetId, focusReq) = ParagraphFlowEngine.focusOrPrependNextParagraph(mutable, idx, "", nextId, nextToken)
                EditorMutationResult(mutable, targetId, focusReq, true)
            }
            SlashCommandKind.ATTACHMENT -> {
                mutable[idx] = DocumentBlock.Paragraph(id = blockId, text = "")
                EditorMutationResult(mutable, blockId, isStructuralConversion = true)
            }
            SlashCommandKind.TABLE -> {
                mutable[idx] = buildEmptySlashBlock(blockId, kind, nextId)
                EditorMutationResult(mutable, blockId, isStructuralConversion = true)
            }
            else -> {
                mutable[idx] = buildEmptySlashBlock(blockId, kind, nextId)
                val subIdx = listSubItemIndex(kind)
                EditorMutationResult(
                    mutable,
                    blockId,
                    EditorFocusRequest(blockId, subItemIndex = subIdx, cursorOffset = 0, requestToken = nextToken()),
                    true
                )
            }
        }
    }

    private fun buildEmptySlashBlock(
        blockId: String,
        kind: SlashCommandKind,
        nextId: () -> String
    ): DocumentBlock = when (kind) {
        SlashCommandKind.PARAGRAPH, SlashCommandKind.ATTACHMENT -> DocumentBlock.Paragraph(id = blockId, text = "")
        SlashCommandKind.HEADING_1 -> DocumentBlock.Heading(id = blockId, text = "", level = 1)
        SlashCommandKind.HEADING_2 -> DocumentBlock.Heading(id = blockId, text = "", level = 2)
        SlashCommandKind.HEADING_3 -> DocumentBlock.Heading(id = blockId, text = "", level = 3)
        SlashCommandKind.CHECKLIST -> DocumentBlock.Checklist(
            id = blockId,
            title = "",
            items = listOf(ChecklistItem(id = nextId(), text = "", isChecked = false))
        )
        SlashCommandKind.BULLET_LIST -> DocumentBlock.BulletList(id = blockId, isNumbered = false, items = listOf(""))
        SlashCommandKind.NUMBERED_LIST -> DocumentBlock.BulletList(id = blockId, isNumbered = true, items = listOf(""))
        SlashCommandKind.TABLE -> DocumentBlock.Table(
            id = blockId,
            headers = listOf("Column 1", "Column 2", "Status"),
            rows = listOf(listOf("", "", ""))
        )
        SlashCommandKind.CALLOUT -> DocumentBlock.Callout(id = blockId, title = "Key Point", text = "", tone = CalloutTone.TIP)
        SlashCommandKind.CODE -> DocumentBlock.CodeSnippet(id = blockId, language = "kotlin", code = "")
        SlashCommandKind.DIVIDER -> DocumentBlock.Divider(id = blockId)
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
                val headText = target.text.substring(0, split)
                val tailText = target.text.substring(split)
                mutable[idx] = target.copy(text = headText)
                val (targetId, focusReq) = ParagraphFlowEngine.focusOrPrependNextParagraph(
                    mutable,
                    idx,
                    tailText,
                    nextId,
                    nextToken
                )
                EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = targetId,
                    focusRequest = focusReq,
                    isStructuralConversion = true
                )
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
            is DocumentBlock.Paragraph -> {
                if (idx == 0) {
                    if (current.text.isEmpty() && blocks.size > 1) {
                        mutable.removeAt(0)
                        val nextActive = mutable.first().id
                        EditorMutationResult(
                            blocks = mutable,
                            activeBlockId = nextActive,
                            focusRequest = EditorFocusRequest(nextActive, cursorOffset = 0, requestToken = nextToken()),
                            isStructuralConversion = true
                        )
                    } else {
                        EditorMutationResult(blocks, blockId)
                    }
                } else {
                    when (val prev = blocks[idx - 1]) {
                        is DocumentBlock.Paragraph -> {
                            val joinOffset = prev.text.length
                            val merged = RichSpanEngine.mergeParagraphs(prev, current)
                            mutable[idx - 1] = merged
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
                                val firstLine = current.text.substring(0, firstNl)
                                mutable[idx - 1] = prev.copy(text = prev.text + firstLine)
                                mutable[idx] = RichSpanEngine.sliceParagraph(
                                    paragraph = current,
                                    start = firstNl + 1,
                                    end = current.text.length,
                                    newId = current.id
                                )
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
                                EditorMutationResult(blocks, blockId)
                            }
                        }
                    }
                }
            }
            else -> EditorMutationResult(blocks, blockId)
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
                val activeItem = current.items[itemIndex]
                if (activeItem.text.isEmpty()) {
                    if (current.items.size <= 1) {
                        mutable[idx] = DocumentBlock.Paragraph(id = blockId, text = "")
                        coalesceParagraphResult(mutable, idx, cursorInBlock = 0, nextToken = nextToken)
                    } else {
                        val trimmed = current.items.filterIndexed { i, _ -> i != itemIndex }
                        mutable[idx] = current.copy(items = trimmed)
                        val (targetId, focusReq) = ParagraphFlowEngine.focusOrPrependNextParagraph(
                            mutable,
                            idx,
                            "",
                            nextId,
                            nextToken
                        )
                        EditorMutationResult(mutable, targetId, focusReq, true)
                    }
                } else {
                    val newItems = current.items.toMutableList().apply {
                        add(itemIndex + 1, ChecklistItem(id = nextId(), text = "", isChecked = false))
                    }
                    mutable[idx] = current.copy(items = newItems)
                    EditorMutationResult(
                        mutable,
                        blockId,
                        EditorFocusRequest(blockId, subItemIndex = itemIndex + 1, cursorOffset = 0, requestToken = nextToken()),
                        true
                    )
                }
            }
            is DocumentBlock.BulletList -> {
                if (itemIndex !in current.items.indices) return EditorMutationResult(blocks, blockId)
                val activeText = current.items[itemIndex]
                if (activeText.isEmpty()) {
                    if (current.items.size <= 1) {
                        mutable[idx] = DocumentBlock.Paragraph(id = blockId, text = "")
                        coalesceParagraphResult(mutable, idx, cursorInBlock = 0, nextToken = nextToken)
                    } else {
                        val trimmed = current.items.filterIndexed { i, _ -> i != itemIndex }
                        mutable[idx] = current.copy(items = trimmed)
                        val (targetId, focusReq) = ParagraphFlowEngine.focusOrPrependNextParagraph(
                            mutable,
                            idx,
                            "",
                            nextId,
                            nextToken
                        )
                        EditorMutationResult(mutable, targetId, focusReq, true)
                    }
                } else {
                    val newItems = current.items.toMutableList().apply {
                        add(itemIndex + 1, "")
                    }
                    mutable[idx] = current.copy(items = newItems)
                    EditorMutationResult(
                        mutable,
                        blockId,
                        EditorFocusRequest(blockId, subItemIndex = itemIndex + 1, cursorOffset = 0, requestToken = nextToken()),
                        true
                    )
                }
            }
            else -> EditorMutationResult(blocks, blockId)
        }
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
                    val prevLen = current.items[prevIdx].text.length
                    mutable[idx] = current.copy(items = current.items.filterIndexed { i, _ -> i != itemIndex })
                    EditorMutationResult(
                        mutable,
                        blockId,
                        EditorFocusRequest(blockId, subItemIndex = prevIdx, cursorOffset = prevLen, requestToken = nextToken()),
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
                    val prevLen = current.items[prevIdx].length
                    mutable[idx] = current.copy(items = current.items.filterIndexed { i, _ -> i != itemIndex })
                    EditorMutationResult(
                        mutable,
                        blockId,
                        EditorFocusRequest(blockId, subItemIndex = prevIdx, cursorOffset = prevLen, requestToken = nextToken()),
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
