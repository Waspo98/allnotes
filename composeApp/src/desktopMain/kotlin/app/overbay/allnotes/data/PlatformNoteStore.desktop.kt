package app.overbay.allnotes.data

import app.overbay.allnotes.db.AllNotesDatabase
import app.overbay.allnotes.model.WorkspaceSnapshot
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Desktop implementation of [NoteStorageBackend].
 *
 * Delegates persistence to [SqlDelightNoteStore] backed by `%APPDATA%/AllNotes/allnotes.db`.
 * Migrates legacy `allnotes_workspace_v1.json` on first run if found.
 */
actual class PlatformNoteStore actual constructor() : NoteStorageBackend {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val sqlStore: SqlDelightNoteStore by lazy {
        val driver = DatabaseDriverFactory().createDriver()
        val database = AllNotesDatabase(driver)
        SqlDelightNoteStore(database)
    }

    private fun resolveLegacyJsonFile(): File {
        val dir = DatabaseDriverFactory.resolveStorageDirectory()
        return File(dir, "allnotes_workspace_v1.json")
    }

    actual override fun loadSnapshot(): WorkspaceSnapshot? {
        return try {
            val dbSnapshot = sqlStore.loadSnapshot()
            if (dbSnapshot != null) {
                return dbSnapshot
            }

            // Migration from legacy JSON if present
            val legacyFile = resolveLegacyJsonFile()
            if (legacyFile.exists()) {
                val legacySnapshot = json.decodeFromString<WorkspaceSnapshot>(legacyFile.readText())
                sqlStore.saveSnapshot(legacySnapshot)
                legacyFile.renameTo(File(legacyFile.parentFile, "allnotes_workspace_v1.json.migrated"))
                return legacySnapshot
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    actual override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        try {
            sqlStore.saveSnapshot(snapshot)
        } catch (_: Exception) {
            // Fail-safe background persistence
        }
    }
}
