package app.overbay.allnotes.data

import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class HttpResponseData(
    val statusCode: Int,
    val body: String
)

interface HttpTransport {
    suspend fun get(url: String): HttpResponseData
    suspend fun post(url: String, jsonBody: String): HttpResponseData
}

expect fun createDefaultHttpTransport(): HttpTransport

class HttpSyncProvider(
    override val endpointUrl: String = "http://localhost:8094/api/v1/sync",
    private val transport: HttpTransport = createDefaultHttpTransport(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) : SyncProvider {

    override suspend fun pullSnapshot(): Result<WorkspaceSnapshot> = runCatching {
        val response = transport.get(endpointUrl)
        if (response.statusCode in 200..299) {
            json.decodeFromString<WorkspaceSnapshot>(response.body)
        } else {
            error("Server GET $endpointUrl failed with HTTP ${response.statusCode}: ${response.body}")
        }
    }

    override suspend fun pushSnapshot(snapshot: WorkspaceSnapshot): Result<WorkspaceSnapshot> = runCatching {
        val payload = json.encodeToString(snapshot)
        val response = transport.post(endpointUrl, payload)
        if (response.statusCode in 200..299) {
            json.decodeFromString<WorkspaceSnapshot>(response.body)
        } else {
            error("Server POST $endpointUrl failed with HTTP ${response.statusCode}: ${response.body}")
        }
    }
}
