@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package app.overbay.allnotes.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
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

fun formatAttachmentSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1_048_576L -> "${bytes / 1024L} KB"
    else -> {
        val tenths = ((bytes * 10L) / 1_048_576L)
        "${tenths / 10}.${tenths % 10} MB"
    }
}
