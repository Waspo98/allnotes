package app.overbay.allnotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.workspace.SlashCommandKind

@Composable
internal fun SlashCommandPopup(
    commands: List<SlashCommandKind>,
    selectedIndex: Int = 0,
    onSelectCommand: (SlashCommandKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(selectedIndex, commands.size) {
        if (commands.isNotEmpty()) {
            val targetPx = (selectedIndex * 96).coerceAtLeast(0)
            scrollState.animateScrollTo(targetPx)
        }
    }

    AnimatedVisibility(
        visible = commands.isNotEmpty(),
        enter = expandVertically(animationSpec = AllNotesMotion.spatialFast()) + fadeIn(animationSpec = AllNotesMotion.effectsFast()),
        exit = shrinkVertically(animationSpec = AllNotesMotion.spatialFast()) + fadeOut(animationSpec = AllNotesMotion.effectsFast()),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .heightIn(max = 260.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "INSERT BLOCK (↑↓, Enter)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
                commands.forEachIndexed { idx, cmd ->
                    val highlighted = idx == selectedIndex
                    Surface(
                        onClick = { onSelectCommand(cmd) },
                        color = if (highlighted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        contentColor = if (highlighted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = cmd.icon(),
                                contentDescription = null,
                                tint = if (highlighted) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cmd.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = cmd.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (highlighted) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Text(
                                text = cmd.command,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (highlighted) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun SlashCommandKind.icon(): ImageVector = when (this) {
    SlashCommandKind.PARAGRAPH -> AllNotesIcons.Paragraph
    SlashCommandKind.HEADING_1, SlashCommandKind.HEADING_2, SlashCommandKind.HEADING_3 -> AllNotesIcons.Heading
    SlashCommandKind.CHECKLIST -> AllNotesIcons.Checklist
    SlashCommandKind.BULLET_LIST -> AllNotesIcons.BulletList
    SlashCommandKind.NUMBERED_LIST -> AllNotesIcons.NumberedList
    SlashCommandKind.TABLE -> AllNotesIcons.Table
    SlashCommandKind.CALLOUT -> AllNotesIcons.Callout
    SlashCommandKind.CODE -> AllNotesIcons.Code
    SlashCommandKind.DIVIDER -> AllNotesIcons.Divider
    SlashCommandKind.ATTACHMENT -> AllNotesIcons.Image
}
