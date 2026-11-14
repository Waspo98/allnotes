@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package app.overbay.allnotes.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.overbay.allnotes.data.platformSynchronized
import kotlin.io.encoding.Base64

data class PickedAttachmentData(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dataBase64: String
)

const val MAX_ATTACHMENT_BYTES: Long = 2_097_152L // 2 MB sync-safe cap

@Composable
expect fun rememberPlatformAttachmentPicker(
    onPicked: (PickedAttachmentData) -> Unit,
    onError: (String) -> Unit = {}
): () -> Unit

@Composable
expect fun rememberPlatformAttachmentExporter(
    onExported: (String) -> Unit = {},
    onError: (String) -> Unit = {}
): (fileName: String, mimeType: String, dataBase64: String) -> Unit

expect fun decodeBytesToImageBitmap(bytes: ByteArray): ImageBitmap?

fun decodeBase64ImageBitmap(base64: String): ImageBitmap? {
    if (base64.isBlank()) return null
    return try {
        val clean = base64.substringAfter("base64,", base64).trim()
        val bytes = Base64.decode(clean)
        decodeBytesToImageBitmap(bytes)
    } catch (_: Exception) {
        null
    }
}

internal object ImageBitmapCache {
    private const val MAX_ENTRIES = 24
    private val lock = Any()
    private val entries = HashMap<String, ImageBitmap>(MAX_ENTRIES)
    private val order = ArrayDeque<String>(MAX_ENTRIES)

    fun keyFor(blockId: String, base64: String): String =
        "$blockId:${base64.length}:${base64.hashCode()}"

    fun get(key: String): ImageBitmap? = platformSynchronized(lock) { entries[key] }

    fun getOrDecode(key: String, base64: String): ImageBitmap? {
        get(key)?.let { return it }
        val decoded = decodeBase64ImageBitmap(base64) ?: return null
        platformSynchronized(lock) {
            if (!entries.containsKey(key)) {
                if (order.size >= MAX_ENTRIES) {
                    val evicted = order.removeFirst()
                    entries.remove(evicted)
                }
                order.addLast(key)
            }
            entries[key] = decoded
        }
        return decoded
    }
}

fun formatAttachmentSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1_048_576L -> "${bytes / 1024L} KB"
    else -> {
        val tenths = ((bytes * 10L) / 1_048_576L)
        "${tenths / 10}.${tenths % 10} MB"
    }
}
