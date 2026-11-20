package app.overbay.allnotes.data

import app.overbay.allnotes.model.DeletedEntity
import app.overbay.allnotes.model.EntityKind
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.WorkspacePreferences
import app.overbay.allnotes.model.WorkspaceSnapshot

interface NoteStorageBackend {
    var storageErrorHandler: ((String) -> Unit)?
        get() = null
        set(_) {}

    fun loadSnapshot(): WorkspaceSnapshot?
    fun saveSnapshot(snapshot: WorkspaceSnapshot)

    fun savePage(page: NotePage, revision: Long) {
        val current = loadSnapshot() ?: return
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

    fun deletePage(pageId: String, revision: Long, deletedAtEpochMs: Long = 0L) {
        val current = loadSnapshot() ?: return
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

    fun loadPreferences(): WorkspacePreferences = WorkspacePreferences()
    fun savePreferences(preferences: WorkspacePreferences) {}
}

expect class PlatformNoteStore() : NoteStorageBackend {
    override var storageErrorHandler: ((String) -> Unit)?
    override fun loadSnapshot(): WorkspaceSnapshot?
    override fun saveSnapshot(snapshot: WorkspaceSnapshot)
    override fun savePage(page: NotePage, revision: Long)
    override fun deletePage(pageId: String, revision: Long, deletedAtEpochMs: Long)
    override fun loadPreferences(): WorkspacePreferences
    override fun savePreferences(preferences: WorkspacePreferences)
}
