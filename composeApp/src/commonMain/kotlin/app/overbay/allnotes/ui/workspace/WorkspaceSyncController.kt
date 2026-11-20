package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.HttpSyncProvider
import app.overbay.allnotes.data.LocalLoopbackSyncProvider
import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates manual, startup, and debounced background synchronization for [WorkspaceViewModel].
 */
internal class WorkspaceSyncController(
    private val repository: NoteRepository,
    private val scope: CoroutineScope,
    private val onStatusChange: (SyncStatus) -> Unit,
    private val onSettingsSaved: (endpointUrl: String, useRemoteHttp: Boolean) -> Unit
) {
    private val syncMutex = Mutex()
    private var autoSyncJob: Job? = null

    fun syncOnLaunchIfEnabled() {
        if (repository.preferences.value.useRemoteHttpSync) {
            scope.launch {
                syncMutex.withLock {
                    val res = repository.pullFromServer()
                    onStatusChange(if (res.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE)
                }
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
            syncMutex.withLock {
                onStatusChange(SyncStatus.SYNCING)
                val result = repository.syncWithServer()
                onStatusChange(if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE)
            }
        }
    }

    fun saveSettingsAndSync(
        endpointUrl: String,
        useRemoteHttp: Boolean,
        authToken: String,
        autoSync: Boolean
    ) {
        autoSyncJob?.cancel()
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
        autoSyncJob?.cancel()
        if (!repository.isRemoteSyncEnabled) {
            repository.flushPendingWrites()
            onStatusChange(SyncStatus.LOCAL_READY)
            return
        }
        scope.launch {
            syncMutex.withLock {
                onStatusChange(SyncStatus.SYNCING)
                val result = repository.syncWithServer()
                onStatusChange(if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.OFFLINE)
            }
        }
    }

    companion object {
        const val DEFAULT_SYNC_ENDPOINT = "https://notes.overbay.app/api/v1/sync"
    }
}
