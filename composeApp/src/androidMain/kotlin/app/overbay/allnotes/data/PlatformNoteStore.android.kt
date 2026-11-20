package app.overbay.allnotes.data

import app.cash.sqldelight.db.SqlDriver
import app.overbay.allnotes.db.AllNotesDatabase
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.WorkspacePreferences
import app.overbay.allnotes.model.WorkspaceSnapshot
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Global Android storage paths and SQLite driver provider.
 * Avoids holding raw Android Context or Activity references in static singletons.
 */
object AndroidStoragePaths {
    @Volatile
    var filesDirectory: File? = null

    @Volatile
    var driverFactory: (() -> SqlDriver)? = null
}

/**
 * Android implementation of [NoteStorageBackend].
 *
 * Delegates persistence to [SqlDelightNoteStore]. On initial startup with an empty database,
 * automatically detects and migrates legacy JSON snapshots (`allnotes_workspace_v1.json`)
 * into SQLite before archiving the file.
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

    private fun resolveLegacyJsonFile(): File? {
        val dir = AndroidStoragePaths.filesDirectory ?: return null
        return File(dir, "allnotes_workspace_v1.json")
    }

    actual override fun loadSnapshot(): WorkspaceSnapshot? {
        return try {
            val dbSnapshot = sqlStore.loadSnapshot()
            if (dbSnapshot != null) {
                return dbSnapshot
            }

            // Migration from legacy JSON if present on device
            val legacyFile = resolveLegacyJsonFile()
            if (legacyFile != null && legacyFile.exists()) {
                val legacySnapshot = json.decodeFromString<WorkspaceSnapshot>(legacyFile.readText())
                sqlStore.saveSnapshot(legacySnapshot)
                legacyFile.renameTo(File(legacyFile.parentFile, "allnotes_workspace_v1.json.migrated"))
                return legacySnapshot
            }

            null
        } catch (e: Exception) {
            System.err.println("AllNotes AndroidStorage: Failed to load snapshot: ${e.message}")
            null
        }
    }

    actual override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        try {
            sqlStore.saveSnapshot(snapshot)
        } catch (e: Exception) {
            System.err.println("AllNotes AndroidStorage: Failed to save snapshot: ${e.message}")
            storageErrorHandler?.invoke("Failed to save workspace locally: ${e.message ?: "storage error"}")
        }
    }

    actual override fun savePage(page: NotePage, revision: Long) {
        try {
            sqlStore.savePage(page, revision)
        } catch (e: Exception) {
            System.err.println("AllNotes AndroidStorage: Failed to save page ${page.id}: ${e.message}")
            storageErrorHandler?.invoke("Failed to save page locally: ${e.message ?: "storage error"}")
        }
    }

    actual override fun deletePage(pageId: String, revision: Long, deletedAtEpochMs: Long) {
        try {
            sqlStore.deletePage(pageId, revision, deletedAtEpochMs)
        } catch (e: Exception) {
            System.err.println("AllNotes AndroidStorage: Failed to delete page $pageId: ${e.message}")
            storageErrorHandler?.invoke("Failed to delete page locally: ${e.message ?: "storage error"}")
        }
    }

    actual override fun loadPreferences(): WorkspacePreferences {
        return try {
            sqlStore.loadPreferencesWithSyncMigration()
        } catch (e: Exception) {
            System.err.println("AllNotes AndroidStorage: Failed to load preferences: ${e.message}")
            WorkspacePreferences()
        }
    }

    actual override fun savePreferences(preferences: WorkspacePreferences) {
        try {
            sqlStore.savePreferences(preferences)
        } catch (e: Exception) {
            System.err.println("AllNotes AndroidStorage: Failed to save preferences: ${e.message}")
            storageErrorHandler?.invoke("Failed to save settings locally: ${e.message ?: "storage error"}")
        }
    }
}
