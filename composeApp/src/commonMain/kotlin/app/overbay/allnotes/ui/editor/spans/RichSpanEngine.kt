package app.overbay.allnotes.ui.editor.spans

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.model.RichTextSpan
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.ui.theme.accentColorForTheme
import app.overbay.allnotes.ui.theme.highlightContainerColor
import app.overbay.allnotes.ui.theme.onHighlightContainerColor

enum class FontColorOption(
    val label: String,
    val spanKind: RichSpanKind?,
    val colorTag: String? = null
) {
    DEFAULT("Default", null, null),
    INDIGO("Indigo", RichSpanKind.COLOR_ACCENT, null),
    EMERALD("Emerald", RichSpanKind.COLOR_ACCENT, "color:EMERALD"),
    TEAL("Teal", RichSpanKind.COLOR_TERTIARY, null),
    AMBER("Amber", RichSpanKind.COLOR_ACCENT, "color:AMBER"),
    RED("Red", RichSpanKind.COLOR_ERROR, null),
    VIOLET("Violet", RichSpanKind.COLOR_ACCENT, "color:VIOLET");

    companion object {
        fun fromSpan(span: RichTextSpan): FontColorOption = when (span.kind) {
            RichSpanKind.COLOR_TERTIARY -> TEAL
            RichSpanKind.COLOR_ERROR -> RED
            RichSpanKind.COLOR_ACCENT -> when (span.url) {
                EMERALD.colorTag -> EMERALD
                AMBER.colorTag -> AMBER
                VIOLET.colorTag -> VIOLET
                else -> INDIGO
            }
            else -> DEFAULT
        }
    }
}

/**
 * Pure character-range rich text span engine supporting selection toggling,
 * mid-span edit offset shifting, paragraph split/merge span preservation,
 * and Material 3 Expressive [AnnotatedString] rendering.
 */
object RichSpanEngine {

    private val colorKinds = setOf(
        RichSpanKind.COLOR_ACCENT,
        RichSpanKind.COLOR_TERTIARY,
        RichSpanKind.COLOR_ERROR
    )

    fun resolveWordRangeAtCursor(text: String, cursor: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null
        val clamped = cursor.coerceIn(0, text.length)
        val probe = when {
            clamped < text.length && !text[clamped].isWhitespace() -> clamped
            clamped > 0 && !text[clamped - 1].isWhitespace() -> clamped - 1
            else -> return null
        }
        var start = probe
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        var end = probe + 1
        while (end < text.length && !text[end].isWhitespace()) end++
        return if (start < end) start to end else null
    }

    fun toggleSelectionSpan(
        paragraph: DocumentBlock.Paragraph,
        rawStart: Int,
        rawEnd: Int,
        kind: RichSpanKind,
        url: String? = null
    ): DocumentBlock.Paragraph {
        val text = paragraph.text
        if (text.isEmpty()) {
            return toggleWholeBlockBoolean(paragraph, kind)
        }

        val minSel = minOf(rawStart, rawEnd).coerceIn(0, text.length)
        val maxSel = maxOf(rawStart, rawEnd).coerceIn(0, text.length)
        val targetRange = if (minSel < maxSel) {
            minSel to maxSel
        } else {
            resolveWordRangeAtCursor(text, minSel) ?: return paragraph
        }

        // Promote any whole-block legacy boolean for this kind into explicit spans before sub-range editing
        val demotedParagraph = clearLegacyBooleanForKind(paragraph, kind)
        val baseSpans = normalizeAndCoalesce(text.length, paragraph.effectiveSpans()).let { spans ->
            if (kind in colorKinds) {
                // Remove competing color kinds inside targetRange before applying new color
                spans.flatMap { s ->
                    if (s.kind in colorKinds && s.kind != kind) {
                        subtractRange(s, targetRange.first, targetRange.second)
                    } else {
                        listOf(s)
                    }
                }
            } else {
                spans
            }
        }

        val updatedSpans = toggleSpanInList(
            textLength = text.length,
            spans = baseSpans,
            selStart = targetRange.first,
            selEnd = targetRange.second,
            kind = kind,
            url = url
        )
        return demotedParagraph.copy(spans = updatedSpans)
    }

