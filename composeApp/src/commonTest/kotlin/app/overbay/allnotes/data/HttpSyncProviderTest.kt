package app.overbay.allnotes.data

import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspacePreferences
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private class FakeHttpTransport(
    var getHandler: (String) -> HttpResponseData = { HttpResponseData(200, "{}") },
    var postHandler: (String, String) -> HttpResponseData = { _, _ -> HttpResponseData(200, "{}") }
) : HttpTransport {
    override suspend fun get(url: String): HttpResponseData = getHandler(url)
    override suspend fun post(url: String, jsonBody: String): HttpResponseData = postHandler(url, jsonBody)
}

private class FakeNoteStorageBackend(initial: WorkspaceSnapshot? = null) : NoteStorageBackend {
    var storedSnapshot: WorkspaceSnapshot? = initial
    override fun loadSnapshot(): WorkspaceSnapshot? = storedSnapshot
    override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        storedSnapshot = snapshot
    }
}

class HttpSyncProviderTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testPullSnapshotSuccess() = runTest {
        val expected = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 42L)
        val transport = FakeHttpTransport(
            getHandler = { url ->
                assertEquals("http://localhost:8094/api/v1/sync", url)
                HttpResponseData(200, json.encodeToString(expected))
            }
        )
        val provider = HttpSyncProvider(transport = transport)
        val result = provider.pullSnapshot()

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrThrow().revision)
        assertEquals(expected.notebooks.size, result.getOrThrow().notebooks.size)
    }

    @Test
    fun testPullSnapshotHttpErrorReturnsFailure() = runTest {
        val transport = FakeHttpTransport(
            getHandler = { HttpResponseData(500, """{"error":"Internal server error"}""") }
        )
        val provider = HttpSyncProvider(transport = transport)
        val result = provider.pullSnapshot()

        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    @Test
    fun testPushSnapshotSuccess() = runTest {
        val current = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 5L)
        val serverBumped = current.copy(revision = 6L)
        val transport = FakeHttpTransport(
            postHandler = { url, body ->
                assertEquals("http://localhost:8094/api/v1/sync", url)
                val sent = json.decodeFromString<WorkspaceSnapshot>(body)
                assertEquals(5L, sent.revision)
                HttpResponseData(200, json.encodeToString(serverBumped))
            }
        )
        val provider = HttpSyncProvider(transport = transport)
        val result = provider.pushSnapshot(current)

        assertTrue(result.isSuccess)
        assertEquals(6L, result.getOrThrow().revision)
    }

    @Test
    fun testPushSnapshotOfflineFailureReturnsFailure() = runTest {
        val current = SampleWorkspaceFactory.createStarterSnapshot()
        val transport = FakeHttpTransport(
            postHandler = { _, _ -> HttpResponseData(503, "Service Unavailable") }
        )
        val provider = HttpSyncProvider(transport = transport)
        val result = provider.pushSnapshot(current)

        assertTrue(result.isFailure)
    }

    @Test
    fun testNoteRepositorySyncWithServerUpdatesStateAndStorage() = runTest {
        val initial = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 1L)
        val storage = FakeNoteStorageBackend(initial)
        val acknowledged = initial.copy(revision = 2L)

        val transport = FakeHttpTransport(
            postHandler = { _, _ -> HttpResponseData(200, json.encodeToString(acknowledged)) }
        )
        val syncProvider = HttpSyncProvider(transport = transport)
        val repository = NoteRepository(storage = storage, syncProvider = syncProvider)

        val result = repository.syncWithServer()
        assertTrue(result.isSuccess)
        assertEquals(2L, repository.snapshot.value.revision)
        assertEquals(2L, storage.storedSnapshot?.revision)
    }

    @Test
    fun testNoteRepositoryPullFromServerUpdatesWhenNewer() = runTest {
        val initial = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 3L)
        val storage = FakeNoteStorageBackend(initial)
        val newer = initial.copy(revision = 10L)

        val transport = FakeHttpTransport(
            getHandler = { HttpResponseData(200, json.encodeToString(newer)) }
        )
        val syncProvider = HttpSyncProvider(transport = transport)
        val repository = NoteRepository(storage = storage, syncProvider = syncProvider)

        val result = repository.pullFromServer()
        assertTrue(result.isSuccess)
        assertEquals(10L, repository.snapshot.value.revision)
        assertEquals(10L, storage.storedSnapshot?.revision)
    }

    @Test
    fun testNoteRepositoryPullFromServerIgnoresStaleRevision() = runTest {
        val initial = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 15L)
        val storage = FakeNoteStorageBackend(initial)
        val older = initial.copy(revision = 5L)

        val transport = FakeHttpTransport(
            getHandler = { HttpResponseData(200, json.encodeToString(older)) }
        )
        val syncProvider = HttpSyncProvider(transport = transport)
        val repository = NoteRepository(storage = storage, syncProvider = syncProvider)

        val result = repository.pullFromServer()
        assertTrue(result.isSuccess)
        // Remains at 15L because 5L < 15L
        assertEquals(15L, repository.snapshot.value.revision)
        assertEquals(15L, storage.storedSnapshot?.revision)
    }

    @Test
    fun testPreferencesDefaultToAutoSyncWhileTestStorageStaysIsolated() {
        val defaults = WorkspacePreferences()
        assertTrue(defaults.useRemoteHttpSync)
        assertTrue(defaults.autoSyncOnEdit)

        val isolatedRepo = NoteRepository(storage = FakeNoteStorageBackend())
        assertFalse(isolatedRepo.isRemoteSyncEnabled)
    }

    @Test
    fun testUntouchedStarterClientAdoptsRemoteWithoutPollutingWithStarterSample() = runTest {
        val remoteWorkspace = WorkspaceSnapshot(
            notebooks = listOf(
                Notebook("nb-real", "Discipleships", "", SectionAccentKey.TEAL, 0, 1762425058873L)
            ),
            sections = listOf(
                NoteSection("sec-real", "nb-real", "Kyle", SectionAccentKey.EMERALD, 0, 1762425077767L)
            ),
            pages = listOf(
                NotePage("page-real", "sec-real", title = "Notes", updatedAtEpochMs = 1795194223243L)
            ),
            revision = 24L
        )
        var postCalls = 0
        val transport = FakeHttpTransport(
            getHandler = { HttpResponseData(200, json.encodeToString(remoteWorkspace)) },
            postHandler = { _, body ->
                postCalls++
                HttpResponseData(200, body)
            }
        )
        val storage = FakeNoteStorageBackend(initial = null)
        val repository = NoteRepository(
            storage = storage,
            syncProvider = HttpSyncProvider(transport = transport)
        )

        val result = repository.syncOnLaunch(pushLocalIfAhead = true)
        assertTrue(result.isSuccess)
        assertEquals(0, postCalls, "Untouched starter seed must adopt remote without pushing sample notebooks")
        assertEquals(listOf("nb-real"), repository.snapshot.value.notebooks.map { it.id })
        assertEquals(24L, repository.snapshot.value.revision)
    }

    @Test
    fun testSyncOnLaunchPushesOfflineLocalEditsWhenAutoSyncEnabled() = runTest {
        val initial = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 5L)
        val storage = FakeNoteStorageBackend(initial)
        var pushedPayload: WorkspaceSnapshot? = null
        val transport = FakeHttpTransport(
            getHandler = { HttpResponseData(200, json.encodeToString(initial)) },
            postHandler = { _, body ->
                val decoded = json.decodeFromString<WorkspaceSnapshot>(body)
                pushedPayload = decoded
                HttpResponseData(200, json.encodeToString(decoded.copy(revision = decoded.revision + 1)))
            }
        )
        val repository = NoteRepository(
            storage = storage,
            syncProvider = HttpSyncProvider(transport = transport)
        )
        repository.createNotebook("Offline Note", "", SectionAccentKey.INDIGO)

        val result = repository.syncOnLaunch(pushLocalIfAhead = true)
        assertTrue(result.isSuccess)
        assertTrue(pushedPayload?.notebooks?.any { it.title == "Offline Note" } == true)
    }
}
