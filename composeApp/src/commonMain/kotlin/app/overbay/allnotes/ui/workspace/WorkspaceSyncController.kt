package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.HttpSyncProvider
import app.overbay.allnotes.data.LocalLoopbackSyncProvider
import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates manual, startup, and debounced background synchronization for [WorkspaceViewModel].
 */
internal class WorkspaceSyncController(
    private val repository: NoteRepository,
    private val scope: CoroutineScope,
    private val onStatusChange: (SyncStatus) -> Unit,
    private val onSettingsSaved: (endpointUrl: String, useRemoteHttp: Boolean) -> Unit
) {
    private var autoSyncJob: Job? = null

    fun syncOnLaunchIfEnabled() {
        if (repository.preferences.value.useRemoteHttpSync) {
            scope.launch {
                val res = repository.pullFromServer()
                onStatusChange(if (res.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE)
            }
        } else {
            onStatusChange(SyncStatus.LOCAL_READY)
        }
    }

    fun scheduleAutoSyncIfEnabled(preferences: app.overbay.allnotes.model.WorkspacePreferences) {
        if (!preferences.autoSyncOnEdit || !preferences.useRemoteHttpSync) return
        autoSyncJob?.cancel()
        autoSyncJob = scope.launch {
            delay(1500L)
            onStatusChange(SyncStatus.SYNCING)
            val result = repository.syncWithServer()
            onStatusChange(if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE)
        }
    }

    fun saveSettingsAndSync(
        endpointUrl: String,
        useRemoteHttp: Boolean,
        authToken: String,
        autoSync: Boolean
    ) {
        val cleanUrl = endpointUrl.trim().ifEmpty { DEFAULT_SYNC_ENDPOINT }
        val cleanToken = authToken.trim()
        repository.updatePreferences {
            it.copy(
                syncEndpointUrl = cleanUrl,
                useRemoteHttpSync = useRemoteHttp,
                syncAuthToken = cleanToken,
                autoSyncOnEdit = autoSync
            )
        }
        val provider = if (useRemoteHttp) {
            HttpSyncProvider(cleanUrl, authTokenProvider = { repository.preferences.value.syncAuthToken })
        } else {
            LocalLoopbackSyncProvider(cleanUrl)
        }
        repository.configureSyncProvider(provider)
        onSettingsSaved(cleanUrl, useRemoteHttp)
        triggerManualSync()
    }

    fun triggerManualSync() {
        if (!repository.isRemoteSyncEnabled) {
            repository.flushPendingWrites()
            onStatusChange(SyncStatus.LOCAL_READY)
            return
        }
        scope.launch {
            onStatusChange(SyncStatus.SYNCING)
            val result = repository.syncWithServer()
            onStatusChange(if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE)
        }
    }

    companion object {
        const val DEFAULT_SYNC_ENDPOINT = "https://notes.overbay.app/api/v1/sync"
    }
}