    fun toggleSpanInList(
        textLength: Int,
        spans: List<RichTextSpan>,
        selStart: Int,
        selEnd: Int,
        kind: RichSpanKind,
        url: String? = null
    ): List<RichTextSpan> {
        val s = minOf(selStart, selEnd).coerceIn(0, textLength)
        val e = maxOf(selStart, selEnd).coerceIn(0, textLength)
        if (s >= e) return normalizeAndCoalesce(textLength, spans)

        val sameKindSpans = spans.filter { it.kind == kind }
        val otherSpans = spans.filterNot { it.kind == kind }

        val fullyCovered = isRangeFullyCovered(sameKindSpans, s, e) &&
            ((kind != RichSpanKind.LINK && kind !in colorKinds) ||
                sameKindSpans.any { it.start <= s && it.end >= e && it.url == url })

        val mutatedSameKind = if (fullyCovered) {
            sameKindSpans.flatMap { subtractRange(it, s, e) }
        } else {
            val cleared = sameKindSpans.flatMap { subtractRange(it, s, e) }
            cleared + RichTextSpan(start = s, end = e, kind = kind, url = url?.trim()?.takeIf { it.isNotEmpty() })
        }

        return normalizeAndCoalesce(textLength, otherSpans + mutatedSameKind)
    }

    fun applyFontColor(
        paragraph: DocumentBlock.Paragraph,
        rawStart: Int,
        rawEnd: Int,
        option: FontColorOption
    ): DocumentBlock.Paragraph {
        val text = paragraph.text
        if (text.isEmpty()) return paragraph

        val minSel = minOf(rawStart, rawEnd).coerceIn(0, text.length)
        val maxSel = maxOf(rawStart, rawEnd).coerceIn(0, text.length)
        val targetRange = if (minSel < maxSel) {
            minSel to maxSel
        } else {
            resolveWordRangeAtCursor(text, minSel) ?: return paragraph
        }

        val clearedColors = normalizeAndCoalesce(text.length, paragraph.effectiveSpans()).flatMap { span ->
            if (span.kind in colorKinds) {
                subtractRange(span, targetRange.first, targetRange.second)
            } else {
                listOf(span)
            }
        }
        val nextSpans = if (option.spanKind != null) {
            clearedColors + RichTextSpan(
                start = targetRange.first,
                end = targetRange.second,
                kind = option.spanKind,
                url = option.colorTag
            )
        } else {
            clearedColors
        }
        return paragraph.copy(spans = normalizeAndCoalesce(text.length, nextSpans))
    }

    fun activeFontColor(
        paragraph: DocumentBlock.Paragraph,
        rawStart: Int,
        rawEnd: Int
    ): FontColorOption {
        if (paragraph.text.isEmpty()) return FontColorOption.DEFAULT
        val colorSpans = paragraph.effectiveSpans().filter { it.kind in colorKinds }
        if (colorSpans.isEmpty()) return FontColorOption.DEFAULT
        val s = minOf(rawStart, rawEnd).coerceIn(0, paragraph.text.length)
        val e = maxOf(rawStart, rawEnd).coerceIn(0, paragraph.text.length)
        if (s < e) {
            val grouped = colorSpans.groupBy { FontColorOption.fromSpan(it) }
            return grouped.entries.firstOrNull { (_, spans) ->
                isRangeFullyCovered(spans, s, e)
            }?.key ?: FontColorOption.DEFAULT
        }
        val hit = colorSpans.firstOrNull { s > it.start && s <= it.end }
            ?: if (s == 0) colorSpans.firstOrNull { it.start == 0 } else null
        return hit?.let { FontColorOption.fromSpan(it) } ?: FontColorOption.DEFAULT
    }

    fun resolveFontColor(
        option: FontColorOption,
        colorScheme: ColorScheme,
        isDarkTheme: Boolean
    ): Color = when (option) {
        FontColorOption.DEFAULT -> colorScheme.onSurface
        FontColorOption.INDIGO -> colorScheme.primary
        FontColorOption.EMERALD -> SectionAccentKey.EMERALD.accentColorForTheme(isDarkTheme)
        FontColorOption.TEAL -> colorScheme.tertiary
        FontColorOption.AMBER -> SectionAccentKey.AMBER.accentColorForTheme(isDarkTheme)
        FontColorOption.RED -> colorScheme.error
        FontColorOption.VIOLET -> SectionAccentKey.VIOLET.accentColorForTheme(isDarkTheme)
    }

