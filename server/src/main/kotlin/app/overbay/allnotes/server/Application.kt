package app.overbay.allnotes.server

import app.overbay.allnotes.data.SampleWorkspaceFactory
import app.overbay.allnotes.model.WorkspaceSnapshot
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val serverJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8094
    val dataDir = File(System.getenv("DATA_DIR") ?: "./data").apply { mkdirs() }
    val webDistDir = File(System.getenv("WEB_DIST_DIR") ?: "./web")
    val snapshotFile = File(dataDir, "workspace_server.json")

    if (!snapshotFile.exists()) {
        val initial = SampleWorkspaceFactory.createStarterSnapshot()
        snapshotFile.writeText(serverJson.encodeToString(initial))
    }

    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
    server.executor = Executors.newCachedThreadPool()

    server.createContext("/api/v1/health") { exchange ->
        sendJson(exchange, 200, """{"status":"ok","service":"AllNotes","domain":"notes.overbay.app"}""")
    }

    server.createContext("/api/v1/sync") { exchange ->
        when (exchange.requestMethod.uppercase()) {
            "GET" -> {
                val payload = if (snapshotFile.exists()) {
                    snapshotFile.readText()
                } else {
                    serverJson.encodeToString(SampleWorkspaceFactory.createStarterSnapshot())
                }
                sendJson(exchange, 200, payload)
            }
            "POST", "PUT" -> {
                val body = exchange.requestBody.bufferedReader().readText()
                try {
                    val incoming = serverJson.decodeFromString<WorkspaceSnapshot>(body)
                    val bumped = incoming.copy(revision = incoming.revision + 1)
                    val encoded = serverJson.encodeToString(bumped)
                    snapshotFile.writeText(encoded)
                    sendJson(exchange, 200, encoded)
                } catch (e: Exception) {
                    sendJson(exchange, 400, """{"error":"Invalid workspace snapshot payload"}""")
                }
            }
            else -> sendJson(exchange, 405, """{"error":"Method not allowed"}""")
        }
    }

    server.createContext("/") { exchange ->
        val reqPath = exchange.requestURI.path.removePrefix("/").ifEmpty { "index.html" }
        val candidate = File(webDistDir, reqPath).canonicalFile
        if (webDistDir.exists() && candidate.startsWith(webDistDir.canonicalFile) && candidate.isFile) {
            val contentType = when {
                candidate.name.endsWith(".html") -> "text/html; charset=utf-8"
                candidate.name.endsWith(".js") -> "application/javascript; charset=utf-8"
                candidate.name.endsWith(".wasm") -> "application/wasm"
                candidate.name.endsWith(".css") -> "text/css; charset=utf-8"
                else -> Files.probeContentType(candidate.toPath()) ?: "application/octet-stream"
            }
            val bytes = candidate.readBytes()
            exchange.responseHeaders.set("Content-Type", contentType)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } else {
            sendJson(
                exchange,
                200,
                """{"service":"AllNotes Sync & Web Host","brand":"Overbay.app","status":"ready"}"""
            )
        }
    }

    println("AllNotes server listening on http://0.0.0.0:$port")
    server.start()
}

private fun sendJson(exchange: HttpExchange, status: Int, jsonBody: String) {
    val bytes = jsonBody.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
