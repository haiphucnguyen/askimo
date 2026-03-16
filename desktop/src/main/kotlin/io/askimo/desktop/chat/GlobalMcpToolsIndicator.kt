/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.chat

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import io.askimo.core.intent.ToolConfig
import io.askimo.core.mcp.GlobalMcpInstanceService
import io.askimo.core.mcp.McpInstance
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.ui.themedTooltip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.get

/**
 * A compact tools indicator badge shown in the chat header for non-project chats.
 *
 * - If no global MCP instances are configured → renders nothing
 * - If instances exist → shows a [Build] icon with total tool count badge
 * - Clicking opens a popover listing tools grouped by MCP instance, each expandable
 */
@Composable
fun globalMcpToolsIndicator() {
    val globalMcpService = remember { get<GlobalMcpInstanceService>(GlobalMcpInstanceService::class.java) }
    var instances by remember { mutableStateOf<List<McpInstance>>(emptyList()) }
    // instanceId → loaded tools (null = loading, empty = error/none)
    val toolsByInstance = remember { mutableStateMapOf<String, List<ToolConfig>?>() }
    var showPopover by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        instances = withContext(Dispatchers.IO) {
            globalMcpService.getInstances().filter { it.enabled }
        }
        // Load tools for each instance concurrently
        instances.forEach { instance ->
            toolsByInstance[instance.id] = null // null = loading
            try {
                val tools = withContext(Dispatchers.IO) {
                    globalMcpService.listTools(instance.id)
                }
                toolsByInstance[instance.id] = tools
            } catch (_: Exception) {
                toolsByInstance[instance.id] = emptyList()
            }
        }
    }

    if (instances.isEmpty()) return

    val totalTools = toolsByInstance.values.filterNotNull().sumOf { it.size }

    Box {
        themedTooltip(text = stringResource("mcp.global.indicator.tooltip")) {
            IconButton(
                onClick = { showPopover = true },
                modifier = Modifier
                    .size(32.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(
                                text = if (totalTools > 0) totalTools.toString() else instances.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = stringResource("mcp.global.indicator.tooltip"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (showPopover) {
            Popup(
                onDismissRequest = { showPopover = false },
                alignment = Alignment.TopEnd,
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = 300.dp, max = 420.dp)
                        .heightIn(max = 520.dp), // cap overall height; shrinks naturally when collapsed
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                ) {
                    Column(modifier = Modifier.padding(12.dp).heightIn(max = 520.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource("mcp.global.indicator.popover.title"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(
                                    "mcp.global.indicator.popover.count",
                                    totalTools.toString(),
                                    instances.size.toString(),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = stringResource("mcp.global.indicator.popover.desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                        )

                        HorizontalDivider()

                        // Scrollable tool list with visible scrollbar — no fixed height,
                        // the Surface's heightIn(max) above acts as the ceiling
                        val scrollState = rememberScrollState()
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(end = 10.dp), // leave room for scrollbar
                            ) {
                                instances.forEachIndexed { index, instance ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    globalMcpInstanceToolsSection(
                                        instance = instance,
                                        tools = toolsByInstance[instance.id],
                                    )
                                    if (index < instances.lastIndex) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(scrollState),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                                style = ScrollbarStyle(
                                    minimalHeight = 16.dp,
                                    thickness = 6.dp,
                                    shape = MaterialTheme.shapes.small,
                                    hoverDurationMillis = 300,
                                    unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun globalMcpInstanceToolsSection(
    instance: McpInstance,
    tools: List<ToolConfig>?,
) {
    // Instance header row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = instance.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    tools == null -> stringResource("mcp.global.indicator.tools.loading")
                    tools.isEmpty() -> stringResource("mcp.global.indicator.tools.unavailable")
                    else -> stringResource("mcp.global.indicator.tools.count", tools.size.toString())
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    tools != null && tools.isEmpty() -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (tools == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Tools always shown when loaded
    if (!tools.isNullOrEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        ) {
            tools.forEachIndexed { index, tool ->
                toolRow(tool = tool, isEven = index % 2 == 0)
            }
        }
    }
}

@Composable
private fun toolRow(tool: ToolConfig, isEven: Boolean) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Alternate even/odd background; darken on hover
    val baseColor = if (isEven) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val bgColor = if (isHovered) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    } else {
        baseColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp),
        )
        Column {
            Text(
                text = tool.specification.name(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            tool.specification.description()?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
