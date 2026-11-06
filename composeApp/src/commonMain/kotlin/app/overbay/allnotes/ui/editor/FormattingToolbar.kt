package app.overbay.allnotes.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WordStyleFormattingRibbon(
    isPinned: Boolean,
    onInsertHeading: () -> Unit,
    onInsertParagraph: () -> Unit,
    onInsertChecklist: () -> Unit,
    onInsertTable: () -> Unit,
    onInsertCallout: () -> Unit,
    onInsertCode: () -> Unit,
    onTogglePin: () -> Unit,
    onDeletePage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = onInsertHeading,
                label = { Text("H2 Heading", fontWeight = FontWeight.SemiBold) }
            )
            AssistChip(
                onClick = onInsertParagraph,
                label = { Text("+ Paragraph") }
            )
            AssistChip(
                onClick = onInsertChecklist,
                label = { Text("+ Checklist") }
            )
            AssistChip(
                onClick = onInsertTable,
                label = { Text("+ Table") }
            )
            AssistChip(
                onClick = onInsertCallout,
                label = { Text("+ Callout") }
            )
            AssistChip(
                onClick = onInsertCode,
                label = { Text("+ Code") }
            )
            AssistChip(
                onClick = onTogglePin,
                label = { Text(if (isPinned) "Unpin Page" else "Pin Page") }
            )
            AssistChip(
                onClick = onDeletePage,
                label = { Text("Delete Page") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MobileExpressiveFloatingToolbar(
    onOpenHierarchy: () -> Unit,
    onInsertHeading: () -> Unit,
    onInsertParagraph: () -> Unit,
    onInsertChecklist: () -> Unit,
    onInsertTable: () -> Unit,
    onInsertCallout: () -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onOpenHierarchy) {
                Text("Notebooks")
            }
            AssistChip(
                onClick = onInsertHeading,
                label = { Text("H2") }
            )
            AssistChip(
                onClick = onInsertParagraph,
                label = { Text("Text") }
            )
            AssistChip(
                onClick = onInsertChecklist,
                label = { Text("To-Do") }
            )
            AssistChip(
                onClick = onInsertTable,
                label = { Text("Table") }
            )
            AssistChip(
                onClick = onInsertCallout,
                label = { Text("Callout") }
            )
        }
    }
}
