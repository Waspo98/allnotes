package app.overbay.allnotes.data

import app.overbay.allnotes.model.WorkspaceSnapshot

interface NoteStorageBackend {
    fun loadSnapshot(): WorkspaceSnapshot?
    fun saveSnapshot(snapshot: WorkspaceSnapshot)
}

expect class PlatformNoteStore() : NoteStorageBackend {
    override fun loadSnapshot(): WorkspaceSnapshot?
    override fun saveSnapshot(snapshot: WorkspaceSnapshot)
}
