package app.overbay.allnotes

import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.NoteStorageBackend
import app.overbay.allnotes.data.markdown.MarkdownCodec
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.model.RichTextSpan
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.workspace.EditorInputController
import app.overbay.allnotes.ui.workspace.SlashCommandKind
import app.overbay.allnotes.ui.workspace.UndoRedoController
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers

private class MemoryNoteStorage : NoteStorageBackend {
    var snap: WorkspaceSnapshot? = null
    override fun loadSnapshot(): WorkspaceSnapshot? = snap
    override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        snap = snapshot
    }
}

class RichSpanAndEditorControllerTest {

    @Test
    fun richSpanToggleSplitMergeAndEditOffsetAdjustmentWorkAccurately() {
        val base = DocumentBlock.Paragraph(id = "p1", text = "Build expressive notes today")

        // Toggle BOLD on "expressive" (6..16)
        val bolded = RichSpanEngine.toggleSelectionSpan(base, 6, 16, RichSpanKind.BOLD)
        assertTrue(RichSpanEngine.hasActiveStyle(bolded, 6, 16, RichSpanKind.BOLD))
        assertFalse(RichSpanEngine.hasActiveStyle(bolded, 0, 5, RichSpanKind.BOLD))

        // Insert "very " at index 6 (before span) -> span shifts to 11..21
        val shiftedSpans = RichSpanEngine.adjustSpansAfterEdit(
            oldText = bolded.text,
            newText = "Build very expressive notes today",
            existingSpans = bolded.spans
        )
        assertEquals(1, shiftedSpans.size)
        assertEquals(11, shiftedSpans.single().start)
        assertEquals(21, shiftedSpans.single().end)

        // Split paragraph right in the middle of "expressive" (at offset 16 in "Build very expressive notes today")
        val updatedPara = bolded.copy(text = "Build very expressive notes today", spans = shiftedSpans)
        val (left, right) = RichSpanEngine.splitParagraphAt(updatedPara, 16, "p2")
        assertEquals("Build very expre", left.text)
        assertEquals("ssive notes today", right.text)
        assertEquals(RichTextSpan(11, 16, RichSpanKind.BOLD), left.spans.single())
        assertEquals(RichTextSpan(0, 5, RichSpanKind.BOLD), right.spans.single())

        // Merge them back -> coalesces back into single contiguous BOLD span 11..21!
        val remerged = RichSpanEngine.mergeParagraphs(left, right)
        assertEquals("Build very expressive notes today", remerged.text)
        assertEquals(RichTextSpan(11, 21, RichSpanKind.BOLD), remerged.spans.single())
    }

    @Test
    fun markdownLiveAutoTriggersSlashCommandsAndListEnterExitWork() {
        var idSeq = 1
        var tokSeq = 1L
        val nextId = { "gen-${idSeq++}" }
        val nextTok = { tokSeq++ }

        val initial = listOf<DocumentBlock>(DocumentBlock.Paragraph(id = "b1", text = ""))

        // Typing "## Roadmap" converts Paragraph into Heading level 2
        val h2Res = EditorInputController.editParagraphWithTriggers(initial, "b1", "## Roadmap", 10, nextId, nextTok)
        val heading = h2Res.blocks.single() as DocumentBlock.Heading
        assertEquals(2, heading.level)
        assertEquals("Roadmap", heading.text)
        assertTrue(h2Res.isStructuralConversion)

        // Backspace at offset 0 of Heading demotes back to Paragraph preserving text
        val demoted = EditorInputController.backspaceAtBlockStart(h2Res.blocks, "b1", nextTok)
        val para = demoted.blocks.single() as DocumentBlock.Paragraph
        assertEquals("Roadmap", para.text)

        // Slash command "/todo" converts Paragraph to Checklist
        val slashMatches = EditorInputController.matchingSlashCommands("/to")
        assertTrue(slashMatches.contains(SlashCommandKind.CHECKLIST))
        val chkRes = EditorInputController.executeSlashCommand(demoted.blocks, "b1", SlashCommandKind.CHECKLIST, nextId, nextTok)
        val chk = chkRes.blocks.single() as DocumentBlock.Checklist
        assertEquals(1, chk.items.size)

        // Pressing Enter on an empty single-item checklist exits list back into a Paragraph
        val exited = EditorInputController.handleListEnter(chkRes.blocks, "b1", 0, nextId, nextTok)
        assertTrue(exited.blocks.single() is DocumentBlock.Paragraph)
    }

