package app.overbay.allnotes

import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.NoteStorageBackend
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.SlashCommandKind
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemoryNoteStorage : NoteStorageBackend {
    var saved: WorkspaceSnapshot? = null
    override fun loadSnapshot(): WorkspaceSnapshot? = saved
    override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        saved = snapshot
    }
}

class WorkspaceViewModelTest {

    private fun createTestFixture(): Pair<WorkspaceViewModel, NoteRepository> {
        val storage = InMemoryNoteStorage()
        val repo = NoteRepository(storage = storage)
        val vm = WorkspaceViewModel(
            repository = repo,
            coroutineDispatcher = Dispatchers.Unconfined
        )
        return Pair(vm, repo)
    }

    // ========================================================
    // 1. Starter Workspace & Navigation
    // ========================================================

    @Test
    fun starterWorkspaceLoadsWithHierarchyAndBlocks() {
        val (vm, _) = createTestFixture()
        val state = vm.uiState.value
        assertTrue(state.notebooks.isNotEmpty(), "Expected starter notebooks")
        assertTrue(state.sectionsForNotebook.isNotEmpty(), "Expected starter sections")
        assertNotNull(state.activePage, "Expected an active starter page")
        assertTrue(state.activePage!!.blocks.isNotEmpty(), "Expected starter blocks")
    }

    @Test
    fun creatingNotebookSectionAndPageUpdatesHierarchyAndPersists() {
        val (vm, repo) = createTestFixture()

        val nb = repo.createNotebook("Work Vault", "Q4 Planning", SectionAccentKey.TEAL)
        vm.selectNotebook(nb.id)
        assertEquals("Work Vault", vm.uiState.value.activeNotebook?.title)

        val sec = repo.createSection(nb.id, "Sprint Notes", SectionAccentKey.AMBER)
        vm.selectSection(sec.id)
        assertEquals("Sprint Notes", vm.uiState.value.activeSection?.title)

        val page = repo.createPage(sec.id, "Retrospective")
        vm.selectPage(page.id)
        assertEquals("Retrospective", vm.uiState.value.activePage?.title)

        vm.insertChecklistBlock()
        val updatedPage = repo.snapshot.value.pages.first { it.id == page.id }
        assertTrue(updatedPage.blocks.any { it is DocumentBlock.Checklist })
    }

    // ========================================================
    // 2. Search Query Filtering Across Page Titles & Block Contents
    // ========================================================

    @Test
    fun searchFilteringMatchesPageTitle() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!

        repo.createPage(sec.id, "Alpha Architecture Blueprint")
        repo.createPage(sec.id, "Beta Performance Audit")

        vm.setSearchQuery("Architecture")
        val filtered = vm.uiState.value.pagesForSection
        assertEquals(1, filtered.size)
        assertEquals("Alpha Architecture Blueprint", filtered.first().title)

