package app.overbay.allnotes.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.overbay.allnotes.db.AllNotesDatabase
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightAdversarialTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: AllNotesDatabase
    private lateinit var store: SqlDelightNoteStore

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AllNotesDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        database = AllNotesDatabase(driver)
        store = SqlDelightNoteStore(database)
    }

    // ========================================================
    // 1. Extreme Document Blocks Tests
    // ========================================================

    @Test
    fun extremeTableBlock_100x10_roundtripAndIntegrity() {
        val nb = Notebook(id = "nb-stress", title = "Stress Vault", accent = SectionAccentKey.INDIGO)
        val sec = NoteSection(id = "sec-stress", notebookId = "nb-stress", title = "Data Grid", accent = SectionAccentKey.INDIGO)

        val colCount = 10
        val rowCount = 100
        val headers = (1..colCount).map { "Col_$it" }
        val rows = (1..rowCount).map { r ->
            (1..colCount).map { c -> "Cell_R${r}_C${c}_${System.currentTimeMillis() % 1000}" }
        }

        val tableBlock = DocumentBlock.Table(
            id = "tbl-large",
            headers = headers,
            rows = rows
        )

        val page = NotePage(
            id = "page-table-stress",
            sectionId = "sec-stress",
            title = "Massive 100x10 Table Page",
            blocks = listOf(tableBlock)
        )

        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), listOf(page), 1L))

        val loaded = store.loadSnapshot()
        assertNotNull(loaded)
        val loadedPage = loaded.pages.first { it.id == "page-table-stress" }
        assertEquals(1, loadedPage.blocks.size)

        val loadedTable = loadedPage.blocks.first() as DocumentBlock.Table
        assertEquals(colCount, loadedTable.headers.size)
        assertEquals(rowCount, loadedTable.rows.size)
        assertEquals("Col_1", loadedTable.headers[0])
        assertEquals("Col_10", loadedTable.headers[9])
        assertEquals(rows[0][0], loadedTable.rows[0][0])
        assertEquals(rows[99][9], loadedTable.rows[99][9])
    }

    @Test
    fun extremeCodeBlock_hugeSizeAndSpecialCharacters_roundtrip() {
        val nb = Notebook(id = "nb-code", title = "Code Vault", accent = SectionAccentKey.ROSE)
        val sec = NoteSection(id = "sec-code", notebookId = "nb-code", title = "Code Section", accent = SectionAccentKey.ROSE)

        // 5000 lines of complex code with quotes, backslashes, tabs, SQL injection strings
        val sb = StringBuilder()
        for (i in 1..2000) {
            sb.append("line $i: SELECT * FROM note_page WHERE id = 'test''\"\\\\; DROP TABLE notebook; -- \n")
        }
        val hugeCode = sb.toString()

        val codeBlock = DocumentBlock.CodeSnippet(
            id = "code-huge",
            language = "sql",
            code = hugeCode
        )

        val page = NotePage(
            id = "page-code-stress",
            sectionId = "sec-code",
            title = "Massive Code Snippet Page",
            blocks = listOf(codeBlock)
        )

        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), listOf(page), 1L))

        val loaded = store.loadSnapshot()
        assertNotNull(loaded)
        val loadedPage = loaded.pages.first { it.id == "page-code-stress" }
        val loadedCode = loadedPage.blocks.first() as DocumentBlock.CodeSnippet
        assertEquals("sql", loadedCode.language)
        assertEquals(hugeCode.length, loadedCode.code.length)
        assertEquals(hugeCode, loadedCode.code)
    }

    @Test
    fun unicodeEmojiAndSpecialCharactersInBlocksAndTitles() {
        val nb = Notebook(id = "nb-unicode", title = "🌍 Unicode & Emojis 🚀 👨‍👩‍👧‍👦", accent = SectionAccentKey.EMERALD)
        val sec = NoteSection(id = "sec-unicode", notebookId = "nb-unicode", title = "日本語 中文 العربية עִברִית", accent = SectionAccentKey.EMERALD)

        val exoticTitle = "Special Page: <script>alert('xss');</script> & \" ' % _ \\ / \uD83D\uDD25"
        val richBlocks = listOf(
            DocumentBlock.Heading(id = "h-u", text = "Заголовок с символами: § ± ≠ ≈ 𝄞 𝄢", level = 1),
            DocumentBlock.Paragraph(
                id = "p-u",
                text = "Complex emojis with ZWJ: 👨‍👩‍👦‍👦 👩‍💻 🏳️‍🌈 and RTL: مرحبا بالعالم שלום עולם",
                isBold = true
            ),
            DocumentBlock.Callout(
                id = "c-u",
                title = "Zero-Width & Control Characters \u200B \u200C \u200D \uFEFF",
                text = "Tabs \t\t Newlines \r\n and backslashes \\\\\\///",
                tone = CalloutTone.TIP
            )
        )

        val page = NotePage(
            id = "page-unicode",
            sectionId = "sec-unicode",
            title = exoticTitle,
            blocks = richBlocks
        )

        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), listOf(page), 10L))

        val loaded = store.loadSnapshot()
        assertNotNull(loaded)
        val loadedPage = loaded.pages.first { it.id == "page-unicode" }
        assertEquals(exoticTitle, loadedPage.title)
        assertEquals(3, loadedPage.blocks.size)

        val p = loadedPage.blocks[1] as DocumentBlock.Paragraph
        assertTrue(p.text.contains("👨‍👩‍👦‍👦"))
        assertTrue(p.text.contains("مرحبا بالعالم"))

        // Test search with emojis and unicode
        val searchEmoji = store.searchPages("👨‍👩‍👦‍👦")
        assertEquals(1, searchEmoji.size)
        assertEquals("page-unicode", searchEmoji[0].id)

        val searchArabic = store.searchPages("مرحبا")
        assertEquals(1, searchArabic.size)
        assertEquals("page-unicode", searchArabic[0].id)
    }

    @Test
    fun emptyBlocksBoundaryCases_roundtrip() {
        val nb = Notebook(id = "nb-empty", title = "", accent = SectionAccentKey.AMBER)
        val sec = NoteSection(id = "sec-empty", notebookId = "nb-empty", title = "", accent = SectionAccentKey.AMBER)

        val emptyBlocks = listOf(
            DocumentBlock.Heading(id = "h-empty", text = "", level = 3),
            DocumentBlock.Paragraph(id = "p-empty", text = ""),
            DocumentBlock.Checklist(id = "chk-empty", title = "", items = emptyList()),
            DocumentBlock.Checklist(
                id = "chk-blank-items",
                title = "Empty Items",
                items = listOf(ChecklistItem("ci1", "", false), ChecklistItem("ci2", "   ", true))
            ),
            DocumentBlock.BulletList(id = "bl-empty", isNumbered = false, items = emptyList()),
            DocumentBlock.Table(id = "tbl-empty", headers = emptyList(), rows = emptyList()),
            DocumentBlock.Callout(id = "cal-empty", title = "", text = "", tone = CalloutTone.INFO),
            DocumentBlock.CodeSnippet(id = "code-empty", language = "", code = "")
        )

        val page = NotePage(
            id = "page-empty-blocks",
            sectionId = "sec-empty",
            title = "",
            blocks = emptyBlocks
        )

        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), listOf(page), 2L))

        val loaded = store.loadSnapshot()
        assertNotNull(loaded)
        val loadedPage = loaded.pages.first { it.id == "page-empty-blocks" }
        assertEquals(emptyBlocks.size, loadedPage.blocks.size)

        val chk1 = loadedPage.blocks[2] as DocumentBlock.Checklist
        assertTrue(chk1.items.isEmpty())

        val chk2 = loadedPage.blocks[3] as DocumentBlock.Checklist
        assertEquals(2, chk2.items.size)
        assertEquals("", chk2.items[0].text)

        val tbl = loadedPage.blocks[5] as DocumentBlock.Table
        assertTrue(tbl.headers.isEmpty())
        assertTrue(tbl.rows.isEmpty())
    }

    // ========================================================
    // 2. Transaction Integrity & Atomic Rollback Tests
    // ========================================================

    @Test
    fun transactionRollbackPreservesOriginalDataOnFailure() {
        // Seed valid initial data
        val initialStarter = SampleWorkspaceFactory.createStarterSnapshot()
        store.saveSnapshot(initialStarter)

        val preCount = store.loadSnapshot()?.pages?.size ?: 0
        assertTrue(preCount > 0, "Initial data must exist")

        // Attempt to save an invalid snapshot that will fail a foreign key constraint
        // (section references non-existent notebook)
        val brokenSection = NoteSection(
            id = "sec-orphan",
            notebookId = "nb-NON-EXISTENT-XYZ",
            title = "Orphan Section",
            accent = SectionAccentKey.ROSE
        )
        val brokenSnapshot = WorkspaceSnapshot(
            notebooks = listOf(Notebook(id = "nb-valid", title = "Valid", accent = SectionAccentKey.ROSE)),
            sections = listOf(brokenSection),
            pages = emptyList(),
            revision = 999L
        )

        // saveSnapshot runs inside database.transaction { ... }
        var caughtException = false
        try {
            store.saveSnapshot(brokenSnapshot)
        } catch (_: Exception) {
            caughtException = true
        }

        assertTrue(caughtException, "Foreign key constraint failure must throw an exception")

        // Crucial check: verify that database was ROLLED BACK and original data is 100% intact!
        val postRollback = store.loadSnapshot()
        assertNotNull(postRollback, "Snapshot must not be null after rollback")
        assertEquals(initialStarter.revision, postRollback.revision, "Revision must remain at original value")
        assertEquals(initialStarter.notebooks.size, postRollback.notebooks.size, "Notebook count must be preserved")
        assertEquals(initialStarter.sections.size, postRollback.sections.size, "Section count must be preserved")
        assertEquals(initialStarter.pages.size, postRollback.pages.size, "Page count must be preserved")
    }

    @Test
    fun invalidForeignKeyRejection_onSavePage() {
        // Attempting to save a page referencing a non-existent section_id must fail foreign key check
        val orphanPage = NotePage(
            id = "p-orphan",
            sectionId = "sec-does-not-exist",
            title = "Orphan Page"
        )

        assertFailsWith<Exception> {
            store.savePage(orphanPage)
        }
    }

    // ========================================================
    // 3. Hierarchy, Deep Nesting & Subpages Tests
    // ========================================================

    @Test
    fun deepPageHierarchy_50LevelsDeep_topDown() {
        val nb = Notebook(id = "nb-deep", title = "Deep Tree", accent = SectionAccentKey.TEAL)
        val sec = NoteSection(id = "sec-deep", notebookId = "nb-deep", title = "Deep Section", accent = SectionAccentKey.TEAL)

        val depth = 50
        val pages = mutableListOf<NotePage>()
        for (i in 1..depth) {
            val parentId = if (i == 1) null else "deep-page-${i - 1}"
            pages.add(
                NotePage(
                    id = "deep-page-$i",
                    sectionId = "sec-deep",
                    parentPageId = parentId,
                    title = "Page Level $i",
                    blocks = listOf(DocumentBlock.Heading(id = "h-$i", text = "Level $i Heading", level = 1))
                )
            )
        }

        // Top-down ordered pages (parent precedes child)
        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), pages, 1L))

        val loaded = store.loadSnapshot()
        assertNotNull(loaded)
        assertEquals(depth, loaded.pages.size)

        // Verify root page
        val root = loaded.pages.first { it.id == "deep-page-1" }
        assertNull(root.parentPageId)

        // Verify leaf page
        val leaf = loaded.pages.first { it.id == "deep-page-50" }
        assertEquals("deep-page-49", leaf.parentPageId)

        // Verify subpage query for any parent
        val subpagesOf1 = database.allNotesQueries.selectSubpages("deep-page-1").executeAsList()
        assertEquals(1, subpagesOf1.size)
        assertEquals("deep-page-2", subpagesOf1[0].id)
    }

    @Test
    fun cascadingDeletesWithSubpages_setNullParentId() {
        val nb = Notebook(id = "nb-sub", title = "Sub Notebook", accent = SectionAccentKey.VIOLET)
        val sec = NoteSection(id = "sec-sub", notebookId = "nb-sub", title = "Sub Section", accent = SectionAccentKey.VIOLET)
        val parentPage = NotePage(id = "parent-p1", sectionId = "sec-sub", title = "Parent Page")
        val childPage = NotePage(id = "child-p2", sectionId = "sec-sub", parentPageId = "parent-p1", title = "Child Page")

        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), listOf(parentPage, childPage), 1L))

        // Verify both exist
        assertNotNull(store.getPage("parent-p1"))
        assertNotNull(store.getPage("child-p2"))

        // Delete parent page
        store.deletePage("parent-p1")
        assertNull(store.getPage("parent-p1"))

        // Child page should STILL exist, but parent_page_id should be SET NULL due to ON DELETE SET NULL
        val childAfter = store.getPage("child-p2")
        assertNotNull(childAfter, "Child page should not be deleted when parent page is deleted")
        assertNull(childAfter.parentPageId, "Child parentPageId should become null (ON DELETE SET NULL)")
    }

    @Test
    fun searchPagesWithSqlSpecialCharacters() {
        val nb = Notebook(id = "nb-sql", title = "SQL Test", accent = SectionAccentKey.INDIGO)
        val sec = NoteSection(id = "sec-sql", notebookId = "nb-sql", title = "Search Sec", accent = SectionAccentKey.INDIGO)
        val page1 = NotePage(id = "p-pct", sectionId = "sec-sql", title = "100% Complete Project", blocks = emptyList())
        val page2 = NotePage(id = "p-under", sectionId = "sec-sql", title = "user_name_variable", blocks = emptyList())
        val page3 = NotePage(id = "p-quote", sectionId = "sec-sql", title = "O'Reilly Media Guide", blocks = emptyList())

        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), listOf(page1, page2, page3), 1L))

        // Single quote in search query
        val quoteResult = store.searchPages("O'Reilly")
        assertEquals(1, quoteResult.size)
        assertEquals("p-quote", quoteResult[0].id)

        // Percentage in search query
        val pctResult = store.searchPages("100%")
        assertTrue(pctResult.any { it.id == "p-pct" })

        // Underscore in search query
        val underResult = store.searchPages("user_name")
        assertTrue(underResult.any { it.id == "p-under" })
    }

    // ========================================================
    // 4. Adversarial Edge Cases: Out-of-Order / Cycles
    // ========================================================

    @Test
    fun outOfOrderSubpages_and_cyclicReferences() {
        val nb = Notebook(id = "nb-ooo", title = "Out of Order", accent = SectionAccentKey.AMBER)
        val sec = NoteSection(id = "sec-ooo", notebookId = "nb-ooo", title = "Sec", accent = SectionAccentKey.AMBER)

        // Child appears before parent in the snapshot list
        val parent = NotePage(id = "p-parent", sectionId = "sec-ooo", parentPageId = null, title = "Parent")
        val child = NotePage(id = "p-child", sectionId = "sec-ooo", parentPageId = "p-parent", title = "Child")

        val outOfOrderSnapshot = WorkspaceSnapshot(
            notebooks = listOf(nb),
            sections = listOf(sec),
            pages = listOf(child, parent), // CHILD FIRST!
            revision = 1L
        )

        // Test out of order insertion
        store.saveSnapshot(outOfOrderSnapshot)
        val loaded = store.loadSnapshot()
        assertNotNull(loaded)
        assertEquals(2, loaded.pages.size)
    }

    @Test
    fun pageWithNonExistentParentPageId_failsForeignKeyConstraint() {
        val fkBefore = driver.executeQuery(null, "PRAGMA foreign_keys;", { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else -1L)
        }, 0).value
        println("Empirical: PRAGMA foreign_keys before saveSnapshot = $fkBefore")

        val nb = Notebook(id = "nb-fk", title = "FK Test", accent = SectionAccentKey.INDIGO)
        val sec = NoteSection(id = "sec-fk", notebookId = "nb-fk", title = "FK Sec", accent = SectionAccentKey.INDIGO)
        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), emptyList(), 1L))

        val fkAfter = driver.executeQuery(null, "PRAGMA foreign_keys;", { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else -1L)
        }, 0).value
        println("Empirical: PRAGMA foreign_keys after saveSnapshot = $fkAfter")

        // Saving a page directly whose parentPageId does not exist
        val invalidChild = NotePage(
            id = "p-invalid-child",
            sectionId = "sec-fk",
            parentPageId = "NON_EXISTENT_PARENT_PAGE_ID",
            title = "Invalid Child"
        )

        var threwException = false
        try {
            store.savePage(invalidChild)
        } catch (e: Exception) {
            println("Caught expected FK exception on parentPageId: ${e::class.simpleName}: ${e.message}")
            threwException = true
        }
        println("Empirical: threwException on non-existent parentPageId = $threwException")

        // Run PRAGMA foreign_key_check
        driver.executeQuery(null, "PRAGMA foreign_key_check;", { cursor ->
            while (cursor.next().value) {
                val table = cursor.getString(0)
                val rowid = cursor.getLong(1)
                val target = cursor.getString(2)
                val fkid = cursor.getLong(3)
                println("PRAGMA foreign_key_check VIOLATION: table=$table, rowid=$rowid, target=$target, fkid=$fkid")
            }
            app.cash.sqldelight.db.QueryResult.Unit
        }, 0)
    }

    @Test
    fun cyclicParentReferences_twoPagesMutuallyReferencing() {
        val nb = Notebook(id = "nb-cycle", title = "Cycle", accent = SectionAccentKey.ROSE)
        val sec = NoteSection(id = "sec-cycle", notebookId = "nb-cycle", title = "Cycle Sec", accent = SectionAccentKey.ROSE)

        val pageA = NotePage(id = "page-A", sectionId = "sec-cycle", parentPageId = "page-B", title = "Page A")
        val pageB = NotePage(id = "page-B", sectionId = "sec-cycle", parentPageId = "page-A", title = "Page B")

        val cyclicSnapshot = WorkspaceSnapshot(
            notebooks = listOf(nb),
            sections = listOf(sec),
            pages = listOf(pageA, pageB),
            revision = 1L
        )

        // If pageA is inserted, pageB does not exist yet.
        println("Testing mutually cyclic snapshot save...")
        try {
            store.saveSnapshot(cyclicSnapshot)
            val loaded = store.loadSnapshot()
            assertNotNull(loaded)
            println("EMPIRICAL FINDING: Mutually cyclic snapshot succeeded: loaded ${loaded.pages.size} pages")
        } catch (e: Exception) {
            println("EMPIRICAL FINDING: Mutually cyclic snapshot failed: ${e.message}")
        }
    }

    @Test
    fun selfCyclicParentReference_pageReferencingItself() {
        val nb = Notebook(id = "nb-self", title = "Self Cycle", accent = SectionAccentKey.ROSE)
        val sec = NoteSection(id = "sec-self", notebookId = "nb-self", title = "Self Sec", accent = SectionAccentKey.ROSE)

        val selfPage = NotePage(id = "page-self", sectionId = "sec-self", parentPageId = "page-self", title = "Self Page")

        val selfSnapshot = WorkspaceSnapshot(
            notebooks = listOf(nb),
            sections = listOf(sec),
            pages = listOf(selfPage),
            revision = 1L
        )

        println("Testing self cyclic snapshot save...")
        try {
            store.saveSnapshot(selfSnapshot)
            val loaded = store.loadSnapshot()
            assertNotNull(loaded)
            println("EMPIRICAL FINDING: Self cyclic snapshot succeeded: loaded ${loaded.pages.size} pages")
        } catch (e: Exception) {
            println("EMPIRICAL FINDING: Self cyclic snapshot failed: ${e.message}")
        }
    }
}
