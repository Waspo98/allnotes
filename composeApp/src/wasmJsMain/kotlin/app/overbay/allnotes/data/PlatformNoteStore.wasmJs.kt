package app.overbay.allnotes.data

import app.overbay.allnotes.model.DeletedEntity
import app.overbay.allnotes.model.EntityKind
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

    private var cachedSnapshot: WorkspaceSnapshot? = null
    private var snapshotLoaded: Boolean = false
    private var cachedPreferences: WorkspacePreferences? = null

    actual override fun loadSnapshot(): WorkspaceSnapshot? {
        if (snapshotLoaded && cachedSnapshot != null) {
            return cachedSnapshot
        }
        return try {
            val raw = localStorage.getItem(storageKey) ?: run {
                snapshotLoaded = true
                return null
            }
            json.decodeFromString<WorkspaceSnapshot>(raw).also {
                cachedSnapshot = it
                snapshotLoaded = true
            }
        } catch (e: Exception) {
            println("AllNotes WasmStorage: Failed to decode workspace snapshot: ${e.message}")
            null
        }
    }

    actual override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        cachedSnapshot = snapshot
        snapshotLoaded = true
        try {
            localStorage.setItem(storageKey, json.encodeToString(snapshot))
        } catch (e: Exception) {
            println("AllNotes WasmStorage: Failed to persist workspace snapshot (quota/private mode): ${e.message}")
        }
    }

    actual override fun savePage(page: NotePage, revision: Long) {
        val current = cachedSnapshot ?: loadSnapshot() ?: return
        val updatedPages = if (current.pages.any { it.id == page.id }) {
            current.pages.map { if (it.id == page.id) page else it }
        } else {
            listOf(page) + current.pages
        }
        val cleanedTombstones = current.tombstones.filterNot {
            it.id == page.id && it.kind == EntityKind.PAGE
        }
        saveSnapshot(current.copy(pages = updatedPages, revision = revision, tombstones = cleanedTombstones))
    }

    actual override fun deletePage(pageId: String, revision: Long, deletedAtEpochMs: Long) {
        val current = cachedSnapshot ?: loadSnapshot() ?: return
        val updatedTombstones = if (deletedAtEpochMs > 0L) {
            current.tombstones.filterNot { it.id == pageId && it.kind == EntityKind.PAGE } +
                DeletedEntity(id = pageId, kind = EntityKind.PAGE, deletedAtEpochMs = deletedAtEpochMs)
        } else {
            current.tombstones
        }
        saveSnapshot(
            current.copy(
                pages = current.pages.filterNot { it.id == pageId },
                revision = revision,
                tombstones = updatedTombstones
            )
        )
    }

    actual override fun loadPreferences(): WorkspacePreferences {
        cachedPreferences?.let { return it }
        return try {
            val raw = localStorage.getItem(preferencesKey) ?: return WorkspacePreferences()
            json.decodeFromString<WorkspacePreferences>(raw).also {
                cachedPreferences = it
            }
        } catch (_: Exception) {
            WorkspacePreferences()
        }
    }

    actual override fun savePreferences(preferences: WorkspacePreferences) {
        cachedPreferences = preferences
        try {
            localStorage.setItem(preferencesKey, json.encodeToString(preferences))
        } catch (e: Exception) {
            println("AllNotes WasmStorage: Failed to persist preferences: ${e.message}")
        }
    }
}
