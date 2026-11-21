package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.MobileAccessoryMenu
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

/**
 * Compact, edge-to-edge keyboard accessory bar docked flush above the mobile soft keyboard.
 * Keeps `+` insert on the left, contextual formatting in the center, and `⋮` / `✓` pinned on the right.
 */
@Composable
fun MobileKeyboardAccessoryBar(
    state: WorkspaceUiState,
    onRequestMediaPicker: () -> Unit,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeBlock = state.activeBlock
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { onAction(WorkspaceAction.SetBlockInsertSheetOpen(true)) },
                    color = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AllNotesIcons.Add,
                            contentDescription = "Insert block",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(22.dp)
                        .padding(horizontal = 6.dp),
                    color = colorScheme.outlineVariant.copy(alpha = 0.45f)
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BlockTypeConverterPill(
                        activeBlock = activeBlock,
                        onOpenCustomMenu = {
                            val next = if (state.mobileAccessoryMenu == MobileAccessoryMenu.BLOCK_TYPE) {
                                MobileAccessoryMenu.NONE
                            } else {
                                MobileAccessoryMenu.BLOCK_TYPE
                            }
                            onAction(WorkspaceAction.SetMobileAccessoryMenu(next))
                        },
                        onAction = onAction
                    )
                    when (activeBlock) {
                        is DocumentBlock.Paragraph -> {
                            ParagraphInlineFormatButtons(
                                activeParagraph = activeBlock,
                                selectionStart = state.selectionStart,
                                selectionEnd = state.selectionEnd,
                                showShortcuts = false,
                                buttonSize = 38.dp,
                                onAction = onAction
                            )
                        }
                        is DocumentBlock.Heading -> {
                            (1..3).forEach { lvl ->
                                val selected = activeBlock.level == lvl
                                Surface(
                                    onClick = { onAction(WorkspaceAction.UpdateHeadingLevel(activeBlock.id, lvl)) },
                                    color = if (selected) colorScheme.secondaryContainer else Color.Transparent,
                                    contentColor = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = "H$lvl",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        is DocumentBlock.Checklist -> {
                            Surface(
                                onClick = { onAction(WorkspaceAction.InsertChecklistItemAfter(activeBlock.id, null)) },
                                color = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "+ Item",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                        is DocumentBlock.BulletList -> {
                            ToolbarToggleIcon(
                                icon = AllNotesIcons.BulletList,
                                contentDescription = "Bulleted list",
                                selected = !activeBlock.isNumbered,
                                buttonSize = 38.dp,
                                onClick = { if (activeBlock.isNumbered) onAction(WorkspaceAction.ToggleBulletListKind(activeBlock.id)) }
                            )
                            ToolbarToggleIcon(
                                icon = AllNotesIcons.NumberedList,
                                contentDescription = "Numbered list",
                                selected = activeBlock.isNumbered,
                                buttonSize = 38.dp,
                                onClick = { if (!activeBlock.isNumbered) onAction(WorkspaceAction.ToggleBulletListKind(activeBlock.id)) }
                            )
                        }
                        is DocumentBlock.Callout -> {
                            CalloutTone.entries.forEach { tone ->
                                val selected = activeBlock.tone == tone
                                Surface(
                                    onClick = { onAction(WorkspaceAction.UpdateCalloutTone(activeBlock.id, tone)) },
                                    color = if (selected) colorScheme.secondaryContainer else Color.Transparent,
                                    contentColor = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = tone.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            IconButton(
                                onClick = { onAction(WorkspaceAction.InsertHeading(2, state.activeBlockId)) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(AllNotesIcons.Heading, contentDescription = "Insert Heading", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { onAction(WorkspaceAction.InsertChecklist(state.activeBlockId)) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(AllNotesIcons.Checklist, contentDescription = "Insert Checklist", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { onAction(WorkspaceAction.InsertTable(state.activeBlockId)) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(AllNotesIcons.Table, contentDescription = "Insert Table", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = onRequestMediaPicker,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(AllNotesIcons.Image, contentDescription = "Attach Image or File", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(22.dp)
                        .padding(horizontal = 4.dp),
                    color = colorScheme.outlineVariant.copy(alpha = 0.45f)
                )

                if (activeBlock != null) {
                    val actionsOpen = state.mobileAccessoryMenu == MobileAccessoryMenu.BLOCK_ACTIONS
                    IconButton(
                        onClick = {
                            val next = if (actionsOpen) {
                                MobileAccessoryMenu.NONE
                            } else {
                                MobileAccessoryMenu.BLOCK_ACTIONS
                            }
                            onAction(WorkspaceAction.SetMobileAccessoryMenu(next))
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = AllNotesIcons.MoreVert,
                            contentDescription = "Block actions",
                            tint = if (actionsOpen) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        onAction(WorkspaceAction.SetActiveBlock(null))
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = AllNotesIcons.Check,
                        contentDescription = "Finish editing",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}
