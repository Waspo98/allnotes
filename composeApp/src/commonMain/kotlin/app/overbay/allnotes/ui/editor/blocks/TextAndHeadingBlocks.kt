package app.overbay.allnotes.ui.editor.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.allNotesSpring
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
internal fun HeadingBlockContent(
    block: DocumentBlock.Heading,
    onAction: (WorkspaceAction) -> Unit
) {
    val headingStyle = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        2 -> MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        else -> MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (block.text.isEmpty()) {
                Text(
                    text = "Heading ${block.level}…",
                    style = headingStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                )
            }
            BasicTextField(
                value = block.text,
                onValueChange = { onAction(WorkspaceAction.UpdateBlockText(block.id, it)) },
                textStyle = headingStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (block.level == 1) {
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
internal fun ParagraphBlockContent(
    block: DocumentBlock.Paragraph,
    onAction: (WorkspaceAction) -> Unit
) {
    val targetBg = if (block.isHighlighted) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    val bg by animateColorAsState(targetValue = targetBg, animationSpec = allNotesSpring())

    val decorations = buildList {
        if (block.isUnderlined) add(TextDecoration.Underline)
        if (block.isStrikethrough) add(TextDecoration.LineThrough)
    }
    val combinedDecoration = if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)

    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedDecoration
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (block.isHighlighted) 12.dp else 2.dp,
                    vertical = if (block.isHighlighted) 10.dp else 4.dp
                )
        ) {
            if (block.text.isEmpty()) {
                Text(
                    text = "Type paragraph text…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
            BasicTextField(
                value = block.text,
                onValueChange = { onAction(WorkspaceAction.UpdateBlockText(block.id, it)) },
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun CalloutBlockContent(
    block: DocumentBlock.Callout,
    onAction: (WorkspaceAction) -> Unit
) {
    val (container, content, badge) = when (block.tone) {
        CalloutTone.INFO -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondary
        )
        CalloutTone.TIP -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
        CalloutTone.IMPORTANT -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AllNotesIcons.Callout,
                    contentDescription = null,
                    tint = badge,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${block.tone.label.uppercase()} · ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = badge
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (block.title.isEmpty()) {
                        Text(
                            text = "Callout title…",
                            style = MaterialTheme.typography.titleSmall,
                            color = content.copy(alpha = 0.5f)
                        )
                    }
                    BasicTextField(
                        value = block.title,
                        onValueChange = { onAction(WorkspaceAction.UpdateCalloutTitle(block.id, it)) },
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = content
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(badge),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (block.text.isEmpty()) {
                    Text(
                        text = "Add callout details…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = block.text,
                    onValueChange = { onAction(WorkspaceAction.UpdateBlockText(block.id, it)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = content),
                    cursorBrush = SolidColor(badge),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
