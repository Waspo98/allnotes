package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.LocalAllNotesMonoFont
import app.overbay.allnotes.ui.theme.highlightContainerColor
import app.overbay.allnotes.ui.theme.isAllNotesDarkTheme
import app.overbay.allnotes.ui.workspace.EditorFocusRequest
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
internal fun RichParagraphField(
    block: DocumentBlock.Paragraph,
    isActive: Boolean,
    searchQuery: String,
    focusRequest: EditorFocusRequest?,
    onAction: (WorkspaceAction) -> Unit,
    onSlashKeyEvent: ((KeyEvent) -> Boolean)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isAllNotesDarkTheme()
    val highlightBg = highlightContainerColor()
    val monoFont = LocalAllNotesMonoFont.current
    val uriHandler = LocalUriHandler.current
    val focusRequester = remember(block.id) { FocusRequester() }
    var isFocused by remember(block.id) { mutableStateOf(false) }

    val fieldHolder = rememberSyncedFieldHolder(block.id, block.text)
    val fieldValue = fieldHolder.fieldValue
    val layoutRef = remember(block.id) { LayoutResultRef() }
    var textLayoutResult by remember(block.id) { mutableStateOf<TextLayoutResult?>(null) }
    val (cursorIntoViewModifier, bringCursorTextLayout) = rememberCursorIntoViewHandler(isFocused, fieldValue.selection)

    if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex < 0) {
        LaunchedEffect(focusRequest.requestToken) {
            val targetCursor = focusRequest.cursorOffset.coerceIn(0, block.text.length)
            fieldHolder.moveCursorTo(targetCursor)
            runCatching { focusRequester.requestFocus() }
            onAction(WorkspaceAction.UpdateActiveSelection(block.id, targetCursor, targetCursor))
            onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
        }
    }

    val visualTransformation = remember(block, searchQuery, colorScheme, monoFont, isDark) {
        VisualTransformation { raw ->
            val previewBlock = if (raw.text == block.text) {
                block
            } else {
                block.copy(
                    text = raw.text,
                    spans = RichSpanEngine.adjustSpansAfterEdit(block.text, raw.text, block.effectiveSpans())
                )
            }
            val annotated = RichSpanEngine.buildParagraphAnnotatedString(
                paragraph = previewBlock,
                colorScheme = colorScheme,
                searchQuery = searchQuery,
                monoFontFamily = monoFont,
                isDarkTheme = isDark,
                includeHighlightBackground = false
            )
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }

    val highlightSpans = remember(block, fieldValue.text) {
        val rawText = fieldValue.text
        val spans = if (rawText == block.text) {
            block.effectiveSpans()
        } else {
            RichSpanEngine.adjustSpansAfterEdit(block.text, rawText, block.effectiveSpans())
        }
        spans.filter { it.kind == RichSpanKind.HIGHLIGHT }
    }

    val activeLink = if (isActive) {
        RichSpanEngine.activeLinkAtSelection(
            paragraph = block,
            rawStart = fieldValue.selection.min,
            rawEnd = fieldValue.selection.max
        )
    } else null

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp)
        ) {
            if (fieldValue.text.isEmpty() && (isActive || isFocused)) {
                Text(
                    text = "Write, or type '/' to insert…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }

            BasicTextField(
                value = fieldValue,
                onValueChange = { updated ->
                    val prevValue = fieldHolder.fieldValue
                    val prevText = prevValue.text
                    val rawText = updated.text
                    val normalizedValue = if (rawText.contains('\r')) {
                        val normalizedText = rawText.replace("\r\n", "\n").replace('\r', '\n')
                        val selStart = rawText.substring(0, updated.selection.start.coerceIn(0, rawText.length))
                            .replace("\r\n", "\n").replace('\r', '\n').length
                        val selEnd = rawText.substring(0, updated.selection.end.coerceIn(0, rawText.length))
                            .replace("\r\n", "\n").replace('\r', '\n').length
                        updated.copy(text = normalizedText, selection = TextRange(selStart, selEnd))
                    } else {
                        updated
                    }
                    val newText = normalizedValue.text

                    val selChanged = normalizedValue.selection != prevValue.selection
                    fieldHolder.applyLocalChange(normalizedValue)

                    if (newText != prevText) {
                        onAction(
                            WorkspaceAction.EditParagraphText(
                                blockId = block.id,
                                newText = newText,
                                cursorOffset = normalizedValue.selection.start
                            )
                        )
                    } else if (selChanged && isFocused) {
                        onAction(
                            WorkspaceAction.UpdateActiveSelection(
                                blockId = block.id,
                                selectionStart = normalizedValue.selection.min,
                                selectionEnd = normalizedValue.selection.max
                            )
                        )
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colorScheme.onSurface
                ),
                visualTransformation = visualTransformation,
                onTextLayout = { result ->
                    layoutRef.value = result
                    textLayoutResult = result
                    bringCursorTextLayout(result)
                },
                cursorBrush = SolidColor(colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val layout = textLayoutResult ?: return@drawBehind
                        if (highlightSpans.isEmpty()) return@drawBehind
                        val layoutText = layout.layoutInput.text.text
                        val maxLen = layoutText.length
                        if (maxLen == 0) return@drawBehind
                        val padX = 2.dp.toPx()
                        val insetY = 1.5.dp.toPx()
                        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        for (span in highlightSpans) {
                            val s = span.start.coerceIn(0, maxLen)
                            val e = span.end.coerceIn(0, maxLen)
                            if (s >= e) continue
                            val startLine = layout.getLineForOffset(s)
                            val endLine = layout.getLineForOffset((e - 1).coerceAtLeast(s))
                            for (line in startLine..endLine) {
                                val lineStart = if (line == startLine) s else layout.getLineStart(line)
                                var segEnd = if (line == endLine) e else layout.getLineEnd(line, visibleEnd = true)
                                while (segEnd > lineStart && layoutText[segEnd - 1] == '\n') {
                                    segEnd--
                                }
                                if (segEnd <= lineStart) continue
                                val startBox = layout.getBoundingBox(lineStart)
                                val endBox = layout.getBoundingBox(segEnd - 1)
                                val left = minOf(startBox.left, endBox.left)
                                val right = maxOf(startBox.right, endBox.right)
                                val top = layout.getLineTop(line) + insetY
                                val bottom = layout.getLineBottom(line) - insetY
                                if (right > left && bottom > top) {
                                    val clampedLeft = (left - padX).coerceAtLeast(0f)
                                    val width = (right - left + padX * 2f).coerceAtMost(size.width - clampedLeft)
                                    if (width > 0f) {
                                        drawRoundRect(
                                            color = highlightBg,
                                            topLeft = Offset(clampedLeft, top),
                                            size = Size(width, bottom - top),
                                            cornerRadius = radius
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .focusRequester(focusRequester)
                    .then(cursorIntoViewModifier)
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (state.isFocused) {
                            onAction(
                                WorkspaceAction.UpdateActiveSelection(
                                    blockId = block.id,
                                    selectionStart = fieldHolder.fieldValue.selection.min,
                                    selectionEnd = fieldHolder.fieldValue.selection.max
                                )
                            )
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (onSlashKeyEvent?.invoke(event) == true) return@onPreviewKeyEvent true
                        if (handleVerticalBoundaryKeyEvent(event, fieldHolder.fieldValue, layoutRef.value, block.id, onAction)) {
                            return@onPreviewKeyEvent true
                        }
                        if (event.key == Key.Backspace &&
                            fieldHolder.fieldValue.selection.collapsed &&
                            fieldHolder.fieldValue.selection.start == 0
                        ) {
                            onAction(WorkspaceAction.BackspaceAtBlockStart(block.id))
                            true
                        } else {
                            false
                        }
                    }
            )
        }

        if (activeLink != null && !activeLink.url.isNullOrBlank()) {
            Surface(
                color = colorScheme.secondaryContainer.copy(alpha = 0.75f),
                contentColor = colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = AllNotesIcons.Link,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = activeLink.url,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    TextButton(
                        onClick = {
                            val targetUrl = activeLink.url.let {
                                if (it.startsWith("http://") || it.startsWith("https://") || it.startsWith("mailto:")) it
                                else "https://$it"
                            }
                            runCatching { uriHandler.openUri(targetUrl) }
                        }
                    ) {
                        Text("Open", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = {
                            onAction(
                                WorkspaceAction.ToggleSelectionSpan(
                                    kind = RichSpanKind.LINK,
                                    blockId = block.id,
                                    selStart = activeLink.start,
                                    selEnd = activeLink.end
                                )
                            )
                        }
                    ) {
                        Text("Unlink", style = MaterialTheme.typography.labelSmall, color = colorScheme.error)
                    }
                }
            }
        }
    }
}
