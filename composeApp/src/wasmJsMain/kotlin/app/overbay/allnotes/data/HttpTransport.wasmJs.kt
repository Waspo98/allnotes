package app.overbay.allnotes.data

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.xhr.XMLHttpRequest

class WasmHttpTransport(
    private val timeoutMs: Int = 15_000
) : HttpTransport {
    override suspend fun get(url: String): HttpResponseData = get(url, emptyMap())

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponseData =
        executeXhr(method = "GET", url = url, headers = headers, body = null)

    override suspend fun post(url: String, jsonBody: String): HttpResponseData = post(url, jsonBody, emptyMap())

    override suspend fun post(url: String, jsonBody: String, headers: Map<String, String>): HttpResponseData =
        executeXhr(method = "POST", url = url, headers = headers, body = jsonBody)

    private suspend fun executeXhr(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?
    ): HttpResponseData = suspendCancellableCoroutine { continuation ->
        var settled = false
        fun completeOnce(response: HttpResponseData) {
            if (!settled && continuation.isActive) {
                settled = true
                continuation.resume(response)
            }
        }

        val xhr = XMLHttpRequest()
        xhr.open(method, url, true)
        xhr.timeout = timeoutMs
        xhr.withCredentials = true
        xhr.setRequestHeader("Accept", "application/json")
        if (body != null) {
            xhr.setRequestHeader("Content-Type", "application/json; charset=utf-8")
        }
        headers.forEach { (k, v) -> xhr.setRequestHeader(k, v) }

        xhr.onload = {
            completeOnce(HttpResponseData(xhr.status.toInt(), xhr.responseText))
        }
        xhr.onerror = {
            val status = xhr.status.toInt().let { if (it == 0) 503 else it }
            completeOnce(HttpResponseData(status, xhr.responseText))
        }
        xhr.ontimeout = {
            completeOnce(HttpResponseData(504, "Request timed out"))
        }

        continuation.invokeOnCancellation {
            settled = true
            runCatching { xhr.abort() }
        }

        if (body != null) {
            xhr.send(body)
        } else {
            xhr.send()
        }
    }
}

actual fun createDefaultHttpTransport(): HttpTransport = WasmHttpTransport()
