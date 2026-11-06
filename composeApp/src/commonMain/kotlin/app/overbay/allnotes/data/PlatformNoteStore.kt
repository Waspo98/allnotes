package app.overbay.allnotes.data

import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.WorkspaceSnapshot

interface NoteStorageBackend {
    fun loadSnapshot(): WorkspaceSnapshot?
    fun saveSnapshot(snapshot: WorkspaceSnapshot)

    fun savePage(page: NotePage, revision: Long) {
        val current = loadSnapshot() ?: return
        val updatedPages = if (current.pages.any { it.id == page.id }) {
            current.pages.map { if (it.id == page.id) page else it }
        } else {
            listOf(page) + current.pages
        }
        saveSnapshot(current.copy(pages = updatedPages, revision = revision))
    }

    fun deletePage(pageId: String, revision: Long) {
        val current = loadSnapshot() ?: return
        saveSnapshot(current.copy(pages = current.pages.filterNot { it.id == pageId }, revision = revision))
    }
}

expect class PlatformNoteStore() : NoteStorageBackend {
    override fun loadSnapshot(): WorkspaceSnapshot?
    override fun saveSnapshot(snapshot: WorkspaceSnapshot)
    override fun savePage(page: NotePage, revision: Long)
    override fun deletePage(pageId: String, revision: Long)
}
