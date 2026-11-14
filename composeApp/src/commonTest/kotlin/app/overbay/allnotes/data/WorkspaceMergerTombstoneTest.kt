package app.overbay.allnotes.data

import app.overbay.allnotes.model.DeletedEntity
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.EntityKind
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspacePreferences
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

private class FakeSyncStorage : NoteStorageBackend {
    var snapshot: WorkspaceSnapshot? = null
    var prefs: WorkspacePreferences = WorkspacePreferences()
    var savePageCount = 0

    override fun loadSnapshot(): WorkspaceSnapshot? = snapshot
    override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        this.snapshot = snapshot
    }

    override fun savePage(page: NotePage, revision: Long) {
        savePageCount++
        val cur = snapshot ?: return
        snapshot = cur.copy(
            pages = listOf(page) + cur.pages.filterNot { it.id == page.id },
            revision = revision
        )
    }

    override fun loadPreferences(): WorkspacePreferences = prefs
    override fun savePreferences(preferences: WorkspacePreferences) {
        prefs = preferences
    }
}

private class FakeRemoteSyncProvider(var remoteSnapshot: WorkspaceSnapshot) : SyncProvider {
    override val endpointUrl: String = "https://notes.overbay.app/api/v1/sync"

    override suspend fun pushSnapshot(snapshot: WorkspaceSnapshot): Result<WorkspaceSnapshot> {
        val merged = WorkspaceMerger.merge(remoteSnapshot, snapshot)
        remoteSnapshot = merged
        return Result.success(merged)
    }

    override suspend fun pullSnapshot(): Result<WorkspaceSnapshot> = Result.success(remoteSnapshot)
}

class WorkspaceMergerTombstoneTest {

    private fun baseSnapshot(): WorkspaceSnapshot = WorkspaceSnapshot(
        notebooks = listOf(Notebook("nb-1", "Engineering", "Core", SectionAccentKey.INDIGO, 0, 1000L)),
        sections = listOf(NoteSection("sec-1", "nb-1", "Architecture", SectionAccentKey.TEAL, 0, 1000L)),
        pages = listOf(
            NotePage(
                id = "page-1",
                sectionId = "sec-1",
                title = "Initial Spec",
                blocks = listOf(DocumentBlock.Paragraph("b-1", "Hello world")),
                updatedAtEpochMs = 1000L
            )
        ),
        revision = 5L
    )

    @Test
    fun offlineCreatedPageSurvivesEvenWhenRemoteRevisionIsHigher() {
        val server = baseSnapshot().copy(revision = 50L)
        val offlineClient = baseSnapshot().copy(
            pages = baseSnapshot().pages + NotePage(
                id = "page-offline-new",
                sectionId = "sec-1",
                title = "Created on Airplane",
                blocks = listOf(DocumentBlock.Paragraph("b-2", "Offline content")),
                updatedAtEpochMs = 2000L
            ),
            revision = 6L
        )

        val merged = WorkspaceMerger.merge(server, offlineClient)
        assertTrue(merged.pages.any { it.id == "page-offline-new" }, "Offline-created page must never be dropped")
        assertTrue(merged.pages.any { it.id == "page-1" })
        assertEquals(51L, merged.revision)
    }

    @Test
    fun pageTombstoneDeletesStaleCopyButNewerEditWinsOverOlderTombstone() {
        val deviceA = baseSnapshot()
        val deviceBWithDelete = baseSnapshot().copy(
            pages = emptyList(),
            tombstones = listOf(DeletedEntity("page-1", EntityKind.PAGE, deletedAtEpochMs = 2500L)),
            revision = 6L
        )

        val mergedDeleted = WorkspaceMerger.merge(deviceA, deviceBWithDelete)
        assertTrue(mergedDeleted.pages.none { it.id == "page-1" }, "Tombstone newer than page must delete page")
        assertTrue(mergedDeleted.tombstones.any { it.id == "page-1" && it.kind == EntityKind.PAGE })

        // If Device A edited the page AFTER the tombstone timestamp, the edit wins (LWW-Element-Set)
        val deviceAEditedLater = baseSnapshot().copy(
            pages = listOf(deviceA.pages.first().copy(title = "Revived Edit", updatedAtEpochMs = 3000L))
        )
        val mergedRevived = WorkspaceMerger.merge(deviceBWithDelete, deviceAEditedLater)
        assertEquals("Revived Edit", mergedRevived.pages.single().title)
    }