    @Test
    fun undoRedoControllerMaintainsBoundedHistoryAndCoalescesTypingBursts() {
        val controller = UndoRedoController(maxHistoryPerPage = 5, typingCoalesceWindowMs = 650L)
        val b0 = listOf<DocumentBlock>(DocumentBlock.Paragraph("p1", ""))
        val b1 = listOf<DocumentBlock>(DocumentBlock.Paragraph("p1", "H"))
        val b2 = listOf<DocumentBlock>(DocumentBlock.Paragraph("p1", "He"))

        // Two keystrokes 100ms apart in same block coalesce into a single undo entry
        controller.recordBeforeMutation("page-1", b0, coalesceTyping = true, activeBlockId = "p1", nowEpochMs = 1000L)
        controller.recordBeforeMutation("page-1", b1, coalesceTyping = true, activeBlockId = "p1", nowEpochMs = 1100L)

        val undone = controller.undo("page-1", b2)
        assertEquals(b0, undone, "Coalesced typing burst should undo directly back to pre-burst state")
        assertFalse(controller.canUndo("page-1"))
        assertTrue(controller.canRedo("page-1"))
        assertEquals(b2, controller.redo("page-1", b0))
    }

    @Test
    fun markdownExportAndImportRoundTripsRichBlocksAndInlineSpans() {
        val page = NotePage(
            id = "p-md",
            sectionId = "sec-1",
            title = "Q4 Release Plan",
            blocks = listOf(
                DocumentBlock.Heading("h1", "Architecture Goals", level = 2),
                DocumentBlock.Paragraph(
                    id = "par1",
                    text = "Ship bold and linked docs with inline code.",
                    spans = listOf(
                        RichTextSpan(5, 9, RichSpanKind.BOLD),
                        RichTextSpan(14, 20, RichSpanKind.LINK, url = "https://notes.overbay.app"),
                        RichTextSpan(31, 42, RichSpanKind.INLINE_CODE)
                    )
                ),
                DocumentBlock.Checklist(
                    id = "chk1",
                    title = "",
                    items = listOf(
                        ChecklistItem("c1", "Write migration", isChecked = true),
                        ChecklistItem("c2", "Verify APK", isChecked = false)
                    )
                ),
                DocumentBlock.Table(
                    id = "tbl1",
                    headers = listOf("Target", "Status"),
                    rows = listOf(listOf("Android", "Ready"), listOf("Windows ARM64", "Ready"))
                ),
                DocumentBlock.Callout("cal1", "Performance Tip", "Zero horizontal panning.", CalloutTone.TIP),
                DocumentBlock.CodeSnippet("cod1", "kotlin", "val ready = true"),
                DocumentBlock.Divider("div1")
            ),
            updatedAtEpochMs = 5000L
        )

        val md = MarkdownCodec.exportPageToMarkdown(page)
        assertTrue(md.contains("# Q4 Release Plan"))
        assertTrue(md.contains("**bold**"))
        assertTrue(md.contains("[linked](https://notes.overbay.app)"))
        assertTrue(md.contains("`inline code`"))
        assertTrue(md.contains("- [x] Write migration"))

        var seq = 1
        val reimported = MarkdownCodec.importMarkdownToPage(md, "sec-1", { "${it}-${seq++}" }, 6000L)
        assertEquals("Q4 Release Plan", reimported.title)
        assertTrue(reimported.blocks.any { it is DocumentBlock.Heading && it.text == "Architecture Goals" })
        val importedPara = reimported.blocks.filterIsInstance<DocumentBlock.Paragraph>().first()
        assertEquals("Ship bold and linked docs with inline code.", importedPara.text)
        assertTrue(importedPara.spans.any { it.kind == RichSpanKind.BOLD })
        assertTrue(importedPara.spans.any { it.kind == RichSpanKind.LINK && it.url == "https://notes.overbay.app" })
        assertTrue(importedPara.spans.any { it.kind == RichSpanKind.INLINE_CODE })
        assertTrue(reimported.blocks.any { it is DocumentBlock.Checklist && it.items.size == 2 })
        assertTrue(reimported.blocks.any { it is DocumentBlock.Table && it.rows.size == 2 })
        assertTrue(reimported.blocks.any { it is DocumentBlock.Callout && it.tone == CalloutTone.TIP })
        assertTrue(reimported.blocks.any { it is DocumentBlock.CodeSnippet && it.language == "kotlin" })
        assertTrue(reimported.blocks.any { it is DocumentBlock.Divider })
    }

