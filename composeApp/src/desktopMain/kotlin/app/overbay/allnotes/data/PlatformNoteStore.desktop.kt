package app.overbay.allnotes.data

import app.overbay.allnotes.model.WorkspaceSnapshot
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual class PlatformNoteStore actual constructor() : NoteStorageBackend {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun resolveStorageFile(): File {
        val appData = System.getenv("APPDATA")
            ?: System.getProperty("user.home")
            ?: "."
        val dir = File(appData, "AllNotes")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "allnotes_workspace_v1.json")
    }

    actual override fun loadSnapshot(): WorkspaceSnapshot? {
        return try {
            val file = resolveStorageFile()
            if (!file.exists()) return null
            json.decodeFromString<WorkspaceSnapshot>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    actual override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        try {
            val file = resolveStorageFile()
            val tmpFile = File(file.parentFile, "${file.name}.tmp")
            tmpFile.writeText(json.encodeToString(snapshot))
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        } catch (_: Exception) {
            // Ignore IO exceptions during background save
        }
    }
}
