package app.overbay.allnotes.data.markdown

import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.model.RichTextSpan
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine

/**
 * Bidirectional GitHub-Flavored Markdown serializer and parser for [NotePage].
 */
object MarkdownCodec {

    fun exportPageToMarkdown(page: NotePage): String = buildString {
        val cleanTitle = page.title.ifBlank { "Untitled Page" }
        append("# ").append(cleanTitle).append("\n\n")

        page.blocks.forEachIndexed { idx, block ->
            when (block) {
                is DocumentBlock.Heading -> {
                    val hashes = "#".repeat(block.level.coerceIn(1, 3))
                    append(hashes).append(' ').append(block.text)
                }
                is DocumentBlock.Paragraph -> {
                    append(renderParagraphMarkdown(block))
                }
                is DocumentBlock.Checklist -> {
                    if (block.title.isNotBlank()) {
                        append("**").append(block.title).append("**\n")
                    }
                    block.items.forEachIndexed { itemIdx, item ->
                        val box = if (item.isChecked) "[x]" else "[ ]"
                        append("- ").append(box).append(' ').append(item.text)
                        if (itemIdx < block.items.lastIndex) append('\n')
                    }
                }
                is DocumentBlock.BulletList -> {
                    block.items.forEachIndexed { itemIdx, item ->
                        val prefix = if (block.isNumbered) "${itemIdx + 1}. " else "- "
                        append(prefix).append(item)
                        if (itemIdx < block.items.lastIndex) append('\n')
                    }
                }
                is DocumentBlock.Table -> {
                    if (block.headers.isNotEmpty()) {
                        append("| ").append(block.headers.joinToString(" | ") { it.replace("|", "\\|") }).append(" |\n")
                        append("| ").append(block.headers.joinToString(" | ") { "---" }).append(" |")
                        for (row in block.rows) {
                            val padded = List(block.headers.size) { c -> row.getOrElse(c) { "" }.replace("|", "\\|") }
                            append("\n| ").append(padded.joinToString(" | ")).append(" |")
                        }
                    }
                }
                is DocumentBlock.Callout -> {
                    val tag = when (block.tone) {
                        CalloutTone.INFO -> "NOTE"
                        CalloutTone.TIP -> "TIP"
                        CalloutTone.IMPORTANT -> "IMPORTANT"
                    }
                    append("> [!").append(tag).append("] ").append(block.title).append('\n')
                    block.text.lines().forEachIndexed { lIdx, line ->
                        append("> ").append(line)
                        if (lIdx < block.text.lines().lastIndex) append('\n')
                    }
                }
                is DocumentBlock.CodeSnippet -> {
                    append("```").append(block.language).append('\n')
                    append(block.code).append("\n```")
                }
                is DocumentBlock.Divider -> {
                    append("---")
                }
                is DocumentBlock.Attachment -> {
                    val label = block.caption.ifBlank { block.fileName }
                    if (block.mimeType.startsWith("image/")) {
                        append("![").append(label).append("](attachment:").append(block.fileName).append(')')
                    } else {
                        append('[').append(label).append("](attachment:").append(block.fileName).append(')')
                    }
                }
            }
            if (idx < page.blocks.lastIndex) {
                append("\n\n")
            } else {
                append('\n')
            }
        }
    }

    private fun renderParagraphMarkdown(paragraph: DocumentBlock.Paragraph): String {
        val text = paragraph.text
        val spans = paragraph.effectiveSpans()
        if (text.isEmpty() || spans.isEmpty()) return text

        val openMarkers = Array(text.length + 1) { mutableListOf<String>() }
        val closeMarkers = Array(text.length + 1) { mutableListOf<String>() }

        for (span in spans) {
            val s = span.start.coerceIn(0, text.length)
            val e = span.end.coerceIn(0, text.length)
            if (s >= e) continue
            when (span.kind) {
                RichSpanKind.BOLD -> {
                    openMarkers[s].add("**")
                    closeMarkers[e].add(0, "**")
                }
                RichSpanKind.ITALIC -> {
                    openMarkers[s].add("*")
                    closeMarkers[e].add(0, "*")
                }
                RichSpanKind.STRIKETHROUGH -> {
                    openMarkers[s].add("~~")
                    closeMarkers[e].add(0, "~~")
                }
                RichSpanKind.INLINE_CODE -> {
                    openMarkers[s].add("`")
                    closeMarkers[e].add(0, "`")
                }
                RichSpanKind.HIGHLIGHT -> {
                    openMarkers[s].add("==")
                    closeMarkers[e].add(0, "==")
                }
                RichSpanKind.LINK -> {
                    val url = span.url?.takeIf { it.isNotBlank() } ?: continue
                    openMarkers[s].add("[")
                    closeMarkers[e].add(0, "]($url)")
                }
                else -> Unit
            }
        }

        return buildString(text.length + 16) {
            for (i in 0..text.length) {
                closeMarkers[i].forEach { append(it) }
                openMarkers[i].forEach { append(it) }
                if (i < text.length) append(text[i])
            }
        }
    }

