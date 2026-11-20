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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
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
    val monoFont = LocalAllNotesMonoFont.current
    val uriHandler = LocalUriHandler.current
    val focusRequester = remember(block.id) { FocusRequester() }
    var isFocused by remember(block.id) { mutableStateOf(false) }

    val fieldHolder = rememberSyncedFieldHolder(block.id, block.text)
    val fieldValue = fieldHolder.fieldValue

    if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex < 0) {
        LaunchedEffect(focusRequest.requestToken) {
            val targetCursor = focusRequest.cursorOffset.coerceIn(0, block.text.length)
            fieldHolder.moveCursorTo(targetCursor)
            runCatching { focusRequester.requestFocus() }
            onAction(WorkspaceAction.UpdateActiveSelection(block.id, targetCursor, targetCursor))
            onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
        }
    }

    val visualTransformation = remember(block, searchQuery, colorScheme, monoFont) {
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
                monoFontFamily = monoFont
            )
            TransformedText(annotated, OffsetMapping.Identity)
        }
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
                    text = "Write, or type '/' for blocks…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }

            BasicTextField(
                value = fieldValue,
                onValueChange = { updated ->
                    val prevText = fieldValue.text
                    val newText = updated.text

                    // Detect single soft-keyboard Enter key press (inserting exactly one '\n')
                    if (newText.length == prevText.length + 1 && updated.composition == null) {
                        val insertedIdx = updated.selection.start - 1
                        if (insertedIdx in newText.indices && newText[insertedIdx] == '\n') {
                            onAction(WorkspaceAction.SplitBlockAtCursor(block.id, insertedIdx))
                            return@BasicTextField
                        }
                    }

                    val selChanged = updated.selection != fieldValue.selection
                    fieldHolder.applyLocalChange(updated)

                    if (newText != prevText) {
                        onAction(
                            WorkspaceAction.EditParagraphText(
                                blockId = block.id,
                                newText = newText,
                                cursorOffset = updated.selection.start
                            )
                        )
                    } else if (selChanged && isFocused) {
                        onAction(
                            WorkspaceAction.UpdateActiveSelection(
                                blockId = block.id,
                                selectionStart = updated.selection.min,
                                selectionEnd = updated.selection.max
                            )
                        )
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colorScheme.onSurface
                ),
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
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
                        when {
                            event.key == Key.Enter && !event.isShiftPressed -> {
                                onAction(WorkspaceAction.SplitBlockAtCursor(block.id, fieldHolder.fieldValue.selection.min))
                                true
                            }
                            event.key == Key.Backspace && fieldHolder.fieldValue.selection.collapsed && fieldHolder.fieldValue.selection.start == 0 -> {
                                onAction(WorkspaceAction.BackspaceAtBlockStart(block.id))
                                true
                            }
                            else -> false
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
