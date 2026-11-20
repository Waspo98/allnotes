package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
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
    val haptics = LocalHapticFeedback.current
    val searchVisual = rememberSearchHighlightTransformation(searchQuery)
    val titleHolder = rememberSyncedFieldHolder(block.id to "checklist-title", block.title)
    var titleFocused by remember(block.id) { mutableStateOf(false) }
    val (titleIntoViewModifier, onTitleLayout) = rememberCursorIntoViewHandler(titleFocused, titleHolder.fieldValue.selection)

    val totalCount = block.items.size
    val completedCount = remember(block.items) { block.items.count { it.isChecked } }
    val completionFraction by animateFloatAsState(
        targetValue = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f,
        animationSpec = AllNotesMotion.spatialFast(),
        label = "ChecklistProgress"
    )
    val allComplete = totalCount > 0 && completedCount == totalCount

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (block.title.isNotEmpty() || isActive || totalCount >= 2) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (titleHolder.fieldValue.text.isEmpty() && isActive) {
                        Text(
                            text = "Checklist title…",
                            style = MaterialTheme.typography.titleSmall,
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
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        ),
                        singleLine = true,
                        visualTransformation = searchVisual,
                        onTextLayout = onTitleLayout,
                        cursorBrush = SolidColor(colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(titleIntoViewModifier)
                            .onFocusChanged { titleFocused = it.isFocused }
                    )
                }

                if (totalCount >= 2) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = if (allComplete) colorScheme.tertiaryContainer else colorScheme.surfaceContainer,
                        contentColor = if (allComplete) colorScheme.onTertiaryContainer else colorScheme.onSurfaceVariant,
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (allComplete) "All done" else "$completedCount of $totalCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        if (totalCount >= 2) {
            val trackColor = colorScheme.surfaceContainerHigh
            val fillColor = if (allComplete) colorScheme.tertiary else colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .height(3.dp)
                    .drawBehind {
                        val r = size.height / 2f
                        drawRoundRect(color = trackColor, cornerRadius = CornerRadius(r, r))
                        val w = (size.width * completionFraction.coerceIn(0f, 1f))
                        if (w > 0f) {
                            drawRoundRect(
                                color = fillColor,
                                size = Size(w, size.height),
                                cornerRadius = CornerRadius(r, r)
                            )
                        }
                    }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        block.items.forEachIndexed { index, item ->
            key(item.id) {
                val requester = remember(item.id) { FocusRequester() }
                val fieldHolder = rememberSyncedFieldHolder(item.id, item.text)
                var isFocused by remember(item.id) { mutableStateOf(false) }
                val (cursorIntoViewModifier, onTextLayout) = rememberCursorIntoViewHandler(isFocused, fieldHolder.fieldValue.selection)
                val itemAlpha by animateFloatAsState(
                    targetValue = if (item.isChecked) 0.62f else 1f,
                    animationSpec = AllNotesMotion.effectsFast(),
                    label = "ChecklistItemAlpha"
                )

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
                        onCheckedChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAction(WorkspaceAction.ToggleChecklistItem(block.id, item.id))
                        },
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .graphicsLayer { alpha = itemAlpha }
                    ) {
                        if (fieldHolder.fieldValue.text.isEmpty() && isActive) {
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
                                color = colorScheme.onSurface
                            ),
                            visualTransformation = searchVisual,
                            onTextLayout = onTextLayout,
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
                            modifier = Modifier.size(40.dp)
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
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        block.items.forEachIndexed { index, item ->
            key(block.id, index) {
                val requester = remember(block.id, index) { FocusRequester() }
                val fieldHolder = rememberSyncedFieldHolder(block.id to index, item)
                var isFocused by remember(block.id, index) { mutableStateOf(false) }
                val (cursorIntoViewModifier, onTextLayout) = rememberCursorIntoViewHandler(isFocused, fieldHolder.fieldValue.selection)

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
                            onTextLayout = onTextLayout,
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
