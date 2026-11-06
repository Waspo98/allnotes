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
    suspend fun pullSnapshot(): Result<WorkspaceSnapshot>
    suspend fun pushSnapshot(snapshot: WorkspaceSnapshot): Result<WorkspaceSnapshot>
}

class LocalLoopbackSyncProvider(
    override val endpointUrl: String = "https://notes.overbay.app/api/v1/sync",
    private var currentSnapshot: WorkspaceSnapshot = SampleWorkspaceFactory.createStarterSnapshot()
) : SyncProvider {
    override suspend fun pullSnapshot(): Result<WorkspaceSnapshot> {
        return Result.success(currentSnapshot)
    }

    override suspend fun pushSnapshot(snapshot: WorkspaceSnapshot): Result<WorkspaceSnapshot> {
        val bumped = snapshot.copy(revision = snapshot.revision + 1)
        currentSnapshot = bumped
        return Result.success(bumped)
    }
}
