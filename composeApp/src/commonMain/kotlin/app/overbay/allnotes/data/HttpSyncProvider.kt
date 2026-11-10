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

    suspend fun get(url: String, headers: Map<String, String>): HttpResponseData = get(url)
    suspend fun post(url: String, jsonBody: String, headers: Map<String, String>): HttpResponseData = post(url, jsonBody)
}

expect fun createDefaultHttpTransport(): HttpTransport

class HttpSyncProvider(
    override val endpointUrl: String = "http://localhost:8094/api/v1/sync",
    private val transport: HttpTransport = createDefaultHttpTransport(),
    private val authTokenProvider: () -> String = { "" },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) : SyncProvider {

    private fun buildAuthHeaders(): Map<String, String> {
        val token = authTokenProvider().trim()
        return if (token.isNotEmpty()) {
            mapOf("Authorization" to "Bearer $token")
        } else {
            emptyMap()
        }
    }

    override suspend fun pullSnapshot(): Result<WorkspaceSnapshot> = runCatching {
        val headers = buildAuthHeaders()
        val response = if (headers.isEmpty()) transport.get(endpointUrl) else transport.get(endpointUrl, headers)
        if (response.statusCode in 200..299) {
            json.decodeFromString<WorkspaceSnapshot>(response.body)
        } else {
            error("Server GET $endpointUrl failed with HTTP ${response.statusCode}: ${response.body}")
        }
    }

    override suspend fun pushSnapshot(snapshot: WorkspaceSnapshot): Result<WorkspaceSnapshot> = runCatching {
        val payload = json.encodeToString(snapshot)
        val headers = buildAuthHeaders()
        val response = if (headers.isEmpty()) transport.post(endpointUrl, payload) else transport.post(endpointUrl, payload, headers)
        if (response.statusCode in 200..299) {
            json.decodeFromString<WorkspaceSnapshot>(response.body)
        } else {
            error("Server POST $endpointUrl failed with HTTP ${response.statusCode}: ${response.body}")
        }
    }
}
