package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.EditorFocusRequest
import app.overbay.allnotes.ui.workspace.WorkspaceAction

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
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        block.items.forEachIndexed { index, item ->
            key(block.id, index) {
                val requester = remember(block.id, index) { FocusRequester() }
                val fieldHolder = rememberSyncedFieldHolder(block.id to index, item)
                var isFocused by remember(block.id, index) { mutableStateOf(false) }
                val layoutRef = remember(block.id, index) { LayoutResultRef() }
                val (cursorIntoViewModifier, bringCursorTextLayout) = rememberCursorIntoViewHandler(isFocused, fieldHolder.fieldValue.selection)

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
                        modifier = Modifier.width(26.dp).padding(top = 1.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (fieldHolder.fieldValue.text.isEmpty() && isActive) {
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
                            onTextLayout = { result ->
                                layoutRef.value = result
                                bringCursorTextLayout(result)
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { onAction(WorkspaceAction.ListEnterAtItem(block.id, index)) }
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(requester)
                                .then(cursorIntoViewModifier)
                                .onFocusChanged { isFocused = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    if (handleVerticalBoundaryKeyEvent(
                                            event = event,
                                            fieldValue = fieldHolder.fieldValue,
                                            layout = layoutRef.value,
                                            blockId = block.id,
                                            onAction = onAction,
                                            subItemIndex = index
                                        )
                                    ) {
                                        return@onPreviewKeyEvent true
                                    }
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
                            modifier = Modifier.size(40.dp)
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
