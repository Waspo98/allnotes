package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MobileFloatingToolbar(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeParagraph = state.activeBlock as? DocumentBlock.Paragraph

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { onAction(WorkspaceAction.SetMobileHierarchySheetOpen(true)) }
            ) {
                Icon(AllNotesIcons.Sidebar, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Notebooks")
            }

            if (activeParagraph != null) {
                VerticalDivider(modifier = Modifier.height(22.dp))
                FilterChip(
                    selected = activeParagraph.isBold,
                    onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(activeParagraph.id, "bold")) },
                    label = { Text("B", fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = activeParagraph.isItalic,
                    onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(activeParagraph.id, "italic")) },
                    label = { Text("I", fontStyle = FontStyle.Italic) }
                )
                FilterChip(
                    selected = activeParagraph.isUnderlined,
                    onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(activeParagraph.id, "underline")) },
                    label = { Text("U", textDecoration = TextDecoration.Underline) }
                )
                FilterChip(
                    selected = activeParagraph.isStrikethrough,
                    onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(activeParagraph.id, "strikethrough")) },
                    label = { Text("S", textDecoration = TextDecoration.LineThrough) }
                )
                FilterChip(
                    selected = activeParagraph.isHighlighted,
                    onClick = { onAction(WorkspaceAction.ToggleParagraphStyle(activeParagraph.id, "highlight")) },
                    label = { Text("Mark") }
                )
            }

            VerticalDivider(modifier = Modifier.height(22.dp))

            AssistChip(
                onClick = { onAction(WorkspaceAction.InsertHeading(2, state.activeBlockId)) },
                leadingIcon = { Icon(AllNotesIcons.Heading, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("H2") }
            )
            AssistChip(
                onClick = { onAction(WorkspaceAction.InsertParagraph(state.activeBlockId)) },
                leadingIcon = { Icon(AllNotesIcons.Paragraph, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("Text") }
            )
            AssistChip(
                onClick = { onAction(WorkspaceAction.InsertChecklist(state.activeBlockId)) },
                leadingIcon = { Icon(AllNotesIcons.Checklist, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("To-Do") }
            )
            AssistChip(
                onClick = { onAction(WorkspaceAction.InsertBulletList(false, state.activeBlockId)) },
                leadingIcon = { Icon(AllNotesIcons.BulletList, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("List") }
            )
            AssistChip(
                onClick = { onAction(WorkspaceAction.InsertBulletList(true, state.activeBlockId)) },
                leadingIcon = { Icon(AllNotesIcons.BulletList, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("1. List") }
            )
            AssistChip(
                onClick = { onAction(WorkspaceAction.InsertTable(state.activeBlockId)) },
                leadingIcon = { Icon(AllNotesIcons.Table, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("Table") }
            )
            AssistChip(
                onClick = { onAction(WorkspaceAction.InsertCallout(state.activeBlockId)) },
                leadingIcon = { Icon(AllNotesIcons.Callout, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("Callout") }
            )
            AssistChip(
                onClick = { onAction(WorkspaceAction.InsertCode(state.activeBlockId)) },
                leadingIcon = { Icon(AllNotesIcons.Code, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("Code") }
            )
            AssistChip(
                onClick = { onAction(WorkspaceAction.DeleteActivePage) },
                leadingIcon = {
                    Icon(
                        AllNotesIcons.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                },
                label = { Text("Delete Page", color = MaterialTheme.colorScheme.error) }
            )
        }
    }
}
