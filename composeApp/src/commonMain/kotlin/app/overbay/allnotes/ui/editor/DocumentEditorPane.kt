package app.overbay.allnotes.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.ui.theme.containerColor
import app.overbay.allnotes.ui.theme.contentColor

@Composable
fun DocumentEditorPane(
    notebook: Notebook?,
    section: NoteSection?,
    page: NotePage?,
    isCompactMobile: Boolean,
    isSidebarOpen: Boolean,
    onToggleSidebar: () -> Unit,
    onOpenMobileHierarchy: () -> Unit,
    onUpdatePageTitle: (String) -> Unit,
    onUpdateBlockText: (String, String) -> Unit,
    onToggleParagraphStyle: (String, String) -> Unit,
    onToggleChecklistItem: (String, String) -> Unit,
    onAddChecklistItem: (String, String) -> Unit,
    onDeleteBlock: (String) -> Unit,
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
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isCompactMobile) 600.dp else 900.dp)
                    .padding(horizontal = if (isCompactMobile) 12.dp else 24.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isCompactMobile) {
                            TextButton(onClick = onToggleSidebar) {
                                Text(if (isSidebarOpen) "Hide Sidebar" else "Show Sidebar")
                            }
                        } else {
                            TextButton(onClick = onOpenMobileHierarchy) {
                                Text("Browse Hierarchy")
                            }
                        }

                        if (notebook != null && section != null) {
                            Surface(
                                color = section.accent.containerColor(),
                                contentColor = section.accent.contentColor(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${notebook.title}  ›  ${section.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isCompactMobile) "Mobile Single-Screen Fit" else "Word Studio View",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isCompactMobile && page != null) {
                    WordStyleFormattingRibbon(
                        isPinned = page.isPinned,
                        onInsertHeading = onInsertHeading,
                        onInsertParagraph = onInsertParagraph,
                        onInsertChecklist = onInsertChecklist,
                        onInsertTable = onInsertTable,
                        onInsertCallout = onInsertCallout,
                        onInsertCode = onInsertCode,
                        onTogglePin = onTogglePin,
                        onDeletePage = onDeletePage
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (page == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select or create a page from the hierarchy to begin writing.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(if (isCompactMobile) 18.dp else 24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = if (isCompactMobile) 14.dp else 32.dp,
                                end = if (isCompactMobile) 14.dp else 32.dp,
                                top = if (isCompactMobile) 16.dp else 28.dp,
                                bottom = if (isCompactMobile) 96.dp else 48.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(key = "page-header-${page.id}") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = page.title,
                                        onValueChange = onUpdatePageTitle,
                                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        placeholder = { Text("Page Title") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }

                            items(page.blocks, key = { it.id }) { block ->
                                DocumentBlockView(
                                    block = block,
                                    onUpdateText = { updated -> onUpdateBlockText(block.id, updated) },
                                    onToggleParagraphStyle = { style -> onToggleParagraphStyle(block.id, style) },
                                    onToggleChecklistItem = { itemId -> onToggleChecklistItem(block.id, itemId) },
                                    onAddChecklistItem = { text -> onAddChecklistItem(block.id, text) },
                                    onDeleteBlock = { onDeleteBlock(block.id) }
                                )
                            }
                        }
                    }
                }
            }

            if (isCompactMobile && page != null) {
                MobileExpressiveFloatingToolbar(
                    onOpenHierarchy = onOpenMobileHierarchy,
                    onInsertHeading = onInsertHeading,
                    onInsertParagraph = onInsertParagraph,
                    onInsertChecklist = onInsertChecklist,
                    onInsertTable = onInsertTable,
                    onInsertCallout = onInsertCallout,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
                )
            }
        }
    }
}
