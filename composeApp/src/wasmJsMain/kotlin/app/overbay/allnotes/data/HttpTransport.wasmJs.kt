package app.overbay.allnotes.data

import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume

class WasmHttpTransport : HttpTransport {
    override suspend fun get(url: String): HttpResponseData = suspendCancellableCoroutine { continuation ->
        val xhr = XMLHttpRequest()
        xhr.open("GET", url, true)
        xhr.setRequestHeader("Accept", "application/json")
        xhr.onload = {
            continuation.resume(HttpResponseData(xhr.status.toInt(), xhr.responseText))
        }
        xhr.onerror = {
            continuation.resume(HttpResponseData(xhr.status.toInt().let { if (it == 0) 503 else it }, xhr.responseText))
        }
        xhr.send()
    }

    override suspend fun post(url: String, jsonBody: String): HttpResponseData = suspendCancellableCoroutine { continuation ->
        val xhr = XMLHttpRequest()
        xhr.open("POST", url, true)
        xhr.setRequestHeader("Content-Type", "application/json; charset=utf-8")
        xhr.setRequestHeader("Accept", "application/json")
        xhr.onload = {
            continuation.resume(HttpResponseData(xhr.status.toInt(), xhr.responseText))
        }
        xhr.onerror = {
            continuation.resume(HttpResponseData(xhr.status.toInt().let { if (it == 0) 503 else it }, xhr.responseText))
        }
        xhr.send(jsonBody)
    }
}

actual fun createDefaultHttpTransport(): HttpTransport = WasmHttpTransport()
