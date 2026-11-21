package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import kotlinx.coroutines.flow.collectLatest

internal class LayoutResultRef(var value: TextLayoutResult? = null)

internal fun handleVerticalBoundaryKeyEvent(
    event: KeyEvent,
    fieldValue: TextFieldValue,
    layout: TextLayoutResult?,
    blockId: String,
    onAction: (WorkspaceAction) -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isShiftPressed || event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
    if (!fieldValue.selection.collapsed) return false

    val caretOffset = fieldValue.selection.start
    return when (event.key) {
        Key.DirectionUp -> {
            val atTop = fieldValue.text.isEmpty() || layout?.let {
                val clamped = caretOffset.coerceIn(0, it.layoutInput.text.length)
                it.getLineForOffset(clamped) == 0
            } == true
            if (atTop) {
                onAction(WorkspaceAction.MoveFocusToAdjacentBlock(blockId, -1))
                true
            } else {
                false
            }
        }
        Key.DirectionDown -> {
            val atBottom = fieldValue.text.isEmpty() || layout?.let {
                val clamped = caretOffset.coerceIn(0, it.layoutInput.text.length)
                it.getLineForOffset(clamped) >= (it.lineCount - 1).coerceAtLeast(0)
            } == true
            if (atBottom) {
                onAction(WorkspaceAction.MoveFocusToAdjacentBlock(blockId, +1))
                true
            } else {
                false
            }
        }
        else -> false
    }
}

/**
 * Keeps the active text caret and a small breathing margin visible inside scrollable
 * editor containers whenever focus, selection, or expanding soft-keyboard (IME) insets change.
 */
@Composable
internal fun rememberCursorIntoViewHandler(
    isFocused: Boolean,
    selection: TextRange
): Pair<Modifier, (TextLayoutResult) -> Unit> {
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val layoutRef = remember { LayoutResultRef() }
    val latestSelection by rememberUpdatedState(selection)
    val marginPx = with(density) { 14.dp.toPx() }
    val imeInsets = WindowInsets.ime

    suspend fun bringCursorIntoView(targetSelection: TextRange) {
        withFrameNanos { }
        val layout = layoutRef.value
        if (layout != null) {
            val offset = targetSelection.end.coerceIn(0, layout.layoutInput.text.length)
            val cursorRect = layout.getCursorRect(offset)
            requester.bringIntoView(
                Rect(
                    left = cursorRect.left,
                    top = (cursorRect.top - marginPx).coerceAtLeast(0f),
                    right = cursorRect.right,
                    bottom = cursorRect.bottom + marginPx
                )
            )
        } else {
            requester.bringIntoView()
        }
    }

    if (isFocused) {
        LaunchedEffect(selection) {
            bringCursorIntoView(selection)
        }
        LaunchedEffect(imeInsets, density) {
            var previousImeBottom = imeInsets.getBottom(density)
            snapshotFlow { imeInsets.getBottom(density) }
                .collectLatest { imeBottom ->
                    val grew = imeBottom > previousImeBottom
                    previousImeBottom = imeBottom
                    if (grew) {
                        bringCursorIntoView(latestSelection)
                    }
                }
        }
    }

    val onTextLayout: (TextLayoutResult) -> Unit = remember {
        { result -> layoutRef.value = result }
    }
    return Modifier.bringIntoViewRequester(requester) to onTextLayout
}

@Composable
internal fun SyncedCursorTextField(
    focusKey: Any,
    holder: SyncedFieldHolder,
    textStyle: TextStyle,
    visualTransformation: VisualTransformation,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    var isFocused by remember(focusKey) { mutableStateOf(false) }
    val (cursorIntoViewModifier, onTextLayout) = rememberCursorIntoViewHandler(isFocused, holder.fieldValue.selection)

    BasicTextField(
        value = holder.fieldValue,
        onValueChange = { updated ->
            val changed = updated.text != holder.fieldValue.text
            holder.applyLocalChange(updated)
            if (changed) onTextChange(updated.text)
        },
        textStyle = textStyle,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        onTextLayout = onTextLayout,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .then(cursorIntoViewModifier)
            .onFocusChanged { isFocused = it.isFocused }
    )
}