    fun hasActiveStyle(
        paragraph: DocumentBlock.Paragraph,
        rawStart: Int,
        rawEnd: Int,
        kind: RichSpanKind
    ): Boolean {
        if (paragraph.text.isEmpty()) {
            return when (kind) {
                RichSpanKind.BOLD -> paragraph.isBold
                RichSpanKind.ITALIC -> paragraph.isItalic
                RichSpanKind.UNDERLINE -> paragraph.isUnderlined
                RichSpanKind.STRIKETHROUGH -> paragraph.isStrikethrough
                RichSpanKind.HIGHLIGHT -> paragraph.isHighlighted
                else -> false
            }
        }
        val effective = paragraph.effectiveSpans().filter { it.kind == kind }
        if (effective.isEmpty()) return false
        val s = minOf(rawStart, rawEnd).coerceIn(0, paragraph.text.length)
        val e = maxOf(rawStart, rawEnd).coerceIn(0, paragraph.text.length)
        return if (s < e) {
            isRangeFullyCovered(effective, s, e)
        } else {
            effective.any { s > it.start && s <= it.end } ||
                (s == 0 && effective.any { it.start == 0 })
        }
    }

    fun activeLinkAtSelection(
        paragraph: DocumentBlock.Paragraph,
        rawStart: Int,
        rawEnd: Int
    ): RichTextSpan? {
        val links = paragraph.effectiveSpans().filter { it.kind == RichSpanKind.LINK && !it.url.isNullOrBlank() }
        if (links.isEmpty()) return null
        val s = minOf(rawStart, rawEnd).coerceIn(0, paragraph.text.length)
        val e = maxOf(rawStart, rawEnd).coerceIn(0, paragraph.text.length)
        return if (s < e) {
            links.firstOrNull { it.start < e && it.end > s }
        } else {
            links.firstOrNull { s in it.start..it.end }
        }
    }

    /**
     * Adjusts existing character-range spans when [oldText] is edited into [newText].
     */
    fun adjustSpansAfterEdit(
        oldText: String,
        newText: String,
        existingSpans: List<RichTextSpan>
    ): List<RichTextSpan> {
        if (existingSpans.isEmpty() || newText.isEmpty()) return emptyList()
        if (oldText == newText) return normalizeAndCoalesce(newText.length, existingSpans)

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

        val delta = newText.length - oldText.length

        val shifted = existingSpans.mapNotNull { span ->
            val newStart = when {
                span.start < prefix -> span.start
                span.start >= oldSuffix -> span.start + delta
                else -> prefix
            }
            val newEnd = when {
                span.end < prefix -> span.end
                span.end == prefix -> prefix
                span.end >= oldSuffix -> span.end + delta
                else -> newSuffix
            }
            val clampedStart = newStart.coerceIn(0, newText.length)
            val clampedEnd = newEnd.coerceIn(0, newText.length)
            if (clampedStart < clampedEnd) span.copy(start = clampedStart, end = clampedEnd) else null
        }

        return normalizeAndCoalesce(newText.length, shifted)
    }

    fun sliceParagraph(
        paragraph: DocumentBlock.Paragraph,
        start: Int,
        end: Int,
        newId: String
    ): DocumentBlock.Paragraph {
        val s = start.coerceIn(0, paragraph.text.length)
        val e = end.coerceIn(s, paragraph.text.length)
        val slicedText = paragraph.text.substring(s, e)
        val slicedSpans = if (slicedText.isEmpty()) {
            emptyList()
        } else {
            normalizeAndCoalesce(
                slicedText.length,
                paragraph.effectiveSpans().mapNotNull { span ->
                    val cs = maxOf(s, span.start)
                    val ce = minOf(e, span.end)
                    if (cs < ce) span.copy(start = cs - s, end = ce - s) else null
                }
            )
        }
        return DocumentBlock.Paragraph(
            id = newId,
            text = slicedText,
            spans = slicedSpans
        )
    }

    fun mergeParagraphs(
        first: DocumentBlock.Paragraph,
        second: DocumentBlock.Paragraph
    ): DocumentBlock.Paragraph = mergeParagraphsWithSeparator(first, second, "").first

