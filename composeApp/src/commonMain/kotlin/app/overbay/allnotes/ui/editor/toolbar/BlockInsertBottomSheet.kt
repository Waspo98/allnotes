package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.workspace.WorkspaceAction

private data class BlockSheetOption(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onSelect: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockInsertBottomSheet(
    afterBlockId: String?,
    showParagraphOption: Boolean = true,
    onRequestMediaPicker: () -> Unit,
    onAction: (WorkspaceAction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Insert Block",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            BlockCategoryGrid(
                title = "Text & Structure",
                options = buildList {
                    add(
                        BlockSheetOption("Heading", "Section title", AllNotesIcons.Heading) {
                            onAction(WorkspaceAction.InsertHeading(2, afterBlockId))
                        }
                    )
                    if (showParagraphOption) {
                        add(
                            BlockSheetOption("Body Text", "Rich text below block", AllNotesIcons.Paragraph) {
                                onAction(WorkspaceAction.InsertParagraph(afterBlockId))
                            }
                        )
                    }
                    add(
                        BlockSheetOption("Callout", "Highlighted note", AllNotesIcons.Lightbulb) {
                            onAction(WorkspaceAction.InsertCallout(afterBlockId))
                        }
                    )
                    add(
                        BlockSheetOption("Divider", "Visual break", AllNotesIcons.Divider) {
                            onAction(WorkspaceAction.InsertDivider(afterBlockId))
                        }
                    )
                }
            )

            BlockCategoryGrid(
                title = "Lists & Data",
                options = listOf(
                    BlockSheetOption("Bullet List", "Unordered items", AllNotesIcons.BulletList) {
                        onAction(WorkspaceAction.InsertBulletList(false, afterBlockId))
                    },
                    BlockSheetOption("Numbered List", "Ordered steps", AllNotesIcons.NumberedList) {
                        onAction(WorkspaceAction.InsertBulletList(true, afterBlockId))
                    },
                    BlockSheetOption("Checklist", "Interactive to-dos", AllNotesIcons.Checklist) {
                        onAction(WorkspaceAction.InsertChecklist(afterBlockId))
                    },
                    BlockSheetOption("Table", "Structured grid", AllNotesIcons.Table) {
                        onAction(WorkspaceAction.InsertTable(afterBlockId))
                    }
                )
            )

            BlockCategoryGrid(
                title = "Media & Code",
                options = listOf(
                    BlockSheetOption("Image or File", "Attach media", AllNotesIcons.Image) {
                        onDismiss()
                        onRequestMediaPicker()
                    },
                    BlockSheetOption("Code Block", "Monospace snippet", AllNotesIcons.Code) {
                        onAction(WorkspaceAction.InsertCode(afterBlockId))
                    }
                )
            )
        }
    }
}

@Composable
private fun BlockCategoryGrid(
    title: String,
    options: List<BlockSheetOption>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { option ->
                    Surface(
                        onClick = option.onSelect,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
