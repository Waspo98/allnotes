package app.overbay.allnotes.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.overbay.allnotes.db.AllNotesDatabase
import app.overbay.allnotes.model.DeletedEntity
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.EntityKind
import app.overbay.allnotes.model.ThemeMode
import app.overbay.allnotes.model.WorkspacePreferences
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlDelightMigrationTest {

    @Test
    fun migratingFromSchemaV1ToV2PreservesAllUserNotesAndEnablesTombstonesAndPreferences() {
        val properties = Properties().apply { put("foreign_keys", "on") }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, properties)

        // Simulate an existing production v1 SQLite database created before 1.sqm existed
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        driver.execute(
            null,
            "CREATE TABLE workspace_meta (id INTEGER NOT NULL PRIMARY KEY, revision INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0);",
            0
        )
        driver.execute(
            null,
            """
            CREATE TABLE notebook (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                subtitle TEXT NOT NULL,
                accent TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            """
            CREATE TABLE note_section (
                id TEXT NOT NULL PRIMARY KEY,
                notebook_id TEXT NOT NULL,
                title TEXT NOT NULL,
                accent TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (notebook_id) REFERENCES notebook(id) ON DELETE CASCADE
            );
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            """
            CREATE TABLE note_page (
                id TEXT NOT NULL PRIMARY KEY,
                section_id TEXT NOT NULL,
                parent_page_id TEXT,
                title TEXT NOT NULL,
                blocks_json TEXT NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL,
                is_pinned INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (section_id) REFERENCES note_section(id) ON DELETE CASCADE,
                FOREIGN KEY (parent_page_id) REFERENCES note_page(id) ON DELETE SET NULL
            );
            """.trimIndent(),
            0
        )

        // Seed sacred v1 user note data
        driver.execute(null, "INSERT INTO workspace_meta(id, revision) VALUES (1, 14);", 0)
        driver.execute(
            null,
            "INSERT INTO notebook(id, title, subtitle, accent, sort_order, updated_at_epoch_ms) VALUES ('nb-v1', 'Sacred V1 Vault', 'Legacy Notes', 'INDIGO', 0, 1700000000000);",
            0
        )
        driver.execute(
            null,
            "INSERT INTO note_section(id, notebook_id, title, accent, sort_order, updated_at_epoch_ms) VALUES ('sec-v1', 'nb-v1', 'Journal', 'TEAL', 0, 1700000000000);",
            0
        )
        driver.execute(
            null,
            """INSERT INTO note_page(id, section_id, parent_page_id, title, blocks_json, updated_at_epoch_ms, is_pinned) VALUES ('page-v1', 'sec-v1', NULL, 'Sacred Note', '[{"type":"paragraph","id":"b1","text":"Never lose user notes","isBold":true}]', 1700000005000, 1);""",
            0
        )

        // Execute real SQLDelight 1.sqm migration (v1 -> current schema version)
        AllNotesDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = AllNotesDatabase.Schema.version)

        val store = SqlDelightNoteStore(AllNotesDatabase(driver))
        val loaded = store.loadSnapshot()
        assertNotNull(loaded, "Migrated database must load existing snapshot cleanly")
        assertEquals(14L, loaded.revision)
        assertEquals("Sacred V1 Vault", loaded.notebooks.single().title)
        val migratedPage = loaded.pages.single()
        assertEquals("Sacred Note", migratedPage.title)
        assertTrue(migratedPage.isPinned)
        val paragraph = migratedPage.blocks.single() as DocumentBlock.Paragraph
        assertEquals("Never lose user notes", paragraph.text)

        // Verify newly migrated v2 tables (deleted_entity + workspace_preferences) work
        val prefs = WorkspacePreferences(
            themeMode = ThemeMode.DARK,
            useDynamicColor = false,
            syncEndpointUrl = "https://notes.overbay.app/api/v1/sync",
            useRemoteHttpSync = true,
            syncAuthToken = "secret-oidc-token",
            autoSyncOnEdit = true,
            lastSyncedEpochMs = 1700000099000L
        )
        store.savePreferences(prefs)
        assertEquals(prefs, store.loadPreferences())

        val withTombstone = loaded.copy(
            tombstones = listOf(DeletedEntity("old-page-99", EntityKind.PAGE, 1700000088000L)),
            revision = 15L
        )
        store.saveSnapshot(withTombstone)
        val reloaded = store.loadSnapshot()!!
        assertEquals(1, reloaded.tombstones.size)
        assertEquals("old-page-99", reloaded.tombstones.single().id)
    }

    @Test
    fun migratingFromSchemaV3ToV4PreservesSectionsAndEnablesSectionPinning() {
        val properties = Properties().apply { put("foreign_keys", "on") }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, properties)

        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        driver.execute(null, "CREATE TABLE workspace_meta (id INTEGER NOT NULL PRIMARY KEY DEFAULT 1, revision INTEGER NOT NULL DEFAULT 1, updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0);", 0)
        driver.execute(null, "CREATE TABLE notebook (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL DEFAULT '', accent TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0);", 0)
        driver.execute(null, "CREATE TABLE note_section (id TEXT PRIMARY KEY NOT NULL, notebook_id TEXT NOT NULL, title TEXT NOT NULL, accent TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, FOREIGN KEY (notebook_id) REFERENCES notebook(id) ON DELETE CASCADE);", 0)
        driver.execute(null, "CREATE TABLE note_page (id TEXT PRIMARY KEY NOT NULL, section_id TEXT NOT NULL, parent_page_id TEXT, title TEXT NOT NULL, blocks_json TEXT NOT NULL, is_pinned INTEGER NOT NULL DEFAULT 0, updated_at_epoch_ms INTEGER NOT NULL DEFAULT 0, FOREIGN KEY (section_id) REFERENCES note_section(id) ON DELETE CASCADE, FOREIGN KEY (parent_page_id) REFERENCES note_page(id) ON DELETE SET NULL);", 0)
        driver.execute(null, "CREATE TABLE deleted_entity (id TEXT NOT NULL, kind TEXT NOT NULL, deleted_at_epoch_ms INTEGER NOT NULL, PRIMARY KEY (id, kind));", 0)
        driver.execute(null, "CREATE TABLE workspace_preferences (id INTEGER PRIMARY KEY NOT NULL DEFAULT 1, prefs_json TEXT NOT NULL);", 0)

        driver.execute(null, "INSERT INTO workspace_meta(id, revision) VALUES (1, 5);", 0)
        driver.execute(null, "INSERT INTO notebook(id, title, subtitle, accent, sort_order, updated_at_epoch_ms) VALUES ('nb-1', 'Discipleships', '', 'TEAL', 0, 1000);", 0)
        driver.execute(null, "INSERT INTO note_section(id, notebook_id, title, accent, sort_order, updated_at_epoch_ms) VALUES ('sec-a', 'nb-1', 'Alpha', 'INDIGO', 0, 1000);", 0)
        driver.execute(null, "INSERT INTO note_section(id, notebook_id, title, accent, sort_order, updated_at_epoch_ms) VALUES ('sec-b', 'nb-1', 'Kyle', 'EMERALD', 1, 1000);", 0)
        driver.execute(null, "INSERT INTO note_page(id, section_id, parent_page_id, title, blocks_json, is_pinned, updated_at_epoch_ms) VALUES ('p-1', 'sec-b', NULL, 'hi page 2', '[]', 0, 1000);", 0)

        AllNotesDatabase.Schema.migrate(driver, oldVersion = 3, newVersion = AllNotesDatabase.Schema.version)

        val store = SqlDelightNoteStore(AllNotesDatabase(driver))
        val loaded = store.loadSnapshot()!!
        assertEquals(2, loaded.sections.size)
        assertTrue(loaded.sections.all { !it.isPinned })

        val pinnedSections = loaded.sections.map {
            if (it.id == "sec-b") it.copy(isPinned = true, updatedAtEpochMs = 2000L) else it
        }
        store.saveSnapshot(loaded.copy(sections = pinnedSections, revision = 6L))

        val reloaded = store.loadSnapshot()!!
        assertEquals(listOf("sec-b", "sec-a"), reloaded.sections.map { it.id })
        assertTrue(reloaded.sections.first().isPinned)
        assertEquals("hi page 2", reloaded.pages.single().title)
    }
}