    fun mergeParagraphsWithSeparator(
        first: DocumentBlock.Paragraph,
        second: DocumentBlock.Paragraph,
        separator: String
    ): Pair<DocumentBlock.Paragraph, Int> {
        if (first.text.isEmpty()) {
            return second.copy(id = first.id) to 0
        }
        if (second.text.isEmpty()) {
            return first to first.text.length
        }
        val joinOffset = first.text.length + separator.length
        val mergedText = first.text + separator + second.text
        val firstSpans = first.effectiveSpans()
        val secondSpans = second.effectiveSpans().map {
            it.copy(start = it.start + joinOffset, end = it.end + joinOffset)
        }
        val merged = DocumentBlock.Paragraph(
            id = first.id,
            text = mergedText,
            spans = normalizeAndCoalesce(mergedText.length, firstSpans + secondSpans)
        )
        return merged to joinOffset
    }

    fun clearSpansInRange(
        paragraph: DocumentBlock.Paragraph,
        rawStart: Int,
        rawEnd: Int
    ): DocumentBlock.Paragraph {
        if (paragraph.text.isEmpty()) {
            return paragraph.copy(
                isBold = false,
                isItalic = false,
                isUnderlined = false,
                isStrikethrough = false,
                isHighlighted = false,
                spans = emptyList()
            )
        }
        val s = minOf(rawStart, rawEnd).coerceIn(0, paragraph.text.length)
        val e = maxOf(rawStart, rawEnd).coerceIn(0, paragraph.text.length)
        if (s >= e) return paragraph
        val remaining = paragraph.effectiveSpans().flatMap { subtractRange(it, s, e) }
        return paragraph.copy(
            isBold = false,
            isItalic = false,
            isUnderlined = false,
            isStrikethrough = false,
            isHighlighted = false,
            spans = normalizeAndCoalesce(paragraph.text.length, remaining)
        )
    }

