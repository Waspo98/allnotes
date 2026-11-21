package app.overbay.allnotes

import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.NoteStorageBackend
import app.overbay.allnotes.data.markdown.MarkdownCodec
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.model.RichTextSpan
import app.overbay.allnotes.model.WorkspaceSnapshot
import app.overbay.allnotes.ui.workspace.EditorInputController
import app.overbay.allnotes.ui.workspace.ParagraphFlowEngine
import app.overbay.allnotes.ui.workspace.SlashCommandKind
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers

private class FlowTestStorage : NoteStorageBackend {
    var saved: WorkspaceSnapshot? = null
    override fun loadSnapshot(): WorkspaceSnapshot? = saved
    override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        saved = snapshot
    }
}

class ParagraphFlowEngineTest {

    private fun newGenerators(): Pair<() -> String, () -> Long> {
        var idSeq = 1
        var tokSeq = 1L
        return ({ "gen-${idSeq++}" }) to ({ tokSeq++ })
    }

    @Test
    fun midParagraphMarkdownTriggersPreserveSpansAndAbsorbSeparators() {
        val (nextId, nextTok) = newGenerators()
        val initial = listOf<DocumentBlock>(
            DocumentBlock.Paragraph(
                id = "p1",
                text = "Bold intro\n##\nItalic outro",
                spans = listOf(
                    RichTextSpan(0, 4, RichSpanKind.BOLD),
                    RichTextSpan(14, 20, RichSpanKind.ITALIC)
                )
            )
        )

        val res = EditorInputController.editParagraphWithTriggers(
            blocks = initial,
            blockId = "p1",
            newText = "Bold intro\n## \nItalic outro",
            cursorOffset = 14,
            nextId = nextId,
            nextToken = nextTok
        )
        assertTrue(res.isStructuralConversion)
        assertEquals(3, res.blocks.size)
        val before = res.blocks[0] as DocumentBlock.Paragraph
        val heading = res.blocks[1] as DocumentBlock.Heading
        val after = res.blocks[2] as DocumentBlock.Paragraph
        assertEquals("Bold intro", before.text)
        assertEquals(listOf(RichTextSpan(0, 4, RichSpanKind.BOLD)), before.spans)
        assertEquals(2, heading.level)
        assertEquals("", heading.text)
        assertEquals("Italic outro", after.text)
        assertEquals(listOf(RichTextSpan(0, 6, RichSpanKind.ITALIC)), after.spans)
        assertEquals(heading.id, res.activeBlockId)

        // Single blank separator newlines above and below trigger line are absorbed cleanly
        val spaced = listOf<DocumentBlock>(DocumentBlock.Paragraph(id = "p2", text = "Intro\n\n##\n\nOutro"))
        val spacedRes = EditorInputController.editParagraphWithTriggers(
            blocks = spaced,
            blockId = "p2",
            newText = "Intro\n\n## \n\nOutro",
            cursorOffset = 10,
            nextId = nextId,
            nextToken = nextTok
        )
        assertEquals(3, spacedRes.blocks.size)
        assertEquals("Intro", (spacedRes.blocks[0] as DocumentBlock.Paragraph).text)
        assertTrue(spacedRes.blocks[1] is DocumentBlock.Heading)
        assertEquals("Outro", (spacedRes.blocks[2] as DocumentBlock.Paragraph).text)

        // Editing an unrelated line does not re-trigger an earlier line starting with "# "
        val untouchedFirstLine = listOf<DocumentBlock>(DocumentBlock.Paragraph(id = "p3", text = "## Pasted\nBody"))
        val bodyEdit = EditorInputController.editParagraphWithTriggers(
            blocks = untouchedFirstLine,
            blockId = "p3",
            newText = "## Pasted\nBody!",
            cursorOffset = 15,
            nextId = nextId,
            nextToken = nextTok
        )
        assertFalse(bodyEdit.isStructuralConversion)
        assertEquals("## Pasted\nBody!", (bodyEdit.blocks.single() as DocumentBlock.Paragraph).text)
    }

