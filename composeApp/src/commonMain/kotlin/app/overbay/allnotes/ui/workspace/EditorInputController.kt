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
 * - Markdown live prefix triggers (`# `, `- `, `1. `, `[] `, `> `, ```` ``` ````, `---`)
 * - Slash `/` inline command replacements
 * - `Enter` key block splitting & list exit
 * - `Backspace` at offset `0` block merging & list demotion
 */
object EditorInputController {

    fun matchingSlashCommands(paragraphText: String): List<SlashCommandKind> {
        if (!paragraphText.startsWith("/")) return emptyList()
        if (paragraphText.contains(' ') || paragraphText.contains('\n') || paragraphText.length > 18) {
            return emptyList()
        }
        val query = paragraphText.removePrefix("/").lowercase()
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

        // Check Markdown live auto-conversion prefixes
        val converted: Pair<DocumentBlock, EditorFocusRequest>? = when {
            newText.startsWith("### ") -> {
                val rest = newText.removePrefix("### ")
                DocumentBlock.Heading(id = blockId, text = rest, level = 3) to
                    EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken())
            }
            newText.startsWith("## ") -> {
                val rest = newText.removePrefix("## ")
                DocumentBlock.Heading(id = blockId, text = rest, level = 2) to
                    EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken())
            }
            newText.startsWith("# ") -> {
                val rest = newText.removePrefix("# ")
                DocumentBlock.Heading(id = blockId, text = rest, level = 1) to
                    EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken())
            }
            newText.startsWith("- [x] ") || newText.startsWith("- [X] ") || newText.startsWith("[x] ") || newText.startsWith("[X] ") -> {
                val rest = newText.substringAfter("] ")
                DocumentBlock.Checklist(
                    id = blockId,
                    title = "",
                    items = listOf(ChecklistItem(id = nextId(), text = rest, isChecked = true))
                ) to EditorFocusRequest(blockId, subItemIndex = 0, cursorOffset = 0, requestToken = nextToken())
            }
            newText.startsWith("- [ ] ") || newText.startsWith("[] ") || newText.startsWith("[ ] ") -> {
                val rest = newText.substringAfter("] ")
                DocumentBlock.Checklist(
                    id = blockId,
                    title = "",
                    items = listOf(ChecklistItem(id = nextId(), text = rest, isChecked = false))
                ) to EditorFocusRequest(blockId, subItemIndex = 0, cursorOffset = 0, requestToken = nextToken())
            }
            newText.startsWith("- ") || newText.startsWith("* ") -> {
                val rest = newText.substring(2)
                DocumentBlock.BulletList(id = blockId, isNumbered = false, items = listOf(rest)) to
                    EditorFocusRequest(blockId, subItemIndex = 0, cursorOffset = 0, requestToken = nextToken())
            }
            newText.startsWith("1. ") -> {
                val rest = newText.removePrefix("1. ")
                DocumentBlock.BulletList(id = blockId, isNumbered = true, items = listOf(rest)) to
                    EditorFocusRequest(blockId, subItemIndex = 0, cursorOffset = 0, requestToken = nextToken())
            }
            newText.startsWith("> ") -> {
                val rest = newText.removePrefix("> ")
                DocumentBlock.Callout(id = blockId, title = "Note", text = rest, tone = CalloutTone.INFO) to
                    EditorFocusRequest(blockId, cursorOffset = rest.length, requestToken = nextToken())
            }
            newText.startsWith("```") && (newText.endsWith(" ") || newText.length == 3) -> {
                val lang = newText.removePrefix("```").trim().ifEmpty { "kotlin" }
                DocumentBlock.CodeSnippet(id = blockId, language = lang, code = "") to
                    EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken())
            }
            newText == "--- " || newText == "---" -> {
                val trailingParaId = nextId()
                val mutable = blocks.toMutableList()
                mutable[idx] = DocumentBlock.Divider(id = blockId)
                mutable.add(idx + 1, DocumentBlock.Paragraph(id = trailingParaId, text = ""))
                return EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = trailingParaId,
                    focusRequest = EditorFocusRequest(trailingParaId, cursorOffset = 0, requestToken = nextToken()),
                    isStructuralConversion = true
                )
            }
            else -> null
        }

        if (converted != null) {
            val mutable = blocks.toMutableList()
            mutable[idx] = converted.first
            return EditorMutationResult(
                blocks = mutable,
                activeBlockId = blockId,
                focusRequest = converted.second,
                isStructuralConversion = true
            )
        }

        val adjustedSpans = RichSpanEngine.adjustSpansAfterEdit(
            oldText = current.text,
            newText = newText,
            existingSpans = current.spans
        )
        val updatedParagraph = current.copy(text = newText, spans = adjustedSpans)
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
        nextToken: () -> Long
    ): EditorMutationResult {
        val idx = blocks.indexOfFirst { it.id == blockId }
        if (idx < 0) return EditorMutationResult(blocks, blockId)
        val mutable = blocks.toMutableList()

        return when (kind) {
            SlashCommandKind.PARAGRAPH -> {
                mutable[idx] = DocumentBlock.Paragraph(id = blockId, text = "")
                EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.HEADING_1 -> {
                mutable[idx] = DocumentBlock.Heading(id = blockId, text = "", level = 1)
                EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.HEADING_2 -> {
                mutable[idx] = DocumentBlock.Heading(id = blockId, text = "", level = 2)
                EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.HEADING_3 -> {
                mutable[idx] = DocumentBlock.Heading(id = blockId, text = "", level = 3)
                EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.CHECKLIST -> {
                mutable[idx] = DocumentBlock.Checklist(
                    id = blockId,
                    title = "",
                    items = listOf(ChecklistItem(id = nextId(), text = "", isChecked = false))
                )
                EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, subItemIndex = 0, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.BULLET_LIST -> {
                mutable[idx] = DocumentBlock.BulletList(id = blockId, isNumbered = false, items = listOf(""))
                EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, subItemIndex = 0, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.NUMBERED_LIST -> {
                mutable[idx] = DocumentBlock.BulletList(id = blockId, isNumbered = true, items = listOf(""))
                EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, subItemIndex = 0, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.TABLE -> {
                mutable[idx] = DocumentBlock.Table(
                    id = blockId,
                    headers = listOf("Column 1", "Column 2", "Status"),
                    rows = listOf(listOf("", "", ""))
                )
                EditorMutationResult(mutable, blockId, isStructuralConversion = true)
            }
            SlashCommandKind.CALLOUT -> {
                mutable[idx] = DocumentBlock.Callout(id = blockId, title = "Key Point", text = "", tone = CalloutTone.TIP)
                EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.CODE -> {
                mutable[idx] = DocumentBlock.CodeSnippet(id = blockId, language = "kotlin", code = "")
                EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.DIVIDER -> {
                val nextParaId = nextId()
                mutable[idx] = DocumentBlock.Divider(id = blockId)
                mutable.add(idx + 1, DocumentBlock.Paragraph(id = nextParaId, text = ""))
                EditorMutationResult(mutable, nextParaId, EditorFocusRequest(nextParaId, cursorOffset = 0, requestToken = nextToken()), true)
            }
            SlashCommandKind.ATTACHMENT -> {
                mutable[idx] = DocumentBlock.Paragraph(id = blockId, text = "")
                EditorMutationResult(mutable, blockId, isStructuralConversion = true)
            }
        }
    }

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
        val newBlockId = nextId()

        return when (val target = blocks[idx]) {
            is DocumentBlock.Paragraph -> {
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
                mutable.add(idx + 1, DocumentBlock.Paragraph(id = newBlockId, text = tailText))
                EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = newBlockId,
                    focusRequest = EditorFocusRequest(newBlockId, cursorOffset = 0, requestToken = nextToken()),
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
                EditorMutationResult(
                    blocks = mutable,
                    activeBlockId = current.id,
                    focusRequest = EditorFocusRequest(current.id, cursorOffset = 0, requestToken = nextToken()),
                    isStructuralConversion = true
                )
            }
            is DocumentBlock.Callout -> {
                if (current.text.isEmpty() && current.title.isEmpty()) {
                    mutable[idx] = DocumentBlock.Paragraph(id = current.id, text = "")
                    EditorMutationResult(
                        blocks = mutable,
                        activeBlockId = current.id,
                        focusRequest = EditorFocusRequest(current.id, cursorOffset = 0, requestToken = nextToken()),
                        isStructuralConversion = true
                    )
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
                            mutable[idx - 1] = prev.copy(text = prev.text + current.text)
                            mutable.removeAt(idx)
                            EditorMutationResult(
                                blocks = mutable,
                                activeBlockId = prev.id,
                                focusRequest = EditorFocusRequest(prev.id, cursorOffset = joinOffset, requestToken = nextToken()),
                                isStructuralConversion = true
                            )
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
                        EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
                    } else {
                        val trimmed = current.items.filterIndexed { i, _ -> i != itemIndex }
                        val newParaId = nextId()
                        mutable[idx] = current.copy(items = trimmed)
                        mutable.add(idx + 1, DocumentBlock.Paragraph(id = newParaId, text = ""))
                        EditorMutationResult(mutable, newParaId, EditorFocusRequest(newParaId, cursorOffset = 0, requestToken = nextToken()), true)
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
                        EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
                    } else {
                        val trimmed = current.items.filterIndexed { i, _ -> i != itemIndex }
                        val newParaId = nextId()
                        mutable[idx] = current.copy(items = trimmed)
                        mutable.add(idx + 1, DocumentBlock.Paragraph(id = newParaId, text = ""))
                        EditorMutationResult(mutable, newParaId, EditorFocusRequest(newParaId, cursorOffset = 0, requestToken = nextToken()), true)
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
                    EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
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
                    EditorMutationResult(mutable, blockId, EditorFocusRequest(blockId, cursorOffset = 0, requestToken = nextToken()), true)
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
        nextToken: () -> Long
    ): EditorMutationResult {
        if (target == SlashCommandKind.ATTACHMENT) return EditorMutationResult(blocks, blockId)
        val converted = BlockMutationEngine.convertBlock(blocks, blockId, target, nextId)
        if (converted == blocks) return EditorMutationResult(blocks, blockId)
        val focusSubIdx = if (
            target == SlashCommandKind.CHECKLIST ||
            target == SlashCommandKind.BULLET_LIST ||
            target == SlashCommandKind.NUMBERED_LIST
        ) 0 else -1
        return EditorMutationResult(
            blocks = converted,
            activeBlockId = blockId,
            focusRequest = EditorFocusRequest(
                blockId = blockId,
                subItemIndex = focusSubIdx,
                cursorOffset = Int.MAX_VALUE,
                requestToken = nextToken()
            ),
            isStructuralConversion = true
        )
    }
}