    @Test
    fun viewModelGlobalSearchUndoRedoAndPageMoveWorkSeamlessly() {
        val repo = NoteRepository(storage = MemoryNoteStorage())
        val vm = WorkspaceViewModel(repository = repo, coroutineDispatcher = Dispatchers.Unconfined)

        val nb2 = repo.createNotebook("Research Lab", "AI & Systems", SectionAccentKey.VIOLET)
        val sec2 = repo.createSection(nb2.id, "Papers", SectionAccentKey.ROSE)
        val targetPage = repo.createPage(sec2.id, "Transformer Attention Notes")

        // Stay in first notebook and search globally for "Transformer"
        vm.selectNotebook(vm.uiState.value.notebooks.first().id)
        vm.setSearchQuery("Transformer")
        assertTrue(vm.uiState.value.pagesForSection.isEmpty(), "Current section should have no match")
        val globalHit = vm.uiState.value.globalSearchResults.singleOrNull { it.page.id == targetPage.id }
        assertNotNull(globalHit, "Global search must surface matches across other notebooks")
        assertEquals("Research Lab", globalHit.notebookTitle)

        // Clear search, select target page, insert divider, then Undo & Redo
        vm.setSearchQuery("")
        vm.selectPage(targetPage.id)
        val beforeCount = vm.uiState.value.activePage!!.blocks.size
        vm.dispatch(WorkspaceAction.InsertDivider())
        assertEquals(beforeCount + 1, vm.uiState.value.activePage!!.blocks.size)
        assertTrue(vm.uiState.value.canUndo)

        vm.dispatch(WorkspaceAction.Undo)
        assertEquals(beforeCount, vm.uiState.value.activePage!!.blocks.size)
        assertTrue(vm.uiState.value.canRedo)

        vm.dispatch(WorkspaceAction.Redo)
        assertEquals(beforeCount + 1, vm.uiState.value.activePage!!.blocks.size)

        // Move active page to first notebook's first section
        val destSec = repo.snapshot.value.sections.first { it.notebookId != nb2.id }
        vm.dispatch(WorkspaceAction.MoveActivePageToSection(destSec.id))
        assertEquals(destSec.id, vm.uiState.value.activePage?.sectionId)
    }

    @Test
    fun markdownCodecRoundtripsTableEscapedPipesUnderlineAndNestedSpans() {
        val page = NotePage(
            id = "md-p1",
            sectionId = "sec-1",
            title = "Formatting Audit",
            blocks = listOf(
                DocumentBlock.Paragraph(
                    id = "b1",
                    text = "bold italic",
                    spans = listOf(
                        RichTextSpan(0, 11, RichSpanKind.BOLD),
                        RichTextSpan(5, 11, RichSpanKind.ITALIC)
                    )
                ),
                DocumentBlock.Paragraph(
                    id = "b2",
                    text = "underlined and bold link",
                    spans = listOf(
                        RichTextSpan(0, 10, RichSpanKind.UNDERLINE),
                        RichTextSpan(15, 24, RichSpanKind.LINK, "https://overbay.app")
                    )
                ),
                DocumentBlock.Table(
                    id = "b3",
                    headers = listOf("Key", "Expr"),
                    rows = listOf(listOf("OR", "A | B"))
                )
            )
        )

        val md = MarkdownCodec.exportPageToMarkdown(page)
        assertTrue(md.contains("<u>underlined</u>"))
        assertTrue(md.contains("[bold link](https://overbay.app)"))
        assertTrue(md.contains("A \\| B"))

        val seq = generateSequence(1) { it + 1 }.iterator()
        val reimported = MarkdownCodec.importMarkdownToPage(
            markdown = md,
            sectionId = "sec-1",
            nextId = { prefix -> "$prefix-${seq.next()}" }
        )

        val p2 = reimported.blocks.filterIsInstance<DocumentBlock.Paragraph>()[1]
        assertTrue(p2.spans.any { it.kind == RichSpanKind.UNDERLINE && p2.text.substring(it.start, it.end) == "underlined" })
        assertTrue(p2.spans.any { it.kind == RichSpanKind.LINK && it.url == "https://overbay.app" })

        val nestedMd = """
            # Nested
            **bold *italic*** and [**Overbay**](https://overbay.app)
        """.trimIndent()
        val nestedPage = MarkdownCodec.importMarkdownToPage(
            markdown = nestedMd,
            sectionId = "sec-1",
            nextId = { prefix -> "$prefix-${seq.next()}" }
        )
        val nestedPara = nestedPage.blocks.single() as DocumentBlock.Paragraph
        assertEquals("bold italic and Overbay", nestedPara.text)
        assertTrue(nestedPara.spans.any { it.kind == RichSpanKind.BOLD && nestedPara.text.substring(it.start, it.end) == "bold italic" })
        assertTrue(nestedPara.spans.any { it.kind == RichSpanKind.ITALIC && nestedPara.text.substring(it.start, it.end) == "italic" })
        assertTrue(nestedPara.spans.any { it.kind == RichSpanKind.BOLD && nestedPara.text.substring(it.start, it.end) == "Overbay" })
        assertTrue(nestedPara.spans.any { it.kind == RichSpanKind.LINK && it.url == "https://overbay.app" })

        val table = reimported.blocks.filterIsInstance<DocumentBlock.Table>().single()
        assertEquals(listOf("OR", "A | B"), table.rows.single())
    }

