package app.overbay.allnotes.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.toolbar.ExpressiveInsertFabMenu
import app.overbay.allnotes.ui.editor.toolbar.MobileFloatingToolbar
import app.overbay.allnotes.ui.editor.toolbar.MobileReadingPill
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
internal fun BoxScope.EditorStatusSnackbar(
    state: WorkspaceUiState,
    isCompactMobile: Boolean,
    onAction: (WorkspaceAction) -> Unit
) {
    AnimatedVisibility(
        visible = state.statusBannerMessage != null,
        enter = fadeIn(AllNotesMotion.effectsFast()) + slideInVertically(AllNotesMotion.spatialFast()) { it / 2 },
        exit = fadeOut(AllNotesMotion.effectsFast()) + slideOutVertically(AllNotesMotion.spatialFast()) { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = if (isCompactMobile) 84.dp else 20.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = if (state.statusBannerAction != null) 8.dp else 16.dp,
                    top = if (state.statusBannerAction != null) 4.dp else 11.dp,
                    bottom = if (state.statusBannerAction != null) 4.dp else 11.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.statusBannerMessage.orEmpty(),
                    style = MaterialTheme.typography.labelLarge
                )
                val bannerAction = state.statusBannerAction
                val bannerLabel = state.statusBannerActionLabel
                if (bannerAction != null && !bannerLabel.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            onAction(bannerAction)
                            onAction(WorkspaceAction.DismissStatusBanner)
                        }
                    ) {
                        Text(
                            text = bannerLabel,
                            color = MaterialTheme.colorScheme.inversePrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.MobileEditorChromeOverlay(
    state: WorkspaceUiState,
    isWritingMode: Boolean,
    readingPillHidden: Boolean,
    onRequestMediaPicker: () -> Unit,
    onAction: (WorkspaceAction) -> Unit
) {
    if (state.isMobileInsertMenuOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onAction(WorkspaceAction.SetMobileInsertMenuOpen(false))
                }
        )
    }

    val pillVisibility by animateFloatAsState(
        targetValue = if (!isWritingMode && readingPillHidden) 0f else 1f,
        animationSpec = AllNotesMotion.spatialFast(),
        label = "MobileChromeVisibility"
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 14.dp, start = 12.dp, end = 12.dp)
            .graphicsLayer {
                alpha = pillVisibility
                translationY = (1f - pillVisibility) * 84f
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedContent(
            targetState = isWritingMode,
            transitionSpec = {
                (fadeIn(AllNotesMotion.effectsFast()) + slideInVertically(AllNotesMotion.spatialFast()) { it / 3 }) togetherWith
                    (fadeOut(AllNotesMotion.effectsFast()) + slideOutVertically(AllNotesMotion.spatialFast()) { it / 3 })
            },
            label = "MobileModeChrome"
        ) { writing ->
            if (writing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    MobileFloatingToolbar(
                        state = state,
                        onRequestMediaPicker = onRequestMediaPicker,
                        onAction = onAction,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveInsertFabMenu(
                        state = state,
                        onRequestMediaPicker = onRequestMediaPicker,
                        onAction = onAction
                    )
                }
            } else {
                MobileReadingPill(
                    onOpenDrawer = { onAction(WorkspaceAction.SetMobileHierarchySheetOpen(true)) },
                    onNewPage = { onAction(WorkspaceAction.CreatePageInstant(false)) },
                    onOpenBlockSheet = { onAction(WorkspaceAction.SetBlockInsertSheetOpen(true)) }
                )
            }
        }
    }
}

@Composable
internal fun EmptyPageStudioState(onAction: (WorkspaceAction) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "A Quiet Desk",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pick a note from your notebooks, or start a fresh page right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            FilledTonalButton(onClick = { onAction(WorkspaceAction.CreatePageInstant(false)) }) {
                Icon(AllNotesIcons.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Page")
            }
        }
    }
}
