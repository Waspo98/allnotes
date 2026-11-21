package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine

internal data class TextEditDiff(
    val prefix: Int,
    val newSuffix: Int,
    val isMultilinePaste: Boolean
)

/**
 * Matches line-level Markdown auto-formatting prefixes and constructs empty slash-command blocks
 * for [EditorInputController].
 */
internal object EditorMarkdownTriggers {

    fun computeEditDiff(oldText: String, newText: String): TextEditDiff {
        var prefix = 0
        val maxPrefix = minOf(oldText.length, newText.length)
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) {
            prefix++
        }
        var oldSuffix = oldText.length
        var newSuffix = newText.length
        while (oldSuffix > prefix && newSuffix > prefix && oldText[oldSuffix - 1] == newText[newSuffix - 1]) {
            oldSuffix--
            newSuffix--
        }
        val insertedSegment = newText.substring(prefix, newSuffix)
        val isMultilinePaste = insertedSegment.contains('\n') && insertedSegment.length > 1
        return TextEditDiff(prefix = prefix, newSuffix = newSuffix, isMultilinePaste = isMultilinePaste)
    }

    private class LineTriggerSpec(
        val cursorOffset: Int,
        val subItemIndex: Int = -1,
        val buildBlock: (String) -> DocumentBlock
    )

    fun tryApplyLineTrigger(
        blocks: List<DocumentBlock>,
        blockIndex: Int,
        updatedParagraph: DocumentBlock.Paragraph,
        line: ParagraphFlowEngine.LineBounds,
        normalizedCursor: Int,
        nextId: () -> String,
        nextToken: () -> Long
    ): EditorMutationResult? {
        val lineText = line.text
        if (lineText == "--- " || lineText == "---") {
            return applyDividerLineTrigger(blocks, blockIndex, updatedParagraph, line, nextId, nextToken)
        }

        val cursorInLine = (normalizedCursor - line.start).coerceIn(0, lineText.length)
        fun shifted(prefixLen: Int, rest: String): Int = (cursorInLine - prefixLen).coerceIn(0, rest.length)

        val spec: LineTriggerSpec? = when {
            lineText.startsWith("### ") -> {
                val rest = lineText.removePrefix("### ")
                LineTriggerSpec(shifted(4, rest)) { id -> DocumentBlock.Heading(id = id, text = rest, level = 3) }
            }
            lineText.startsWith("## ") -> {
                val rest = lineText.removePrefix("## ")
                LineTriggerSpec(shifted(3, rest)) { id -> DocumentBlock.Heading(id = id, text = rest, level = 2) }
            }
            lineText.startsWith("# ") -> {
                val rest = lineText.removePrefix("# ")
                LineTriggerSpec(shifted(2, rest)) { id -> DocumentBlock.Heading(id = id, text = rest, level = 1) }
            }
            lineText.startsWith("- [x] ") || lineText.startsWith("- [X] ") ||
                lineText.startsWith("[x] ") || lineText.startsWith("[X] ") -> {
                val rest = lineText.substringAfter("] ")
                LineTriggerSpec(shifted(lineText.length - rest.length, rest), subItemIndex = 0) { id ->
                    DocumentBlock.Checklist(
                        id = id,
                        title = "",
                        items = listOf(ChecklistItem(id = nextId(), text = rest, isChecked = true))
                    )
                }
            }
            lineText.startsWith("- [ ] ") || lineText.startsWith("[] ") || lineText.startsWith("[ ] ") -> {
                val rest = lineText.substringAfter("] ")
                LineTriggerSpec(shifted(lineText.length - rest.length, rest), subItemIndex = 0) { id ->
                    DocumentBlock.Checklist(
                        id = id,
                        title = "",
                        items = listOf(ChecklistItem(id = nextId(), text = rest, isChecked = false))
                    )
                }
            }
            lineText.startsWith("- ") || lineText.startsWith("* ") -> {
                val rest = lineText.substring(2)
                LineTriggerSpec(shifted(2, rest), subItemIndex = 0) { id ->
                    DocumentBlock.BulletList(id = id, isNumbered = false, items = listOf(rest))
                }
            }
            lineText.startsWith("1. ") -> {
                val rest = lineText.removePrefix("1. ")
                LineTriggerSpec(shifted(3, rest), subItemIndex = 0) { id ->
                    DocumentBlock.BulletList(id = id, isNumbered = true, items = listOf(rest))
                }
            }
            lineText.startsWith("> ") -> {
                val rest = lineText.removePrefix("> ")
                LineTriggerSpec(shifted(2, rest)) { id ->
                    DocumentBlock.Callout(id = id, title = "Note", text = rest, tone = CalloutTone.INFO)
                }
            }
            lineText.startsWith("```") && lineText.endsWith(" ") && !lineText.removePrefix("```").dropLast(1).contains(' ') -> {
                val lang = lineText.removePrefix("```").trim().ifEmpty { "kotlin" }
                LineTriggerSpec(0) { id -> DocumentBlock.CodeSnippet(id = id, language = lang, code = "") }
            }
            else -> null
        }

        if (spec == null) return null

        val splice = ParagraphFlowEngine.replaceLineRangeWithBlocks(
            blocks = blocks,
            blockIndex = blockIndex,
            paragraph = updatedParagraph,
            rangeStart = line.start,
            rangeEnd = line.end,
            nextId = nextId
        ) { preferredId -> listOf(spec.buildBlock(preferredId)) }
        val targetId = splice.primaryBlockId
        return EditorMutationResult(
            blocks = splice.blocks,
            activeBlockId = targetId,
            focusRequest = EditorFocusRequest(
                blockId = targetId,
                subItemIndex = spec.subItemIndex,
                cursorOffset = spec.cursorOffset,
                requestToken = nextToken()
            ),
            isStructuralConversion = true
        )
    }

    private fun applyDividerLineTrigger(
        blocks: List<DocumentBlock>,
        blockIndex: Int,
        paragraph: DocumentBlock.Paragraph,
        line: ParagraphFlowEngine.LineBounds,
        nextId: () -> String,
        nextToken: () -> Long
    ): EditorMutationResult {
        val splice = ParagraphFlowEngine.replaceLineRangeWithBlocks(
            blocks = blocks,
            blockIndex = blockIndex,
            paragraph = paragraph,
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

    fun buildEmptySlashBlock(
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

    fun removeRangePreservingSpans(
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
        return DocumentBlock.Paragraph(id = paragraph.id, text = updatedText, spans = updatedSpans)
    }

    fun executeSlashCommandOnWholeBlock(
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
                val subIdx = if (
                    kind == SlashCommandKind.CHECKLIST ||
                    kind == SlashCommandKind.BULLET_LIST ||
                    kind == SlashCommandKind.NUMBERED_LIST
                ) 0 else -1
                EditorMutationResult(
                    mutable,
                    blockId,
                    EditorFocusRequest(blockId, subItemIndex = subIdx, cursorOffset = 0, requestToken = nextToken()),
                    true
                )
            }
        }
    }
}
