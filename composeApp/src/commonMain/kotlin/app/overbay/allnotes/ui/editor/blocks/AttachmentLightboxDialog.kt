package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.media.ImageBitmapCache
import app.overbay.allnotes.ui.media.formatAttachmentSize
import app.overbay.allnotes.ui.media.rememberPlatformAttachmentExporter
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AttachmentLightboxDialog(
    attachment: DocumentBlock.Attachment,
    onAction: (WorkspaceAction) -> Unit
) {
    val cacheKey = ImageBitmapCache.keyFor(attachment.id, attachment.dataBase64)
    val bitmap by produceState<ImageBitmap?>(initialValue = ImageBitmapCache.get(cacheKey), key1 = cacheKey) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                ImageBitmapCache.getOrDecode(cacheKey, attachment.dataBase64)
            }
        }
    }
    val exporter = rememberPlatformAttachmentExporter(
        onExported = { msg -> onAction(WorkspaceAction.ShowStatusBanner(msg)) },
        onError = { msg -> onAction(WorkspaceAction.ShowStatusBanner(msg)) }
    )

    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember(attachment.id) { Animatable(1f) }
    val offsetAnim = remember(attachment.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var viewportSize by remember(attachment.id) { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val nextScale = (scaleAnim.value * zoomChange).coerceIn(1f, 4f)
        val maxOffsetX = (viewportSize.width * (nextScale - 1f)) / 2f
        val maxOffsetY = (viewportSize.height * (nextScale - 1f)) / 2f
        val nextOffset = if (nextScale > 1.02f) {
            val raw = offsetAnim.value + panChange
            Offset(
                x = raw.x.coerceIn(-maxOffsetX, maxOffsetX),
                y = raw.y.coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            Offset.Zero
        }
        coroutineScope.launch {
            scaleAnim.snapTo(nextScale)
            offsetAnim.snapTo(nextOffset)
        }
    }

    Dialog(
        onDismissRequest = { onAction(WorkspaceAction.DismissAttachmentPreview) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.fileName.ifBlank { "Image Preview" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = attachment.caption.ifBlank { formatAttachmentSize(attachment.sizeBytes) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    exporter(attachment.fileName, attachment.mimeType, attachment.dataBase64)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(AllNotesIcons.Attach, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save")
                            }
                            IconButton(
                                onClick = { onAction(WorkspaceAction.DismissAttachmentPreview) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    AllNotesIcons.Close,
                                    contentDescription = "Close preview",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(attachment.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    coroutineScope.launch {
                                        if (scaleAnim.value > 1.2f) {
                                            launch { scaleAnim.animateTo(1f, AllNotesMotion.spatialFast()) }
                                            launch { offsetAnim.animateTo(Offset.Zero, AllNotesMotion.spatialFast()) }
                                        } else {
                                            launch { scaleAnim.animateTo(2.25f, AllNotesMotion.spatialFast()) }
                                        }
                                    }
                                }
                            )
                        }
                        .transformable(state = transformState),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = attachment.caption.ifBlank { attachment.fileName },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .graphicsLayer {
                                    scaleX = scaleAnim.value
                                    scaleY = scaleAnim.value
                                    translationX = offsetAnim.value.x
                                    translationY = offsetAnim.value.y
                                }
                        )
                    } else {
                        Text(
                            text = "Decoding image…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
