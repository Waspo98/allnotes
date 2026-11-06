package app.overbay.allnotes.data

import android.content.Context
import app.overbay.allnotes.db.AllNotesDatabase
import app.overbay.allnotes.model.WorkspaceSnapshot
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Global Android storage paths and application context holder.
 */
object AndroidStoragePaths {
    @Volatile
    var filesDirectory: File? = null

    @Volatile
    var context: Context? = null
}

/**
 * Android implementation of [NoteStorageBackend].
 *
 * Delegates persistence to [SqlDelightNoteStore]. On initial startup with an empty database,
 * automatically detects and migrates legacy JSON snapshots (`allnotes_workspace_v1.json`)
 * into SQLite before archiving the file.
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
                // Archive legacy JSON file
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
            // Fail-safe local persistence
        }
    }
}
