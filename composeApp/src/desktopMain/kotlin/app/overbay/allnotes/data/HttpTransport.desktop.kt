package app.overbay.allnotes.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopHttpTransport(
    private val timeoutMs: Int = 15_000
) : HttpTransport {
    override suspend fun get(url: String): HttpResponseData = get(url, emptyMap())

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponseData = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponseData(code, body)
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun post(url: String, jsonBody: String): HttpResponseData = post(url, jsonBody, emptyMap())

    override suspend fun post(url: String, jsonBody: String, headers: Map<String, String>): HttpResponseData = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponseData(code, body)
        } finally {
            conn.disconnect()
        }
    }
}

actual fun createDefaultHttpTransport(): HttpTransport = DesktopHttpTransport()
