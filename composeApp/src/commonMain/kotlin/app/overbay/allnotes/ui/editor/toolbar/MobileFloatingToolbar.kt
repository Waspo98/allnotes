package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.CalloutTone
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
    var blockActionsOpen by remember { mutableStateOf(false) }

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
                    Box {
                        IconButton(
                            onClick = { blockActionsOpen = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = AllNotesIcons.MoreVert,
                                contentDescription = "Block actions",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = blockActionsOpen,
                            onDismissRequest = { blockActionsOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Move Up") },
                                leadingIcon = { Icon(AllNotesIcons.ArrowUp, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    blockActionsOpen = false
                                    onAction(WorkspaceAction.MoveBlock(activeBlock.id, -1))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move Down") },
                                leadingIcon = { Icon(AllNotesIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    blockActionsOpen = false
                                    onAction(WorkspaceAction.MoveBlock(activeBlock.id, 1))
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Remove Block", color = colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        AllNotesIcons.Delete,
                                        contentDescription = null,
                                        tint = colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    blockActionsOpen = false
                                    onAction(WorkspaceAction.DeleteBlock(activeBlock.id))
                                }
                            )
                        }
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
