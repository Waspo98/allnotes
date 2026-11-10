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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.EditorFocusRequest
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
internal fun ChecklistBlockContent(
    block: DocumentBlock.Checklist,
    isActive: Boolean,
    searchQuery: String,
    focusRequest: EditorFocusRequest?,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val searchVisual = remember(searchQuery, colorScheme) {
        VisualTransformation { raw ->
            TransformedText(
                RichSpanEngine.buildSearchHighlightedString(raw.text, colorScheme, searchQuery),
                OffsetMapping.Identity
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (block.title.isNotEmpty() || isActive) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 2.dp)) {
                if (block.title.isEmpty()) {
                    Text(
                        text = "Checklist heading (optional)…",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
                BasicTextField(
                    value = block.title,
                    onValueChange = { onAction(WorkspaceAction.UpdateChecklistTitle(block.id, it)) },
                    textStyle = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary
                    ),
                    singleLine = true,
                    visualTransformation = searchVisual,
                    cursorBrush = SolidColor(colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        block.items.forEachIndexed { index, item ->
            val requester = remember(item.id) { FocusRequester() }
            var fieldValue by remember(item.id) {
                mutableStateOf(TextFieldValue(text = item.text, selection = TextRange(item.text.length)))
            }
            if (fieldValue.text != item.text) {
                val pos = fieldValue.selection.start.coerceIn(0, item.text.length)
                fieldValue = TextFieldValue(text = item.text, selection = TextRange(pos))
            }

            LaunchedEffect(focusRequest?.requestToken) {
                if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex == index) {
                    val pos = focusRequest.cursorOffset.coerceIn(0, item.text.length)
                    fieldValue = fieldValue.copy(selection = TextRange(pos))
                    runCatching { requester.requestFocus() }
                    onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { onAction(WorkspaceAction.ToggleChecklistItem(block.id, item.id)) },
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                    if (fieldValue.text.isEmpty()) {
                        Text(
                            text = "To-do item…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { updated ->
                            if (updated.text.contains('\n')) {
                                val cleaned = updated.text.replace("\n", "")
                                fieldValue = updated.copy(text = cleaned)
                                onAction(WorkspaceAction.UpdateChecklistItemText(block.id, item.id, cleaned))
                                onAction(WorkspaceAction.ListEnterAtItem(block.id, index))
                            } else {
                                val changed = updated.text != fieldValue.text
                                fieldValue = updated
                                if (changed) {
                                    onAction(WorkspaceAction.UpdateChecklistItemText(block.id, item.id, updated.text))
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (item.isChecked) colorScheme.onSurfaceVariant else colorScheme.onSurface
                        ),
                        visualTransformation = searchVisual,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { onAction(WorkspaceAction.ListEnterAtItem(block.id, index)) }
                        ),
                        cursorBrush = SolidColor(colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    event.key == Key.Enter -> {
                                        onAction(WorkspaceAction.ListEnterAtItem(block.id, index))
                                        true
                                    }
                                    event.key == Key.Backspace && fieldValue.text.isEmpty() -> {
                                        onAction(WorkspaceAction.ListBackspaceOnEmptyItem(block.id, index))
                                        true
                                    }
                                    else -> false
                                }
                            }
                    )
                }
                if (isActive && block.items.size > 1) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.DeleteChecklistItem(block.id, item.id)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Close,
                            contentDescription = "Delete item",
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BulletListBlockContent(
    block: DocumentBlock.BulletList,
    isActive: Boolean,
    searchQuery: String,
    focusRequest: EditorFocusRequest?,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val searchVisual = remember(searchQuery, colorScheme) {
        VisualTransformation { raw ->
            TransformedText(
                RichSpanEngine.buildSearchHighlightedString(raw.text, colorScheme, searchQuery),
                OffsetMapping.Identity
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        block.items.forEachIndexed { index, item ->
            val requester = remember(block.id, index) { FocusRequester() }
            var fieldValue by remember(block.id, index) {
                mutableStateOf(TextFieldValue(text = item, selection = TextRange(item.length)))
            }
            if (fieldValue.text != item) {
                val pos = fieldValue.selection.start.coerceIn(0, item.length)
                fieldValue = TextFieldValue(text = item, selection = TextRange(pos))
            }

            LaunchedEffect(focusRequest?.requestToken) {
                if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex == index) {
                    val pos = focusRequest.cursorOffset.coerceIn(0, item.length)
                    fieldValue = fieldValue.copy(selection = TextRange(pos))
                    runCatching { requester.requestFocus() }
                    onAction(WorkspaceAction.ConsumeFocusRequest(focusRequest.requestToken))
                }
            }

            val prefix = if (block.isNumbered) "${index + 1}." else "•"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary,
                    modifier = Modifier.width(24.dp).padding(top = 1.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (fieldValue.text.isEmpty()) {
                        Text(
                            text = "List item…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { updated ->
                            if (updated.text.contains('\n')) {
                                val cleaned = updated.text.replace("\n", "")
                                fieldValue = updated.copy(text = cleaned)
                                onAction(WorkspaceAction.UpdateBulletListItem(block.id, index, cleaned))
                                onAction(WorkspaceAction.ListEnterAtItem(block.id, index))
                            } else {
                                val changed = updated.text != fieldValue.text
                                fieldValue = updated
                                if (changed) {
                                    onAction(WorkspaceAction.UpdateBulletListItem(block.id, index, updated.text))
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = colorScheme.onSurface
                        ),
                        visualTransformation = searchVisual,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { onAction(WorkspaceAction.ListEnterAtItem(block.id, index)) }
                        ),
                        cursorBrush = SolidColor(colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    event.key == Key.Enter -> {
                                        onAction(WorkspaceAction.ListEnterAtItem(block.id, index))
                                        true
                                    }
                                    event.key == Key.Backspace && fieldValue.text.isEmpty() -> {
                                        onAction(WorkspaceAction.ListBackspaceOnEmptyItem(block.id, index))
                                        true
                                    }
                                    else -> false
                                }
                            }
                    )
                }
                if (isActive && block.items.size > 1) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.DeleteBulletListItem(block.id, index)) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Close,
                            contentDescription = "Remove list item",
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
