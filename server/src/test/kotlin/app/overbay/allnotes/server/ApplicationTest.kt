package app.overbay.allnotes.server

import app.overbay.allnotes.data.SampleWorkspaceFactory
import app.overbay.allnotes.model.WorkspaceSnapshot
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString

class ApplicationTest {
    private lateinit var tempDir: File
    private lateinit var webDir: File
    private lateinit var server: AllNotesServer
    private var baseUrl: String = ""

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("allnotes_test").toFile()
        webDir = File(tempDir, "web").apply { mkdirs() }
        File(webDir, "index.html").writeText("<html><body>AllNotes Test</body></html>")
        File(webDir, "allnotes.js").writeText("console.log('test');")
        File(webDir, "test.wasm").writeBytes(byteArrayOf(0x00, 0x61, 0x73, 0x6d))

        // Port 0 binds to a random free ephemeral port
        server = AllNotesServer(port = 0, dataDir = tempDir, webDistDir = webDir)
        server.start()
        baseUrl = "http://127.0.0.1:${server.boundPort}"
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        tempDir.deleteRecursively()
    }

    @Test
    fun testHealthEndpointReturns200WithCors() {
        val conn = (URL("$baseUrl/api/v1/health").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
        }
        assertEquals(200, conn.responseCode)
        assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))
        assertEquals("application/json; charset=utf-8", conn.getHeaderField("Content-Type"))
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        assertTrue(body.contains("\"status\":\"ok\""))
        assertTrue(body.contains("notes.overbay.app"))
    }

    @Test
    fun testHealthOptionsReturns204WithCors() {
        val conn = (URL("$baseUrl/api/v1/health").openConnection() as HttpURLConnection).apply {
            requestMethod = "OPTIONS"
        }
        assertEquals(204, conn.responseCode)
        assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))
        assertTrue(conn.getHeaderField("Access-Control-Allow-Methods").contains("OPTIONS"))
    }

    @Test
    fun testSyncOptionsReturns204WithCors() {
        val conn = (URL("$baseUrl/api/v1/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = "OPTIONS"
        }
        assertEquals(204, conn.responseCode)
        assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))
        assertTrue(conn.getHeaderField("Access-Control-Allow-Methods").contains("POST"))
    }

    @Test
    fun testSyncGetReturnsStarterSnapshot() {
        val conn = (URL("$baseUrl/api/v1/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
        }
        assertEquals(200, conn.responseCode)
        assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val snapshot = serverJson.decodeFromString<WorkspaceSnapshot>(body)
        assertTrue(snapshot.notebooks.isNotEmpty())
        assertTrue(snapshot.revision >= 1L)
    }

    @Test
    fun testSyncPostIncrementsRevisionMonotonically() {
        val initial = SampleWorkspaceFactory.createStarterSnapshot().copy(revision = 5L)
        val conn1 = (URL("$baseUrl/api/v1/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn1.outputStream.use { it.write(serverJson.encodeToString(initial).toByteArray()) }
        assertEquals(200, conn1.responseCode)
        val body1 = conn1.inputStream.bufferedReader().use { it.readText() }
        val resp1 = serverJson.decodeFromString<WorkspaceSnapshot>(body1)
        assertEquals(6L, resp1.revision)

        // Post with stale revision (e.g. 2L), new revision should be maxOf(2, 6) + 1 = 7L
        val stale = initial.copy(revision = 2L)
        val conn2 = (URL("$baseUrl/api/v1/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn2.outputStream.use { it.write(serverJson.encodeToString(stale).toByteArray()) }
        assertEquals(200, conn2.responseCode)
        val body2 = conn2.inputStream.bufferedReader().use { it.readText() }
        val resp2 = serverJson.decodeFromString<WorkspaceSnapshot>(body2)
        assertEquals(7L, resp2.revision)
    }

    @Test
    fun testSyncPostInvalidJsonReturns400() {
        val conn = (URL("$baseUrl/api/v1/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write("{ invalid json ".toByteArray()) }
        assertEquals(400, conn.responseCode)
    }

    @Test
    fun testStaticFilesServingCorrectContentTypes() {
        val htmlConn = (URL("$baseUrl/index.html").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
        }
        assertEquals(200, htmlConn.responseCode)
        assertEquals("text/html; charset=utf-8", htmlConn.getHeaderField("Content-Type"))

        val jsConn = (URL("$baseUrl/allnotes.js").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
        }
        assertEquals(200, jsConn.responseCode)
        assertEquals("application/javascript; charset=utf-8", jsConn.getHeaderField("Content-Type"))

        val wasmConn = (URL("$baseUrl/test.wasm").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
        }
        assertEquals(200, wasmConn.responseCode)
        assertEquals("application/wasm", wasmConn.getHeaderField("Content-Type"))
    }

    @Test
    fun testAuthTokenProtectsSyncEndpointAndRejectsSpoofedHeaders() {
        val authDir = Files.createTempDirectory("allnotes_auth_test").toFile()
        val protectedServer = AllNotesServer(
            port = 0,
            dataDir = authDir,
            webDistDir = webDir,
            authToken = "overbay-secret-token-42",
            trustAuthentikForwardAuth = false
        )
        protectedServer.start()
        val authBase = "http://127.0.0.1:${protectedServer.boundPort}"
        try {
            // 1. Missing Authorization header -> 401
            val unauthConn = (URL("$authBase/api/v1/sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
            }
            assertEquals(401, unauthConn.responseCode)

            // 2. Spoofed X-authentik-username when trustAuthentikForwardAuth=false -> 401
            val spoofConn = (URL("$authBase/api/v1/sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("X-authentik-username", "neal")
            }
            assertEquals(401, spoofConn.responseCode)

            // 3. Valid Bearer token -> 200
            val validConn = (URL("$authBase/api/v1/sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer overbay-secret-token-42")
            }
            assertEquals(200, validConn.responseCode)

            // 4. Health check remains accessible for Docker health probes -> 200
            val healthConn = (URL("$authBase/api/v1/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
            }
            assertEquals(200, healthConn.responseCode)
        } finally {
            protectedServer.stop(0)
            authDir.deleteRecursively()
        }
    }
}