    fun importMarkdownToPage(
        markdown: String,
        sectionId: String,
        nextId: (String) -> String,
        updatedAtEpochMs: Long = 0L
    ): NotePage {
        val rawLines = markdown.replace("\r\n", "\n").lines()
        var index = 0
        while (index < rawLines.size && rawLines[index].isBlank()) index++

        var pageTitle = "Imported Note"
        if (index < rawLines.size && rawLines[index].startsWith("# ")) {
            pageTitle = rawLines[index].removePrefix("# ").trim().ifEmpty { "Imported Note" }
            index++
        }

        val blocks = mutableListOf<DocumentBlock>()

        while (index < rawLines.size) {
            val line = rawLines[index]
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> {
                    index++
                }
                trimmed == "---" || trimmed == "***" -> {
                    blocks.add(DocumentBlock.Divider(id = nextId("blk")))
                    index++
                }
                trimmed.startsWith("```") -> {
                    val lang = trimmed.removePrefix("```").trim().ifEmpty { "text" }
                    index++
                    val codeLines = mutableListOf<String>()
                    while (index < rawLines.size && !rawLines[index].trim().startsWith("```")) {
                        codeLines.add(rawLines[index])
                        index++
                    }
                    if (index < rawLines.size) index++
                    blocks.add(
                        DocumentBlock.CodeSnippet(
                            id = nextId("blk"),
                            language = lang,
                            code = codeLines.joinToString("\n")
                        )
                    )
                }
                trimmed.startsWith("### ") -> {
                    blocks.add(DocumentBlock.Heading(id = nextId("blk"), text = trimmed.removePrefix("### ").trim(), level = 3))
                    index++
                }
                trimmed.startsWith("## ") -> {
                    blocks.add(DocumentBlock.Heading(id = nextId("blk"), text = trimmed.removePrefix("## ").trim(), level = 2))
                    index++
                }
                trimmed.startsWith("# ") -> {
                    blocks.add(DocumentBlock.Heading(id = nextId("blk"), text = trimmed.removePrefix("# ").trim(), level = 1))
                    index++
                }
                trimmed.startsWith(">") -> {
                    val quoteLines = mutableListOf<String>()
                    while (index < rawLines.size && rawLines[index].trim().startsWith(">")) {
                        quoteLines.add(rawLines[index].trim().removePrefix(">").removePrefix(" "))
                        index++
                    }
                    val firstLine = quoteLines.firstOrNull().orEmpty()
                    val (tone, title, bodyLines) = when {
                        firstLine.startsWith("[!IMPORTANT]") -> Triple(
                            CalloutTone.IMPORTANT,
                            firstLine.removePrefix("[!IMPORTANT]").trim().ifEmpty { "Key Point" },
                            quoteLines.drop(1)
                        )
                        firstLine.startsWith("[!TIP]") -> Triple(
                            CalloutTone.TIP,
                            firstLine.removePrefix("[!TIP]").trim().ifEmpty { "Tip" },
                            quoteLines.drop(1)
                        )
                        firstLine.startsWith("[!NOTE]") -> Triple(
                            CalloutTone.INFO,
                            firstLine.removePrefix("[!NOTE]").trim().ifEmpty { "Note" },
                            quoteLines.drop(1)
                        )
                        else -> Triple(CalloutTone.INFO, "Note", quoteLines)
                    }
                    blocks.add(
                        DocumentBlock.Callout(
                            id = nextId("blk"),
                            title = title,
                            text = bodyLines.joinToString("\n"),
                            tone = tone
                        )
                    )
                }
                trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                    val items = mutableListOf<ChecklistItem>()
                    while (index < rawLines.size) {
                        val t = rawLines[index].trim()
                        val checked = when {
                            t.startsWith("- [x] ") || t.startsWith("- [X] ") -> true
                            t.startsWith("- [ ] ") -> false
                            else -> break
                        }
                        items.add(ChecklistItem(id = nextId("chk"), text = t.substring(6), isChecked = checked))
                        index++
                    }
                    blocks.add(DocumentBlock.Checklist(id = nextId("blk"), title = "", items = items))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val items = mutableListOf<String>()
                    while (index < rawLines.size) {
                        val t = rawLines[index].trim()
                        if (t.startsWith("- [ ] ") || t.startsWith("- [x] ") || t.startsWith("- [X] ")) break
                        if (t.startsWith("- ") || t.startsWith("* ")) {
                            items.add(t.substring(2))
                            index++
                        } else break
                    }
                    blocks.add(DocumentBlock.BulletList(id = nextId("blk"), isNumbered = false, items = items))
                }
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val items = mutableListOf<String>()
                    while (index < rawLines.size) {
                        val t = rawLines[index].trim()
                        if (t.matches(Regex("^\\d+\\.\\s+.*"))) {
                            items.add(t.substringAfter(". ").trimStart())
                            index++
                        } else break
                    }
                    blocks.add(DocumentBlock.BulletList(id = nextId("blk"), isNumbered = true, items = items))
                }
                trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    while (index < rawLines.size && rawLines[index].trim().let { it.startsWith("|") && it.endsWith("|") }) {
                        tableLines.add(rawLines[index].trim())
                        index++
                    }
                    val parsedRows = tableLines.map { rowStr ->
                        rowStr.removeSurrounding("|").split("|").map { it.trim() }
                    }
                    val headers = parsedRows.firstOrNull().orEmpty().ifEmpty { listOf("Column 1") }
                    val dataStart = if (parsedRows.size >= 2 && parsedRows[1].all { c -> c.all { ch -> ch == '-' || ch == ':' } && c.isNotEmpty() }) 2 else 1
                    val rows = parsedRows.drop(dataStart).map { r ->
                        List(headers.size) { col -> r.getOrElse(col) { "" } }
                    }.ifEmpty { listOf(List(headers.size) { "" }) }
                    blocks.add(DocumentBlock.Table(id = nextId("blk"), headers = headers, rows = rows))
                }
                else -> {
                    blocks.add(parseInlineParagraph(nextId("blk"), line))
                    index++
                }
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(DocumentBlock.Paragraph(id = nextId("blk"), text = ""))
        }

        return NotePage(
            id = nextId("page"),
            sectionId = sectionId,
            title = pageTitle,
            blocks = blocks,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    private fun parseInlineParagraph(id: String, raw: String): DocumentBlock.Paragraph {
        val sb = StringBuilder()
        val spans = mutableListOf<RichTextSpan>()
        var i = 0
        while (i < raw.length) {
            when {
                raw.startsWith("**", i) -> {
                    val close = raw.indexOf("**", i + 2)
                    if (close > i + 2) {
                        val start = sb.length
                        sb.append(raw.substring(i + 2, close))
                        spans.add(RichTextSpan(start, sb.length, RichSpanKind.BOLD))
                        i = close + 2
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw.startsWith("~~", i) -> {
                    val close = raw.indexOf("~~", i + 2)
                    if (close > i + 2) {
                        val start = sb.length
                        sb.append(raw.substring(i + 2, close))
                        spans.add(RichTextSpan(start, sb.length, RichSpanKind.STRIKETHROUGH))
                        i = close + 2
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw.startsWith("==", i) -> {
                    val close = raw.indexOf("==", i + 2)
                    if (close > i + 2) {
                        val start = sb.length
                        sb.append(raw.substring(i + 2, close))
                        spans.add(RichTextSpan(start, sb.length, RichSpanKind.HIGHLIGHT))
                        i = close + 2
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw[i] == '`' -> {
                    val close = raw.indexOf('`', i + 1)
                    if (close > i + 1) {
                        val start = sb.length
                        sb.append(raw.substring(i + 1, close))
                        spans.add(RichTextSpan(start, sb.length, RichSpanKind.INLINE_CODE))
                        i = close + 1
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw[i] == '*' -> {
                    val close = raw.indexOf('*', i + 1)
                    if (close > i + 1) {
                        val start = sb.length
                        sb.append(raw.substring(i + 1, close))
                        spans.add(RichTextSpan(start, sb.length, RichSpanKind.ITALIC))
                        i = close + 1
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw[i] == '[' -> {
                    val closeBracket = raw.indexOf("](", i + 1)
                    val closeParen = if (closeBracket > i) raw.indexOf(')', closeBracket + 2) else -1
                    if (closeBracket > i && closeParen > closeBracket + 2) {
                        val label = raw.substring(i + 1, closeBracket)
                        val url = raw.substring(closeBracket + 2, closeParen)
                        val start = sb.length
                        sb.append(label)
                        spans.add(RichTextSpan(start, sb.length, RichSpanKind.LINK, url))
                        i = closeParen + 1
                    } else {
                        sb.append(raw[i++])
                    }
                }
                else -> sb.append(raw[i++])
            }
        }
        val cleanText = sb.toString()
        return DocumentBlock.Paragraph(
            id = id,
            text = cleanText,
            spans = RichSpanEngine.normalizeAndCoalesce(cleanText.length, spans)
        )
    }
}
