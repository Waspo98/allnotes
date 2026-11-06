package app.overbay.allnotes.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock

@Composable
internal fun ResponsiveTableBlockView(
    block: DocumentBlock.Table,
    onUpdateCell: (rowIndex: Int, colIndex: Int, text: String) -> Unit = { _, _, _ -> },
    onAddRow: () -> Unit = {},
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Structured Table (Screen-Fit)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    TextButton(onClick = onAddRow) {
                        Text("+ Row", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onDeleteBlock) {
                        Text("Remove", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                block.headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            block.rows.forEachIndexed { rIdx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { cIdx, cell ->
                        OutlinedTextField(
                            value = cell,
                            onValueChange = { updated -> onUpdateCell(rIdx, cIdx, updated) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
internal fun CodeSnippetBlockView(
    block: DocumentBlock.CodeSnippet,
    onUpdateText: (String) -> Unit,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CODE (${block.language})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onDeleteBlock) {
                    Text("Remove", style = MaterialTheme.typography.labelSmall)
                }
            }
            OutlinedTextField(
                value = block.code,
                onValueChange = onUpdateText,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}
