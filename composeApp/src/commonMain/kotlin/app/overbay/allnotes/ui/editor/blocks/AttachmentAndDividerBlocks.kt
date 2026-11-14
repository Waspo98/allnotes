package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.media.ImageBitmapCache
import app.overbay.allnotes.ui.media.formatAttachmentSize
import app.overbay.allnotes.ui.media.rememberPlatformAttachmentExporter
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DividerBlockContent(
    isActive: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalDivider(
            thickness = if (isActive) 2.dp else 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    }
}

@Composable
internal fun AttachmentBlockContent(
    block: DocumentBlock.Attachment,
    isActive: Boolean,
    onAction: (WorkspaceAction) -> Unit
) {
    val isImageMime = block.mimeType.lowercase().startsWith("image/")
    val cacheKey = ImageBitmapCache.keyFor(block.id, block.dataBase64)
    val decodedBitmap by produceState<ImageBitmap?>(
        initialValue = if (isImageMime) ImageBitmapCache.get(cacheKey) else null,
        key1 = cacheKey,
        key2 = isImageMime
    ) {
        value = if (!isImageMime || block.dataBase64.isBlank()) {
            null
        } else {
            ImageBitmapCache.get(cacheKey) ?: withContext(Dispatchers.Default) {
                ImageBitmapCache.getOrDecode(cacheKey, block.dataBase64)
            }
        }
    }

    val exporter = rememberPlatformAttachmentExporter(
        onExported = { msg -> onAction(WorkspaceAction.ShowStatusBanner(msg)) },
        onError = { msg -> onAction(WorkspaceAction.ShowStatusBanner(msg)) }
    )
    val captionHolder = rememberSyncedFieldHolder(block.id to "caption", block.caption)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val bitmap = decodedBitmap
            if (bitmap != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = block.caption.ifBlank { block.fileName },
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onAction(WorkspaceAction.OpenAttachmentPreview(block)) }
                    )
                    if (isActive && block.dataBase64.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { onAction(WorkspaceAction.OpenAttachmentPreview(block)) }
                                ) {
                                    Text("Zoom", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(
                                    onClick = { exporter(block.fileName, block.mimeType, block.dataBase64) }
                                ) {
                                    Text("Save", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isImageMime) AllNotesIcons.Image else AllNotesIcons.Attach,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = block.fileName.ifBlank { "Attachment" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isImageMime && block.dataBase64.isNotBlank()) "Loading image preview…"
                                else "${block.mimeType} · ${formatAttachmentSize(block.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (block.dataBase64.isNotBlank()) {
                        IconButton(
                            onClick = { exporter(block.fileName, block.mimeType, block.dataBase64) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Attach,
                                contentDescription = "Save attachment",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (block.caption.isNotEmpty() || isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    if (captionHolder.fieldValue.text.isEmpty()) {
                        Text(
                            text = "Add caption…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    BasicTextField(
                        value = captionHolder.fieldValue,
                        onValueChange = { updated ->
                            val changed = updated.text != captionHolder.fieldValue.text
                            captionHolder.applyLocalChange(updated)
                            if (changed) onAction(WorkspaceAction.UpdateAttachmentCaption(block.id, updated.text))
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
