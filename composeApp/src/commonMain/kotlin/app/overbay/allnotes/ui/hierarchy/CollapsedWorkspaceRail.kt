package app.overbay.allnotes.ui.hierarchy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.toolbar.StudioTooltip
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor
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
            .width(64.dp),
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
                StudioTooltip("Expand sidebar", position = TooltipAnchorPosition.End) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.ToggleSidebar) },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Sidebar,
                            contentDescription = "Expand hierarchy sidebar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                StudioTooltip("Quick Jump  ·  Ctrl+P", position = TooltipAnchorPosition.End) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.QUICK_JUMP)) },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Search,
                            contentDescription = "Quick Jump (Ctrl+P)",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                StudioTooltip("New Page  ·  Ctrl+N", position = TooltipAnchorPosition.End) {
                    Surface(
                        onClick = { onAction(WorkspaceAction.CreatePageInstant(false)) },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                AllNotesIcons.EditNote,
                                contentDescription = "New Page (Ctrl+N)",
                                modifier = Modifier.size(19.dp)
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
                    val sections = state.sectionsForNotebook
                    sections.forEach { sec ->
                        val selected = sec.id == state.activeSection?.id
                        val badgeShape = MaterialTheme.shapes.small
                        val accent = sec.accent.accentColor()
                        var menuOpen by remember(sec.id) { mutableStateOf(false) }

                        Box(contentAlignment = Alignment.Center) {
                            StudioTooltip(
                                label = buildString {
                                    append(sec.title.ifBlank { "Section" })
                                    if (sec.isPinned) append("  ·  Pinned")
                                },
                                position = TooltipAnchorPosition.End
                            ) {
                                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(badgeShape)
                                            .background(
                                                if (selected) sec.accent.containerColor()
                                                else MaterialTheme.colorScheme.surfaceContainer
                                            )
                                            .border(
                                                width = if (selected) 1.5.dp else 1.dp,
                                                color = if (selected) accent else accent.copy(alpha = 0.25f),
                                                shape = badgeShape
                                            )
                                            .clickableWithContextAction(
                                                onClick = { onAction(WorkspaceAction.SelectSection(sec.id)) },
                                                onContextMenu = { menuOpen = true }
                                            )
                                    ) {
                                        Text(
                                            text = titleMonogram(sec.title, fallback = "S"),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (selected) sec.accent.contentColor()
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (sec.isPinned) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 1.dp, y = (-1).dp)
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                                .padding(1.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        ) {
                                            Icon(
                                                imageVector = AllNotesIcons.PinFilled,
                                                contentDescription = "Pinned section",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            SectionContextMenu(
                                section = sec,
                                sectionsInNotebook = sections,
                                expanded = menuOpen,
                                onDismiss = { menuOpen = false },
                                onAction = onAction
                            )
                        }
                    }

                    StudioTooltip("Manage & Reorder Sections", position = TooltipAnchorPosition.End) {
                        IconButton(
                            onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.MANAGE_SECTIONS)) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                AllNotesIcons.Add,
                                contentDescription = "Manage or add sections",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Spacer(modifier = Modifier.size(4.dp))
                StudioTooltip("Sync workspace (${state.syncStatus.label})", position = TooltipAnchorPosition.End) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.TriggerSync) },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Sync,
                            contentDescription = "Sync workspace",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                StudioTooltip("Settings & Sync", position = TooltipAnchorPosition.End) {
                    IconButton(
                        onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.SYNC_SETTINGS)) },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            AllNotesIcons.Settings,
                            contentDescription = "Settings & Sync",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}
