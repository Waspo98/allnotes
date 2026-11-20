package app.overbay.allnotes.ui.hierarchy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor

internal fun notebookMonogram(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "N"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}

/**
 * Tier 1 Hierarchy Anchor: Tactile full-width Notebook Switcher card with accent
 * monogram badge, subtitle, and dropdown switcher—visually distinct from Section tabs.
 */
@Composable
internal fun NotebookSwitcherCard(
    activeNotebook: Notebook?,
    notebooks: List<Notebook>,
    onSelectNotebook: (String) -> Unit,
    onEditNotebook: () -> Unit,
    onCreateNotebook: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val nb = activeNotebook ?: return

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { menuExpanded = true },
            color = colorScheme.surfaceContainerHigh,
            contentColor = colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(nb.accent.containerColor()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = notebookMonogram(nb.title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = nb.accent.contentColor()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nb.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (nb.subtitle.isNotBlank()) {
                        Text(
                            text = nb.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = AllNotesIcons.ChevronDown,
                    contentDescription = "Switch notebook",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            notebooks.forEach { item ->
                val isCurrent = item.id == nb.id
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = item.title,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(item.accent.containerColor()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = notebookMonogram(item.title),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = item.accent.contentColor()
                            )
                        }
                    },
                    trailingIcon = if (isCurrent) {
                        { Icon(AllNotesIcons.Check, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = {
                        menuExpanded = false
                        onSelectNotebook(item.id)
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Edit \"${nb.title}\"…") },
                leadingIcon = { Icon(AllNotesIcons.Edit, contentDescription = null, modifier = Modifier.size(17.dp)) },
                onClick = {
                    menuExpanded = false
                    onEditNotebook()
                }
            )
            DropdownMenuItem(
                text = { Text("New Notebook…", color = colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(AllNotesIcons.Add, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(17.dp)) },
                onClick = {
                    menuExpanded = false
                    onCreateNotebook()
                }
            )
        }
    }
}
