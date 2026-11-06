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
import java.io.File
import java.util.Properties
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightNoteStoreTest {

    private lateinit var database: AllNotesDatabase
    private lateinit var store: SqlDelightNoteStore

    @BeforeTest
    fun setup() {
        val properties = Properties().apply {
            put("foreign_keys", "on")
        }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, properties)
        AllNotesDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        database = AllNotesDatabase(driver)
        store = SqlDelightNoteStore(database)
    }

    @Test
    fun freshDatabaseReturnsNullSnapshot() {
        val snapshot = store.loadSnapshot()
        assertNull(snapshot, "Fresh database must return null to trigger starter workspace creation")
    }

    @Test
    fun starterWorkspaceSaveAndLoadRoundtrip() {
        val starter = SampleWorkspaceFactory.createStarterSnapshot()
        store.saveSnapshot(starter)

        val loaded = store.loadSnapshot()
        assertNotNull(loaded, "Loaded snapshot must not be null")
        assertEquals(starter.revision, loaded.revision)
        assertEquals(starter.notebooks.size, loaded.notebooks.size)
        assertEquals(starter.sections.size, loaded.sections.size)
        assertEquals(starter.pages.size, loaded.pages.size)

        for (i in starter.notebooks.indices) {
            val expectedNb = starter.notebooks[i]
            val actualNb = loaded.notebooks.firstOrNull { it.id == expectedNb.id }
            assertNotNull(actualNb)
            assertEquals(expectedNb.title, actualNb.title)
            assertEquals(expectedNb.accent, actualNb.accent)
        }
    }

    @Test
    fun polymorphicBlocksRoundtripPreservesAllSevenVariants() {
        val nb = Notebook(id = "nb-1", title = "Vault", accent = SectionAccentKey.TEAL)
        val sec = NoteSection(id = "sec-1", notebookId = "nb-1", title = "Docs", accent = SectionAccentKey.TEAL)

        val richBlocks = listOf(
            DocumentBlock.Heading(id = "b1", text = "Document Header", level = 1),
            DocumentBlock.Paragraph(
                id = "b2",
                text = "Formatted text sample",
                isBold = true,
                isItalic = true,
                isUnderlined = true,
                isHighlighted = true
            ),
            DocumentBlock.Checklist(
                id = "b3",
                title = "Launch Checklist",
                items = listOf(
                    ChecklistItem("c1", "Task 1", isChecked = true),
                    ChecklistItem("c2", "Task 2", isChecked = false)
                )
            ),
            DocumentBlock.BulletList(
                id = "b4",
                isNumbered = true,
                items = listOf("Step One", "Step Two", "Step Three")
            ),
            DocumentBlock.Table(
                id = "b5",
                headers = listOf("Feature", "Status"),
                rows = listOf(
                    listOf("SQLDelight", "Complete"),
                    listOf("M3E UI", "Ready")
                )
            ),
            DocumentBlock.Callout(
                id = "b6",
                title = "Notice",
                text = "Important key point text",
                tone = CalloutTone.IMPORTANT
            ),
            DocumentBlock.CodeSnippet(
                id = "b7",
                language = "kotlin",
                code = "val test = true"
            )
        )

        val page = NotePage(
            id = "page-1",
            sectionId = "sec-1",
            parentPageId = null,
            title = "All Blocks Page",
            blocks = richBlocks,
            isPinned = true,
            updatedAtEpochMs = 1762420000000L
        )

        val snapshot = WorkspaceSnapshot(
            notebooks = listOf(nb),
            sections = listOf(sec),
            pages = listOf(page),
            revision = 5L
        )

        store.saveSnapshot(snapshot)
        val reloaded = store.loadSnapshot()
        assertNotNull(reloaded)

        val loadedPage = reloaded.pages.first { it.id == "page-1" }
        assertEquals(7, loadedPage.blocks.size)

        // Verify Heading
        val h = loadedPage.blocks[0] as DocumentBlock.Heading
        assertEquals("Document Header", h.text)
        assertEquals(1, h.level)

        // Verify Paragraph
        val p = loadedPage.blocks[1] as DocumentBlock.Paragraph
        assertEquals("Formatted text sample", p.text)
        assertTrue(p.isBold && p.isItalic && p.isUnderlined && p.isHighlighted)

        // Verify Checklist
        val chk = loadedPage.blocks[2] as DocumentBlock.Checklist
        assertEquals(2, chk.items.size)
        assertEquals("Task 1", chk.items[0].text)
        assertTrue(chk.items[0].isChecked)

        // Verify BulletList
        val bl = loadedPage.blocks[3] as DocumentBlock.BulletList
        assertTrue(bl.isNumbered)
        assertEquals(3, bl.items.size)

        // Verify Table
        val tbl = loadedPage.blocks[4] as DocumentBlock.Table
        assertEquals(2, tbl.headers.size)
        assertEquals(2, tbl.rows.size)
        assertEquals("Complete", tbl.rows[0][1])

        // Verify Callout
        val cal = loadedPage.blocks[5] as DocumentBlock.Callout
        assertEquals(CalloutTone.IMPORTANT, cal.tone)
        assertEquals("Important key point text", cal.text)

        // Verify CodeSnippet
        val code = loadedPage.blocks[6] as DocumentBlock.CodeSnippet
        assertEquals("kotlin", code.language)
        assertEquals("val test = true", code.code)
    }

    @Test
    fun fullTextSearchQueriesAcrossTitleAndBlockContents() {
        val nb = Notebook(id = "nb-1", title = "Projects", accent = SectionAccentKey.AMBER)
        val sec = NoteSection(id = "sec-1", notebookId = "nb-1", title = "Sprint", accent = SectionAccentKey.AMBER)
        val page1 = NotePage(
            id = "p1",
            sectionId = "sec-1",
            title = "Kubernetes Deployment",
            blocks = listOf(DocumentBlock.Paragraph(id = "b1", text = "Container orchestration notes"))
        )
        val page2 = NotePage(
            id = "p2",
            sectionId = "sec-1",
            title = "Personal Journal",
            blocks = listOf(DocumentBlock.CodeSnippet(id = "b2", language = "bash", code = "docker compose up -d"))
        )

        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), listOf(page1, page2), 1L))

        // Search by page title
        val titleMatch = store.searchPages("Kubernetes")
        assertEquals(1, titleMatch.size)
        assertEquals("p1", titleMatch[0].id)

        // Search by code block text
        val blockMatch = store.searchPages("docker compose")
        assertEquals(1, blockMatch.size)
        assertEquals("p2", blockMatch[0].id)

        // Non-matching query
        val emptyMatch = store.searchPages("nonexistent_keyword_xyz")
        assertTrue(emptyMatch.isEmpty())
    }

    @Test
    fun granularPageOperations() {
        val nb = Notebook(id = "nb-1", title = "Vault", accent = SectionAccentKey.ROSE)
        val sec = NoteSection(id = "sec-1", notebookId = "nb-1", title = "Notes", accent = SectionAccentKey.ROSE)
        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), emptyList(), 1L))

        val page = NotePage(
            id = "page-g1",
            sectionId = "sec-1",
            title = "Initial Page",
            blocks = listOf(DocumentBlock.Heading(id = "h1", text = "Start", level = 1))
        )

        // Direct savePage
        store.savePage(page)
        val fetched = store.getPage("page-g1")
        assertNotNull(fetched)
        assertEquals("Initial Page", fetched.title)

        // Update page
        val updated = fetched.copy(title = "Updated Page")
        store.savePage(updated)
        assertEquals("Updated Page", store.getPage("page-g1")?.title)

        // Delete page
        store.deletePage("page-g1")
        assertNull(store.getPage("page-g1"))
    }

    @Test
    fun cascadingDeletesRemoveChildEntities() {
        val nb = Notebook(id = "nb-cascade", title = "Cascade Notebook", accent = SectionAccentKey.EMERALD)
        val sec = NoteSection(id = "sec-cascade", notebookId = "nb-cascade", title = "Cascade Section", accent = SectionAccentKey.EMERALD)
        val page = NotePage(id = "page-cascade", sectionId = "sec-cascade", title = "Cascade Page")

        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), listOf(page), 1L))

        // Ensure saved
        val loaded = store.loadSnapshot()
        assertNotNull(loaded)
        assertEquals(1, loaded.notebooks.size)
        assertEquals(1, loaded.sections.size)
        assertEquals(1, loaded.pages.size)

        // Delete the notebook
        database.allNotesQueries.deleteNotebook("nb-cascade")

        // In SQLite with ON DELETE CASCADE, note_section should be deleted
        val sectionsRemaining = database.allNotesQueries.selectSectionsByNotebook("nb-cascade").executeAsList()
        assertTrue(sectionsRemaining.isEmpty(), "Dependent sections should be cascaded on notebook delete")
    }

    @Test
    fun repositoryInitializesStarterDataInSqlite() {
        val repo = NoteRepository(storage = store)
        val snapshot = repo.snapshot.value

        assertTrue(snapshot.notebooks.isNotEmpty(), "Repository must create starter notebooks")
        assertTrue(snapshot.sections.isNotEmpty(), "Repository must create starter sections")
        assertTrue(snapshot.pages.isNotEmpty(), "Repository must create starter pages")

        // Verify database actually persisted the starter data
        val dbSnapshot = store.loadSnapshot()
        assertNotNull(dbSnapshot)
        assertEquals(snapshot.revision, dbSnapshot.revision)
        assertEquals(snapshot.notebooks.size, dbSnapshot.notebooks.size)
    }

    @Test
    fun repositoryMutationsPersistAcrossRepositoryInstances() {
        val repo1 = NoteRepository(storage = store)
        val nb = repo1.createNotebook("Architecture Vault", "System Designs", SectionAccentKey.VIOLET)
        val sec = repo1.createSection(nb.id, "Milestone 1", SectionAccentKey.VIOLET)
        val page = repo1.createPage(sec.id, "SQLDelight Spec")

        repo1.appendBlock(
            pageId = page.id,
            block = DocumentBlock.CodeSnippet(id = "c1", language = "sql", code = "SELECT * FROM notebook;")
        )
        repo1.togglePagePin(page.id)

        // Instantiate a second repository from the same SQLite database (simulating app relaunch)
        val repo2 = NoteRepository(storage = store)
        val reloadedSnapshot = repo2.snapshot.value

        val reloadedNb = reloadedSnapshot.notebooks.firstOrNull { it.id == nb.id }
        assertNotNull(reloadedNb)
        assertEquals("Architecture Vault", reloadedNb.title)

        val reloadedPage = reloadedSnapshot.pages.firstOrNull { it.id == page.id }
        assertNotNull(reloadedPage)
        assertEquals("SQLDelight Spec", reloadedPage.title)
        assertTrue(reloadedPage.isPinned)
        assertTrue(reloadedPage.blocks.any { it is DocumentBlock.CodeSnippet })
    }

    @Test
    fun foreignKeyConstraintsEnforcedAcrossConsecutiveOperations() {
        // 1. Initial valid workspace setup
        val nb = Notebook(id = "nb-fk-test", title = "Valid Notebook", accent = SectionAccentKey.INDIGO)
        val sec = NoteSection(id = "sec-fk-test", notebookId = "nb-fk-test", title = "Valid Section", accent = SectionAccentKey.INDIGO)
        store.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), emptyList(), 1L))

        val loaded = store.loadSnapshot()
        assertNotNull(loaded)
        assertEquals(1, loaded.notebooks.size)
        assertEquals(1, loaded.sections.size)

        // 2. Consecutive Operation: Attempting to insert a section with an invalid notebookId must fail FK constraint
        val orphanSection = NoteSection(
            id = "sec-orphan-op",
            notebookId = "non-existent-notebook-id",
            title = "Orphan Section",
            accent = SectionAccentKey.ROSE
        )
        assertFailsWith<Exception> {
            database.allNotesQueries.insertSection(
                id = orphanSection.id,
                notebookId = orphanSection.notebookId,
                title = orphanSection.title,
                accent = orphanSection.accent.name,
                sortOrder = orphanSection.sortOrder.toLong(),
                updatedAtEpochMs = orphanSection.updatedAtEpochMs
            )
        }

        // Verify rejected section was not persisted
        assertNull(database.allNotesQueries.selectSectionById("sec-orphan-op").executeAsOneOrNull())

        // 3. Consecutive Operation: Attempting to insert a page with an invalid sectionId via store.savePage must fail FK constraint
        val orphanPage = NotePage(
            id = "page-orphan-op",
            sectionId = "non-existent-section-id",
            title = "Orphan Page"
        )
        assertFailsWith<Exception> {
            store.savePage(orphanPage)
        }

        // Verify rejected page was not persisted
        assertNull(store.getPage("page-orphan-op"))

        // 4. Consecutive Operation: Attempting to insert a page with invalid sectionId via direct query must also fail
        assertFailsWith<Exception> {
            database.allNotesQueries.insertPage(
                id = "page-orphan-query-op",
                sectionId = "non-existent-section-id",
                parentPageId = null,
                title = "Orphan Query Page",
                blocksJson = "[]",
                isPinned = 0L,
                updatedAtEpochMs = 1000L
            )
        }
        assertNull(database.allNotesQueries.selectPageById("page-orphan-query-op").executeAsOneOrNull())

        // 5. Consecutive Operation: Valid page insertion succeeds and links correctly
        val validPage = NotePage(
            id = "page-valid-op",
            sectionId = "sec-fk-test",
            title = "Valid Page",
            blocks = listOf(DocumentBlock.Paragraph(id = "b1", text = "Valid content"))
        )
        store.savePage(validPage)
        val fetchedPage = store.getPage("page-valid-op")
        assertNotNull(fetchedPage)
        assertEquals("Valid Page", fetchedPage.title)

        // 6. Consecutive Operation: Verify cascading delete cleans up dependent entities
        database.allNotesQueries.deleteNotebook("nb-fk-test")
        assertTrue(database.allNotesQueries.selectSectionsByNotebook("nb-fk-test").executeAsList().isEmpty(), "Dependent sections must cascade delete")
        assertTrue(database.allNotesQueries.selectPagesBySection("sec-fk-test").executeAsList().isEmpty(), "Dependent pages must cascade delete")
    }

    @Test
    fun foreignKeysEnforcedAcrossSeparateDatabaseConnections() {
        val tempFile = File.createTempFile("allnotes_fk_conn_test", ".db")
        tempFile.deleteOnExit()

        val properties = Properties().apply {
            put("foreign_keys", "on")
            put("journal_mode", "WAL")
            put("synchronous", "NORMAL")
            put("busy_timeout", "5000")
        }

        try {
            // First connection: initialize schema and write valid base data
            val driver1 = JdbcSqliteDriver("jdbc:sqlite:${tempFile.absolutePath}", properties)
            AllNotesDatabase.Schema.create(driver1)
            val db1 = AllNotesDatabase(driver1)
            val store1 = SqlDelightNoteStore(db1)

            val nb = Notebook(id = "nb-conn1", title = "Notebook Conn1", accent = SectionAccentKey.INDIGO)
            val sec = NoteSection(id = "sec-conn1", notebookId = "nb-conn1", title = "Section Conn1", accent = SectionAccentKey.INDIGO)
            store1.saveSnapshot(WorkspaceSnapshot(listOf(nb), listOf(sec), emptyList(), 1L))
            driver1.close()

            // Second connection: open a fresh connection to simulate app relaunch / separate pooled connection
            val driver2 = JdbcSqliteDriver("jdbc:sqlite:${tempFile.absolutePath}", properties)
            val db2 = AllNotesDatabase(driver2)
            val store2 = SqlDelightNoteStore(db2)

            // Foreign keys must be enforced immediately on this second consecutive connection
            val orphanPage = NotePage(
                id = "page-conn2-orphan",
                sectionId = "non-existent-section-conn2",
                title = "Orphan on Conn2"
            )
            assertFailsWith<Exception> {
                store2.savePage(orphanPage)
            }
            assertNull(store2.getPage("page-conn2-orphan"))

            val orphanSection = NoteSection(
                id = "sec-conn2-orphan",
                notebookId = "non-existent-nb-conn2",
                title = "Orphan Section Conn2",
                accent = SectionAccentKey.INDIGO
            )
            assertFailsWith<Exception> {
                db2.allNotesQueries.insertSection(
                    id = orphanSection.id,
                    notebookId = orphanSection.notebookId,
                    title = orphanSection.title,
                    accent = orphanSection.accent.name,
                    sortOrder = 0L,
                    updatedAtEpochMs = 1000L
                )
            }
            assertNull(db2.allNotesQueries.selectSectionById("sec-conn2-orphan").executeAsOneOrNull())

            driver2.close()
        } finally {
            tempFile.delete()
        }
    }
}