    @Test
    fun midParagraphSlashCommandsAndEscapeDismissalPreserveSurroundingLines() {
        val (nextId, nextTok) = newGenerators()
        val text = "Hello\n/to\nWorld"
        assertTrue(EditorInputController.matchingSlashCommands(text, cursorOffset = 9).contains(SlashCommandKind.CHECKLIST))
        assertTrue(EditorInputController.matchingSlashCommands(text, cursorOffset = 3).isEmpty())

        val blocks = listOf<DocumentBlock>(
            DocumentBlock.Paragraph(
                id = "p1",
                text = text,
                spans = listOf(
                    RichTextSpan(0, 5, RichSpanKind.BOLD),
                    RichTextSpan(10, 15, RichSpanKind.ITALIC)
                )
            )
        )

        val checklistRes = EditorInputController.executeSlashCommand(
            blocks = blocks,
            blockId = "p1",
            kind = SlashCommandKind.CHECKLIST,
            nextId = nextId,
            nextToken = nextTok,
            cursorOffset = 9
        )
        assertEquals(3, checklistRes.blocks.size)
        assertEquals("Hello", (checklistRes.blocks[0] as DocumentBlock.Paragraph).text)
        assertTrue(checklistRes.blocks[1] is DocumentBlock.Checklist)
        assertEquals("World", (checklistRes.blocks[2] as DocumentBlock.Paragraph).text)

        val dismissed = EditorInputController.dismissSlashCommand(
            blocks = blocks,
            blockId = "p1",
            cursorOffset = 9,
            nextToken = nextTok
        )
        val dismissedPara = dismissed.blocks.single() as DocumentBlock.Paragraph
        assertEquals("Hello\n\nWorld", dismissedPara.text)
        assertEquals(6, dismissed.focusRequest?.cursorOffset)
        assertEquals(
            listOf(RichTextSpan(0, 5, RichSpanKind.BOLD), RichTextSpan(7, 12, RichSpanKind.ITALIC)),
            dismissedPara.spans
        )

        val imgBlocks = listOf<DocumentBlock>(
            DocumentBlock.Paragraph(
                id = "p2",
                text = "Hello\n/image\nWorld",
                spans = listOf(RichTextSpan(13, 18, RichSpanKind.ITALIC))
            )
        )
        val imgSlash = EditorInputController.executeSlashCommand(
            blocks = imgBlocks,
            blockId = "p2",
            kind = SlashCommandKind.ATTACHMENT,
            nextId = nextId,
            nextToken = nextTok,
            cursorOffset = 12
        )
        val imgPara = imgSlash.blocks.single() as DocumentBlock.Paragraph
        assertEquals("Hello\n\nWorld", imgPara.text)
        assertEquals(6, imgSlash.focusRequest?.cursorOffset)
        assertEquals(listOf(RichTextSpan(7, 12, RichSpanKind.ITALIC)), imgPara.spans)
    }

    @Test
    fun lineLevelToolbarConversionAndBackToBodyCoalescing() {
        val repo = NoteRepository(storage = FlowTestStorage())
        val vm = WorkspaceViewModel(repository = repo, coroutineDispatcher = Dispatchers.Unconfined)
        val p = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Paragraph>().first()

        vm.updateBlockText(p.id, "Intro line\nHeading line\nFooter line")
        vm.dispatch(WorkspaceAction.UpdateActiveSelection(p.id, 15, 15))
        vm.dispatch(WorkspaceAction.ConvertActiveBlock(SlashCommandKind.HEADING_2))

        val splitBlocks = vm.uiState.value.activePage!!.blocks
        val headingIdx = splitBlocks.indexOfFirst { it is DocumentBlock.Heading && it.text == "Heading line" }
        assertTrue(headingIdx > 0)
        assertEquals("Intro line", (splitBlocks[headingIdx - 1] as DocumentBlock.Paragraph).text)
        assertEquals("Footer line", (splitBlocks[headingIdx + 1] as DocumentBlock.Paragraph).text)
        assertEquals(splitBlocks[headingIdx].id, vm.uiState.value.activeBlockId)

        // Converting that Heading back into Body coalesces all 3 back into one continuous Paragraph
        vm.dispatch(WorkspaceAction.ConvertActiveBlock(SlashCommandKind.PARAGRAPH))
        val coalescedBlocks = vm.uiState.value.activePage!!.blocks
        val mergedPara = coalescedBlocks[headingIdx - 1] as DocumentBlock.Paragraph
        assertEquals("Intro line\nHeading line\nFooter line", mergedPara.text)
        assertFalse(coalescedBlocks.any { it is DocumentBlock.Heading && it.text == "Heading line" })

        // Multi-line selection conversion to Checklist extracts only selected lines
        vm.dispatch(WorkspaceAction.UpdateActiveSelection(mergedPara.id, 11, 30))
        vm.dispatch(WorkspaceAction.ConvertActiveBlock(SlashCommandKind.CHECKLIST))
        val afterList = vm.uiState.value.activePage!!.blocks
        val createdList = vm.uiState.value.activeBlock as DocumentBlock.Checklist
        val listIdx = afterList.indexOfFirst { it.id == createdList.id }
        assertTrue(listIdx > 0)
        assertEquals("Intro line", (afterList[listIdx - 1] as DocumentBlock.Paragraph).text)
        assertEquals(listOf("Heading line", "Footer line"), createdList.items.map { it.text })
    }

