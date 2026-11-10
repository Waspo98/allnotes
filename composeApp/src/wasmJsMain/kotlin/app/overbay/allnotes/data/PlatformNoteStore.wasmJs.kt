package app.overbay.allnotes.data

import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.WorkspacePreferences
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlinx.browser.localStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual class PlatformNoteStore actual constructor() : NoteStorageBackend {
    private val storageKey = "allnotes_workspace_v1"
    private val preferencesKey = "allnotes_preferences_v1"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    actual override fun loadSnapshot(): WorkspaceSnapshot? {
        return try {
            val raw = localStorage.getItem(storageKey) ?: return null
            json.decodeFromString<WorkspaceSnapshot>(raw)
        } catch (_: Exception) {
            null
        }
    }

    actual override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        try {
            localStorage.setItem(storageKey, json.encodeToString(snapshot))
        } catch (_: Exception) {
            // Browser storage quota or private mode fallback
        }
    }

    actual override fun savePage(page: NotePage, revision: Long) {
        super.savePage(page, revision)
    }

    actual override fun deletePage(pageId: String, revision: Long, deletedAtEpochMs: Long) {
        super.deletePage(pageId, revision, deletedAtEpochMs)
    }

    actual override fun loadPreferences(): WorkspacePreferences {
        return try {
            val raw = localStorage.getItem(preferencesKey) ?: return WorkspacePreferences()
            json.decodeFromString<WorkspacePreferences>(raw)
        } catch (_: Exception) {
            WorkspacePreferences()
        }
    }

    actual override fun savePreferences(preferences: WorkspacePreferences) {
        try {
            localStorage.setItem(preferencesKey, json.encodeToString(preferences))
        } catch (_: Exception) {
        }
    }
}
