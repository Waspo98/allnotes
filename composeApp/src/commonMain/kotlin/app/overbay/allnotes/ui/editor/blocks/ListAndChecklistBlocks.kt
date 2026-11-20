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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
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
    val searchVisual = rememberSearchHighlightTransformation(searchQuery)
    val titleHolder = rememberSyncedFieldHolder(block.id to "checklist-title", block.title)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (block.title.isNotEmpty() || isActive) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 2.dp)) {
                if (titleHolder.fieldValue.text.isEmpty()) {
                    Text(
                        text = "Checklist heading (optional)…",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
                BasicTextField(
                    value = titleHolder.fieldValue,
                    onValueChange = { updated ->
                        val changed = updated.text != titleHolder.fieldValue.text
                        titleHolder.applyLocalChange(updated)
                        if (changed) onAction(WorkspaceAction.UpdateChecklistTitle(block.id, updated.text))
                    },
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
            key(item.id) {
                val requester = remember(item.id) { FocusRequester() }
                val fieldHolder = rememberSyncedFieldHolder(item.id, item.text)

                if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex == index) {
                    LaunchedEffect(focusRequest.requestToken) {
                        val pos = focusRequest.cursorOffset.coerceIn(0, item.text.length)
                        fieldHolder.moveCursorTo(pos)
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
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                        if (fieldHolder.fieldValue.text.isEmpty()) {
                            Text(
                                text = "To-do item…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                        BasicTextField(
                            value = fieldHolder.fieldValue,
                            onValueChange = { updated ->
                                if (updated.text.contains('\n')) {
                                    val cleaned = updated.text.replace("\n", "")
                                    fieldHolder.applyLocalChange(updated.copy(text = cleaned), cleaned)
                                    onAction(WorkspaceAction.UpdateChecklistItemText(block.id, item.id, cleaned))
                                    onAction(WorkspaceAction.ListEnterAtItem(block.id, index))
                                } else {
                                    val changed = updated.text != fieldHolder.fieldValue.text
                                    fieldHolder.applyLocalChange(updated)
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
                                        event.key == Key.Backspace && fieldHolder.fieldValue.text.isEmpty() -> {
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
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Close,
                                contentDescription = "Delete item",
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
    val searchVisual = rememberSearchHighlightTransformation(searchQuery)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        block.items.forEachIndexed { index, item ->
            key(block.id, index) {
                val requester = remember(block.id, index) { FocusRequester() }
                val fieldHolder = rememberSyncedFieldHolder(block.id to index, item)

                if (focusRequest != null && focusRequest.blockId == block.id && focusRequest.subItemIndex == index) {
                    LaunchedEffect(focusRequest.requestToken) {
                        val pos = focusRequest.cursorOffset.coerceIn(0, item.length)
                        fieldHolder.moveCursorTo(pos)
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
                        if (fieldHolder.fieldValue.text.isEmpty()) {
                            Text(
                                text = "List item…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                        BasicTextField(
                            value = fieldHolder.fieldValue,
                            onValueChange = { updated ->
                                if (updated.text.contains('\n')) {
                                    val cleaned = updated.text.replace("\n", "")
                                    fieldHolder.applyLocalChange(updated.copy(text = cleaned), cleaned)
                                    onAction(WorkspaceAction.UpdateBulletListItem(block.id, index, cleaned))
                                    onAction(WorkspaceAction.ListEnterAtItem(block.id, index))
                                } else {
                                    val changed = updated.text != fieldHolder.fieldValue.text
                                    fieldHolder.applyLocalChange(updated)
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
                                        event.key == Key.Backspace && fieldHolder.fieldValue.text.isEmpty() -> {
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
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Close,
                                contentDescription = "Remove list item",
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
