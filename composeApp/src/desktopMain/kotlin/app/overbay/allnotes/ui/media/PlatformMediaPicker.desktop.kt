@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package app.overbay.allnotes.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

@Composable
actual fun rememberPlatformAttachmentPicker(
    onPicked: (PickedAttachmentData) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnError by rememberUpdatedState(onError)

    return {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dialog = FileDialog(null as Frame?, "Attach Image or File — AllNotes", FileDialog.LOAD)
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val fileName = dialog.file
                    if (dir == null || fileName == null) return@withContext null

                    val file = File(dir, fileName)
                    if (!file.isFile) error("Selected path is not a readable file.")
                    if (file.length() > MAX_ATTACHMENT_BYTES) {
                        error("File exceeds 2 MB sync limit (${formatAttachmentSize(file.length())}).")
                    }
                    val bytes = file.readBytes()
                    val probedMime = runCatching { Files.probeContentType(file.toPath()) }.getOrNull()
                        ?: guessMimeFromName(file.name)

                    PickedAttachmentData(
                        fileName = file.name,
                        mimeType = probedMime,
                        sizeBytes = bytes.size.toLong(),
                        dataBase64 = Base64.encode(bytes)
                    )
                }
            }
            if (result != null) {
                result.onSuccess(currentOnPicked).onFailure {
                    currentOnError(it.message ?: "Failed to attach file.")
                }
            }
        }
    }
}

private fun guessMimeFromName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "md", "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}

actual fun decodeBytesToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}
