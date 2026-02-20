/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.askimo.core.chat.domain.Project
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import java.awt.Cursor

/**
 * Tab types for the side panel
 */
enum class PanelTab(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelKey: String, // Localization key instead of label
) {
    RAG_SOURCES(Icons.Default.AutoAwesome, "panel.tab.rag.sources"),
    MCP(Icons.Default.Extension, "panel.tab.mcp"),
    // Add more tabs here in the future
}

/**
 * Side panel with tabs for RAG sources, MCP, and more.
 * Shows as icon bar when collapsed, full panel when expanded.
 * Supports drag-to-resize when expanded.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun projectSidePanel(
    project: Project?,
    ragIndexingStatus: String?,
    ragIndexingPercentage: Int?,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Currently selected tab
    var selectedTab by remember { mutableStateOf(PanelTab.RAG_SOURCES) }

    // Panel width state (default 350dp, min 250dp, max 600dp)
    var panelWidth by remember { mutableStateOf(350.dp) }

    // Unified card containing both content panel and icon bar
    Card(
        modifier = modifier
            .width(if (isExpanded) panelWidth else 56.dp)
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = ComponentColors.sidebarSurfaceColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(0.dp), // No rounded corners
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Draggable resize handle (left edge) - only when expanded
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newWidth = panelWidth - dragAmount.x.toDp()
                                    // Constrain width between 250dp and 600dp
                                    panelWidth = newWidth.coerceIn(250.dp, 600.dp)
                                },
                            )
                        },
                )
            }

            // Content panel (left side) - only visible when expanded
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .weight(1f) // Take remaining space after resize handle and icon bar
                        .fillMaxHeight()
                        .padding(16.dp),
                ) {
                    // Header with title and collapse button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(selectedTab.labelKey),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )

                        IconButton(
                            onClick = { onExpandedChange(false) },
                            modifier = Modifier
                                .size(32.dp)
                                .pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = stringResource("panel.collapse"),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when (selectedTab) {
                            PanelTab.RAG_SOURCES -> {
                                ragSourcesTabContent(
                                    project = project,
                                    ragIndexingStatus = ragIndexingStatus,
                                    ragIndexingPercentage = ragIndexingPercentage,
                                )
                            }
                            PanelTab.MCP -> {
                                mcpTabContent(project = project)
                            }
                        }
                    }
                }
            }

            // Icon bar (right side) - always visible
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Tab icons
                PanelTab.entries.forEach { tab ->
                    tabIcon(
                        tab = tab,
                        isSelected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            if (!isExpanded) {
                                onExpandedChange(true)
                            }
                        },
                        ragIndexingStatus = if (tab == PanelTab.RAG_SOURCES) ragIndexingStatus else null,
                        ragIndexingPercentage = if (tab == PanelTab.RAG_SOURCES) ragIndexingPercentage else null,
                        project = project,
                    )
                }
            }
        }
    }
}

/**
 * Tab icon with tooltip and selection state
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun tabIcon(
    tab: PanelTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    ragIndexingStatus: String?,
    ragIndexingPercentage: Int?,
    project: Project?,
) {
    val tooltipText = when (tab) {
        PanelTab.RAG_SOURCES -> stringResource("panel.tab.rag.sources")
        PanelTab.MCP -> stringResource("panel.tab.mcp")
    }

    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = tooltipText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(0.dp), // No rounded shape
                )
                .clickable(
                    onClick = onClick,
                    indication = null, // Remove ripple/focus effect
                    interactionSource = remember { MutableInteractionSource() },
                )
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = stringResource(tab.labelKey),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * RAG Sources tab content
 */
@Composable
private fun ragSourcesTabContent(
    project: Project?,
    ragIndexingStatus: String?,
    ragIndexingPercentage: Int?,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // RAG Status indicator (only show if sources exist)
        if (project != null && project.knowledgeSources.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource("rag.status.icon"),
                    tint = when (ragIndexingStatus) {
                        "completed" -> MaterialTheme.colorScheme.onSurface
                        "failed" -> MaterialTheme.colorScheme.error
                        "inprogress" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(20.dp),
                )

                Text(
                    text = when (ragIndexingStatus) {
                        "started" -> stringResource("rag.status.started")
                        "inprogress" -> ragIndexingPercentage?.let {
                            stringResource("rag.status.inprogress", it)
                        } ?: stringResource("rag.status.inprogress.unknown")
                        "completed" -> stringResource("rag.status.ready")
                        "failed" -> stringResource("rag.status.failed")
                        else -> stringResource("rag.status.not.indexed")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (ragIndexingStatus) {
                        "completed" -> MaterialTheme.colorScheme.onSurface
                        "failed" -> MaterialTheme.colorScheme.error
                        "inprogress" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )

                if (ragIndexingStatus == "inprogress") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Content area
        if (project == null || project.knowledgeSources.isEmpty()) {
            // Empty state
            ragSourcesEmptyState()
        } else {
            // RAG sources tree
            ragSourcesTree(
                sources = project.knowledgeSources,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * Empty state for RAG sources
 */
@Composable
private fun ragSourcesEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = ComponentColors.tertiaryIconColor(),
            )

            Text(
                text = stringResource("rag.empty.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )

            Text(
                text = stringResource("rag.empty.description"),
                style = MaterialTheme.typography.bodySmall,
                color = ComponentColors.secondaryTextColor(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            // Placeholder for future "Add Sources" button
            Button(
                onClick = { /* TODO: Implement add sources dialog */ },
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource("rag.empty.button.add"))
            }

            Text(
                text = stringResource("rag.empty.coming.soon"),
                style = MaterialTheme.typography.labelSmall,
                color = ComponentColors.tertiaryTextColor(),
            )
        }
    }
}