    fun buildParagraphAnnotatedString(
        paragraph: DocumentBlock.Paragraph,
        colorScheme: ColorScheme,
        searchQuery: String = "",
        monoFontFamily: FontFamily = FontFamily.Monospace,
        isDarkTheme: Boolean = false,
        includeHighlightBackground: Boolean = true
    ): AnnotatedString = buildAnnotatedString {
        val text = paragraph.text
        append(text)
        if (text.isEmpty()) return@buildAnnotatedString

        val highlightBg = highlightContainerColor(isDarkTheme)
        val highlightFg = onHighlightContainerColor(isDarkTheme)

        for (span in paragraph.effectiveSpans()) {
            val s = span.start.coerceIn(0, text.length)
            val e = span.end.coerceIn(0, text.length)
            if (s >= e) continue
            val style = when (span.kind) {
                RichSpanKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                RichSpanKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                RichSpanKind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                RichSpanKind.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                RichSpanKind.HIGHLIGHT -> if (includeHighlightBackground) {
                    SpanStyle(background = highlightBg, color = highlightFg)
                } else {
                    SpanStyle(color = highlightFg)
                }
                RichSpanKind.INLINE_CODE -> SpanStyle(
                    fontFamily = monoFontFamily,
                    background = colorScheme.surfaceContainerHighest,
                    color = colorScheme.primary
                )
                RichSpanKind.COLOR_ACCENT,
                RichSpanKind.COLOR_TERTIARY,
                RichSpanKind.COLOR_ERROR -> SpanStyle(
                    color = resolveFontColor(FontColorOption.fromSpan(span), colorScheme, isDarkTheme),
                    fontWeight = FontWeight.Medium
                )
                RichSpanKind.LINK -> SpanStyle(
                    color = colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
            }
            addStyle(style, s, e)
            if (span.kind == RichSpanKind.LINK && !span.url.isNullOrBlank()) {
                addStringAnnotation(tag = "URL", annotation = span.url, start = s, end = e)
            }
        }

        applySearchHighlights(text, searchQuery, colorScheme)
    }

    fun buildSearchHighlightedString(
        text: String,
        colorScheme: ColorScheme,
        searchQuery: String
    ): AnnotatedString = buildAnnotatedString {
        append(text)
        applySearchHighlights(text, searchQuery, colorScheme)
    }

    private fun AnnotatedString.Builder.applySearchHighlights(
        text: String,
        searchQuery: String,
        colorScheme: ColorScheme
    ) {
        val needle = searchQuery.trim()
        if (needle.isEmpty() || text.isEmpty()) return
        val lowerText = text.lowercase()
        val lowerNeedle = needle.lowercase()
        if (lowerNeedle.isEmpty()) return

        var searchStart = 0
        while (searchStart < lowerText.length) {
            val idx = lowerText.indexOf(lowerNeedle, startIndex = searchStart)
            if (idx < 0) break
            val end = (idx + lowerNeedle.length).coerceAtMost(text.length)
            if (idx < end) {
                addStyle(
                    SpanStyle(
                        background = colorScheme.primaryContainer,
                        color = colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    ),
                    idx,
                    end
                )
            }
            searchStart = idx + lowerNeedle.length.coerceAtLeast(1)
        }
    }

    private fun isRangeFullyCovered(spans: List<RichTextSpan>, start: Int, end: Int): Boolean {
        if (start >= end) return false
        val relevant = spans
            .filter { it.end > start && it.start < end }
            .sortedBy { it.start }
        var cursor = start
        for (span in relevant) {
            if (span.start > cursor) return false
            if (span.end > cursor) cursor = span.end
            if (cursor >= end) return true
        }
        return cursor >= end
    }

    private fun subtractRange(span: RichTextSpan, cutStart: Int, cutEnd: Int): List<RichTextSpan> {
        if (span.end <= cutStart || span.start >= cutEnd) return listOf(span)
        val out = mutableListOf<RichTextSpan>()
        if (span.start < cutStart) {
            out.add(span.copy(end = cutStart))
        }
        if (span.end > cutEnd) {
            out.add(span.copy(start = cutEnd))
        }
        return out
    }

    fun normalizeAndCoalesce(textLength: Int, spans: List<RichTextSpan>): List<RichTextSpan> {
        if (textLength <= 0 || spans.isEmpty()) return emptyList()
        val grouped = spans
            .mapNotNull { s ->
                val st = s.start.coerceIn(0, textLength)
                val en = s.end.coerceIn(0, textLength)
                if (st < en) s.copy(start = st, end = en) else null
            }
            .groupBy { it.kind to it.url }

        val result = mutableListOf<RichTextSpan>()
        for ((_, group) in grouped) {
            val sorted = group.sortedWith(compareBy<RichTextSpan> { it.start }.thenBy { it.end })
            var active = sorted.first()
            for (i in 1 until sorted.size) {
                val next = sorted[i]
                if (next.start <= active.end) {
                    active = active.copy(end = maxOf(active.end, next.end))
                } else {
                    result.add(active)
                    active = next
                }
            }
            result.add(active)
        }
        return result.sortedWith(compareBy<RichTextSpan> { it.start }.thenBy { it.end }.thenBy { it.kind.ordinal })
    }

    private fun toggleWholeBlockBoolean(
        paragraph: DocumentBlock.Paragraph,
        kind: RichSpanKind
    ): DocumentBlock.Paragraph = when (kind) {
        RichSpanKind.BOLD -> paragraph.copy(isBold = !paragraph.isBold)
        RichSpanKind.ITALIC -> paragraph.copy(isItalic = !paragraph.isItalic)
        RichSpanKind.UNDERLINE -> paragraph.copy(isUnderlined = !paragraph.isUnderlined)
        RichSpanKind.STRIKETHROUGH -> paragraph.copy(isStrikethrough = !paragraph.isStrikethrough)
        RichSpanKind.HIGHLIGHT -> paragraph.copy(isHighlighted = !paragraph.isHighlighted)
        else -> paragraph
    }

    private fun clearLegacyBooleanForKind(
        paragraph: DocumentBlock.Paragraph,
        kind: RichSpanKind
    ): DocumentBlock.Paragraph = when (kind) {
        RichSpanKind.BOLD -> paragraph.copy(isBold = false)
        RichSpanKind.ITALIC -> paragraph.copy(isItalic = false)
        RichSpanKind.UNDERLINE -> paragraph.copy(isUnderlined = false)
        RichSpanKind.STRIKETHROUGH -> paragraph.copy(isStrikethrough = false)
        RichSpanKind.HIGHLIGHT -> paragraph.copy(isHighlighted = false)
        else -> paragraph
    }
}
