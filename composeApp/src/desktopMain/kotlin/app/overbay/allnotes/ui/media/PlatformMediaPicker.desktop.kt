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
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

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
            val selectedFile = withContext(Dispatchers.Main) {
                val dialog = FileDialog(null as Frame?, "Attach Image or File — AllNotes", FileDialog.LOAD)
                dialog.isVisible = true
                val dir = dialog.directory
                val fileName = dialog.file
                if (dir != null && fileName != null) File(dir, fileName) else null
            } ?: return@launch

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (!selectedFile.isFile) error("Selected path is not a readable file.")
                    val rawSize = selectedFile.length()
                    if (rawSize > 12_000_000L) {
                        error("Selected file is too large (max 2 MB for non-images, 12 MB for photos).")
                    }
                    val probedMime = runCatching { Files.probeContentType(selectedFile.toPath()) }.getOrNull()
                        ?: guessMimeFromName(selectedFile.name)
                    val isImage = probedMime.startsWith("image/")
                    if (!isImage && rawSize > MAX_ATTACHMENT_BYTES) {
                        error("File exceeds 2 MB sync limit (${formatAttachmentSize(rawSize)}).")
                    }

                    val rawBytes = selectedFile.readBytes()
                    val (finalBytes, finalMime) = if (isImage && rawBytes.size > 900_000) {
                        compressDesktopImage(rawBytes) to "image/jpeg"
                    } else {
                        rawBytes to probedMime
                    }
                    if (finalBytes.size > MAX_ATTACHMENT_BYTES) {
                        error("Attachment exceeds 2 MB sync limit (${formatAttachmentSize(finalBytes.size.toLong())}).")
                    }

                    PickedAttachmentData(
                        fileName = selectedFile.name,
                        mimeType = finalMime,
                        sizeBytes = finalBytes.size.toLong(),
                        dataBase64 = Base64.encode(finalBytes)
                    )
                }
            }
            result.onSuccess(currentOnPicked).onFailure {
                currentOnError(it.message ?: "Failed to attach file.")
            }
        }
    }
}

@Composable
actual fun rememberPlatformAttachmentExporter(
    onExported: (String) -> Unit,
    onError: (String) -> Unit
): (fileName: String, mimeType: String, dataBase64: String) -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnExported by rememberUpdatedState(onExported)
    val currentOnError by rememberUpdatedState(onError)

    return { fileName, _, dataBase64 ->
        scope.launch {
            val outFile = withContext(Dispatchers.Main) {
                val dialog = FileDialog(null as Frame?, "Save Attachment — AllNotes", FileDialog.SAVE).apply {
                    file = fileName.ifBlank { "attachment" }
                    isVisible = true
                }
                val dir = dialog.directory
                val picked = dialog.file
                if (dir != null && picked != null) File(dir, picked) else null
            } ?: return@launch

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val clean = dataBase64.substringAfter("base64,", dataBase64).trim()
                    outFile.writeBytes(Base64.decode(clean))
                    "Saved ${outFile.name}"
                }
            }
            result.onSuccess(currentOnExported).onFailure {
                currentOnError(it.message ?: "Failed to save attachment.")
            }
        }
    }
}

private fun compressDesktopImage(rawBytes: ByteArray): ByteArray {
    return runCatching {
        val src = Image.makeFromEncoded(rawBytes)
        val maxDim = 1440
        val scale = minOf(1f, maxDim.toFloat() / maxOf(src.width, src.height).coerceAtLeast(1))
        val targetW = (src.width * scale).toInt().coerceAtLeast(1)
        val targetH = (src.height * scale).toInt().coerceAtLeast(1)
        val encoded = Surface.makeRasterN32Premul(targetW, targetH).use { surface ->
            val paint = Paint().apply { isAntiAlias = true }
            surface.canvas.drawImageRect(
                image = src,
                src = Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
                dst = Rect.makeWH(targetW.toFloat(), targetH.toFloat()),
                samplingMode = SamplingMode.MITCHELL,
                paint = paint,
                strict = true
            )
            paint.close()
            surface.makeImageSnapshot().use { snapshot ->
                val primary = snapshot.encodeToData(EncodedImageFormat.JPEG, 82)?.bytes
                if (primary != null && primary.size <= MAX_ATTACHMENT_BYTES) {
                    primary
                } else {
                    snapshot.encodeToData(EncodedImageFormat.JPEG, 64)?.bytes ?: rawBytes
                }
            }
        }
        src.close()
        encoded
    }.getOrDefault(rawBytes)
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
