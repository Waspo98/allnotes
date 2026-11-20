@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package app.overbay.allnotes.ui.media

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayInputStream
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

                    val isImage = mime.startsWith("image/")
                    val streamLimit = if (isImage) 12_000_000L else MAX_ATTACHMENT_BYTES
                    val rawBytes = resolver.openInputStream(uri)?.use { stream ->
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = stream.read(chunk)
                            if (read <= 0) break
                            total += read
                            if (total > streamLimit) {
                                val msg = if (isImage) {
                                    "Selected photo is too large (max 12 MB before compression)."
                                } else {
                                    "File exceeds 2 MB sync limit."
                                }
                                error(msg)
                            }
                            buffer.write(chunk, 0, read)
                        }
                        buffer.toByteArray()
                    } ?: error("Unable to read selected file.")

                    val needsReencode = isImage &&
                        (rawBytes.size > 900_000 || readExifOrientation(rawBytes) != ExifInterface.ORIENTATION_NORMAL)
                    val (finalBytes, finalMime) = if (needsReencode) {
                        compressAndOrientAndroidImage(rawBytes) to "image/jpeg"
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

@Composable
actual fun rememberPlatformAttachmentExporter(
    onExported: (String) -> Unit,
    onError: (String) -> Unit
): (fileName: String, mimeType: String, dataBase64: String) -> Unit {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val currentOnExported by rememberUpdatedState(onExported)
    val currentOnError by rememberUpdatedState(onError)
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val pending = pendingExport
        pendingExport = null
        val uri = activityResult.data?.data
        if (activityResult.resultCode != Activity.RESULT_OK || uri == null || pending == null) {
            return@rememberLauncherForActivityResult
        }
        val (fileName, dataBase64) = pending
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val clean = dataBase64.substringAfter("base64,", dataBase64).trim()
                    val bytes = Base64.decode(clean)
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    } ?: error("Could not open destination stream.")
                    "Saved $fileName"
                }
            }
            result.onSuccess(currentOnExported).onFailure {
                currentOnError(it.message ?: "Failed to save attachment.")
            }
        }
    }

    return { fileName, mimeType, dataBase64 ->
        val safeName = fileName.ifBlank { "attachment" }
        pendingExport = safeName to dataBase64
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_TITLE, safeName)
        }
        launcher.launch(intent)
    }
}

private fun readExifOrientation(rawBytes: ByteArray): Int = runCatching {
    ExifInterface(ByteArrayInputStream(rawBytes)).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private fun compressAndOrientAndroidImage(rawBytes: ByteArray): ByteArray {
    val orientation = readExifOrientation(rawBytes)
    val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
    var scaled: Bitmap = decoded
    var oriented: Bitmap = decoded
    try {
        val maxDim = 1440
        val scale = minOf(1f, maxDim.toFloat() / maxOf(decoded.width, decoded.height).coerceAtLeast(1))
        scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        oriented = applyExifMatrix(scaled, orientation)
        val out = ByteArrayOutputStream()
        oriented.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (out.size() > MAX_ATTACHMENT_BYTES) {
            out.reset()
            oriented.compress(Bitmap.CompressFormat.JPEG, 64, out)
        }
        return out.toByteArray()
    } finally {
        if (oriented !== scaled && oriented !== decoded) oriented.recycle()
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
    }
}

private fun applyExifMatrix(source: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return source
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

actual fun decodeBytesToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
