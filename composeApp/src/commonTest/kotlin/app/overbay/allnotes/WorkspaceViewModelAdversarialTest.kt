package app.overbay.allnotes

import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.NoteStorageBackend
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemoryAdversarialStorage : NoteStorageBackend {
    var saved: WorkspaceSnapshot? = null
    override fun loadSnapshot(): WorkspaceSnapshot? = saved
    override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        saved = snapshot
    }
}

class WorkspaceViewModelAdversarialTest {

    private fun createFixture(): Pair<WorkspaceViewModel, NoteRepository> {
        val storage = InMemoryAdversarialStorage()
        val repo = NoteRepository(storage = storage)
        val vm = WorkspaceViewModel(
            repository = repo,
            coroutineDispatcher = Dispatchers.Unconfined
        )
        return Pair(vm, repo)
    }

    // ========================================================
    // 1. Rapid Sequential Block Mutations (150 Additions, Updates, Deletions)
    // ========================================================

    @Test
    fun rapidSequentialBlockMutations_150Blocks_addUpdateDeleteStress() {
        val (vm, repo) = createFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "High Volume Stress Page")
        vm.selectPage(page.id)

        // 1. Sequentially add 150 blocks of varied types
        for (i in 1..150) {
            when (i % 7) {
                0 -> vm.insertHeadingBlock(level = (i % 3) + 1)
                1 -> vm.insertParagraphBlock()
                2 -> vm.insertChecklistBlock()
                3 -> vm.insertTableBlock()
                4 -> vm.insertCalloutBlock()
                5 -> vm.insertCodeBlock()
                6 -> vm.insertBulletListBlock(isNumbered = (i % 2 == 0))
            }
        }

        val active = vm.uiState.value.activePage!!
        // Starter page has 1 empty paragraph + 150 added blocks = 151
        assertEquals(151, active.blocks.size)

        // Verify all IDs are unique
        val uniqueIds = active.blocks.map { it.id }.toSet()
        assertEquals(151, uniqueIds.size, "All block IDs must be distinct")

        // 2. Update text across all applicable blocks
        active.blocks.forEachIndexed { idx, block ->
            vm.updateBlockText(block.id, "Mutated content $idx")
        }

        val refreshed = vm.uiState.value.activePage!!
        refreshed.blocks.forEachIndexed { idx, block ->
            when (block) {
                is DocumentBlock.Heading -> assertEquals("Mutated content $idx", block.text)
                is DocumentBlock.Paragraph -> assertEquals("Mutated content $idx", block.text)
                is DocumentBlock.Callout -> assertEquals("Mutated content $idx", block.text)
                is DocumentBlock.CodeSnippet -> assertEquals("Mutated content $idx", block.code)
                else -> Unit
            }
        }

        // 3. Delete 75 blocks (every odd index)
        val toDelete = refreshed.blocks.filterIndexed { index, _ -> index % 2 == 1 }.map { it.id }
        assertEquals(75, toDelete.size)
        toDelete.forEach { blockId ->
            vm.deleteBlock(blockId)
        }

        val afterDeletes = vm.uiState.value.activePage!!
        assertEquals(76, afterDeletes.blocks.size)
        toDelete.forEach { deletedId ->
            assertFalse(afterDeletes.blocks.any { it.id == deletedId }, "Deleted block must not remain")
        }

        // 4. Delete all remaining blocks down to 0
        val remainingIds = afterDeletes.blocks.map { it.id }
        remainingIds.forEach { id ->
            vm.deleteBlock(id)
        }
        val emptyPage = vm.uiState.value.activePage!!
        assertEquals(0, emptyPage.blocks.size)

