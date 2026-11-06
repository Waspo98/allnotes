package app.overbay.allnotes.data

import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private class MockHttpTransport(
    var getHandler: suspend (String) -> HttpResponseData = { HttpResponseData(200, "{}") },
    var postHandler: suspend (String, String) -> HttpResponseData = { _, _ -> HttpResponseData(200, "{}") }
) : HttpTransport {
    override suspend fun get(url: String): HttpResponseData = getHandler(url)
    override suspend fun post(url: String, jsonBody: String): HttpResponseData = postHandler(url, jsonBody)
}

private class MockNoteStorage(initial: WorkspaceSnapshot? = null) : NoteStorageBackend {
    var snapshot: WorkspaceSnapshot? = initial
    override fun loadSnapshot(): WorkspaceSnapshot? = snapshot
    override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        this.snapshot = snapshot
    }
}

class HttpSyncProviderAdversarialTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ========================================================
    // 1. Offline & Network Failure Stress Tests (No Unhandled Exceptions)
    // ========================================================

    @Test
    fun pullSnapshot_handlesRawNetworkExceptionsGracefully() = runTest {
        val exceptions = listOf(
            ConnectException("Connection refused: connect to 127.0.0.1:8094"),
            SocketTimeoutException("Read timed out after 8000ms"),
            IOException("Network unreachable (errno 101)"),
            IllegalStateException("Http connection pool shutdown"),
            RuntimeException("Generic platform socket crash")
        )

        for (exception in exceptions) {
            val transport = MockHttpTransport(
                getHandler = { throw exception }
            )
            val provider = HttpSyncProvider(transport = transport)

            val result = provider.pullSnapshot()
            assertTrue(result.isFailure, "Must return Result.failure for ${exception::class.simpleName}")
            assertNotNull(result.exceptionOrNull())
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun pushSnapshot_handlesRawNetworkExceptionsGracefully() = runTest {
        val current = SampleWorkspaceFactory.createStarterSnapshot()
        val exceptions = listOf(
            ConnectException("Connection refused: connect to 127.0.0.1:8094"),
            SocketTimeoutException("Read timed out after 8000ms"),
            IOException("Broken pipe"),
            RuntimeException("Connection reset by peer")
        )

        for (exception in exceptions) {
            val transport = MockHttpTransport(
                postHandler = { _, _ -> throw exception }
            )
            val provider = HttpSyncProvider(transport = transport)

            val result = provider.pushSnapshot(current)
            assertTrue(result.isFailure, "Must return Result.failure for ${exception::class.simpleName}")
            assertNotNull(result.exceptionOrNull())
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }
    }

    // ========================================================
    // 2. HTTP Error Status Code Spectrum (4xx, 5xx)
    // ========================================================

    @Test
    fun pullSnapshot_handlesAllHttpErrorCodes() = runTest {
        val errorCodes = listOf(400, 401, 403, 404, 408, 429, 500, 502, 503, 504)

        for (code in errorCodes) {
            val transport = MockHttpTransport(
                getHandler = { HttpResponseData(code, """{"error":"HTTP $code occurred"}""") }
            )
            val provider = HttpSyncProvider(transport = transport)

            val result = provider.pullSnapshot()
            assertTrue(result.isFailure, "HTTP $code must produce Result.failure")
            assertTrue(
                result.exceptionOrNull()?.message?.contains("HTTP $code") == true,
                "Exception message must mention HTTP $code"
            )
        }
    }

    @Test
    fun pushSnapshot_handlesAllHttpErrorCodes() = runTest {
        val current = SampleWorkspaceFactory.createStarterSnapshot()
        val errorCodes = listOf(400, 401, 403, 404, 422, 500, 502, 503)

        for (code in errorCodes) {
            val transport = MockHttpTransport(
                postHandler = { _, _ -> HttpResponseData(code, "Error payload $code") }
            )
            val provider = HttpSyncProvider(transport = transport)

            val result = provider.pushSnapshot(current)
            assertTrue(result.isFailure, "HTTP $code must produce Result.failure")
            assertTrue(
                result.exceptionOrNull()?.message?.contains("HTTP $code") == true,
                "Exception message must mention HTTP $code"
            )
        }
    }

    // ========================================================
    // 3. Corrupted, Truncated & Hostile JSON Payloads
    // ========================================================

    @Test
    fun pullAndPush_handlesMalformedCorruptedAndTruncatedJson() = runTest {
        val hostilePayloads = listOf(
            "", // empty body
            "   ", // whitespace only
            "<html><head><title>502 Bad Gateway</title></head><body>502 Bad Gateway</body></html>", // HTML error page
            "{\"notebooks\": [", // truncated json
            "{\"revision\": \"not-a-number\"}", // type mismatch
            "{ invalid json syntax", // syntax error
            "null", // null json
            "{\"revision\": 1, \"notebooks\": null}" // null field where list expected
        )

        for (badPayload in hostilePayloads) {
            val transport = MockHttpTransport(
                getHandler = { HttpResponseData(200, badPayload) },
                postHandler = { _, _ -> HttpResponseData(200, badPayload) }
            )
            val provider = HttpSyncProvider(transport = transport)

            val pullResult = provider.pullSnapshot()
            assertTrue(pullResult.isFailure, "pullSnapshot must fail on bad payload: $badPayload")

            val pushResult = provider.pushSnapshot(SampleWorkspaceFactory.createStarterSnapshot())
            assertTrue(pushResult.isFailure, "pushSnapshot must fail on bad payload: $badPayload")
        }
    }

    // ========================================================
    // 4. NoteRepository Sync With Server: Commit Verification
    // ========================================================

    @Test
    fun noteRepository_syncWithServer_commitsToBothStateAndStorage() = runTest {
        val initial = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 10L)
        val storage = MockNoteStorage(initial)

        val serverAcknowledged = initial.copy(
            revision = 11L,
            notebooks = initial.notebooks.map { it.copy(title = it.title + " (Synced)") }
        )

        val transport = MockHttpTransport(
            postHandler = { _, _ -> HttpResponseData(200, json.encodeToString(serverAcknowledged)) }
        )
        val repository = NoteRepository(
            storage = storage,
            syncProvider = HttpSyncProvider(transport = transport)
        )

        val result = repository.syncWithServer()
        assertTrue(result.isSuccess)
        assertEquals(serverAcknowledged, result.getOrThrow())

        // Verify _snapshot.value is committed
        assertEquals(11L, repository.snapshot.value.revision)
        assertEquals(serverAcknowledged.notebooks.first().title, repository.snapshot.value.notebooks.first().title)

        // Verify storage.saveSnapshot was committed
        assertEquals(11L, storage.snapshot?.revision)
        assertEquals(serverAcknowledged.notebooks.first().title, storage.snapshot?.notebooks?.first()?.title)
    }

    @Test
    fun noteRepository_syncWithServer_outageDoesNotCorruptLocalData() = runTest {
        val initial = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 5L)
        val storage = MockNoteStorage(initial)

        var isServerOnline = false
        val transport = MockHttpTransport(
            postHandler = { _, _ ->
                if (!isServerOnline) {
                    throw ConnectException("Connection refused: 127.0.0.1:8094")
                } else {
                    HttpResponseData(200, json.encodeToString(storage.snapshot!!.copy(revision = 100L)))
                }
            }
        )
        val repository = NoteRepository(
            storage = storage,
            syncProvider = HttpSyncProvider(transport = transport)
        )

        // 1. Attempt sync while offline
        val failedSync = repository.syncWithServer()
        assertTrue(failedSync.isFailure)

        // Verify local state and storage were untouched
        assertEquals(5L, repository.snapshot.value.revision)
        assertEquals(5L, storage.snapshot?.revision)

        // 2. Perform local operations while offline
        val nb = repository.createNotebook("Offline Vault", "Created during outage", SectionAccentKey.ROSE)
        val sec = repository.createSection(nb.id, "Offline Section", SectionAccentKey.ROSE)
        val page = repository.createPage(sec.id, "Offline Page")
        repository.appendBlock(page.id, DocumentBlock.Paragraph("blk-off", "Offline text"))

        // Local revisions must have progressed
        val offlineRev = repository.snapshot.value.revision
        assertTrue(offlineRev > 5L, "Local revision must have incremented during local edits")
        assertEquals(offlineRev, storage.snapshot?.revision)

        // 3. Second failed sync while still offline
        val failedSync2 = repository.syncWithServer()
        assertTrue(failedSync2.isFailure)
        assertEquals(offlineRev, repository.snapshot.value.revision)
        assertEquals(offlineRev, storage.snapshot?.revision)

        // 4. Server comes back online
        isServerOnline = true
        val recoverySync = repository.syncWithServer()
        assertTrue(recoverySync.isSuccess)

        // Both memory state and storage now reflect the server-acknowledged snapshot
        assertEquals(100L, repository.snapshot.value.revision)
        assertEquals(100L, storage.snapshot?.revision)
    }

    @Test
    fun noteRepository_pullFromServer_revisionComparisonRules() = runTest {
        val initial = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 50L)
        val storage = MockNoteStorage(initial)

        var remotePayload = initial.copy(revision = 40L) // Stale remote
        val transport = MockHttpTransport(
            getHandler = { HttpResponseData(200, json.encodeToString(remotePayload)) }
        )
        val repository = NoteRepository(
            storage = storage,
            syncProvider = HttpSyncProvider(transport = transport)
        )

        // 1. Pull stale revision (40 <= 50) -> ignored
        val staleResult = repository.pullFromServer()
        assertTrue(staleResult.isSuccess)
        assertEquals(50L, repository.snapshot.value.revision)
        assertEquals(50L, storage.snapshot?.revision)

        // 2. Pull equal revision (50 <= 50) -> ignored
        remotePayload = initial.copy(revision = 50L)
        val equalResult = repository.pullFromServer()
        assertTrue(equalResult.isSuccess)
        assertEquals(50L, repository.snapshot.value.revision)
        assertEquals(50L, storage.snapshot?.revision)

        // 3. Pull newer revision (51 > 50) -> accepted and committed
        remotePayload = initial.copy(revision = 51L)
        val newerResult = repository.pullFromServer()
        assertTrue(newerResult.isSuccess)
        assertEquals(51L, repository.snapshot.value.revision)
        assertEquals(51L, storage.snapshot?.revision)

        // 4. Pull fails due to server 500 error -> failure returned, local state intact
        transport.getHandler = { HttpResponseData(500, "Server error") }
        val failResult = repository.pullFromServer()
        assertTrue(failResult.isFailure)
        assertEquals(51L, repository.snapshot.value.revision)
        assertEquals(51L, storage.snapshot?.revision)
    }
}
