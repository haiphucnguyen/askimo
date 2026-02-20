/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ExtensionOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.langchain4j.agent.tool.ToolSpecification
import io.askimo.core.chat.domain.Project
import io.askimo.core.mcp.ProjectMcpInstance
import io.askimo.core.mcp.ProjectMcpInstanceService
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext as KoinGlobalContext

/**
 * Shows list of MCP instances and their available tools.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun mcpTabContent(project: Project?) {
    if (project == null) {
        mcpEmptyState()
        return
    }

    val mcpService = remember { KoinGlobalContext.get().get<ProjectMcpInstanceService>() }
    var instances by remember(project.id) { mutableStateOf<List<ProjectMcpInstance>>(emptyList()) }
    var selectedInstanceId by remember { mutableStateOf<String?>(null) }
    var instanceTools by remember { mutableStateOf<Map<String, List<ToolSpecification>>>(emptyMap()) }
    var loadingTools by remember { mutableStateOf<Set<String>>(emptySet()) }
    var failedInstances by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTool by remember { mutableStateOf<Pair<String, String>?>(null) } // instanceId to toolName
    val coroutineScope = rememberCoroutineScope()

    // Load instances when project changes
    LaunchedEffect(project.id) {
        instances = withContext(Dispatchers.IO) {
            mcpService.getInstances(project.id)
        }
    }

    if (instances.isEmpty()) {
        // No MCP instances configured
        mcpNoInstancesState()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // MCP Instances list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(instances) { instance ->
                mcpInstanceItem(
                    instance = instance,
                    isSelected = selectedInstanceId == instance.id,
                    isExpanded = selectedInstanceId == instance.id,
                    isLoadingTools = loadingTools.contains(instance.id),
                    hasFailed = failedInstances.contains(instance.id),
                    tools = instanceTools[instance.id] ?: emptyList(),
                    selectedTool = selectedTool,
                    onToolSelected = { toolName ->
                        selectedTool = instance.id to toolName
                    },
                    onClick = {
                        if (selectedInstanceId == instance.id) {
                            // Collapse
                            selectedInstanceId = null
                        } else {
                            // Expand and load tools
                            selectedInstanceId = instance.id

                            // If failed before, allow retry by clearing failed state
                            if (failedInstances.contains(instance.id)) {
                                failedInstances = failedInstances - instance.id
                            }

                            if (instanceTools[instance.id] == null &&
                                !loadingTools.contains(instance.id) &&
                                !failedInstances.contains(instance.id)
                            ) {
                                // Load tools for this instance
                                loadingTools = loadingTools + instance.id
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        // Add timeout to prevent hanging
                                        kotlinx.coroutines.withTimeout(15000L) {
                                            // 15 second timeout
                                            val client = mcpService.createMcpClient(instance, "ui_${instance.id}")
                                            if (client != null) {
                                                val tools = client.listTools()
                                                // Update UI state - no need for withContext since we're already in a coroutine
                                                instanceTools = instanceTools + (instance.id to tools)
                                                loadingTools = loadingTools - instance.id
                                            } else {
                                                loadingTools = loadingTools - instance.id
                                                failedInstances = failedInstances + instance.id
                                            }
                                        }
                                    } catch (e: TimeoutCancellationException) {
                                        // Connection timeout
                                        loadingTools = loadingTools - instance.id
                                        failedInstances = failedInstances + instance.id
                                    } catch (e: Exception) {
                                        // Other errors (connection failed, etc.)
                                        loadingTools = loadingTools - instance.id
                                        failedInstances = failedInstances + instance.id
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * MCP instance item with expandable tools list
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun mcpInstanceItem(
    instance: ProjectMcpInstance,
    isSelected: Boolean,
    isExpanded: Boolean,
    isLoadingTools: Boolean,
    hasFailed: Boolean,
    tools: List<ToolSpecification>,
    selectedTool: Pair<String, String>?,
    onToolSelected: (String) -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Instance header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(4.dp),
                )
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                )
                .padding(8.dp)
                .pointerHoverIcon(PointerIcon.Hand),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Expand/collapse icon
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = if (isExpanded) {
                    stringResource("rag.tree.collapse")
                } else {
                    stringResource("rag.tree.expand")
                },
                tint = ComponentColors.secondaryIconColor(),
                modifier = Modifier.size(20.dp),
            )

            // Instance icon
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = if (instance.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    ComponentColors.secondaryIconColor()
                },
                modifier = Modifier.size(20.dp),
            )

            // Instance name and status
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = instance.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!instance.enabled) {
                    Text(
                        text = stringResource("mcp.instance.disabled"),
                        style = MaterialTheme.typography.bodySmall,
                        color = ComponentColors.secondaryTextColor(),
                    )
                }
            }

            // Tool count badge (when tools loaded)
            if (tools.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "${tools.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        // Tools list (when expanded)
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when {
                    isLoadingTools -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource("mcp.tools.loading"),
                                style = MaterialTheme.typography.bodySmall,
                                color = ComponentColors.secondaryTextColor(),
                            )
                        }
                    }
                    hasFailed -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource("mcp.tools.failed"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    tools.isEmpty() -> {
                        Text(
                            text = stringResource("mcp.tools.none"),
                            style = MaterialTheme.typography.bodySmall,
                            color = ComponentColors.secondaryTextColor(),
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    else -> {
                        tools.forEach { tool ->
                            toolItem(
                                tool = tool,
                                instanceId = instance.id,
                                isSelected = selectedTool?.first == instance.id && selectedTool.second == tool.name(),
                                onSelected = { onToolSelected(tool.name()) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual tool item with selection support and detailed tooltip
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun toolItem(
    tool: ToolSpecification,
    instanceId: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.widthIn(max = 500.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Tool name
                    Text(
                        text = tool.name(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Tool description
                    tool.description()?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Parameters section
                    if (tool.parameters() != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        Text(
                            text = stringResource("mcp.tool.parameters"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Show parameter structure (simplified)
                        val params = tool.parameters()
                        if (params != null) {
                            val properties = params.properties()
                            if (properties != null && properties.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(start = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    properties.entries.take(5).forEach { (paramName, _) ->
                                        Text(
                                            text = "• $paramName",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ComponentColors.secondaryTextColor(),
                                        )
                                    }
                                    if (properties.size > 5) {
                                        Text(
                                            text = "... and ${properties.size - 5} more",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ComponentColors.tertiaryTextColor(),
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(4.dp),
                )
                .clickable(
                    onClick = onSelected,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                )
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .pointerHoverIcon(PointerIcon.Hand),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )

            Text(
                text = tool.name(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Empty state when no project is selected
 */
@Composable
private fun mcpEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = ComponentColors.tertiaryIconColor(),
            )

            Text(
                text = stringResource("mcp.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = stringResource("mcp.description"),
                style = MaterialTheme.typography.bodySmall,
                color = ComponentColors.secondaryTextColor(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Empty state when no MCP instances configured
 */
@Composable
private fun mcpNoInstancesState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ExtensionOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = ComponentColors.tertiaryIconColor(),
            )

            Text(
                text = stringResource("mcp.no.instances.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = stringResource("mcp.no.instances.description"),
                style = MaterialTheme.typography.bodySmall,
                color = ComponentColors.secondaryTextColor(),
                textAlign = TextAlign.Center,
            )
        }
    }
}