    @Test
    fun editorInputControllerSplitsMultilinePasteAndShiftsCursorOnHeadingPrefix() {
        var idSeq = 1
        var tokSeq = 1L
        val nextId = { "paste-${idSeq++}" }
        val nextTok = { tokSeq++ }

        val pasted = EditorInputController.editParagraphWithTriggers(
            blocks = listOf(DocumentBlock.Paragraph(id = "p1", text = "")),
            blockId = "p1",
            newText = "line one\nline two\nline three",
            cursorOffset = 28,
            nextId = nextId,
            nextToken = nextTok
        )
        assertEquals(3, pasted.blocks.size)
        assertEquals(listOf("line one", "line two", "line three"), pasted.blocks.map { (it as DocumentBlock.Paragraph).text })
        assertEquals(pasted.blocks.last().id, pasted.activeBlockId)
        assertEquals("line three".length, pasted.focusRequest?.cursorOffset)

        val prefixed = EditorInputController.editParagraphWithTriggers(
            blocks = listOf(DocumentBlock.Paragraph(id = "p2", text = "Existing text")),
            blockId = "p2",
            newText = "## Existing text",
            cursorOffset = 3,
            nextId = nextId,
            nextToken = nextTok
        )
        val h2 = prefixed.blocks.single() as DocumentBlock.Heading
        assertEquals(2, h2.level)
        assertEquals("Existing text", h2.text)
        assertEquals(0, prefixed.focusRequest?.cursorOffset)

        // Code fence prefix waits for trailing space so typed language tags like ```python work
        val typingFence = EditorInputController.editParagraphWithTriggers(
            blocks = listOf(DocumentBlock.Paragraph(id = "p3", text = "")),
            blockId = "p3",
            newText = "```",
            cursorOffset = 3,
            nextId = nextId,
            nextToken = nextTok
        )
        assertTrue(typingFence.blocks.single() is DocumentBlock.Paragraph)

        val typedLang = EditorInputController.editParagraphWithTriggers(
            blocks = typingFence.blocks,
            blockId = "p3",
            newText = "```python ",
            cursorOffset = 10,
            nextId = nextId,
            nextToken = nextTok
        )
        val snippet = typedLang.blocks.single() as DocumentBlock.CodeSnippet
        assertEquals("python", snippet.language)

        // Collapsed cursor on whitespace in a non-empty paragraph does not toggle whole-block bold
        val nonEmpty = DocumentBlock.Paragraph(id = "p4", text = "Hello ")
        val untouched = RichSpanEngine.toggleSelectionSpan(nonEmpty, 6, 6, RichSpanKind.BOLD)
        assertFalse(untouched.isBold)
        assertTrue(untouched.spans.isEmpty())
    }
}
