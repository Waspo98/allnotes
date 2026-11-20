package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveInsertFabMenu(
    state: WorkspaceUiState,
    onRequestMediaPicker: () -> Unit,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = state.isMobileInsertMenuOpen
    val afterId = state.activeBlockId
    val haptics = LocalHapticFeedback.current

    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = AllNotesMotion.spatialFast(),
        label = "FabIconRotation"
    )
    val fabContainerColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = AllNotesMotion.effectsFast(),
        label = "FabContainerColor"
    )
    val fabContentColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = AllNotesMotion.effectsFast(),
        label = "FabContentColor"
    )

    FloatingActionButtonMenu(
        expanded = expanded,
        horizontalAlignment = Alignment.End,
        modifier = modifier,
        button = {
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAction(WorkspaceAction.SetMobileInsertMenuOpen(!expanded))
                },
                shape = CircleShape,
                containerColor = fabContainerColor,
                contentColor = fabContentColor
            ) {
                Icon(
                    imageVector = AllNotesIcons.Add,
                    contentDescription = if (expanded) "Close insert menu" else "Insert block",
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    ) {
        FloatingActionButtonMenuItem(
            onClick = { onAction(WorkspaceAction.SetBlockInsertSheetOpen(true)) },
            icon = { Icon(AllNotesIcons.GridApps, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text("All Blocks…") }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onAction(WorkspaceAction.SetMobileInsertMenuOpen(false))
                onRequestMediaPicker()
            },
            icon = { Icon(AllNotesIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text("Image or File") }
        )
        FloatingActionButtonMenuItem(
            onClick = { onAction(WorkspaceAction.InsertChecklist(afterId)) },
            icon = { Icon(AllNotesIcons.Checklist, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text("Checklist") }
        )
        FloatingActionButtonMenuItem(
            onClick = { onAction(WorkspaceAction.InsertHeading(2, afterId)) },
            icon = { Icon(AllNotesIcons.Heading, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text("Heading") }
        )
    }
}
