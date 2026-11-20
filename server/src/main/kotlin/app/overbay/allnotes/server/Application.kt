package app.overbay.allnotes.server

import app.overbay.allnotes.data.SampleWorkspaceFactory
import app.overbay.allnotes.data.WorkspaceMerger
import app.overbay.allnotes.model.WorkspaceSnapshot
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val serverJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

class AllNotesServer(
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8094,
    val dataDir: File = File(System.getenv("DATA_DIR") ?: "./data").apply { mkdirs() },
    val webDistDir: File = File(System.getenv("WEB_DIR") ?: System.getenv("WEB_DIST_DIR") ?: "./web"),
    val authToken: String = System.getenv("ALLNOTES_AUTH_TOKEN")?.trim().orEmpty(),
    val trustAuthentikForwardAuth: Boolean = System.getenv("TRUST_AUTHENTIK_HEADERS")?.toBoolean() == true,
    val allowedCorsOrigins: Set<String> = parseAllowedOrigins(System.getenv("ALLNOTES_ALLOWED_ORIGINS"))
) {
    private var httpServer: HttpServer? = null
    private val snapshotLock = Any()
    private var webBundleVersion: String = "dev"
    val boundPort: Int get() = httpServer?.address?.port ?: port

    fun start(): HttpServer {
        dataDir.mkdirs()
        webBundleVersion = computeWebBundleVersion()
        val snapshotFile = File(dataDir, "workspace_server.json")

        synchronized(snapshotLock) {
            if (!snapshotFile.exists()) {
                val initial = SampleWorkspaceFactory.createStarterSnapshot()
                atomicWriteSnapshot(snapshotFile, serverJson.encodeToString(initial))
            }
        }

        val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
        server.executor = Executors.newCachedThreadPool()

        server.createContext("/api/v1/health") { exchange ->
            when (exchange.requestMethod.uppercase()) {
                "OPTIONS" -> sendNoContent(exchange)
                "GET", "HEAD" -> {
                    sendJson(exchange, 200, """{"status":"ok","service":"AllNotes","domain":"notes.overbay.app"}""")
                }
                else -> sendJson(exchange, 405, """{"error":"Method not allowed"}""")
            }
        }

        server.createContext("/api/v1/sync") { exchange ->
            val method = exchange.requestMethod.uppercase()
            if (method == "OPTIONS") {
                sendNoContent(exchange)
                return@createContext
            }
            if (!isAuthorized(exchange)) {
                exchange.responseHeaders.set("WWW-Authenticate", "Bearer realm=\"notes.overbay.app\"")
                sendJson(exchange, 401, """{"error":"Unauthorized"}""")
                return@createContext
            }
            when (method) {
                "GET" -> {
                    val payload = synchronized(snapshotLock) {
                        if (snapshotFile.exists()) {
                            snapshotFile.readText()
                        } else {
                            val starter = SampleWorkspaceFactory.createStarterSnapshot()
                            val serialized = serverJson.encodeToString(starter)
                            atomicWriteSnapshot(snapshotFile, serialized)
                            serialized
                        }
                    }
                    sendJson(exchange, 200, payload)
                }
                "POST", "PUT" -> {
                    val body = readBodyWithLimit(exchange, MAX_SYNC_BODY_BYTES)
                    if (body == null) {
                        sendJson(exchange, 413, """{"error":"Workspace snapshot payload exceeds size limit"}""")
                        return@createContext
                    }
                    try {
                        val incoming = serverJson.decodeFromString<WorkspaceSnapshot>(body)
                        val encoded = synchronized(snapshotLock) {
                            val existing = if (snapshotFile.exists()) {
                                try {
                                    serverJson.decodeFromString<WorkspaceSnapshot>(snapshotFile.readText())
                                } catch (_: Exception) { null }
                            } else null
                            val merged = if (existing != null) {
                                WorkspaceMerger.merge(existing, incoming)
                            } else {
                                incoming.copy(revision = maxOf(incoming.revision, 0L) + 1)
                            }
                            val serialized = serverJson.encodeToString(merged)
                            atomicWriteSnapshot(snapshotFile, serialized)
                            serialized
                        }
                        sendJson(exchange, 200, encoded)
                    } catch (e: Exception) {
                        sendJson(exchange, 400, """{"error":"Invalid workspace snapshot payload"}""")
                    }
                }
                else -> sendJson(exchange, 405, """{"error":"Method not allowed"}""")
            }
        }

        server.createContext("/") { exchange ->
            when (exchange.requestMethod.uppercase()) {
                "OPTIONS" -> sendNoContent(exchange)
                "GET", "HEAD" -> serveStatic(exchange)
                else -> sendJson(exchange, 405, """{"error":"Method not allowed"}""")
            }
        }

        server.start()
        httpServer = server
        return server
    }

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        if (authToken.isEmpty() && !trustAuthentikForwardAuth) return true
        if (trustAuthentikForwardAuth) {
            val authentikUser = exchange.requestHeaders.getFirst("X-authentik-username")?.trim().orEmpty()
            if (authentikUser.isNotEmpty()) return true
        }
        if (authToken.isNotEmpty()) {
            val header = exchange.requestHeaders.getFirst("Authorization")?.trim().orEmpty()
            if (header.startsWith("Bearer ", ignoreCase = true)) {
                val provided = header.substring(7).trim()
                return MessageDigest.isEqual(
                    provided.toByteArray(Charsets.UTF_8),
                    authToken.toByteArray(Charsets.UTF_8)
                )
            }
        }
        return false
    }

    fun stop(delaySeconds: Int = 0) {
        httpServer?.stop(delaySeconds)
        httpServer = null
    }

    private fun serveStatic(exchange: HttpExchange) {
        val rawPath = exchange.requestURI.path.removePrefix("/")
        val rawQuery = exchange.requestURI.rawQuery
        val reqPath = rawPath.ifEmpty { "index.html" }
        val candidate = File(webDistDir, reqPath).canonicalFile

        if (webDistDir.exists() && candidate.startsWith(webDistDir.canonicalFile) && candidate.isFile) {
            if (candidate.name == "index.html") {
                serveIndexHtml(exchange, candidate)
            } else {
                val contentType = resolveContentType(candidate.name)
                val cacheControl = resolveStaticCacheControl(candidate.name, rawQuery)
                sendBytes(exchange, 200, contentType, candidate.readBytes(), cacheControl)
            }
        } else if (webDistDir.exists() && !reqPath.contains(".") && File(webDistDir, "index.html").isFile) {
            serveIndexHtml(exchange, File(webDistDir, "index.html"))
        } else if (webDistDir.exists() && rawPath.isNotEmpty()) {
            sendJson(exchange, 404, serverJson.encodeToString(mapOf("error" to "Asset not found: $rawPath")))
        } else {
            sendJson(
                exchange,
                200,
                """{"service":"AllNotes Sync & Web Host","brand":"Overbay.app","status":"ready"}"""
            )
        }
    }

    private fun serveIndexHtml(exchange: HttpExchange, indexFile: File) {
        val rawHtml = indexFile.readText(Charsets.UTF_8)
        // Stamp entry script & service worker URLs with the bundle digest so Cloudflare/browser caches
        // never mix stale allnotes.js or sw.js with newly hashed .wasm artifacts across deploys.
        val stampedHtml = rawHtml
            .replace("""src="allnotes.js"""", """src="allnotes.js?v=$webBundleVersion"""")
            .replace("""'sw.js'""", """'sw.js?v=$webBundleVersion'""")
        sendBytes(
            exchange = exchange,
            status = 200,
            contentType = "text/html; charset=utf-8",
            bytes = stampedHtml.toByteArray(Charsets.UTF_8),
            cacheControl = NO_STORE_CACHE_CONTROL
        )
    }

    private fun computeWebBundleVersion(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var hasInputs = false
        for (name in listOf("allnotes.js", "sw.js", "index.html")) {
            val file = File(webDistDir, name)
            if (file.isFile) {
                digest.update(name.toByteArray(Charsets.UTF_8))
                digest.update(file.readBytes())
                hasInputs = true
            }
        }
        if (!hasInputs) return "dev"
        return digest.digest().take(6).joinToString("") { "%02x".format(it) }
    }

    private fun resolveStaticCacheControl(fileName: String, rawQuery: String?): String = when {
        HASHED_WASM_REGEX.matches(fileName) -> PRIVATE_IMMUTABLE_CACHE_CONTROL
        fileName == "allnotes.js" && rawQuery?.contains("v=$webBundleVersion") == true -> PRIVATE_IMMUTABLE_CACHE_CONTROL
        fileName in NO_STORE_ASSET_NAMES -> NO_STORE_CACHE_CONTROL
        else -> "private, no-cache, must-revalidate"
    }

    private fun resolveContentType(fileName: String): String = when {
        fileName.endsWith(".html") -> "text/html; charset=utf-8"
        fileName.endsWith(".js") || fileName.endsWith(".mjs") -> "application/javascript; charset=utf-8"
        fileName.endsWith(".wasm") -> "application/wasm"
        fileName.endsWith(".css") -> "text/css; charset=utf-8"
        fileName.endsWith(".ttf") -> "font/ttf"
        fileName.endsWith(".woff2") -> "font/woff2"
        fileName.endsWith(".webmanifest") -> "application/manifest+json; charset=utf-8"
        fileName.endsWith(".json") -> "application/json; charset=utf-8"
        fileName.endsWith(".svg") -> "image/svg+xml"
        fileName.endsWith(".png") -> "image/png"
        fileName.endsWith(".ico") -> "image/x-icon"
        fileName.endsWith(".map") -> "application/json"
        else -> "application/octet-stream"
    }

    private fun atomicWriteSnapshot(targetFile: File, content: String) {
        val tempFile = File(dataDir, "${targetFile.name}.tmp")
        tempFile.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun addCorsHeaders(exchange: HttpExchange) {
        val headers = exchange.responseHeaders
        val requestOrigin = exchange.requestHeaders.getFirst("Origin")?.trim()?.takeIf { it.isNotEmpty() }
        if (requestOrigin != null) {
            headers.set("Vary", "Origin")
            if (isAllowedOrigin(requestOrigin)) {
                headers.set("Access-Control-Allow-Origin", requestOrigin)
                headers.set("Access-Control-Allow-Credentials", "true")
            }
        } else {
            headers.set("Access-Control-Allow-Origin", "*")
        }
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization")
    }

    private fun isAllowedOrigin(origin: String): Boolean {
        if (origin in allowedCorsOrigins) return true
        return LOCALHOST_ORIGIN_REGEX.matches(origin)
    }

    private fun sendJson(exchange: HttpExchange, status: Int, jsonBody: String) {
        sendBytes(
            exchange = exchange,
            status = status,
            contentType = "application/json; charset=utf-8",
            bytes = jsonBody.toByteArray(Charsets.UTF_8),
            cacheControl = NO_STORE_CACHE_CONTROL
        )
    }

    private fun sendBytes(
        exchange: HttpExchange,
        status: Int,
        contentType: String,
        bytes: ByteArray,
        cacheControl: String = NO_STORE_CACHE_CONTROL
    ) {
        addCorsHeaders(exchange)
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.responseHeaders.set("Cache-Control", cacheControl)
        if (cacheControl == NO_STORE_CACHE_CONTROL) {
            exchange.responseHeaders.set("Pragma", "no-cache")
        }
        if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) {
            exchange.responseHeaders.set("Content-Length", bytes.size.toString())
            exchange.sendResponseHeaders(status, -1)
            exchange.responseBody.close()
        } else {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun sendNoContent(exchange: HttpExchange) {
        addCorsHeaders(exchange)
        exchange.sendResponseHeaders(204, -1)
        exchange.responseBody.close()
    }

    private fun readBodyWithLimit(exchange: HttpExchange, maxBytes: Int): String? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        exchange.requestBody.use { stream ->
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                total += read
                if (total > maxBytes) return null
                out.write(buf, 0, read)
            }
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    companion object {
        private const val MAX_SYNC_BODY_BYTES = 32 * 1024 * 1024
        private const val NO_STORE_CACHE_CONTROL = "private, no-cache, no-store, must-revalidate"
        private const val PRIVATE_IMMUTABLE_CACHE_CONTROL = "private, max-age=31536000, immutable"
        private val NO_STORE_ASSET_NAMES = setOf("index.html", "sw.js", "manifest.json", "allnotes.js")
        private val HASHED_WASM_REGEX = Regex("^[0-9a-f]{16,}\\.wasm$")
        private val LOCALHOST_ORIGIN_REGEX = Regex("^https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?$")

        fun parseAllowedOrigins(raw: String?): Set<String> {
            val defaults = setOf("https://notes.overbay.app")
            val extras = raw.orEmpty()
                .split(',')
                .map { it.trim().trimEnd('/') }
                .filter { it.isNotEmpty() }
            return defaults + extras
        }
    }
}

fun main() {
    val server = AllNotesServer()
    server.start()
    println("AllNotes server listening on http://0.0.0.0:${server.boundPort}")
}
