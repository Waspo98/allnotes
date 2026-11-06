package app.overbay.allnotes.data

import app.overbay.allnotes.model.WorkspaceSnapshot
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AndroidStoragePaths {
    @Volatile
    var filesDirectory: File? = null
}

actual class PlatformNoteStore actual constructor() : NoteStorageBackend {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun resolveFile(): File? {
        val dir = AndroidStoragePaths.filesDirectory ?: return null
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "allnotes_workspace_v1.json")
    }

    actual override fun loadSnapshot(): WorkspaceSnapshot? {
        return try {
            val file = resolveFile() ?: return null
            if (!file.exists()) return null
            json.decodeFromString<WorkspaceSnapshot>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    actual override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        try {
            val file = resolveFile() ?: return
            val tmpFile = File(file.parentFile, "${file.name}.tmp")
            tmpFile.writeText(json.encodeToString(snapshot))
            tmpFile.copyTo(file, overwrite = true)
            tmpFile.delete()
        } catch (_: Exception) {
            // Best-effort local persistence
        }
    }
}
