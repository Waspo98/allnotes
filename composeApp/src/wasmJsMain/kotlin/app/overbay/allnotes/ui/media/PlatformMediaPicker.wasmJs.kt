@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package app.overbay.allnotes.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.browser.document
import org.jetbrains.skia.Image
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
actual fun rememberPlatformAttachmentPicker(
    onPicked: (PickedAttachmentData) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnError by rememberUpdatedState(onError)

    return {
        try {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.onchange = {
                val file = input.files?.get(0)
                if (file != null) {
                    val size = file.size.toDouble().toLong()
                    if (size > MAX_ATTACHMENT_BYTES) {
                        currentOnError("File exceeds 2 MB sync limit (${formatAttachmentSize(size)}).")
                    } else {
                        val reader = FileReader()
                        reader.onload = {
                            val dataUrl = reader.result?.toString().orEmpty()
                            val base64 = dataUrl.substringAfter("base64,", "")
                            val mime = file.type.ifBlank { "application/octet-stream" }
                            if (base64.isNotEmpty()) {
                                currentOnPicked(
                                    PickedAttachmentData(
                                        fileName = file.name,
                                        mimeType = mime,
                                        sizeBytes = size,
                                        dataBase64 = base64
                                    )
                                )
                            } else {
                                currentOnError("Unable to encode selected file.")
                            }
                        }
                        reader.onerror = {
                            currentOnError("Browser failed to read selected file.")
                        }
                        reader.readAsDataURL(file)
                    }
                }
            }
            input.click()
        } catch (e: Exception) {
            currentOnError(e.message ?: "File picker unavailable.")
        }
    }
}

actual fun decodeBytesToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}
