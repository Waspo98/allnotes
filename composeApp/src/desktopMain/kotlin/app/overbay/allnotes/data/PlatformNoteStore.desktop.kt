package app.overbay.allnotes.data

import app.overbay.allnotes.db.AllNotesDatabase
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.WorkspacePreferences
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
    actual override var storageErrorHandler: ((String) -> Unit)? = null

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
        } catch (e: Exception) {
            System.err.println("AllNotes DesktopStorage: Failed to load snapshot: ${e.message}")
            null
        }
    }

    actual override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        try {
            sqlStore.saveSnapshot(snapshot)
        } catch (e: Exception) {
            System.err.println("AllNotes DesktopStorage: Failed to save snapshot: ${e.message}")
            storageErrorHandler?.invoke("Failed to save workspace locally: ${e.message ?: "storage error"}")
        }
    }

    actual override fun savePage(page: NotePage, revision: Long) {
        try {
            sqlStore.savePage(page, revision)
        } catch (e: Exception) {
            System.err.println("AllNotes DesktopStorage: Failed to save page ${page.id}: ${e.message}")
            storageErrorHandler?.invoke("Failed to save page locally: ${e.message ?: "storage error"}")
        }
    }

    actual override fun deletePage(pageId: String, revision: Long, deletedAtEpochMs: Long) {
        try {
            sqlStore.deletePage(pageId, revision, deletedAtEpochMs)
        } catch (e: Exception) {
            System.err.println("AllNotes DesktopStorage: Failed to delete page $pageId: ${e.message}")
            storageErrorHandler?.invoke("Failed to delete page locally: ${e.message ?: "storage error"}")
        }
    }

    actual override fun loadPreferences(): WorkspacePreferences {
        return try {
            sqlStore.loadPreferences()
        } catch (e: Exception) {
            System.err.println("AllNotes DesktopStorage: Failed to load preferences: ${e.message}")
            WorkspacePreferences()
        }
    }

    actual override fun savePreferences(preferences: WorkspacePreferences) {
        try {
            sqlStore.savePreferences(preferences)
        } catch (e: Exception) {
            System.err.println("AllNotes DesktopStorage: Failed to save preferences: ${e.message}")
            storageErrorHandler?.invoke("Failed to save settings locally: ${e.message ?: "storage error"}")
        }
    }
}
