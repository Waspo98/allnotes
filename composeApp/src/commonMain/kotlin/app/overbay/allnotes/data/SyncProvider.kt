package app.overbay.allnotes.data

import app.overbay.allnotes.model.WorkspaceSnapshot

enum class SyncStatus(val label: String) {
    LOCAL_READY("Saved locally"),
    SYNCING("Syncing with notes.overbay.app…"),
    SYNCED("Synced · Overbay.app"),
    OFFLINE("Offline · Local changes queued")
}

interface SyncProvider {
    val endpointUrl: String
    suspend fun pushSnapshot(snapshot: WorkspaceSnapshot): Result<WorkspaceSnapshot>
}

class LocalLoopbackSyncProvider(
    override val endpointUrl: String = "https://notes.overbay.app/api/v1/sync"
) : SyncProvider {
    override suspend fun pushSnapshot(snapshot: WorkspaceSnapshot): Result<WorkspaceSnapshot> {
        return Result.success(snapshot.copy(revision = snapshot.revision + 1))
    }
}