    @Test
    fun cursorAwareToolbarBlockInsertionSplitsAroundActiveLineAndParagraphInsertsNewline() {
        val repo = NoteRepository(storage = FlowTestStorage())
        val vm = WorkspaceViewModel(repository = repo, coroutineDispatcher = Dispatchers.Unconfined)
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Cursor Insert Test")
        vm.selectPage(page.id)

        val p = vm.uiState.value.activePage!!.blocks.single() as DocumentBlock.Paragraph
        vm.updateBlockText(p.id, "Line 1\nLine 2\nLine 3")
        vm.dispatch(WorkspaceAction.UpdateActiveSelection(p.id, 4, 4))
        vm.dispatch(WorkspaceAction.InsertTable(afterBlockId = p.id))

        val withTable = vm.uiState.value.activePage!!.blocks
        assertEquals(3, withTable.size)
        assertEquals("Line 1", (withTable[0] as DocumentBlock.Paragraph).text)
        assertTrue(withTable[1] is DocumentBlock.Table)
        assertEquals("Line 2\nLine 3", (withTable[2] as DocumentBlock.Paragraph).text)

        // InsertParagraph while inside an active Paragraph inserts a newline instead of spawning a duplicate block
        val firstPara = withTable[0] as DocumentBlock.Paragraph
        vm.dispatch(WorkspaceAction.UpdateActiveSelection(firstPara.id, 4, 4))
        vm.dispatch(WorkspaceAction.InsertParagraph(afterBlockId = firstPara.id))
        val afterNewline = vm.uiState.value.activePage!!.blocks
        assertEquals(3, afterNewline.size)
        assertEquals("Line\n 1", (afterNewline[0] as DocumentBlock.Paragraph).text)
        assertEquals(5, vm.uiState.value.selectionEnd)
    }

    @Test
    fun smartBackspaceBoundariesAndDividerRemoval() {
        val (_, nextTok) = newGenerators()

        // 1. Multi-line Paragraph below Heading pulls only its first line into the Heading
        val headingPull = EditorInputController.backspaceAtBlockStart(
            blocks = listOf(
                DocumentBlock.Heading("h1", "Title", level = 2),
                DocumentBlock.Paragraph("p1", "Line A\nLine B")
            ),
            blockId = "p1",
            nextToken = nextTok
        )
        assertEquals(2, headingPull.blocks.size)
        assertEquals("TitleLine A", (headingPull.blocks[0] as DocumentBlock.Heading).text)
        assertEquals("Line B", (headingPull.blocks[1] as DocumentBlock.Paragraph).text)
        assertEquals(5, headingPull.focusRequest?.cursorOffset)

        // 2. Backspace at start of Paragraph after Divider deletes Divider and coalesces surrounding prose
        val dividerMerge = EditorInputController.backspaceAtBlockStart(
            blocks = listOf(
                DocumentBlock.Paragraph("p1", "Above"),
                DocumentBlock.Divider("d1"),
                DocumentBlock.Paragraph("p2", "Below")
            ),
            blockId = "p2",
            nextToken = nextTok
        )
        val mergedAfterDivider = dividerMerge.blocks.single() as DocumentBlock.Paragraph
        assertEquals("Above\nBelow", mergedAfterDivider.text)
        assertEquals(6, dividerMerge.focusRequest?.cursorOffset)

        // 3. Backspace at start of Heading between two Paragraphs demotes and coalesces all three
        val headingDemote = EditorInputController.backspaceAtBlockStart(
            blocks = listOf(
                DocumentBlock.Paragraph("p1", "Top"),
                DocumentBlock.Heading("h1", "Mid", level = 2),
                DocumentBlock.Paragraph("p2", "Bottom")
            ),
            blockId = "h1",
            nextToken = nextTok
        )
        val coalesced = headingDemote.blocks.single() as DocumentBlock.Paragraph
        assertEquals("Top\nMid\nBottom", coalesced.text)
        assertEquals(4, headingDemote.focusRequest?.cursorOffset)
    }

