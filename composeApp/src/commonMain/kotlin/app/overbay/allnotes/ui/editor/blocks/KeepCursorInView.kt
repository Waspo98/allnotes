package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp

/**
 * Keeps the active text caret and a small breathing margin visible inside scrollable
 * editor containers whenever focus, selection, or soft-keyboard (IME) insets change.
 */
@Composable
internal fun rememberCursorIntoViewHandler(
    isFocused: Boolean,
    selection: TextRange
): Pair<Modifier, (TextLayoutResult) -> Unit> {
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var latestLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val marginPx = with(density) { 14.dp.toPx() }

    if (isFocused) {
        val imeBottomPx = WindowInsets.ime.getBottom(density)
        LaunchedEffect(imeBottomPx, selection) {
            withFrameNanos { }
            val layout = latestLayout
            if (layout != null) {
                val offset = selection.end.coerceIn(0, layout.layoutInput.text.length)
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
    }

    val onTextLayout: (TextLayoutResult) -> Unit = remember {
        { result -> latestLayout = result }
    }
    return Modifier.bringIntoViewRequester(requester) to onTextLayout
}