    @Test
    fun cascadingSectionDeletionDropsChildPagesAndNormalizesOrphanedSubpages() {
        val snapWithSubpage = baseSnapshot().copy(
            pages = baseSnapshot().pages + NotePage(
                id = "page-child",
                sectionId = "sec-1",
                parentPageId = "page-1",
                title = "Nested Child",
                blocks = emptyList(),
                updatedAtEpochMs = 1500L
            )
        )
        // Delete parent page-1 via tombstone: child subpage must survive with parentPageId normalized to null
        val parentDeleted = snapWithSubpage.copy(
            pages = snapWithSubpage.pages.filterNot { it.id == "page-1" },
            tombstones = listOf(DeletedEntity("page-1", EntityKind.PAGE, deletedAtEpochMs = 2000L))
        )
        val mergedSubpage = WorkspaceMerger.merge(snapWithSubpage, parentDeleted)
        val survivingChild = mergedSubpage.pages.single()
        assertEquals("page-child", survivingChild.id)
        assertNull(survivingChild.parentPageId, "Orphaned subpage must normalize parentPageId to null")

        // Delete entire section sec-1 via tombstone: all pages in sec-1 must also be removed
        val secDeleted = baseSnapshot().copy(
            sections = emptyList(),
            pages = emptyList(),
            tombstones = listOf(DeletedEntity("sec-1", EntityKind.SECTION, deletedAtEpochMs = 4000L))
        )
        val mergedSecDelete = WorkspaceMerger.merge(snapWithSubpage, secDeleted)
        assertTrue(mergedSecDelete.sections.isEmpty())
        assertTrue(mergedSecDelete.pages.isEmpty())
    }

    @Test
    fun pullFromServerMergesConcurrentEditsWithoutOverwritingLocalUnsyncedWork() = runTest {
        val remoteInitial = baseSnapshot().copy(
            pages = baseSnapshot().pages + NotePage(
                id = "page-remote",
                sectionId = "sec-1",
                title = "Remote Colleague Note",
                blocks = listOf(DocumentBlock.Paragraph("br-1", "Server note")),
                updatedAtEpochMs = 5000L
            ),
            revision = 25L
        )
        val storage = FakeSyncStorage().apply { snapshot = baseSnapshot() }
        val syncProvider = FakeRemoteSyncProvider(remoteInitial)
        val repo = NoteRepository(storage = storage, syncProvider = syncProvider)

        // Create local unsynced page (revision becomes 6L, which is < server's 25L)
        val localPage = repo.createPage("sec-1", "My Local Unsynced Draft")

        val pulled = repo.pullFromServer().getOrThrow()
        assertTrue(pulled.pages.any { it.id == localPage.id }, "Local unsynced page must survive pullFromServer")
        assertTrue(pulled.pages.any { it.id == "page-remote" }, "Remote page must be merged in")
    }

    @Test
    fun debouncedTypingCoalescesDiskWritesAndFlushesImmediatelyOnDemand() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val storage = FakeSyncStorage().apply { snapshot = baseSnapshot() }
        val repo = NoteRepository(
            storage = storage,
            typingDebounceMs = 300L,
            repositoryScope = CoroutineScope(SupervisorJob() + testDispatcher)
        )

        repo.updatePageTitle("page-1", "T", debounced = true)
        repo.updatePageTitle("page-1", "Ty", debounced = true)
        repo.updatePageTitle("page-1", "Typ", debounced = true)
        repo.updatePageTitle("page-1", "Typed Title", debounced = true)

        // In-memory state updates immediately at 0ms latency, while disk was not hammered 4 times
        assertEquals("Typed Title", repo.snapshot.value.pages.first { it.id == "page-1" }.title)
        assertEquals(0, storage.savePageCount, "Debounced keystrokes should not write synchronously before window")

        // Explicit flush commits immediately (e.g., on app stop / window close / sync)
        repo.flushPendingWrites()
        assertEquals(1, storage.savePageCount)
        assertEquals("Typed Title", storage.snapshot?.pages?.first { it.id == "page-1" }?.title)
        assertFalse(repo.snapshot.value.pages.isEmpty())
    }

    @Test
    fun mergerBreaksMultiNodeCircularSubpageReferencesSafely() {
        val base = baseSnapshot()
        val pageA = NotePage(id = "cycle-a", sectionId = "sec-1", parentPageId = "cycle-b", title = "A", updatedAtEpochMs = 2000L)
        val pageB = NotePage(id = "cycle-b", sectionId = "sec-1", parentPageId = "cycle-c", title = "B", updatedAtEpochMs = 2000L)
        val pageC = NotePage(id = "cycle-c", sectionId = "sec-1", parentPageId = "cycle-a", title = "C", updatedAtEpochMs = 2000L)

        val merged = WorkspaceMerger.merge(
            base.copy(pages = base.pages + pageA + pageB),
            base.copy(pages = base.pages + pageC)
        )
        val byId = merged.pages.associateBy { it.id }
        // Verify walking parent chains from every page terminates cleanly with zero cycles
        for (page in merged.pages) {
            val seen = HashSet<String>()
            var cursor: NotePage? = page
            while (cursor != null) {
                assertTrue(seen.add(cursor.id), "Subpage parent chain must remain acyclic")
                cursor = cursor.parentPageId?.let { byId[it] }
            }
        }
    }
}
