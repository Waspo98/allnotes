package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.editor.spans.FontColorOption
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.theme.isAllNotesDarkTheme
import app.overbay.allnotes.ui.workspace.WorkspaceAction

@Composable
internal fun FontColorPickerButton(
    activeParagraph: DocumentBlock.Paragraph?,
    selectionStart: Int,
    selectionEnd: Int,
    showTooltip: Boolean,
    buttonSize: Dp = 40.dp,
    onOpenCustomMenu: (() -> Unit)? = null,
    onAction: (WorkspaceAction) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isAllNotesDarkTheme()
    val activeOption = if (activeParagraph != null) {
        RichSpanEngine.activeFontColor(activeParagraph, selectionStart, selectionEnd)
    } else {
        FontColorOption.DEFAULT
    }
    val hasActiveColor = activeOption != FontColorOption.DEFAULT
    val indicatorColor = if (hasActiveColor) {
        RichSpanEngine.resolveFontColor(activeOption, colorScheme, isDark)
    } else {
        colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    val bgState = animateColorAsState(
        targetValue = if (hasActiveColor) colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = AllNotesMotion.effectsFast(),
        label = "FontColorToggleBg"
    )
    val iconTint = if (hasActiveColor) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant

    var menuExpanded by remember { mutableStateOf(false) }
    var capturedBlockId by remember { mutableStateOf<String?>(null) }
    var capturedStart by remember { mutableIntStateOf(0) }
    var capturedEnd by remember { mutableIntStateOf(0) }

    Box {
        StudioTooltip(label = "Font Color", enabled = showTooltip) {
            IconButton(
                onClick = {
                    if (onOpenCustomMenu != null) {
                        onOpenCustomMenu()
                    } else {
                        capturedBlockId = activeParagraph?.id
                        capturedStart = selectionStart
                        capturedEnd = selectionEnd
                        menuExpanded = true
                    }
                },
                modifier = Modifier
                    .size(buttonSize)
                    .focusProperties { canFocus = false }
                    .clip(MaterialTheme.shapes.extraSmall)
                    .drawBehind {
                        val r = 8.dp.toPx()
                        drawRoundRect(
                            color = bgState.value,
                            cornerRadius = CornerRadius(r, r)
                        )
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = AllNotesIcons.Palette,
                        contentDescription = "Font Color",
                        tint = iconTint,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Box(
                        modifier = Modifier
                            .width(15.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(indicatorColor)
                    )
                }
            }
        }

        if (onOpenCustomMenu == null) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                FontColorSwatchStrip(
                    selectedOption = activeOption,
                    showTooltips = showTooltip,
                    onSelect = { chosen ->
                        menuExpanded = false
                        onAction(
                            WorkspaceAction.ApplyFontColor(
                                option = chosen,
                                blockId = capturedBlockId,
                                selStart = capturedStart,
                                selEnd = capturedEnd
                            )
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
internal fun FontColorSwatchStrip(
    selectedOption: FontColorOption,
    showTooltips: Boolean,
    onSelect: (FontColorOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isAllNotesDarkTheme()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Font Color",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FontColorOption.entries.forEach { option ->
                val isSelected = option == selectedOption
                val swatchColor = RichSpanEngine.resolveFontColor(option, colorScheme, isDark)
                val isDefault = option == FontColorOption.DEFAULT

                StudioTooltip(label = option.label, enabled = showTooltips) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(if (isDefault) colorScheme.surfaceContainerLowest else swatchColor)
                            .clickable { onSelect(option) },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isDefault -> Text(
                                text = "A",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            isSelected -> Icon(
                                imageVector = AllNotesIcons.Check,
                                contentDescription = option.label,
                                tint = if (isDark) colorScheme.surface else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
