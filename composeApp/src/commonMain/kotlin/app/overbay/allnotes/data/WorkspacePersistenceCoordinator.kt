package app.overbay.allnotes.data

import app.overbay.allnotes.model.WorkspacePreferences
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serializes and debounces disk persistence for [NoteRepository].
 *
 * When [persistOnBackground] is enabled, UI mutations complete with zero main-thread
 * disk I/O while a coalescing single-writer coroutine drains snapshots, page updates,
 * deletions, and preferences to [storage]. Calling [flush] drains all pending writes
 * synchronously under [storageLock] before app stop or sync.
 */
internal class WorkspacePersistenceCoordinator(
    private val storage: NoteStorageBackend,
    private val typingDebounceMs: Long,
    private val scope: CoroutineScope,
    private val persistOnBackground: Boolean,
    initialRevision: Long,
    private val snapshotProvider: () -> WorkspaceSnapshot,
    private val preferencesProvider: () -> WorkspacePreferences
) {
    private val pendingLock = Any()
    private val storageLock = Any()

    private val dirtyPageIds = LinkedHashSet<String>()
    private val pendingDeletedPages = LinkedHashMap<String, Long>()
    private val pendingDebounceJobs = LinkedHashMap<String, Job>()

    private var dirtyFullSnapshot = false
    private var lastFullSnapshotRevRequired = 0L
    private var lastPersistedRevision = initialRevision
    private var prefsVersion = 0L
    private var lastPersistedPrefsVersion = 0L
    private var backgroundPersistJob: Job? = null

    fun recordPreferencesChanged(preferences: WorkspacePreferences) {
        val targetVer = platformSynchronized(pendingLock) { ++prefsVersion }
        if (persistOnBackground) {
            scheduleBackgroundFlush()
        } else {
            platformSynchronized(storageLock) {
                storage.savePreferences(preferences)
                if (targetVer > lastPersistedPrefsVersion) {
                    lastPersistedPrefsVersion = targetVer
                }
            }
        }
    }

    fun recordFullSnapshot(snapshot: WorkspaceSnapshot) {
        if (!persistOnBackground) {
            flush()
        }
        platformSynchronized(pendingLock) {
            if (snapshot.revision > lastFullSnapshotRevRequired) {
                lastFullSnapshotRevRequired = snapshot.revision
            }
        }
        if (persistOnBackground) {
            platformSynchronized(pendingLock) {
                pendingDebounceJobs.values.forEach { it.cancel() }
                pendingDebounceJobs.clear()
                dirtyPageIds.clear()
                pendingDeletedPages.clear()
                dirtyFullSnapshot = true
            }
            scheduleBackgroundFlush()
        } else {
            platformSynchronized(storageLock) {
                storage.saveSnapshot(snapshot)
                if (snapshot.revision > lastPersistedRevision) {
                    lastPersistedRevision = snapshot.revision
                }
            }
        }
    }

    fun recordPageWrite(pageId: String, debounced: Boolean) {
        if (debounced && typingDebounceMs > 0L) {
            platformSynchronized(pendingLock) {
                dirtyPageIds.add(pageId)
                pendingDebounceJobs.remove(pageId)?.cancel()
                pendingDebounceJobs[pageId] = scope.launch {
                    delay(typingDebounceMs)
                    val stillPending = platformSynchronized(pendingLock) {
                        pendingDebounceJobs.remove(pageId)
                        dirtyPageIds.remove(pageId)
                    }
                    if (stillPending) {
                        if (persistOnBackground) {
                            platformSynchronized(pendingLock) { dirtyPageIds.add(pageId) }
                            scheduleBackgroundFlush()
                        } else {
                            writeSinglePageNow(pageId)
                        }
                    }
                }
            }
            return
        }

        platformSynchronized(pendingLock) {
            pendingDebounceJobs.remove(pageId)?.cancel()
            pendingDeletedPages.remove(pageId)
            if (persistOnBackground) {
                dirtyPageIds.add(pageId)
            } else {
                dirtyPageIds.remove(pageId)
            }
        }
        if (persistOnBackground) {
            scheduleBackgroundFlush()
        } else {
            writeSinglePageNow(pageId)
        }
    }

    fun recordPageDelete(pageId: String, deletedAtEpochMs: Long, hasOrphanedChildren: Boolean, snapshot: WorkspaceSnapshot) {
        if (!persistOnBackground) {
            flush()
        }
        if (hasOrphanedChildren) {
            recordFullSnapshot(snapshot)
            return
        }
        if (persistOnBackground) {
            platformSynchronized(pendingLock) {
                pendingDebounceJobs.remove(pageId)?.cancel()
                dirtyPageIds.remove(pageId)
                pendingDeletedPages[pageId] = deletedAtEpochMs
            }
            scheduleBackgroundFlush()
        } else {
            platformSynchronized(pendingLock) {
                pendingDebounceJobs.remove(pageId)?.cancel()
                dirtyPageIds.remove(pageId)
            }
            platformSynchronized(storageLock) {
                storage.deletePage(pageId, snapshot.revision, deletedAtEpochMs)
                if (snapshot.revision > lastPersistedRevision) {
                    lastPersistedRevision = snapshot.revision
                }
            }
        }
    }

    fun flush() {
        val batch = platformSynchronized(pendingLock) {
            pendingDebounceJobs.values.forEach { it.cancel() }
            pendingDebounceJobs.clear()
            popBatchLocked()
        }
        executeBatch(batch)
    }

    private fun writeSinglePageNow(pageId: String) {
        platformSynchronized(storageLock) {
            val snap = snapshotProvider()
            val page = snap.pages.firstOrNull { it.id == pageId }
            if (page != null) {
                storage.savePage(page, snap.revision)
                if (snap.revision > lastPersistedRevision) {
                    lastPersistedRevision = snap.revision
                }
            }
        }
    }

    private fun scheduleBackgroundFlush() {
        platformSynchronized(pendingLock) {
            if (backgroundPersistJob?.isActive == true) return@platformSynchronized
            backgroundPersistJob = scope.launch {
                while (true) {
                    val batch = platformSynchronized(pendingLock) { popBatchLocked() }
                    executeBatch(batch)
                    val moreWork = platformSynchronized(pendingLock) {
                        dirtyFullSnapshot ||
                            dirtyPageIds.isNotEmpty() ||
                            pendingDeletedPages.isNotEmpty() ||
                            lastPersistedRevision < lastFullSnapshotRevRequired ||
                            lastPersistedPrefsVersion < prefsVersion
                    }
                    if (!moreWork) break
                }
            }
        }
    }

    private fun popBatchLocked(): PersistBatch {
        val pages = dirtyPageIds.toList().also { dirtyPageIds.clear() }
        val deletions = pendingDeletedPages.toMap().also { pendingDeletedPages.clear() }
        val full = dirtyFullSnapshot.also { dirtyFullSnapshot = false }
        return PersistBatch(
            fullSnapshot = full,
            fullRevRequired = lastFullSnapshotRevRequired,
            prefsVerRequired = prefsVersion,
            pageIds = pages,
            deletedPages = deletions
        )
    }

    private fun executeBatch(batch: PersistBatch) {
        platformSynchronized(storageLock) {
            val (curFullRev, curPrefsVer) = platformSynchronized(pendingLock) {
                maxOf(batch.fullRevRequired, lastFullSnapshotRevRequired) to maxOf(batch.prefsVerRequired, prefsVersion)
            }
            val latestSnap = snapshotProvider()
            val latestPrefs = preferencesProvider()
            val needFull = batch.fullSnapshot || lastPersistedRevision < curFullRev
            if (needFull) {
                storage.saveSnapshot(latestSnap)
                if (latestSnap.revision > lastPersistedRevision) {
                    lastPersistedRevision = latestSnap.revision
                }
            } else if (batch.pageIds.isNotEmpty() || batch.deletedPages.isNotEmpty()) {
                for ((deletedId, deletedAt) in batch.deletedPages) {
                    storage.deletePage(deletedId, latestSnap.revision, deletedAt)
                }
                for (pageId in batch.pageIds) {
                    val page = latestSnap.pages.firstOrNull { it.id == pageId }
                    if (page != null) {
                        storage.savePage(page, latestSnap.revision)
                    }
                }
                if (latestSnap.revision > lastPersistedRevision) {
                    lastPersistedRevision = latestSnap.revision
                }
            }
            if (lastPersistedPrefsVersion < curPrefsVer) {
                storage.savePreferences(latestPrefs)
                lastPersistedPrefsVersion = curPrefsVer
            }
        }
    }

    private data class PersistBatch(
        val fullSnapshot: Boolean,
        val fullRevRequired: Long,
        val prefsVerRequired: Long,
        val pageIds: List<String>,
        val deletedPages: Map<String, Long>
    )
}
