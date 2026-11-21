package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.PagePillStripContent
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

/**
 * Compact, centered floating capsule pill for mobile reading mode.
 * Hosts larger page-switcher pills with a quick `+` new-page button, and morphs
 * cleanly into contextual block actions when a non-IME block is selected.
 */
@Composable
fun MobileReadingPill(
    state: WorkspaceUiState,
    showBlockActions: Boolean,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val section = state.activeSection
    val activeBlock = if (showBlockActions) state.activeBlock else null
    if (activeBlock == null && section == null) return

    var lastActiveBlock by remember { mutableStateOf(activeBlock) }
    if (activeBlock != null) {
        lastActiveBlock = activeBlock
    }

    Surface(
        modifier = modifier,
        color = colorScheme.surfaceContainerHigh,
        contentColor = colorScheme.onSurface,
        shape = CircleShape,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        AnimatedContent(
            targetState = activeBlock != null,
            transitionSpec = {
                (fadeIn(AllNotesMotion.effectsFast()) togetherWith fadeOut(AllNotesMotion.effectsFast()))
                    .using(SizeTransform(clip = true) { _, _ -> AllNotesMotion.spatialFast() })
            },
            label = "MobileBottomPillMode"
        ) { showingBlockActions ->
            if (showingBlockActions) {
                val block = activeBlock ?: lastActiveBlock
                if (block != null) {
                    ReadingBlockActionsStrip(
                        block = block,
                        onAction = onAction
                    )
                }
            } else if (section != null) {
                PagePillStripContent(
                    pages = state.pagesForSection,
                    activePageId = state.activePage?.id,
                    sectionAccent = section.accent.accentColor(),
                    compact = true,
                    onAction = onAction,
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
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
            .heightIn(min = 52.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (nonConvertibleLabel != null) {
            Text(
                text = nonConvertibleLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        } else {
            BlockTypeConverterPill(
                activeBlock = block,
                onAction = onAction
            )
        }
        VerticalDivider(
            modifier = Modifier
                .height(22.dp)
                .padding(horizontal = 4.dp),
            color = colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
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
