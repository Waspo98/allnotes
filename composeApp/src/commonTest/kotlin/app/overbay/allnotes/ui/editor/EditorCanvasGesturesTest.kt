package app.overbay.allnotes.ui.editor

import app.overbay.allnotes.model.DocumentBlock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorCanvasGesturesTest {

    @Test
    fun isBlankPageBodyMatchesOnlyEmptyOrSingleEmptyParagraph() {
        assertTrue(isBlankPageBody(emptyList()))
        assertTrue(isBlankPageBody(listOf(DocumentBlock.Paragraph(id = "p1", text = ""))))

        assertFalse(isBlankPageBody(listOf(DocumentBlock.Paragraph(id = "p1", text = "Hello"))))
        assertFalse(isBlankPageBody(listOf(DocumentBlock.Heading(id = "h1", text = ""))))
        assertFalse(
            isBlankPageBody(
                listOf(
                    DocumentBlock.Paragraph(id = "p1", text = ""),
                    DocumentBlock.Paragraph(id = "p2", text = "")
                )
            )
        )
    }

    @Test
    fun blankPageTapTargetsEntireCanvasBelowHeaderIncludingViewportPadding() {
        val pageId = "page-1"
        val blocks = listOf(DocumentBlock.Paragraph(id = "p1", text = ""))
        // viewportStartOffset = -40 (e.g. 40px top contentPadding), so item offset 0 sits at y=40px.
        val visibleItems = listOf(
            CanvasItemBounds(key = pageHeaderItemKey(pageId), offset = 0, size = 180),
            CanvasItemBounds(key = "p1", offset = 200, size = 60),
            CanvasItemBounds(key = trailingCanvasItemKey(pageId), offset = 280, size = 140)
        )
        // Header bottom in viewport coordinates = 0 + 180 - (-40) = 220f.
        assertFalse(shouldFocusBodyOnCanvasTap(visibleItems, -40, pageId, blocks, tapY = 120f))
        assertFalse(shouldFocusBodyOnCanvasTap(visibleItems, -40, pageId, blocks, tapY = 219f))

        // Tapping right below divider, beside empty paragraph, inside trailing box, or at bottom of screen all focus body.
        assertTrue(shouldFocusBodyOnCanvasTap(visibleItems, -40, pageId, blocks, tapY = 220f))
        assertTrue(shouldFocusBodyOnCanvasTap(visibleItems, -40, pageId, blocks, tapY = 260f))
        assertTrue(shouldFocusBodyOnCanvasTap(visibleItems, -40, pageId, blocks, tapY = 400f))
        assertTrue(shouldFocusBodyOnCanvasTap(visibleItems, -40, pageId, blocks, tapY = 1600f))
    }

    @Test
    fun blankPageWithZeroBlocksFocusesBodyBelowHeader() {
        val pageId = "page-empty"
        val visibleItems = listOf(
            CanvasItemBounds(key = pageHeaderItemKey(pageId), offset = 0, size = 160),
            CanvasItemBounds(key = trailingCanvasItemKey(pageId), offset = 180, size = 140)
        )
        assertFalse(shouldFocusBodyOnCanvasTap(visibleItems, 0, pageId, emptyList(), tapY = 159f))
        assertTrue(shouldFocusBodyOnCanvasTap(visibleItems, 0, pageId, emptyList(), tapY = 160f))
        assertTrue(shouldFocusBodyOnCanvasTap(visibleItems, 0, pageId, emptyList(), tapY = 900f))
    }

    @Test
    fun populatedShortPageIgnoresSideMarginTapsAboveLastBlockAndFocusesBelowLastBlock() {
        val pageId = "page-2"
        val blocks = listOf(
            DocumentBlock.Heading(id = "h1", text = "Title", level = 2),
            DocumentBlock.Paragraph(id = "p1", text = "Body copy")
        )
        val visibleItems = listOf(
            CanvasItemBounds(key = pageHeaderItemKey(pageId), offset = 0, size = 180),
            CanvasItemBounds(key = "h1", offset = 200, size = 80),
            CanvasItemBounds(key = "p1", offset = 300, size = 100),
            CanvasItemBounds(key = trailingCanvasItemKey(pageId), offset = 420, size = 140)
        )
        // Last content item bottom = 300 + 100 - (-20) = 420f.
        assertFalse(shouldFocusBodyOnCanvasTap(visibleItems, -20, pageId, blocks, tapY = 250f))
        assertFalse(shouldFocusBodyOnCanvasTap(visibleItems, -20, pageId, blocks, tapY = 419f))
        assertTrue(shouldFocusBodyOnCanvasTap(visibleItems, -20, pageId, blocks, tapY = 420f))
        assertTrue(shouldFocusBodyOnCanvasTap(visibleItems, -20, pageId, blocks, tapY = 1100f))
    }

    @Test
    fun longScrolledPageDoesNotFocusTrailingBodyWhenTrailingCanvasNotVisible() {
        val pageId = "page-long"
        val blocks = listOf(
            DocumentBlock.Paragraph(id = "p1", text = "First"),
            DocumentBlock.Paragraph(id = "p2", text = "Second")
        )
        val visibleItemsWithoutTrailing = listOf(
            CanvasItemBounds(key = pageHeaderItemKey(pageId), offset = 0, size = 180),
            CanvasItemBounds(key = "p1", offset = 200, size = 400),
            CanvasItemBounds(key = "p2", offset = 620, size = 400)
        )
        assertFalse(
            shouldFocusBodyOnCanvasTap(visibleItemsWithoutTrailing, -20, pageId, blocks, tapY = 1200f)
        )
    }
}
