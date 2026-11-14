package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.theme.AllNotesIcons
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

    FloatingActionButtonMenu(
        expanded = expanded,
        horizontalAlignment = Alignment.End,
        modifier = modifier,
        button = {
            FloatingActionButton(
                onClick = { onAction(WorkspaceAction.SetMobileInsertMenuOpen(!expanded)) },
                shape = CircleShape,
                containerColor = if (expanded) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (expanded) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            ) {
                Icon(
                    imageVector = if (expanded) AllNotesIcons.Close else AllNotesIcons.Add,
                    contentDescription = if (expanded) "Close insert menu" else "Insert block",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    onAction(WorkspaceAction.SetMobileInsertMenuOpen(false))
                    onRequestMediaPicker()
                },
                icon = { Icon(AllNotesIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Image or File") }
            )
            FloatingActionButtonMenuItem(
                onClick = { onAction(WorkspaceAction.InsertDivider(afterId)) },
                icon = { Icon(AllNotesIcons.Divider, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Divider") }
            )
            FloatingActionButtonMenuItem(
                onClick = { onAction(WorkspaceAction.InsertCode(afterId)) },
                icon = { Icon(AllNotesIcons.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Code Block") }
            )
            FloatingActionButtonMenuItem(
                onClick = { onAction(WorkspaceAction.InsertCallout(afterId)) },
                icon = { Icon(AllNotesIcons.Callout, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Callout") }
            )
            FloatingActionButtonMenuItem(
                onClick = { onAction(WorkspaceAction.InsertTable(afterId)) },
                icon = { Icon(AllNotesIcons.Table, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Table") }
            )
            FloatingActionButtonMenuItem(
                onClick = { onAction(WorkspaceAction.InsertBulletList(true, afterId)) },
                icon = { Icon(AllNotesIcons.NumberedList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Numbered List") }
            )
            FloatingActionButtonMenuItem(
                onClick = { onAction(WorkspaceAction.InsertBulletList(false, afterId)) },
                icon = { Icon(AllNotesIcons.BulletList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Bullet List") }
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
            FloatingActionButtonMenuItem(
                onClick = { onAction(WorkspaceAction.InsertParagraph(afterId)) },
                icon = { Icon(AllNotesIcons.Paragraph, contentDescription = null, modifier = Modifier.size(18.dp)) },
                text = { Text("Paragraph") }
            )
        }
    }
}
