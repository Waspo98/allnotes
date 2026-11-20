package app.overbay.allnotes.ui.hierarchy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.data.currentEpochMillis
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.ui.editor.formatRelativeTimestamp
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import kotlinx.coroutines.launch

/**
 * Tier 3 Hierarchy Item: Airy page card with relative timestamp, smoothly rotating subpage chevron,
 * and SwipeToDismissBox gestures (swipe right to pin, swipe left to delete).
 */
@Composable
internal fun PageHierarchyItem(
    page: NotePage,
    isSelected: Boolean,
    onClick: () -> Unit,
    hasChildren: Boolean = false,
    isCollapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    searchQuery: String = "",
    onTogglePin: (() -> Unit)? = null,
    onRequestDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isSubpage = page.parentPageId != null

    val containerColorState = animateColorAsState(
        targetValue = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
        animationSpec = AllNotesMotion.effectsFast(),
        label = "PageRowBg"
    )
    val contentColor = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface

    val snippet = remember(page.id, page.updatedAtEpochMs, page.blocks) {
        page.blocks.firstNotNullOfOrNull { block ->
            when (block) {
                is DocumentBlock.Paragraph -> block.text.takeIf { it.isNotBlank() }
                is DocumentBlock.Heading -> block.text.takeIf { it.isNotBlank() }
                is DocumentBlock.Callout -> block.text.takeIf { it.isNotBlank() }
                is DocumentBlock.Checklist -> block.title.takeIf { it.isNotBlank() }
                    ?: block.items.firstOrNull { it.text.isNotBlank() }?.text
                is DocumentBlock.BulletList -> block.items.firstOrNull { it.isNotBlank() }
                is DocumentBlock.Attachment -> block.caption.ifBlank { block.fileName }.takeIf { it.isNotBlank() }
                is DocumentBlock.Divider -> null
                else -> null
            }
        } ?: "Blank page"
    }

    val relativeTime = remember(page.updatedAtEpochMs) {
        formatRelativeTimestamp(page.updatedAtEpochMs, currentEpochMillis())
    }

    val titleAnnotated = remember(page.title, searchQuery, colorScheme) {
        RichSpanEngine.buildSearchHighlightedString(
            text = page.title.ifBlank { "Untitled Page" },
            colorScheme = colorScheme,
            searchQuery = searchQuery
        )
    }
    val snippetAnnotated = remember(snippet, searchQuery, colorScheme) {
        RichSpanEngine.buildSearchHighlightedString(
            text = snippet,
            colorScheme = colorScheme,
            searchQuery = searchQuery
        )
    }

    val dismissState = rememberSwipeToDismissBoxState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (isSubpage) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSubpage) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(colorScheme.outlineVariant)
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else if (hasChildren && onToggleCollapse != null) {
            val chevronRotation by animateFloatAsState(
                targetValue = if (isCollapsed) 0f else 90f,
                animationSpec = AllNotesMotion.spatialFast(),
                label = "SubpageChevronRotation"
            )
            IconButton(
                onClick = onToggleCollapse,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = AllNotesIcons.ChevronRight,
                    contentDescription = if (isCollapsed) "Expand subpages" else "Collapse subpages",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }

        val itemShape = MaterialTheme.shapes.medium
        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier.weight(1f).clip(itemShape),
            enableDismissFromStartToEnd = onTogglePin != null,
            enableDismissFromEndToStart = onRequestDelete != null,
            onDismiss = { direction ->
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTogglePin?.invoke()
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRequestDelete?.invoke()
                    }
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                scope.launch { dismissState.reset() }
            },
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val bg = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> colorScheme.tertiaryContainer
                    SwipeToDismissBoxValue.EndToStart -> colorScheme.errorContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                }
                val fg = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> colorScheme.onTertiaryContainer
                    SwipeToDismissBoxValue.EndToStart -> colorScheme.onErrorContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                }
                val alignment = if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bg)
                        .padding(horizontal = 18.dp),
                    contentAlignment = alignment
                ) {
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Icon(
                            imageVector = if (page.isPinned) AllNotesIcons.Pin else AllNotesIcons.PinFilled,
                            contentDescription = "Pin",
                            tint = fg,
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Icon(
                            imageVector = AllNotesIcons.Delete,
                            contentDescription = "Delete",
                            tint = fg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .drawBehind {
                        val radius = 16.dp.toPx()
                        drawRoundRect(
                            color = containerColorState.value,
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    }
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = titleAnnotated,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (page.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = AllNotesIcons.PinFilled,
                            contentDescription = "Pinned",
                            tint = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = snippetAnnotated,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (relativeTime.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = relativeTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.56f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
