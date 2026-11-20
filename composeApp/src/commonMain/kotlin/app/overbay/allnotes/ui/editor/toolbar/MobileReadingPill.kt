package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.PagePillStripContent
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

/**
 * Calm, unified mobile reading bottom dock combining a scrollable page switcher strip
 * (or contextual non-IME block actions) with primary navigation and 1-tap capture in the thumb zone.
 */
@Composable
fun MobileReadingPill(
    state: WorkspaceUiState,
    showBlockActions: Boolean,
    onOpenDrawer: () -> Unit,
    onNewPage: () -> Unit,
    onOpenBlockSheet: () -> Unit,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val section = state.activeSection
    val activeBlock = if (showBlockActions) state.activeBlock else null
    val hasTopStrip = activeBlock != null || section != null

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerHigh,
        contentColor = colorScheme.onSurface,
        shape = if (hasTopStrip) RoundedCornerShape(26.dp) else CircleShape,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (activeBlock != null) {
                ReadingBlockActionsStrip(
                    block = activeBlock,
                    onAction = onAction
                )
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))
            } else if (section != null) {
                PagePillStripContent(
                    pages = state.pagesForSection,
                    activePageId = state.activePage?.id,
                    sectionAccent = section.accent.accentColor(),
                    compact = true,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .padding(horizontal = 8.dp)
                )
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = AllNotesIcons.Menu,
                        contentDescription = "Browse notebooks & pages",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    onClick = onNewPage,
                    color = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = AllNotesIcons.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Page",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(
                    onClick = onOpenBlockSheet,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = AllNotesIcons.Add,
                        contentDescription = "Insert block",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingBlockActionsStrip(
    block: DocumentBlock,
    onAction: (WorkspaceAction) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val colorScheme = MaterialTheme.colorScheme
    val nonConvertibleLabel = when (block) {
        is DocumentBlock.Table -> "Table"
        is DocumentBlock.Divider -> "Divider"
        is DocumentBlock.Attachment -> "Attachment"
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (nonConvertibleLabel != null) {
            Text(
                text = nonConvertibleLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        } else {
            BlockTypeConverterPill(
                activeBlock = block,
                onAction = onAction
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onAction(WorkspaceAction.MoveBlock(block.id, -1)) },
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = AllNotesIcons.ArrowUp,
                contentDescription = "Move block up",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = { onAction(WorkspaceAction.MoveBlock(block.id, 1)) },
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = AllNotesIcons.ArrowDown,
                contentDescription = "Move block down",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = { onAction(WorkspaceAction.DeleteBlock(block.id)) },
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = AllNotesIcons.Delete,
                contentDescription = "Remove block",
                tint = colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = {
                focusManager.clearFocus(force = true)
                onAction(WorkspaceAction.SetActiveBlock(null))
            },
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = AllNotesIcons.Check,
                contentDescription = "Done selecting block",
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