        vm.setSearchQuery("Audit")
        assertEquals(1, vm.uiState.value.pagesForSection.size)
        assertEquals("Beta Performance Audit", vm.uiState.value.pagesForSection.first().title)
    }

    @Test
    fun searchFilteringIsCaseInsensitiveAndTrimsWhitespace() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!
        repo.createPage(sec.id, "Machine Learning Notes")

        vm.setSearchQuery("   MACHINE   ")
        val results = vm.uiState.value.pagesForSection
        assertTrue(results.any { it.title == "Machine Learning Notes" })
    }

    @Test
    fun searchFilteringReturnsEmptyWhenNoMatch() {
        val (vm, _) = createTestFixture()
        vm.setSearchQuery("NonExistentString12345!@#$")
        assertTrue(vm.uiState.value.pagesForSection.isEmpty())
    }

    @Test
    fun searchFilteringEmptyQueryReturnsAllSectionPagesOrderedByPinAndDate() {
        val (vm, repo) = createTestFixture()
        val nb = repo.createNotebook("Sort Vault", "Sorting test", SectionAccentKey.INDIGO)
        vm.selectNotebook(nb.id)
        val sec = repo.createSection(nb.id, "Sort Section", SectionAccentKey.AMBER)
        vm.selectSection(sec.id)

        val p1 = repo.createPage(sec.id, "Page One")
        val p2 = repo.createPage(sec.id, "Page Two")
        repo.togglePagePin(p2.id) // Pin Page Two

        vm.setSearchQuery("")
        val pages = vm.uiState.value.pagesForSection
        assertTrue(pages.size >= 2)
        // Pinned page must come first
        assertEquals(p2.id, pages.first().id)
    }

    @Test
    fun searchFilteringMatchesAllSevenBlockTypes() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!

        val pHeading = repo.createPage(sec.id, "P-Heading").also {
            repo.updatePageBlocks(it.id, listOf(DocumentBlock.Heading("h1", "Quantum Mechanics Research", 1)))
        }
        val pParagraph = repo.createPage(sec.id, "P-Paragraph").also {
            repo.updatePageBlocks(it.id, listOf(DocumentBlock.Paragraph("par1", "Photosynthesis cellular process")))
        }
        val pChecklist = repo.createPage(sec.id, "P-Checklist").also {
            repo.updatePageBlocks(it.id, listOf(
                DocumentBlock.Checklist("chk1", "Grocery List", listOf(ChecklistItem("c1", "Avocado Hass")))
            ))
        }
        val pBullet = repo.createPage(sec.id, "P-Bullet").also {
            repo.updatePageBlocks(it.id, listOf(
                DocumentBlock.BulletList("bl1", false, listOf("Electromagnetism principle"))
            ))
        }
        val pTable = repo.createPage(sec.id, "P-Table").also {
            repo.updatePageBlocks(it.id, listOf(
                DocumentBlock.Table("tbl1", listOf("Metric", "Value"), listOf(listOf("ThroughputGbps", "42")))
            ))
        }
        val pCallout = repo.createPage(sec.id, "P-Callout").also {
            repo.updatePageBlocks(it.id, listOf(
                DocumentBlock.Callout("cal1", "Security Tip", "Rotate JWT secret keys monthly", CalloutTone.IMPORTANT)
            ))
        }
        val pCode = repo.createPage(sec.id, "P-Code").also {
            repo.updatePageBlocks(it.id, listOf(
                DocumentBlock.CodeSnippet("cod1", "rust", "fn compute_fibonacci() -> u64 { 0 }")
            ))
        }

        // Test Heading search
        vm.setSearchQuery("Quantum")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pHeading.id })

        // Test Paragraph search
        vm.setSearchQuery("Photosynthesis")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pParagraph.id })

        // Test Checklist title & item search
        vm.setSearchQuery("Grocery")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pChecklist.id })
        vm.setSearchQuery("Avocado")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pChecklist.id })

        // Test BulletList search
        vm.setSearchQuery("Electromagnetism")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pBullet.id })

        // Test Table header & cell search
        vm.setSearchQuery("Metric")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pTable.id })
        vm.setSearchQuery("ThroughputGbps")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pTable.id })

        // Test Callout title & body search
        vm.setSearchQuery("Security Tip")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pCallout.id })
        vm.setSearchQuery("Rotate JWT")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pCallout.id })

        // Test CodeSnippet search
        vm.setSearchQuery("compute_fibonacci")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == pCode.id })
    }

    // ========================================================
    // 3. Block Additions & Deletion
    // ========================================================

    @Test
    fun insertingAllBlockTypesAppendsBlocksInOrder() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Block Sandbox")
        vm.selectPage(page.id)

        vm.insertHeadingBlock(1)
        vm.insertParagraphBlock()
        vm.insertChecklistBlock()
        vm.insertTableBlock()
        vm.insertCalloutBlock()
        vm.insertCodeBlock()

        val activePage = vm.uiState.value.activePage!!
        val blocks = activePage.blocks

        assertTrue(blocks.any { it is DocumentBlock.Heading })
        assertTrue(blocks.any { it is DocumentBlock.Paragraph })
        assertTrue(blocks.any { it is DocumentBlock.Checklist })
        assertTrue(blocks.any { it is DocumentBlock.Table })
        assertTrue(blocks.any { it is DocumentBlock.Callout })
        assertTrue(blocks.any { it is DocumentBlock.CodeSnippet })
    }

    @Test
    fun deletingBlockRemovesSpecifiedBlockById() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Delete Sandbox")
        vm.selectPage(page.id)

        vm.insertHeadingBlock(2)
        vm.insertParagraphBlock()
        val blocksBefore = vm.uiState.value.activePage!!.blocks
        assertEquals(3, blocksBefore.size) // Starter page has 1 empty paragraph + 2 added

        val blockToDelete = blocksBefore[1].id
        vm.deleteBlock(blockToDelete)

        val blocksAfter = vm.uiState.value.activePage!!.blocks
        assertEquals(2, blocksAfter.size)
        assertFalse(blocksAfter.any { it.id == blockToDelete })
    }

    // ========================================================
    // 4. Heading Level Switching & Clamping
    // ========================================================

    @Test
    fun headingLevelSwitchingClampsBetween1And3() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Heading Test")
        vm.selectPage(page.id)

        vm.insertHeadingBlock(2)
        val headingBlock = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Heading>().first()
        assertEquals(2, headingBlock.level)

        vm.updateHeadingLevel(headingBlock.id, 1)
        var updated = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Heading>().first()
        assertEquals(1, updated.level)

        vm.updateHeadingLevel(headingBlock.id, 3)
        updated = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Heading>().first()
        assertEquals(3, updated.level)

        // Verify clamping below 1
        vm.updateHeadingLevel(headingBlock.id, 0)
        updated = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Heading>().first()
        assertEquals(1, updated.level)

        // Verify clamping above 3
        vm.updateHeadingLevel(headingBlock.id, 5)
        updated = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Heading>().first()
        assertEquals(3, updated.level)
    }

    // ========================================================
    // 5. Checklist Adding & Toggling
    // ========================================================

    @Test
    fun checklistItemAdditionAndToggling() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Checklist Page")
        vm.selectPage(page.id)

        vm.insertChecklistBlock()
        val chkBlock = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Checklist>().first()
        val initialItemCount = chkBlock.items.size

        // Add a new checklist item
        vm.addChecklistItem(chkBlock.id, "Verify SQLDelight schema")
        val updatedChk = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Checklist>().first()
        assertEquals(initialItemCount + 1, updatedChk.items.size)

        val newItem = updatedChk.items.first { it.text == "Verify SQLDelight schema" }
        assertFalse(newItem.isChecked)

        // Toggle checked state
        vm.toggleChecklistItem(chkBlock.id, newItem.id)
        val toggledChk = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Checklist>().first()
        val toggledItem = toggledChk.items.first { it.id == newItem.id }
        assertTrue(toggledItem.isChecked)

        // Toggle back to unchecked
        vm.toggleChecklistItem(chkBlock.id, newItem.id)
        val untoggledChk = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Checklist>().first()
        assertFalse(untoggledChk.items.first { it.id == newItem.id }.isChecked)
    }

    // ========================================================
    // 6. Paragraph Styling (Bold, Italic, Underline, Highlight)
    // ========================================================

    @Test
    fun paragraphStylingTogglesIndependentlyAndCombines() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Styling Page")
        vm.selectPage(page.id)

        vm.insertParagraphBlock()
        val parBlock = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Paragraph>().last()
        assertFalse(parBlock.isBold)
        assertFalse(parBlock.isItalic)
        assertFalse(parBlock.isUnderlined)
        assertFalse(parBlock.isHighlighted)

        // Toggle bold
        vm.toggleParagraphStyle(parBlock.id, "bold")
        var p = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Paragraph>().last()
        assertTrue(p.isBold)
        assertFalse(p.isItalic)

        // Toggle italic (bold remains true)
        vm.toggleParagraphStyle(parBlock.id, "italic")
        p = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Paragraph>().last()
        assertTrue(p.isBold)
        assertTrue(p.isItalic)

        // Toggle underline and highlight
        vm.toggleParagraphStyle(parBlock.id, "underline")
        vm.toggleParagraphStyle(parBlock.id, "highlight")
        p = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Paragraph>().last()
        assertTrue(p.isBold && p.isItalic && p.isUnderlined && p.isHighlighted)

        // Untoggle bold (others remain true)
        vm.toggleParagraphStyle(parBlock.id, "bold")
        p = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Paragraph>().last()
        assertFalse(p.isBold)
        assertTrue(p.isItalic && p.isUnderlined && p.isHighlighted)
    }

    // ========================================================
    // 7. Table Mutations (Cell Update, Add Row, Add Column)
    // ========================================================

    @Test
    fun tableMutationsUpdateCellsAddRowsAndColumns() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Table Page")
        vm.selectPage(page.id)

        vm.insertTableBlock()
        val tbl = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        assertEquals(3, tbl.headers.size)
        assertEquals(2, tbl.rows.size)

        // 1. Update cell at (row=0, col=1)
        vm.updateTableCell(tbl.id, rowIndex = 0, colIndex = 1, text = "Mutated Cell Content")
        var updatedTbl = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        assertEquals("Mutated Cell Content", updatedTbl.rows[0][1])

        // 2. Add row with custom cells
        val newRow = listOf("Item C", "Details C", "Completed")
        vm.addTableRow(tbl.id, newRow)
        updatedTbl = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        assertEquals(3, updatedTbl.rows.size)
        assertEquals(newRow, updatedTbl.rows[2])

        // 3. Add column with header and default cell content
        vm.addTableColumn(tbl.id, headerTitle = "Priority", defaultCellText = "Medium")
        updatedTbl = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        assertEquals(4, updatedTbl.headers.size)
        assertEquals("Priority", updatedTbl.headers[3])
        assertEquals("Medium", updatedTbl.rows[0][3])
        assertEquals("Medium", updatedTbl.rows[1][3])
        assertEquals("Medium", updatedTbl.rows[2][3])
    }

    // ========================================================
    // 8. Text Updates Across Editable Blocks
    // ========================================================

    @Test
    fun textUpdatesModifyHeadingParagraphCalloutAndCode() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Edit Sandbox")
        vm.selectPage(page.id)

        vm.insertHeadingBlock(1)
        vm.insertParagraphBlock()
        vm.insertCalloutBlock()
        vm.insertCodeBlock()

        val p = vm.uiState.value.activePage!!
        val h = p.blocks.filterIsInstance<DocumentBlock.Heading>().first()
        val par = p.blocks.filterIsInstance<DocumentBlock.Paragraph>().last()
        val cal = p.blocks.filterIsInstance<DocumentBlock.Callout>().first()
        val cod = p.blocks.filterIsInstance<DocumentBlock.CodeSnippet>().first()

        vm.updateBlockText(h.id, "New Heading Value")
        vm.updateBlockText(par.id, "New Paragraph Value")
        vm.updateBlockText(cal.id, "New Callout Value")
        vm.updateBlockText(cod.id, "println(\"Hello AllNotes\")")

        val refreshed = vm.uiState.value.activePage!!
        assertEquals("New Heading Value", refreshed.blocks.filterIsInstance<DocumentBlock.Heading>().first().text)
        assertEquals("New Paragraph Value", refreshed.blocks.filterIsInstance<DocumentBlock.Paragraph>().last().text)
        assertEquals("New Callout Value", refreshed.blocks.filterIsInstance<DocumentBlock.Callout>().first().text)
        assertEquals("println(\"Hello AllNotes\")", refreshed.blocks.filterIsInstance<DocumentBlock.CodeSnippet>().first().code)
    }

    // ========================================================
    // 9. Full Hierarchy CRUD, Subpage Indenting & Recency Timestamps
    // ========================================================

    @Test
    fun notebookAndSectionEditAndDeleteAndSubpageIndentingWork() {
        val (vm, _) = createTestFixture()
        vm.dispatch(app.overbay.allnotes.ui.workspace.WorkspaceAction.CreateNotebook("Engineering", "Core", SectionAccentKey.TEAL))
        assertEquals("Engineering", vm.uiState.value.activeNotebook?.title)

        vm.dispatch(app.overbay.allnotes.ui.workspace.WorkspaceAction.UpdateActiveNotebook("Engineering v2", "Updated", SectionAccentKey.VIOLET))
        assertEquals("Engineering v2", vm.uiState.value.activeNotebook?.title)
        assertEquals(SectionAccentKey.VIOLET, vm.uiState.value.activeNotebook?.accent)

        vm.dispatch(app.overbay.allnotes.ui.workspace.WorkspaceAction.CreateSection("API Specs", SectionAccentKey.ROSE))
        assertEquals("API Specs", vm.uiState.value.activeSection?.title)

        vm.dispatch(app.overbay.allnotes.ui.workspace.WorkspaceAction.UpdateActiveSection("REST & Sync Specs", SectionAccentKey.AMBER))
        assertEquals("REST & Sync Specs", vm.uiState.value.activeSection?.title)

        // Create root page and nested subpage
        vm.dispatch(app.overbay.allnotes.ui.workspace.WorkspaceAction.CreatePage("Root Spec Page", asSubpageOfActive = false))
        val rootPageId = vm.uiState.value.activePage!!.id
        assertTrue(vm.uiState.value.activePage!!.updatedAtEpochMs > 0L)

        vm.dispatch(app.overbay.allnotes.ui.workspace.WorkspaceAction.CreatePage("Child Subpage", asSubpageOfActive = true))
        val childPage = vm.uiState.value.activePage!!
        assertEquals(rootPageId, childPage.parentPageId)

        // Promote subpage back to root
        vm.dispatch(app.overbay.allnotes.ui.workspace.WorkspaceAction.TogglePageSubpageIndent(childPage.id))
        assertEquals(null, vm.uiState.value.activePage?.parentPageId)
    }

    // ========================================================
    // 10. Rich Block In-Place Mutations & Reordering
    // ========================================================

    @Test
    fun richBlockInPlaceMutationsAndOrderingWork() {
        val (vm, repo) = createTestFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Rich Mutation Page")
        vm.selectPage(page.id)

        vm.insertChecklistBlock()
        vm.insertBulletListBlock(isNumbered = false)
        vm.insertTableBlock()
        vm.insertCalloutBlock()
        vm.insertCodeBlock()

        val chk = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Checklist>().first()
        vm.updateChecklistTitle(chk.id, "Sprint Tasks")
        val firstChkItem = chk.items.first()
        vm.updateChecklistItemText(chk.id, firstChkItem.id, "Edited Task 1")
        assertEquals("Sprint Tasks", vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Checklist>().first().title)
        assertEquals("Edited Task 1", vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Checklist>().first().items.first().text)

        val bl = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.BulletList>().first()
        vm.toggleBulletListKind(bl.id)
        vm.updateBulletListItem(bl.id, 0, "Updated step 1")
        val updatedBl = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.BulletList>().first()
        assertTrue(updatedBl.isNumbered)
        assertEquals("Updated step 1", updatedBl.items[0])

        val tbl = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        vm.updateTableHeader(tbl.id, 0, "Renamed Header")
        assertEquals("Renamed Header", vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first().headers[0])

        val cal = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Callout>().first()
        vm.updateCalloutTitle(cal.id, "Architecture Alert")
        vm.updateCalloutTone(cal.id, CalloutTone.IMPORTANT)
        val updatedCal = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Callout>().first()
        assertEquals("Architecture Alert", updatedCal.title)
        assertEquals(CalloutTone.IMPORTANT, updatedCal.tone)

        val cod = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.CodeSnippet>().first()
        vm.updateCodeLanguage(cod.id, "python")
        assertEquals("python", vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.CodeSnippet>().first().language)

        // Move code block up
        val blocksBeforeMove = vm.uiState.value.activePage!!.blocks
        val lastId = blocksBeforeMove.last().id
        vm.moveBlock(lastId, -1)
        val blocksAfterMove = vm.uiState.value.activePage!!.blocks
        assertEquals(lastId, blocksAfterMove[blocksAfterMove.lastIndex - 1].id)

        // Inline Enter-key item insertion after active checklist and bullet list items
        val chkBefore = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Checklist>().first()
        vm.dispatch(app.overbay.allnotes.ui.workspace.WorkspaceAction.InsertChecklistItemAfter(chkBefore.id, chkBefore.items.first().id))
        val chkAfter = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Checklist>().first()
        assertEquals(chkBefore.items.size + 1, chkAfter.items.size)
        assertEquals("", chkAfter.items[1].text)

        val blBefore = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.BulletList>().first()
        vm.dispatch(app.overbay.allnotes.ui.workspace.WorkspaceAction.InsertBulletListItemAfter(blBefore.id, 0))
        val blAfter = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.BulletList>().first()
        assertEquals(blBefore.items.size + 1, blAfter.items.size)
        assertEquals("", blAfter.items[1])
    }

    @Test
    fun insertAttachmentBlockPreservesBase64PayloadAndCaptionSeparately() {
        val (vm, _) = createTestFixture()
        val payload = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
        vm.insertAttachmentBlock(
            fileName = "diagram.png",
            mimeType = "image/png",
            sizeBytes = 2048L,
            dataBase64 = payload,
            caption = "Architecture diagram"
        )
        val attachment = vm.uiState.value.activePage!!.blocks
            .filterIsInstance<DocumentBlock.Attachment>()
            .single()
        assertEquals("diagram.png", attachment.fileName)
        assertEquals("image/png", attachment.mimeType)
        assertEquals(2048L, attachment.sizeBytes)
        assertEquals(payload, attachment.dataBase64)
        assertEquals("Architecture diagram", attachment.caption)
    }

    @Test
    fun requestDeletePageOpensConfirmationPromotesSubpagesAndClosesMenus() {
        val (vm, repo) = createTestFixture()
        val secId = vm.uiState.value.activeSection!!.id
        val parent = repo.createPage(secId, "Parent Spec")
        val child = repo.createPage(secId, "Child Spec", parentPageId = parent.id)
        vm.selectPage(parent.id)
        vm.dispatch(WorkspaceAction.SetPageMenuOpen(true))
        vm.dispatch(WorkspaceAction.SetMobileHierarchySheetOpen(true))

        vm.dispatch(WorkspaceAction.RequestDeletePage(parent.id))
        val confirming = vm.uiState.value
        assertEquals(ActiveDialog.CONFIRM_DELETE_PAGE, confirming.activeDialog)
        assertEquals(parent.id, confirming.pendingDeletePageId)
        assertFalse(confirming.isPageMenuOpen)
        assertFalse(confirming.isMobileHierarchySheetOpen)

        vm.dispatch(WorkspaceAction.DeletePage(parent.id))
        val afterDelete = vm.uiState.value
        assertEquals(ActiveDialog.NONE, afterDelete.activeDialog)
        assertNull(afterDelete.pendingDeletePageId)
        assertFalse(afterDelete.pages.any { it.id == parent.id })
        assertNull(afterDelete.pages.first { it.id == child.id }.parentPageId)
    }

    @Test
    fun convertActiveBlockAndClearFormattingPreserveContentAndSupportUndo() {
        val (vm, _) = createTestFixture()
        val para = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Paragraph>().first()
        vm.updateBlockText(para.id, "Sprint Retrospective")
        vm.dispatch(WorkspaceAction.ToggleParagraphStyle(para.id, "bold"))
        vm.dispatch(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.ITALIC, null, para.id, 0, 6))
        vm.dispatch(WorkspaceAction.SetActiveBlock(para.id))

        vm.dispatch(WorkspaceAction.ClearActiveParagraphFormatting)
        val cleared = vm.uiState.value.activePage!!.blocks.first { it.id == para.id } as DocumentBlock.Paragraph
        assertEquals("Sprint Retrospective", cleared.text)
        assertFalse(cleared.isBold)
        assertTrue(cleared.spans.isEmpty())

        vm.dispatch(WorkspaceAction.ConvertActiveBlock(SlashCommandKind.HEADING_2))
        val heading = vm.uiState.value.activePage!!.blocks.first { it.id == para.id } as DocumentBlock.Heading
        assertEquals(2, heading.level)
        assertEquals("Sprint Retrospective", heading.text)

        vm.dispatch(WorkspaceAction.Undo)
        assertTrue(vm.uiState.value.activePage!!.blocks.first { it.id == para.id } is DocumentBlock.Paragraph)

        vm.dispatch(WorkspaceAction.Redo)
        vm.dispatch(WorkspaceAction.ConvertActiveBlock(SlashCommandKind.PARAGRAPH))
        val backToPara = vm.uiState.value.activePage!!.blocks.first { it.id == para.id } as DocumentBlock.Paragraph
        assertEquals("Sprint Retrospective", backToPara.text)
    }

    @Test
    fun uiStateHandlesSubpageCyclesGlobalSearchSnippetsAndSearchFocusTokens() {
        val nb1 = Notebook("nb-1", "Core", "", SectionAccentKey.INDIGO, 0)
        val nb2 = Notebook("nb-2", "Archive", "", SectionAccentKey.EMERALD, 1)
        val sec1 = NoteSection("sec-1", "nb-1", "Current", SectionAccentKey.INDIGO, 0)
        val sec2 = NoteSection("sec-2", "nb-2", "Notes", SectionAccentKey.EMERALD, 0)
        val p1 = NotePage(id = "p-1", sectionId = "sec-1", parentPageId = "p-2", title = "Cycle A", updatedAtEpochMs = 100L)
        val p2 = NotePage(id = "p-2", sectionId = "sec-1", parentPageId = "p-1", title = "Cycle B", updatedAtEpochMs = 90L)
        val target = NotePage(
            id = "p-3",
            sectionId = "sec-2",
            title = "Quarterly Review",
            blocks = listOf(DocumentBlock.Paragraph("b-1", "Discussing offline synchronization latency targets for Q4.")),
            updatedAtEpochMs = 80L
        )
        val state = WorkspaceUiState(
            notebooks = listOf(nb1, nb2),
            sections = listOf(sec1, sec2),
            pages = listOf(p1, p2, target),
            selectedNotebookId = "nb-1",
            selectedSectionId = "sec-1",
            selectedPageId = "p-1",
            searchQuery = "synchronization"
        )
        assertEquals(2, state.copy(searchQuery = "").pagesForSection.size)
        val hit = state.globalSearchResults.single()
        assertEquals("p-3", hit.page.id)
        assertNotNull(hit.matchSnippet)
        assertTrue(hit.matchSnippet.contains("synchronization", ignoreCase = true))

        val (vm, _) = createTestFixture()
        vm.dispatch(WorkspaceAction.RequestSearchFocus)
        val tok = vm.uiState.value.searchFocusRequestToken
        assertTrue(tok > 0L)
        assertTrue(vm.uiState.value.isSidebarOpen)
        assertTrue(vm.uiState.value.isMobileHierarchySheetOpen)
        vm.dispatch(WorkspaceAction.ConsumeSearchFocusRequest(tok))
        assertEquals(0L, vm.uiState.value.searchFocusRequestToken)

        val att = DocumentBlock.Attachment("a-1", "chart.png", "image/png", 10L, "", "")
        vm.dispatch(WorkspaceAction.OpenAttachmentPreview(att))
        assertEquals(att, vm.uiState.value.previewAttachment)
        vm.dispatch(WorkspaceAction.DismissAttachmentPreview)
        assertNull(vm.uiState.value.previewAttachment)
    }

    @Test
    fun createPageInstantFocusesTitleAndDeleteBlockProvidesUndoFeedback() {
        val (vm, _) = createTestFixture()
        vm.dispatch(WorkspaceAction.SetMobileHierarchySheetOpen(true))
        vm.dispatch(WorkspaceAction.CreatePageInstant(asSubpageOfActive = false))

        val createdState = vm.uiState.value
        assertNotNull(createdState.activePage)
        assertEquals("", createdState.activePage!!.title)
        assertFalse(createdState.isMobileHierarchySheetOpen)
        assertTrue(createdState.titleFocusRequestToken > 0L)
        assertNull(createdState.activeBlockId)

        val tok = createdState.titleFocusRequestToken
        vm.dispatch(WorkspaceAction.ConsumeTitleFocusRequest(tok))
        assertEquals(0L, vm.uiState.value.titleFocusRequestToken)

        vm.insertParagraphBlock()
        val targetBlockId = vm.uiState.value.activePage!!.blocks.last().id
        vm.dispatch(WorkspaceAction.DeleteBlock(targetBlockId))

        val afterDelete = vm.uiState.value
        assertEquals("Block removed", afterDelete.statusBannerMessage)
        assertEquals("Undo", afterDelete.statusBannerActionLabel)
        assertEquals(WorkspaceAction.Undo, afterDelete.statusBannerAction)

        vm.dispatch(afterDelete.statusBannerAction!!)
        assertTrue(vm.uiState.value.activePage!!.blocks.any { it.id == targetBlockId })
    }
}
