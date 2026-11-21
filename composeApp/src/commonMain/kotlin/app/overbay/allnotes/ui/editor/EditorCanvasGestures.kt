package app.overbay.allnotes.ui.editor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import app.overbay.allnotes.model.DocumentBlock

internal fun pageHeaderItemKey(pageId: String): String = "page-header-$pageId"

internal fun trailingCanvasItemKey(pageId: String): String = "trailing-canvas-$pageId"

internal data class CanvasItemBounds(
    val key: Any,
    val offset: Int,
    val size: Int
)

/**
 * Returns true when the page has no body content yet (no blocks, or a single empty paragraph).
 */
internal fun isBlankPageBody(blocks: List<DocumentBlock>): Boolean =
    blocks.isEmpty() ||
        (blocks.size == 1 && (blocks[0] as? DocumentBlock.Paragraph)?.text?.isEmpty() == true)

/**
 * Determines whether an unconsumed tap at [tapY] inside the editor viewport lands in the
 * blank trailing canvas below the page's content (or below the header on a blank page).
 */
internal fun shouldFocusBodyOnCanvasTap(
    visibleItems: List<CanvasItemBounds>,
    viewportStartOffset: Int,
    pageId: String,
    blocks: List<DocumentBlock>,
    tapY: Float
): Boolean {
    val trailingKey = trailingCanvasItemKey(pageId)
    if (visibleItems.none { it.key == trailingKey }) return false

    val anchorItem = if (isBlankPageBody(blocks)) {
        visibleItems.firstOrNull { it.key == pageHeaderItemKey(pageId) }
    } else {
        visibleItems.lastOrNull { it.key != trailingKey }
    }

    val thresholdY = if (anchorItem != null) {
        (anchorItem.offset + anchorItem.size - viewportStartOffset).toFloat()
    } else {
        0f
    }
    return tapY >= thresholdY
}

/**
 * Routes unconsumed taps in the empty canvas space below a page's content (or anywhere below
 * the header on a blank page) to [onFocusTrailingBody]. Placed after padding/insets modifiers on
 * the editor `LazyColumn` so tap coordinates share the viewport's origin without hijacking taps
 * in horizontal margins beside upper blocks on long notes.
 */
@Composable
internal fun Modifier.tapBlankCanvasToFocus(
    listState: LazyListState,
    pageId: String,
    blocks: List<DocumentBlock>,
    onFocusTrailingBody: () -> Unit
): Modifier {
    val latestBlocks by rememberUpdatedState(blocks)
    val latestOnFocus by rememberUpdatedState(onFocusTrailingBody)
    return pointerInput(pageId) {
        detectTapGestures { tapOffset ->
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo.map {
                CanvasItemBounds(key = it.key, offset = it.offset, size = it.size)
            }
            if (shouldFocusBodyOnCanvasTap(
                    visibleItems = visibleItems,
                    viewportStartOffset = layoutInfo.viewportStartOffset,
                    pageId = pageId,
                    blocks = latestBlocks,
                    tapY = tapOffset.y
                )
            ) {
                latestOnFocus()
            }
        }
    }
}
