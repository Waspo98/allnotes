@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package app.overbay.allnotes.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPlatformAttachmentPicker(
    onPicked: (PickedAttachmentData) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnError by rememberUpdatedState(onError)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = appContext.contentResolver
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    var displayName = "attachment"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                displayName = cursor.getString(nameIdx) ?: displayName
                            }
                        }
                    }

                    val rawBytes = resolver.openInputStream(uri)?.use { stream ->
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = stream.read(chunk)
                            if (read <= 0) break
                            total += read
                            if (total > 12_000_000L) {
                                error("Selected file is too large (max 2 MB for non-images, 12 MB for photos).")
                            }
                            buffer.write(chunk, 0, read)
                        }
                        buffer.toByteArray()
                    } ?: error("Unable to read selected file.")

                    val (finalBytes, finalMime) = if (mime.startsWith("image/") && rawBytes.size > 900_000) {
                        compressAndroidImage(rawBytes) to "image/jpeg"
                    } else {
                        rawBytes to mime
                    }

                    if (finalBytes.size > MAX_ATTACHMENT_BYTES) {
                        error("Attachment exceeds 2 MB sync limit (${formatAttachmentSize(finalBytes.size.toLong())}).")
                    }

                    PickedAttachmentData(
                        fileName = displayName,
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

    return { launcher.launch(arrayOf("*/*")) }
}

private fun compressAndroidImage(rawBytes: ByteArray): ByteArray {
    val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
    val maxDim = 1440
    val scale = minOf(1f, maxDim.toFloat() / maxOf(decoded.width, decoded.height).coerceAtLeast(1))
    val targetBitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        decoded
    }
    val out = ByteArrayOutputStream()
    targetBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return out.toByteArray()
}

actual fun decodeBytesToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
