package app.overbay.allnotes.ui.editor.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.workspace.MobileAccessoryMenu
import app.overbay.allnotes.ui.workspace.SlashCommandKind
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

/**
 * In-window anchored menu overlay for [MobileKeyboardAccessoryBar].
 * Renders inside the editor root window instead of a focusable Android PopupWindow so
 * opening block actions or block-type conversion never steals focus or collapses the IME.
 */
@Composable
internal fun BoxScope.MobileAccessoryMenuOverlay(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit
) {
    val activeBlock = state.activeBlock
    val isVisible = state.mobileAccessoryMenu != MobileAccessoryMenu.NONE && activeBlock != null
    var lastMenuKind by remember { mutableStateOf(MobileAccessoryMenu.BLOCK_ACTIONS) }
    if (state.mobileAccessoryMenu != MobileAccessoryMenu.NONE) {
        lastMenuKind = state.mobileAccessoryMenu
    }

    val dismissMenu = {
        onAction(WorkspaceAction.SetMobileAccessoryMenu(MobileAccessoryMenu.NONE))
    }

    if (isVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 49.dp)
                .pointerInput(Unit) {
                    detectTapGestures { dismissMenu() }
                }
        )
    }

    val menuAlignment = when (lastMenuKind) {
        MobileAccessoryMenu.BLOCK_TYPE -> Alignment.BottomStart
        else -> Alignment.BottomEnd
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(AllNotesMotion.effectsFast()) + slideInVertically(AllNotesMotion.spatialFast()) { it / 5 },
        exit = fadeOut(AllNotesMotion.effectsFast()) + slideOutVertically(AllNotesMotion.spatialFast()) { it / 5 },
        modifier = Modifier
            .align(menuAlignment)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 56.dp)
    ) {
        if (activeBlock != null) {
            val colorScheme = MaterialTheme.colorScheme
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerHigh,
                contentColor = colorScheme.onSurface,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.widthIn(min = 196.dp, max = 248.dp)
            ) {
                when (lastMenuKind) {
                    MobileAccessoryMenu.BLOCK_ACTIONS -> {
                        val blocks = state.activePage?.blocks.orEmpty()
                        val blockIdx = blocks.indexOfFirst { it.id == activeBlock.id }
                        val canMoveUp = blockIdx > 0
                        val canMoveDown = blockIdx in 0 until blocks.lastIndex

                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            MobileAccessoryMenuRow(
                                label = "Move Up",
                                icon = AllNotesIcons.ArrowUp,
                                enabled = canMoveUp,
                                onClick = {
                                    dismissMenu()
                                    onAction(WorkspaceAction.MoveBlock(activeBlock.id, -1))
                                }
                            )
                            MobileAccessoryMenuRow(
                                label = "Move Down",
                                icon = AllNotesIcons.ArrowDown,
                                enabled = canMoveDown,
                                onClick = {
                                    dismissMenu()
                                    onAction(WorkspaceAction.MoveBlock(activeBlock.id, 1))
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                            MobileAccessoryMenuRow(
                                label = "Remove Block",
                                icon = AllNotesIcons.Delete,
                                tint = colorScheme.error,
                                onClick = {
                                    dismissMenu()
                                    onAction(WorkspaceAction.DeleteBlock(activeBlock.id))
                                }
                            )
                        }
                    }
                    MobileAccessoryMenu.BLOCK_TYPE -> {
                        val targets = if (activeBlock is DocumentBlock.Paragraph) {
                            ConvertibleBlockTargets.filterNot { (kind, _) -> kind == SlashCommandKind.PARAGRAPH }
                        } else {
                            ConvertibleBlockTargets
                        }
                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 6.dp)
                        ) {
                            targets.forEach { (kind, icon) ->
                                MobileAccessoryMenuRow(
                                    label = kind.title,
                                    icon = icon,
                                    onClick = {
                                        dismissMenu()
                                        onAction(WorkspaceAction.ConvertActiveBlock(kind))
                                    }
                                )
                            }
                        }
                    }
                    MobileAccessoryMenu.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun MobileAccessoryMenuRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val effectiveTint = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effectiveTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = effectiveTint
        )
    }
}