    @Test
    fun headingAndListEnterExitReuseFollowingParagraph() {
        val (nextId, nextTok) = newGenerators()

        val headingExit = EditorInputController.splitBlockAtCursor(
            blocks = listOf(
                DocumentBlock.Heading("h1", "Title", level = 1),
                DocumentBlock.Paragraph(
                    id = "p1",
                    text = "Existing body",
                    spans = listOf(RichTextSpan(0, 8, RichSpanKind.BOLD))
                )
            ),
            blockId = "h1",
            cursorOffset = 5,
            nextId = nextId,
            nextToken = nextTok
        )
        assertEquals(2, headingExit.blocks.size)
        val updatedPara = headingExit.blocks[1] as DocumentBlock.Paragraph
        assertEquals("\nExisting body", updatedPara.text)
        assertEquals(listOf(RichTextSpan(1, 9, RichSpanKind.BOLD)), updatedPara.spans)
        assertEquals(0, headingExit.focusRequest?.cursorOffset)

        val listExit = EditorInputController.handleListEnter(
            blocks = listOf(
                DocumentBlock.Paragraph("p0", "Intro"),
                DocumentBlock.Checklist("c1", "", listOf(ChecklistItem("i1", "", isChecked = false))),
                DocumentBlock.Paragraph("p2", "Outro")
            ),
            blockId = "c1",
            itemIndex = 0,
            nextId = nextId,
            nextToken = nextTok
        )
        val merged = listExit.blocks.single() as DocumentBlock.Paragraph
        assertEquals("Intro\nOutro", merged.text)
    }

    @Test
    fun crossBlockArrowTraversalSkipsNonEditableBlocks() {
        val (_, nextTok) = newGenerators()
        val blocks = listOf(
            DocumentBlock.Paragraph("p1", "First"),
            DocumentBlock.Heading("h1", "Heading", level = 2),
            DocumentBlock.Divider("d1"),
            DocumentBlock.Checklist("c1", "", listOf(ChecklistItem("i1", "One"), ChecklistItem("i2", "Two"))),
            DocumentBlock.Table("t1", listOf("Col"), listOf(listOf("Val"))),
            DocumentBlock.Paragraph("p2", "Last")
        )

        val downToHeading = ParagraphFlowEngine.focusAdjacentBlock(blocks, "p1", +1, nextTok)
        assertEquals("h1", downToHeading.activeBlockId)
        assertEquals(0, downToHeading.focusRequest?.cursorOffset)

        val downToChecklist = ParagraphFlowEngine.focusAdjacentBlock(blocks, "h1", +1, nextTok)
        assertEquals("c1", downToChecklist.activeBlockId)
        assertEquals(0, downToChecklist.focusRequest?.subItemIndex)

        val downToLast = ParagraphFlowEngine.focusAdjacentBlock(blocks, "c1", +1, nextTok)
        assertEquals("p2", downToLast.activeBlockId)
        assertEquals(0, downToLast.focusRequest?.cursorOffset)

        val upToChecklist = ParagraphFlowEngine.focusAdjacentBlock(blocks, "p2", -1, nextTok)
        assertEquals("c1", upToChecklist.activeBlockId)
        assertEquals(1, upToChecklist.focusRequest?.subItemIndex)
        assertEquals(Int.MAX_VALUE, upToChecklist.focusRequest?.cursorOffset)

        val upToHeading = ParagraphFlowEngine.focusAdjacentBlock(blocks, "c1", -1, nextTok)
        assertEquals("h1", upToHeading.activeBlockId)
        assertEquals(Int.MAX_VALUE, upToHeading.focusRequest?.cursorOffset)
    }

    @Test
    fun multilineMarkdownRoundTripsCrossLineSpansAndStructuralBoundaries() {
        val page = NotePage(
            id = "md-flow",
            sectionId = "sec-1",
            title = "Flowing Prose",
            blocks = listOf(
                DocumentBlock.Paragraph(
                    id = "p1",
                    text = "First bold\nSecond bold\n\nThird para",
                    spans = listOf(RichTextSpan(0, 22, RichSpanKind.BOLD))
                ),
                DocumentBlock.Heading("h1", "Next Section", level = 2),
                DocumentBlock.Paragraph("p2", "After heading")
            )
        )

        val exported = MarkdownCodec.exportPageToMarkdown(page)
        assertTrue(exported.contains("**First bold**\n**Second bold**\n\nThird para"))

        var seq = 1
        val reimported = MarkdownCodec.importMarkdownToPage(exported, "sec-1", { "$it-${seq++}" })
        assertEquals(3, reimported.blocks.size)
        val firstPara = reimported.blocks[0] as DocumentBlock.Paragraph
        assertEquals("First bold\nSecond bold\n\nThird para", firstPara.text)
        assertTrue(firstPara.spans.any { it.kind == RichSpanKind.BOLD && firstPara.text.substring(it.start, it.end) == "First bold" })
        assertTrue(firstPara.spans.any { it.kind == RichSpanKind.BOLD && firstPara.text.substring(it.start, it.end) == "Second bold" })
        assertTrue(reimported.blocks[1] is DocumentBlock.Heading)
        assertEquals("After heading", (reimported.blocks[2] as DocumentBlock.Paragraph).text)
    }
}
