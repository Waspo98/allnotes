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
    val webDistDir: File = File(System.getenv("WEB_DIR") ?: System.getenv("WEB_DIST_DIR") ?: "./web")
) {
    private var httpServer: HttpServer? = null
    private val snapshotLock = Any()
    val boundPort: Int get() = httpServer?.address?.port ?: port

    fun start(): HttpServer {
        dataDir.mkdirs()
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
            when (exchange.requestMethod.uppercase()) {
                "OPTIONS" -> sendNoContent(exchange)
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
                    val body = exchange.requestBody.bufferedReader(Charsets.UTF_8).readText()
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

    fun stop(delaySeconds: Int = 0) {
        httpServer?.stop(delaySeconds)
        httpServer = null
    }

    private fun serveStatic(exchange: HttpExchange) {
        val rawPath = exchange.requestURI.path.removePrefix("/")
        val reqPath = rawPath.ifEmpty { "index.html" }
        val candidate = File(webDistDir, reqPath).canonicalFile

        if (webDistDir.exists() && candidate.startsWith(webDistDir.canonicalFile) && candidate.isFile) {
            val contentType = resolveContentType(candidate.name)
            val bytes = candidate.readBytes()
            addCorsHeaders(exchange)
            exchange.responseHeaders.set("Content-Type", contentType)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } else if (webDistDir.exists() && !reqPath.contains(".") && File(webDistDir, "index.html").isFile) {
            val indexFile = File(webDistDir, "index.html")
            val bytes = indexFile.readBytes()
            addCorsHeaders(exchange)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } else if (webDistDir.exists() && rawPath.isNotEmpty()) {
            sendJson(exchange, 404, """{"error":"Asset not found: $rawPath"}""")
        } else {
            sendJson(
                exchange,
                200,
                """{"service":"AllNotes Sync & Web Host","brand":"Overbay.app","status":"ready"}"""
            )
        }
    }

    private fun resolveContentType(fileName: String): String = when {
        fileName.endsWith(".html") -> "text/html; charset=utf-8"
        fileName.endsWith(".js") -> "application/javascript; charset=utf-8"
        fileName.endsWith(".wasm") -> "application/wasm"
        fileName.endsWith(".css") -> "text/css; charset=utf-8"
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
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization")
    }

    private fun sendJson(exchange: HttpExchange, status: Int, jsonBody: String) {
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        addCorsHeaders(exchange)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendNoContent(exchange: HttpExchange) {
        addCorsHeaders(exchange)
        exchange.sendResponseHeaders(204, -1)
        exchange.responseBody.close()
    }
}

fun main() {
    val server = AllNotesServer()
    server.start()
    println("AllNotes server listening on http://0.0.0.0:${server.boundPort}")
}
