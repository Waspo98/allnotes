package app.overbay.allnotes.ui.hierarchy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
fun CollapsedWorkspaceRail(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(60.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RailTooltip("Expand sidebar") {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.ToggleSidebar) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Sidebar,
                            contentDescription = "Expand hierarchy sidebar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                RailTooltip("Search notes (Ctrl+F)") {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.RequestSearchFocus) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Search,
                            contentDescription = "Search notes (Ctrl+F)",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                RailTooltip("New Page (Ctrl+N)") {
                    Surface(
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_PAGE)) },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                AllNotesIcons.Add,
                                contentDescription = "New Page (Ctrl+N)",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.notebooks.forEach { nb ->
                        val selected = nb.id == state.activeNotebook?.id
                        RailTooltip(nb.title.ifBlank { "Notebook" }) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainer
                                    )
                                    .then(
                                        if (selected) {
                                            Modifier.border(2.dp, nb.accent.accentColor(), CircleShape)
                                        } else Modifier
                                    )
                                    .clickable { onAction(WorkspaceAction.SelectNotebook(nb.id)) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(nb.accent.accentColor())
                                )
                            }
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Spacer(modifier = Modifier.size(4.dp))
                RailTooltip("Sync workspace (${state.syncStatus.label})") {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.TriggerSync) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Sync,
                            contentDescription = "Sync workspace",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                RailTooltip("Workspace & Sync Settings") {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.SYNC_SETTINGS)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Settings,
                            contentDescription = "Workspace & Sync Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailTooltip(
    label: String,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.End,
            spacingBetweenTooltipAndAnchor = 8.dp
        ),
        tooltip = {
            PlainTooltip {
                Text(label)
            }
        },
        state = rememberTooltipState(),
        content = content
    )
}
