/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.askimo.core.mcp.GlobalMcpInstanceService
import io.askimo.core.mcp.McpInstance
import io.askimo.core.mcp.McpServerDefinition
import io.askimo.core.mcp.TransportType
import io.askimo.core.mcp.config.McpServersConfig
import io.askimo.core.mcp.extractVariables
import io.askimo.ui.common.components.dangerButton
import io.askimo.ui.common.components.linkButton
import io.askimo.ui.common.components.primaryButton
import io.askimo.ui.common.components.secondaryButton
import io.askimo.ui.common.i18n.stringResource
import io.askimo.ui.common.theme.AppComponents
import io.askimo.ui.common.theme.Spacing
import io.askimo.ui.common.theme.ThemePreferences
import io.askimo.ui.common.ui.clickableCard
import io.askimo.ui.common.ui.themedTooltip
import io.askimo.ui.project.mcpToolsDialog
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import java.awt.Desktop
import java.net.URI

@Composable
fun mcpServerTemplatesSection() {
    var servers by remember { mutableStateOf(McpServersConfig.getAll().filter { !it.tags.contains("global") }) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<McpServerDefinition?>(null) }
    var deletingServer by remember { mutableStateOf<McpServerDefinition?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // Global MCP instance state
    val globalMcpService = remember { get<GlobalMcpInstanceService>(GlobalMcpInstanceService::class.java) }
    var globalInstances by remember { mutableStateOf(globalMcpService.getInstances()) }
    var showAddGlobalDialog by remember { mutableStateOf(false) }
    var editingGlobalInstance by remember { mutableStateOf<McpInstance?>(null) }
    var deletingGlobalInstance by remember { mutableStateOf<McpInstance?>(null) }
    var showAddDropdown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ThemePreferences.CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 36.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.large),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = stringResource("mcp.servers.title"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = stringResource("mcp.servers.description"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                        linkButton(
                            onClick = {
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().browse(URI("https://askimo.chat/docs/desktop/mcp-integration/"))
                                    }
                                } catch (_: Exception) {}
                            },
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource("mcp.servers.guide"),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }

                    // Action buttons row — split "Add" dropdown
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        Box {
                            primaryButton(
                                onClick = { showAddDropdown = true },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(Spacing.small))
                                Text(stringResource("mcp.servers.add"))
                            }

                            AppComponents.dropdownMenu(
                                expanded = showAddDropdown,
                                onDismissRequest = { showAddDropdown = false },
                            ) {
                                DropdownMenuItem(
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .layout { measurable, constraints ->
                                            val placeable = measurable.measure(constraints)
                                            // Shift item up by 8dp to cancel menu's top inset
                                            val offset = 8.dp.roundToPx()
                                            layout(placeable.width, placeable.height + offset) {
                                                placeable.placeRelative(0, -offset)
                                            }
                                        },
                                    contentPadding = PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = Spacing.medium,
                                    ),
                                    text = {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = stringResource("mcp.servers.add.template"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = stringResource("mcp.servers.add.template.desc"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        showAddDropdown = false
                                        showAddDialog = true
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .layout { measurable, constraints ->
                                            val placeable = measurable.measure(constraints)
                                            // Extend item down by 8dp to cancel menu's bottom inset
                                            val offset = 8.dp.roundToPx()
                                            layout(placeable.width, placeable.height + offset) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        },
                                    contentPadding = PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = Spacing.medium,
                                    ),
                                    text = {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = stringResource("mcp.global.instance.add"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = stringResource("mcp.global.instance.add.desc"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        showAddDropdown = false
                                        showAddGlobalDialog = true
                                    },
                                )
                            }
                        }

                        secondaryButton(onClick = { showResetConfirm = true }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(Modifier.width(Spacing.small))
                            Text(stringResource("mcp.servers.reset"))
                        }
                    }

                    // Templates list
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppComponents.bannerCardColors(),
                    ) {
                        if (servers.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.extraLarge),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource("mcp.servers.empty"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(Spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(Spacing.small),
                            ) {
                                servers.forEach { server ->
                                    mcpServerTemplateCard(
                                        server = server,
                                        onEdit = { editingServer = server },
                                        onDelete = { deletingServer = server },
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource("mcp.servers.count", servers.size.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }

                HorizontalDivider()

                // ── Global MCP Instances Section ─────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                    Column {
                        Text(
                            text = stringResource("mcp.global.instances.title"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource("mcp.global.instances.description"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = AppComponents.bannerCardColors(),
                    ) {
                        if (globalInstances.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.extraLarge),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource("mcp.global.instances.empty"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(Spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(Spacing.small),
                            ) {
                                globalInstances.forEach { instance ->
                                    globalMcpInstanceCard(
                                        instance = instance,
                                        onToggleEnabled = {
                                            scope.launch {
                                                globalMcpService.updateInstance(
                                                    instanceId = instance.id,
                                                    enabled = !instance.enabled,
                                                )
                                                globalInstances = globalMcpService.getInstances()
                                            }
                                        },
                                        onEdit = { editingGlobalInstance = instance },
                                        onDelete = { deletingGlobalInstance = instance },
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource("mcp.global.instances.count", globalInstances.size.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 8.dp,
                shape = MaterialTheme.shapes.small,
                hoverDurationMillis = 300,
                unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            ),
        )
    }

    // Add/Edit Template Dialog
    if (showAddDialog || editingServer != null) {
        mcpServerTemplateDialog(
            server = editingServer,
            onDismiss = {
                showAddDialog = false
                editingServer = null
            },
            onSave = { server ->
                McpServersConfig.add(server)
                servers = McpServersConfig.getAll().filter { !it.tags.contains("global") }
                showAddDialog = false
                editingServer = null
            },
        )
    }

    // Add Global MCP Instance Dialog — uses simplified one-step creation
    if (showAddGlobalDialog) {
        addGlobalMcpInstanceDialog(
            onDismiss = { showAddGlobalDialog = false },
            onSave = { serverId, name, parameters ->
                scope.launch {
                    globalMcpService.createInstance(
                        serverId = serverId,
                        name = name,
                        parameterValues = parameters,
                    )
                    globalInstances = globalMcpService.getInstances()
                }
                showAddGlobalDialog = false
            },
        )
    }

    // Edit Global MCP Instance Dialog
    editingGlobalInstance?.let { instance ->
        addGlobalMcpInstanceDialog(
            existingInstance = instance,
            onDismiss = { editingGlobalInstance = null },
            onSave = { serverId, name, parameters ->
                scope.launch {
                    globalMcpService.updateInstance(
                        instanceId = instance.id,
                        name = name,
                        parameterValues = parameters,
                    )
                    globalInstances = globalMcpService.getInstances()
                }
                editingGlobalInstance = null
            },
        )
    }

    // Delete Template Confirmation
    deletingServer?.let { server ->
        AppComponents.alertDialog(
            onDismissRequest = { deletingServer = null },
            title = { Text(stringResource("mcp.servers.delete.confirm.title")) },
            text = { Text(stringResource("mcp.servers.delete.confirm.message", server.name)) },
            confirmButton = {
                dangerButton(
                    onClick = {
                        McpServersConfig.remove(server.id)
                        servers = McpServersConfig.getAll().filter { !it.tags.contains("global") }
                        deletingServer = null
                    },
                ) {
                    Text(stringResource("mcp.servers.delete"))
                }
            },
            dismissButton = {
                secondaryButton(onClick = { deletingServer = null }) {
                    Text(stringResource("dialog.cancel"))
                }
            },
        )
    }

    // Delete Global Instance Confirmation
    deletingGlobalInstance?.let { instance ->
        AppComponents.alertDialog(
            onDismissRequest = { deletingGlobalInstance = null },
            title = { Text(stringResource("mcp.global.instance.delete.confirm.title")) },
            text = { Text(stringResource("mcp.global.instance.delete.confirm.message", instance.name)) },
            confirmButton = {
                dangerButton(
                    onClick = {
                        scope.launch {
                            globalMcpService.deleteInstance(instance.id)
                            globalInstances = globalMcpService.getInstances()
                        }
                        deletingGlobalInstance = null
                    },
                ) {
                    Text(stringResource("action.delete"))
                }
            },
            dismissButton = {
                secondaryButton(onClick = { deletingGlobalInstance = null }) {
                    Text(stringResource("dialog.cancel"))
                }
            },
        )
    }

    // Reset Templates Confirmation
    if (showResetConfirm) {
        AppComponents.alertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource("mcp.servers.reset.confirm.title")) },
            text = { Text(stringResource("mcp.servers.reset.confirm.message")) },
            confirmButton = {
                dangerButton(
                    onClick = {
                        McpServersConfig.resetToDefaults()
                        servers = McpServersConfig.getAll().filter { !it.tags.contains("global") }
                        showResetConfirm = false
                    },
                ) {
                    Text(stringResource("mcp.servers.reset"))
                }
            },
            dismissButton = {
                secondaryButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource("dialog.cancel"))
                }
            },
        )
    }
}

@Composable
private fun globalMcpInstanceCard(
    instance: McpInstance,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val serverDef = remember(instance.serverId) { McpServersConfig.get(instance.serverId) }
    var showToolsDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = instance.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = stringResource("mcp.global.instance.badge"),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }

                serverDef?.let { def ->
                    Text(
                        text = def.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }

                Text(
                    text = stringResource("mcp.global.instance.server.label", instance.serverId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Enable / disable toggle
                themedTooltip(
                    text = stringResource(
                        if (instance.enabled) {
                            "mcp.global.instance.disable.tooltip"
                        } else {
                            "mcp.global.instance.enable.tooltip"
                        },
                    ),
                ) {
                    Switch(
                        checked = instance.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .size(width = 44.dp, height = 24.dp),
                    )
                }

                themedTooltip(text = stringResource("mcp.integrations.view.tools.tooltip")) {
                    IconButton(
                        onClick = { showToolsDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = stringResource("mcp.integrations.view.tools.tooltip"),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                themedTooltip(text = stringResource("mcp.global.instance.edit.tooltip")) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource("mcp.global.instance.edit.tooltip"),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                themedTooltip(text = stringResource("mcp.integrations.delete.tooltip")) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource("mcp.integrations.delete.tooltip"),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    if (showToolsDialog) {
        mcpToolsDialog(
            instance = instance,
            onDismiss = { showToolsDialog = false },
        )
    }
}

@Composable
private fun mcpServerTemplateCard(
    server: McpServerDefinition,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val validation = remember(server) { McpServersConfig.validate(server) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickableCard(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Validation status indicator
                    if (validation.isValid) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Valid",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Invalid",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }

                    // Tags
                    server.tags.take(2).forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                Text(
                    text = server.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )

                // Show first validation error if invalid
                if (!validation.isValid && validation.errors.isNotEmpty()) {
                    Text(
                        text = "⚠ ${validation.errors.first()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                val variableCount = when (server.transportType) {
                    TransportType.STDIO -> server.stdioConfig?.extractVariables()?.size ?: 0
                    TransportType.HTTP -> server.httpConfig?.extractVariables()?.size ?: 0
                }

                Text(
                    text = "${ server.transportType} • $variableCount variable(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource("mcp.servers.edit"),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource("mcp.servers.delete"),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
