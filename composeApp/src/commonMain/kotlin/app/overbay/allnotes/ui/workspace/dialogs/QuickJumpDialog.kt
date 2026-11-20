package app.overbay.allnotes.ui.workspace.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.overbay.allnotes.ui.editor.formatRelativeTimestamp
import app.overbay.allnotes.data.currentEpochMillis
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.workspace.PageSnippetExtractor
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
internal fun QuickJumpDialog(
    state: WorkspaceUiState,
    onAction: (WorkspaceAction) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val sectionMap = remember(state.sections) { state.sections.associateBy { it.id } }
    val notebookMap = remember(state.notebooks) { state.notebooks.associateBy { it.id } }

    val matches = remember(state.pages, query, sectionMap, notebookMap) {
        val q = query.trim().lowercase()
        state.pages
            .asSequence()
            .filter { page -> q.isEmpty() || PageSnippetExtractor.pageMatchesQuery(page, q) }
            .sortedWith(compareByDescending<app.overbay.allnotes.model.NotePage> { it.isPinned }.thenByDescending { it.updatedAtEpochMs })
            .take(18)
            .toList()
    }
    var selectedIdx by remember(matches.size, query) { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AllNotesIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Jump to any page across notebooks…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (matches.isNotEmpty()) selectedIdx = (selectedIdx + 1) % matches.size
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (matches.isNotEmpty()) selectedIdx = (selectedIdx - 1 + matches.size) % matches.size
                                            true
                                        }
                                        Key.Enter -> {
                                            val hit = matches.getOrNull(selectedIdx)
                                            if (hit != null) {
                                                onDismiss()
                                                onAction(WorkspaceAction.SelectPage(hit.id))
                                            }
                                            true
                                        }
                                        Key.Escape -> {
                                            onDismiss()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (matches.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching pages found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val nowMs = remember { currentEpochMillis() }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = matches,
                            key = { _, p -> p.id },
                            contentType = { _, _ -> "quick-jump-page" }
                        ) { idx, page ->
                            val highlighted = idx == selectedIdx
                            val sec = sectionMap[page.sectionId]
                            val nb = sec?.let { notebookMap[it.notebookId] }
                            Surface(
                                onClick = {
                                    onDismiss()
                                    onAction(WorkspaceAction.SelectPage(page.id))
                                },
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
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (sec != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(sec.accent.accentColor())
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = page.title.ifBlank { "Untitled Page" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (nb != null) {
                                                Text(
                                                    text = "${nb.title}  ›  ${sec.title}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (highlighted) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    val rel = formatRelativeTimestamp(page.updatedAtEpochMs, nowMs)
                                    if (rel.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = rel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (highlighted) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
