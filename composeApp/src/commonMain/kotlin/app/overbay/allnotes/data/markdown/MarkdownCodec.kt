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

    private val NumberedListLineRegex = Regex("^\\d+\\.\\s+.*")

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

        val result = StringBuilder(text.length + 16)
        var lineStart = 0
        while (lineStart <= text.length) {
            val nlIdx = text.indexOf('\n', lineStart)
            val lineEnd = if (nlIdx < 0) text.length else nlIdx
            val lineText = text.substring(lineStart, lineEnd)
            val lineSpans = if (lineText.isEmpty()) {
                emptyList()
            } else {
                spans.mapNotNull { span ->
                    val s = maxOf(lineStart, span.start) - lineStart
                    val e = minOf(lineEnd, span.end) - lineStart
                    if (s < e) span.copy(start = s, end = e) else null
                }
            }
            result.append(renderLineMarkdown(lineText, lineSpans))
            if (nlIdx < 0) break
            result.append('\n')
            lineStart = nlIdx + 1
        }
        return result.toString()
    }

    private fun renderLineMarkdown(text: String, spans: List<RichTextSpan>): String {
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
                RichSpanKind.UNDERLINE -> {
                    openMarkers[s].add("<u>")
                    closeMarkers[e].add(0, "</u>")
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
                trimmed.matches(NumberedListLineRegex) -> {
                    val items = mutableListOf<String>()
                    while (index < rawLines.size) {
                        val t = rawLines[index].trim()
                        if (t.matches(NumberedListLineRegex)) {
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
                        splitMarkdownTableRow(rowStr)
                    }
                    val headers = parsedRows.firstOrNull().orEmpty().ifEmpty { listOf("Column 1") }
                    val dataStart = if (parsedRows.size >= 2 && parsedRows[1].all { c -> c.all { ch -> ch == '-' || ch == ':' } && c.isNotEmpty() }) 2 else 1
                    val rows = parsedRows.drop(dataStart).map { r ->
                        List(headers.size) { col -> r.getOrElse(col) { "" } }
                    }.ifEmpty { listOf(List(headers.size) { "" }) }
                    blocks.add(DocumentBlock.Table(id = nextId("blk"), headers = headers, rows = rows))
                }
                else -> {
                    val proseLines = mutableListOf<String>()
                    var pendingBlanks = 0
                    while (index < rawLines.size) {
                        val candidate = rawLines[index]
                        val candidateTrimmed = candidate.trim()
                        if (candidateTrimmed.isEmpty()) {
                            pendingBlanks++
                            index++
                        } else if (isStructuralMarkdownLine(candidateTrimmed)) {
                            break
                        } else {
                            repeat(pendingBlanks) { proseLines.add("") }
                            pendingBlanks = 0
                            proseLines.add(candidate)
                            index++
                        }
                    }
                    blocks.add(parseMultilineProseParagraph(nextId("blk"), proseLines))
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

    private fun splitMarkdownTableRow(rowStr: String): List<String> {
        val trimmed = rowStr.trim()
        val inner = if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val ch = inner[i]
            if (ch == '\\' && i + 1 < inner.length && inner[i + 1] == '|') {
                current.append('|')
                i += 2
            } else if (ch == '|') {
                cells.add(current.toString().trim())
                current.clear()
                i++
            } else {
                current.append(ch)
                i++
            }
        }
        cells.add(current.toString().trim())
        return cells
    }

    private fun isStructuralMarkdownLine(trimmed: String): Boolean =
        trimmed == "---" ||
            trimmed == "***" ||
            trimmed.startsWith("```") ||
            trimmed.startsWith("### ") ||
            trimmed.startsWith("## ") ||
            trimmed.startsWith("# ") ||
            trimmed.startsWith(">") ||
            trimmed.startsWith("- [ ] ") ||
            trimmed.startsWith("- [x] ") ||
            trimmed.startsWith("- [X] ") ||
            trimmed.startsWith("- ") ||
            trimmed.startsWith("* ") ||
            trimmed.matches(NumberedListLineRegex) ||
            (trimmed.startsWith("|") && trimmed.endsWith("|"))

    private fun parseMultilineProseParagraph(id: String, lines: List<String>): DocumentBlock.Paragraph {
        if (lines.size == 1) return parseInlineParagraph(id, lines.single())
        val combined = StringBuilder()
        val spans = mutableListOf<RichTextSpan>()
        lines.forEachIndexed { lineIdx, rawLine ->
            if (lineIdx > 0) combined.append('\n')
            val lineOffset = combined.length
            val parsedLine = parseInlineParagraph("", rawLine)
            combined.append(parsedLine.text)
            parsedLine.spans.forEach { span ->
                spans.add(span.copy(start = span.start + lineOffset, end = span.end + lineOffset))
            }
        }
        val text = combined.toString()
        return DocumentBlock.Paragraph(
            id = id,
            text = text,
            spans = RichSpanEngine.normalizeAndCoalesce(text.length, spans)
        )
    }

    private fun parseInlineParagraph(id: String, raw: String): DocumentBlock.Paragraph {
        val sb = StringBuilder()
        val spans = mutableListOf<RichTextSpan>()
        appendParsedInline(raw, 0, raw.length, sb, spans)
        val cleanText = sb.toString()
        return DocumentBlock.Paragraph(
            id = id,
            text = cleanText,
            spans = RichSpanEngine.normalizeAndCoalesce(cleanText.length, spans)
        )
    }

    private fun appendParsedInline(
        raw: String,
        startIndex: Int,
        endIndex: Int,
        sb: StringBuilder,
        spans: MutableList<RichTextSpan>
    ) {
        var i = startIndex
        while (i < endIndex) {
            when {
                raw.startsWith("**", i) && i + 2 < endIndex -> {
                    var close = raw.indexOf("**", i + 2)
                    if (close in (i + 2) until endIndex) {
                        val innerSingleStar = raw.indexOf('*', i + 2)
                        if (close + 2 < endIndex && raw[close + 2] == '*' && innerSingleStar in (i + 2) until close) {
                            close++
                        }
                        val spanStart = sb.length
                        appendParsedInline(raw, i + 2, close, sb, spans)
                        spans.add(RichTextSpan(spanStart, sb.length, RichSpanKind.BOLD))
                        i = close + 2
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw.startsWith("<u>", i, ignoreCase = true) -> {
                    val close = raw.indexOf("</u>", i + 3, ignoreCase = true)
                    if (close in (i + 3) until endIndex) {
                        val spanStart = sb.length
                        appendParsedInline(raw, i + 3, close, sb, spans)
                        spans.add(RichTextSpan(spanStart, sb.length, RichSpanKind.UNDERLINE))
                        i = close + 4
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw.startsWith("~~", i) && i + 2 < endIndex -> {
                    val close = raw.indexOf("~~", i + 2)
                    if (close in (i + 2) until endIndex) {
                        val spanStart = sb.length
                        appendParsedInline(raw, i + 2, close, sb, spans)
                        spans.add(RichTextSpan(spanStart, sb.length, RichSpanKind.STRIKETHROUGH))
                        i = close + 2
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw.startsWith("==", i) && i + 2 < endIndex -> {
                    val close = raw.indexOf("==", i + 2)
                    if (close in (i + 2) until endIndex) {
                        val spanStart = sb.length
                        appendParsedInline(raw, i + 2, close, sb, spans)
                        spans.add(RichTextSpan(spanStart, sb.length, RichSpanKind.HIGHLIGHT))
                        i = close + 2
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw[i] == '`' -> {
                    val close = raw.indexOf('`', i + 1)
                    if (close in (i + 1) until endIndex) {
                        val spanStart = sb.length
                        sb.append(raw, i + 1, close)
                        spans.add(RichTextSpan(spanStart, sb.length, RichSpanKind.INLINE_CODE))
                        i = close + 1
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw[i] == '*' -> {
                    val close = raw.indexOf('*', i + 1)
                    if (close in (i + 1) until endIndex) {
                        val spanStart = sb.length
                        appendParsedInline(raw, i + 1, close, sb, spans)
                        spans.add(RichTextSpan(spanStart, sb.length, RichSpanKind.ITALIC))
                        i = close + 1
                    } else {
                        sb.append(raw[i++])
                    }
                }
                raw[i] == '[' -> {
                    val closeBracket = raw.indexOf("](", i + 1)
                    val closeParen = if (closeBracket in (i + 1) until endIndex) {
                        raw.indexOf(')', closeBracket + 2)
                    } else -1
                    if (closeBracket in (i + 1) until endIndex && closeParen in (closeBracket + 2) until endIndex) {
                        val url = raw.substring(closeBracket + 2, closeParen)
                        val spanStart = sb.length
                        appendParsedInline(raw, i + 1, closeBracket, sb, spans)
                        spans.add(RichTextSpan(spanStart, sb.length, RichSpanKind.LINK, url))
                        i = closeParen + 1
                    } else {
                        sb.append(raw[i++])
                    }
                }
                else -> sb.append(raw[i++])
            }
        }
    }
}