        // 5. Append fresh blocks onto emptied page
        vm.insertHeadingBlock(1)
        vm.insertParagraphBlock()
        val rehydrated = vm.uiState.value.activePage!!
        assertEquals(2, rehydrated.blocks.size)
        assertTrue(rehydrated.blocks[0] is DocumentBlock.Heading)
        assertTrue(rehydrated.blocks[1] is DocumentBlock.Paragraph)
    }

    // ========================================================
    // 2. Search Query Adversarial Boundaries & Regex Injection Safety
    // ========================================================

    @Test
    fun searchQuery_adversarialBoundaries_whitespaceAndNullAndLongPayloads() {
        val (vm, repo) = createFixture()
        val sec = vm.uiState.value.activeSection!!

        repo.createPage(sec.id, "Alpha Normal Page")
        val needlePage = repo.createPage(sec.id, "Needle Special Chars").also {
            repo.updatePageBlocks(it.id, listOf(
                DocumentBlock.Paragraph("par-special", "Regex test: (.*) [a-z]+ \\d{3} ?+*{}^$()[]|\\ \u0000 🚀")
            ))
        }

        // Whitespace variations must not filter out pages
        listOf("", "   ", "\t\t", "\n\r", "   \t\n  ").forEach { blankQuery ->
            vm.setSearchQuery(blankQuery)
            assertTrue(vm.uiState.value.pagesForSection.size >= 2, "Whitespace query '$blankQuery' must return all pages")
        }

        // Regex injection payloads must NOT throw PatternSyntaxException (literal substring match required)
        val regexProbes = listOf(
            "(.*)",
            "[a-zA-Z0-9]+",
            "\\d{3}-\\d{4}",
            "?+*{}^$()[]|\\",
            "(?i)",
            "(?<=foo)bar",
            "[^abc]",
            "\\bword\\b",
            "(((((((((((",
            ")))))))))))"
        )

        regexProbes.forEach { probe ->
            vm.setSearchQuery(probe)
            // Call must complete safely without exception
            val results = vm.uiState.value.pagesForSection
            assertNotNull(results)
        }

        // Exact literal match on special characters
        vm.setSearchQuery("?+*{}^$()[]|\\")
        val matched = vm.uiState.value.pagesForSection
        assertEquals(1, matched.size)
        assertEquals(needlePage.id, matched.first().id)

        // Unicode and emoji search
        vm.setSearchQuery("🚀")
        assertTrue(vm.uiState.value.pagesForSection.any { it.id == needlePage.id })

        // 10,000 char long query must not crash or hang
        val massiveQuery = "Z".repeat(10000)
        vm.setSearchQuery(massiveQuery)
        assertTrue(vm.uiState.value.pagesForSection.isEmpty())
    }

    // ========================================================
    // 3. Search Query Deep Content Matching Across Nested Blocks
    // ========================================================

    @Test
    fun searchQuery_deepBlockContentMatching_nestedTableAndListCells() {
        val (vm, repo) = createFixture()
        val sec = vm.uiState.value.activeSection!!

        val pTable = repo.createPage(sec.id, "Table Data Page").also {
            repo.updatePageBlocks(it.id, listOf(
                DocumentBlock.Table(
                    id = "tbl-deep",
                    headers = listOf("H1", "H2", "H3"),
                    rows = listOf(
                        listOf("val1", "val2", "val3"),
                        listOf("val4", "val5", "DEEP_TABLE_TARGET_CELL")
                    )
                )
            ))
        }

        val pBullet = repo.createPage(sec.id, "Bullet Data Page").also {
            repo.updatePageBlocks(it.id, listOf(
                DocumentBlock.BulletList(
                    id = "bl-deep",
                    isNumbered = true,
                    items = listOf("Intro item", "NESTED_BULLET_SECRET_VALUE", "Ending item")
                )
            ))
        }

        // Deep table cell search
        vm.setSearchQuery("deep_table_target")
        assertEquals(1, vm.uiState.value.pagesForSection.size)
        assertEquals(pTable.id, vm.uiState.value.pagesForSection.first().id)

        // Nested bullet search
        vm.setSearchQuery("bullet_secret")
        assertEquals(1, vm.uiState.value.pagesForSection.size)
        assertEquals(pBullet.id, vm.uiState.value.pagesForSection.first().id)
    }

    // ========================================================
    // 4. Heading Level Adversarial Clamping & Target Safety
    // ========================================================

    @Test
    fun headingLevel_adversarialClamping_extremeValuesAndNonHeadingTargets() {
        val (vm, repo) = createFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Heading Adversarial Page")
        vm.selectPage(page.id)

        vm.insertHeadingBlock(level = 2)
        vm.insertParagraphBlock()
        val hBlock = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Heading>().first()
        val parBlock = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Paragraph>().last()

        // Negative and MinValue clamping
        listOf(Int.MIN_VALUE, -9999, -1, 0).forEach { badLevel ->
            vm.updateHeadingLevel(hBlock.id, badLevel)
            val level = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Heading>().first().level
            assertEquals(1, level, "Level $badLevel must clamp to 1")
        }

        // Above max and MaxValue clamping
        listOf(4, 5, 100, Int.MAX_VALUE).forEach { highLevel ->
            vm.updateHeadingLevel(hBlock.id, highLevel)
            val level = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Heading>().first().level
            assertEquals(3, level, "Level $highLevel must clamp to 3")
        }

        // Mutating a non-heading block must safely no-op
        vm.updateHeadingLevel(parBlock.id, 1)
        val refreshedPar = vm.uiState.value.activePage!!.blocks.first { it.id == parBlock.id }
        assertTrue(refreshedPar is DocumentBlock.Paragraph, "Target must remain a Paragraph")

        // Mutating non-existent block must safely no-op
        vm.updateHeadingLevel("fake-block-id-999", 2)
        assertEquals(3, vm.uiState.value.activePage!!.blocks.size)
    }

    // ========================================================
    // 5. Table Mutations: Adversarial Indices & Special Characters
    // ========================================================

    @Test
    fun tableMutations_adversarialIndicesAndSpecialCharacters() {
        val (vm, repo) = createFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Table Adversarial Page")
        vm.selectPage(page.id)

        vm.insertTableBlock()
        val tbl = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        val origRows = tbl.rows

        // Out-of-bounds row and col indices must not throw IndexOutOfBoundsException
        val badIndices = listOf(
            Pair(-1, 0),
            Pair(0, -1),
            Pair(-999, -999),
            Pair(100, 0),
            Pair(0, 100),
            Pair(Int.MAX_VALUE, 0),
            Pair(0, Int.MAX_VALUE),
            Pair(Int.MIN_VALUE, Int.MIN_VALUE)
        )

        badIndices.forEach { (r, c) ->
            vm.updateTableCell(tbl.id, rowIndex = r, colIndex = c, text = "Should Not Throw")
        }

        // Verify original cells remain intact
        val afterBadUpdates = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        assertEquals(origRows, afterBadUpdates.rows)

        // Mutating with non-existent blockId must safely no-op
        vm.updateTableCell("bogus-tbl-id", 0, 0, "No-op text")

        // Hostile text in valid cell (20KB, newlines, quotes, unicode emojis)
        val hostilePayload = "Line1\r\nLine2 \"quoted\" \u0000 🌟 \uD83D\uDE80 " + "A".repeat(20000)
        vm.updateTableCell(tbl.id, rowIndex = 0, colIndex = 0, text = hostilePayload)
        val updatedTbl = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        assertEquals(hostilePayload, updatedTbl.rows[0][0])

        // Add row with empty rowData generates default empty cells matching header size
        vm.addTableRow(tbl.id)
        val afterDefaultRow = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        assertEquals(origRows.size + 1, afterDefaultRow.rows.size)
        assertEquals(afterDefaultRow.headers.size, afterDefaultRow.rows.last().size)
        assertTrue(afterDefaultRow.rows.last().all { it.isEmpty() })

        // Add column expands headers and all rows
        vm.addTableColumn(tbl.id, headerTitle = "Priority 🔥", defaultCellText = "Low")
        val afterCol = vm.uiState.value.activePage!!.blocks.filterIsInstance<DocumentBlock.Table>().first()
        assertEquals(4, afterCol.headers.size)
        assertEquals("Priority 🔥", afterCol.headers[3])
        afterCol.rows.forEach { row ->
            assertEquals(4, row.size)
            assertEquals("Low", row[3])
        }
    }

    // ========================================================
    // 6. Checklist & BulletList Edge Case Blank Rejection
    // ========================================================

    @Test
    fun checklistAndBulletList_blankItemsRejectedAndInvalidIdsSafe() {
        val (vm, repo) = createFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "List Sanitization Page")
        vm.selectPage(page.id)

        vm.insertChecklistBlock()
        vm.insertBulletListBlock()
        val p = vm.uiState.value.activePage!!
        val chk = p.blocks.filterIsInstance<DocumentBlock.Checklist>().first()
        val bl = p.blocks.filterIsInstance<DocumentBlock.BulletList>().first()

        val initialChkSize = chk.items.size
        val initialBlSize = bl.items.size

        // Blank or whitespace-only inputs must be rejected
        listOf("", "   ", "\t\t", "\n\r").forEach { blank ->
            vm.addChecklistItem(chk.id, blank)
            vm.addBulletListItem(bl.id, blank)
        }

        val afterBlanks = vm.uiState.value.activePage!!
        assertEquals(initialChkSize, afterBlanks.blocks.filterIsInstance<DocumentBlock.Checklist>().first().items.size)
        assertEquals(initialBlSize, afterBlanks.blocks.filterIsInstance<DocumentBlock.BulletList>().first().items.size)

        // Invalid IDs must safely no-op
        vm.addChecklistItem("fake-id", "Valid Text")
        vm.addBulletListItem("fake-id", "Valid Text")
        vm.toggleChecklistItem(chk.id, "non-existent-item-id")
    }

    // ========================================================
    // 7. Hierarchy State Synchronization & Orphan Resilience
    // ========================================================

    @Test
    fun hierarchyStateSync_singlePageDeletionAndOrphanRecovery() {
        val (vm, repo) = createFixture()
        val nb = repo.createNotebook("Solo Vault", "Testing solo page", SectionAccentKey.VIOLET)
        vm.selectNotebook(nb.id)
        val sec = repo.createSection(nb.id, "Solo Section", SectionAccentKey.VIOLET)
        vm.selectSection(sec.id)

        // Solo Section has 1 default untitled page
        assertNotNull(vm.uiState.value.activePage)
        val initialPageId = vm.uiState.value.activePage!!.id

        // Delete the only page in the section
        vm.deleteActivePage()

        // Page should now be empty and activePage null
        val stateAfterDelete = vm.uiState.value.activePage
        assertNull(stateAfterDelete, "Active page must be null when all pages in section are deleted")
        assertEquals("", vm.uiState.value.selectedPageId)

        // Block operations while activePage is null must safely no-op without crashing
        vm.insertParagraphBlock()
        vm.insertHeadingBlock()
        vm.insertTableBlock()
        vm.deleteBlock("any-id")
        vm.updateHeadingLevel("any-id", 2)
        vm.updateTableCell("any-id", 0, 0, "test")

        // Selecting non-existent notebook/section/page falls back safely
        vm.selectNotebook("non-existent-nb-id")
        assertNotNull(vm.uiState.value.activeNotebook)

        vm.selectSection("non-existent-sec-id")
        assertNotNull(vm.uiState.value.activeSection)

        vm.selectPage("non-existent-page-id")
        assertNotNull(vm.uiState.value.activePage)
    }

    // ========================================================
    // 8. Isolated Minimal Reproduction: vm.createPage State Desync
    // ========================================================

    @Test
    fun createPage_failsToSynchronizeUiStatePages_minimalRepro() {
        val (vm, _) = createFixture()
        vm.createPage("Isolated Bug Repro Page")
        val state = vm.uiState.value

        // In uiState, pages must contain the newly created page
        assertTrue(
            state.pages.any { it.title == "Isolated Bug Repro Page" },
            "uiState.pages is stale! createPage() failed to call syncStateFromRepository()"
        )
        assertNotNull(state.activePage, "Active page must not be null after createPage")
        assertEquals("Isolated Bug Repro Page", state.activePage?.title)
        assertEquals(state.selectedPageId, state.activePage?.id)
    }

    // ========================================================
    // 8. Concurrent Block Operations & Thread Safety
    // ========================================================

    @Test
    fun concurrentBlockOperations_synchronousStateIntegrity() = runBlocking(Dispatchers.Default) {
        val (vm, repo) = createFixture()
        val sec = vm.uiState.value.activeSection!!
        val page = repo.createPage(sec.id, "Concurrent Stress Page")
        vm.selectPage(page.id)

        // Launch 50 concurrent coroutines performing operations
        val jobs = (1..50).map { i ->
            async {
                when (i % 5) {
                    0 -> vm.insertParagraphBlock()
                    1 -> vm.insertHeadingBlock(level = (i % 3) + 1)
                    2 -> vm.toggleSidebar()
                    3 -> vm.setSearchQuery("query_$i")
                    4 -> vm.setSearchQuery("")
                }
            }
        }
        jobs.awaitAll()

        // Clear any search filter that was applied during concurrent execution
        vm.setSearchQuery("")

        // Verify state is non-null and internally coherent
        val finalState = vm.uiState.value
        assertNotNull(finalState.activeNotebook)
        assertNotNull(finalState.activeSection)
        assertNotNull(finalState.activePage)
        assertTrue(finalState.activePage!!.blocks.isNotEmpty())
    }
}
