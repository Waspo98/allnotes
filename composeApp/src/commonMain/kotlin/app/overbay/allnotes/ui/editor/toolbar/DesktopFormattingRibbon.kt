package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
fun DesktopFormattingRibbon(
    state: WorkspaceUiState,
    onRequestMediaPicker: () -> Unit,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    state.activePage ?: return
    val activeBlock = state.activeBlock
    val activeParagraph = activeBlock as? DocumentBlock.Paragraph
    val activeHeading = activeBlock as? DocumentBlock.Heading
    val activeCallout = activeBlock as? DocumentBlock.Callout
    var moreInsertOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BlockTypeConverterPill(
                activeBlock = activeBlock,
                showTooltip = true,
                onAction = onAction
            )
            ParagraphInlineFormatButtons(
                activeParagraph = activeParagraph,
                selectionStart = state.selectionStart,
                selectionEnd = state.selectionEnd,
                showShortcuts = true,
                buttonSize = 38.dp,
                onAction = onAction
            )

            if (activeHeading != null) {
                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 6.dp))
                (1..3).forEach { lvl ->
                    val selected = activeHeading.level == lvl
                    StudioTooltip(label = "Heading $lvl") {
                        Surface(
                            onClick = { onAction(WorkspaceAction.UpdateHeadingLevel(activeHeading.id, lvl)) },
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "H$lvl",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            } else if (activeCallout != null) {
                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 6.dp))
                CalloutTone.entries.forEach { tone ->
                    val selected = activeCallout.tone == tone
                    StudioTooltip(label = "Callout tone: ${tone.label}") {
                        Surface(
                            onClick = { onAction(WorkspaceAction.UpdateCalloutTone(activeCallout.id, tone)) },
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = tone.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 6.dp))

            StudioTooltip(label = "Insert Heading") {
                IconButton(onClick = { onAction(WorkspaceAction.InsertHeading(2, state.activeBlockId)) }, modifier = Modifier.size(38.dp)) {
                    Icon(AllNotesIcons.Heading, contentDescription = "Insert Heading", modifier = Modifier.size(18.dp))
                }
            }
            StudioTooltip(label = "Insert Checklist  ·  Ctrl+Shift+L") {
                IconButton(onClick = { onAction(WorkspaceAction.InsertChecklist(state.activeBlockId)) }, modifier = Modifier.size(38.dp)) {
                    Icon(AllNotesIcons.Checklist, contentDescription = "Insert Checklist", modifier = Modifier.size(18.dp))
                }
            }
            StudioTooltip(label = "Insert Bullet List") {
                IconButton(onClick = { onAction(WorkspaceAction.InsertBulletList(false, state.activeBlockId)) }, modifier = Modifier.size(38.dp)) {
                    Icon(AllNotesIcons.BulletList, contentDescription = "Insert Bullet List", modifier = Modifier.size(18.dp))
                }
            }
            StudioTooltip(label = "Insert Table") {
                IconButton(onClick = { onAction(WorkspaceAction.InsertTable(state.activeBlockId)) }, modifier = Modifier.size(38.dp)) {
                    Icon(AllNotesIcons.Table, contentDescription = "Insert Table", modifier = Modifier.size(18.dp))
                }
            }
            StudioTooltip(label = "Attach Image or File") {
                IconButton(onClick = onRequestMediaPicker, modifier = Modifier.size(38.dp)) {
                    Icon(AllNotesIcons.Image, contentDescription = "Attach Image or File", modifier = Modifier.size(18.dp))
                }
            }

            Box {
                StudioTooltip(label = "More block types") {
                    Surface(
                        onClick = { moreInsertOpen = true },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                        ) {
                            Icon(AllNotesIcons.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("More", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                DropdownMenu(
                    expanded = moreInsertOpen,
                    onDismissRequest = { moreInsertOpen = false }
                ) {
                    if (activeParagraph == null) {
                        DropdownMenuItem(
                            text = { Text("Body Text") },
                            leadingIcon = { Icon(AllNotesIcons.Paragraph, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { moreInsertOpen = false; onAction(WorkspaceAction.InsertParagraph(state.activeBlockId)) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Numbered List") },
                        leadingIcon = { Icon(AllNotesIcons.NumberedList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { moreInsertOpen = false; onAction(WorkspaceAction.InsertBulletList(true, state.activeBlockId)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Callout") },
                        leadingIcon = { Icon(AllNotesIcons.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { moreInsertOpen = false; onAction(WorkspaceAction.InsertCallout(state.activeBlockId)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Code Block") },
                        leadingIcon = { Icon(AllNotesIcons.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { moreInsertOpen = false; onAction(WorkspaceAction.InsertCode(state.activeBlockId)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Divider") },
                        leadingIcon = { Icon(AllNotesIcons.Divider, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { moreInsertOpen = false; onAction(WorkspaceAction.InsertDivider(state.activeBlockId)) }
                    )
                }
            }

            if (activeBlock != null) {
                Spacer(modifier = Modifier.weight(1f))
                VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 6.dp))
                StudioTooltip(label = "Move block up") {
                    IconButton(onClick = { onAction(WorkspaceAction.MoveBlock(activeBlock.id, -1)) }, modifier = Modifier.size(38.dp)) {
                        Icon(AllNotesIcons.ArrowUp, contentDescription = "Move block up", modifier = Modifier.size(17.dp))
                    }
                }
                StudioTooltip(label = "Move block down") {
                    IconButton(onClick = { onAction(WorkspaceAction.MoveBlock(activeBlock.id, 1)) }, modifier = Modifier.size(38.dp)) {
                        Icon(AllNotesIcons.ArrowDown, contentDescription = "Move block down", modifier = Modifier.size(17.dp))
                    }
                }
                StudioTooltip(label = "Remove block") {
                    IconButton(onClick = { onAction(WorkspaceAction.DeleteBlock(activeBlock.id)) }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            AllNotesIcons.Delete,
                            contentDescription = "Remove block",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}
