/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.askimo.core.mcp.McpServerDefinition
import io.askimo.core.mcp.config.McpServersConfig
import io.askimo.desktop.common.components.dangerButton
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.components.secondaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.Spacing
import io.askimo.desktop.common.ui.clickableCard

@Composable
fun mcpServerTemplatesSection() {
    var servers by remember { mutableStateOf(McpServersConfig.getAll()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<McpServerDefinition?>(null) }
    var deletingServer by remember { mutableStateOf<McpServerDefinition?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        // Section Header
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
        }

        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            primaryButton(
                onClick = { showAddDialog = true },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.small))
                Text(stringResource("mcp.servers.add"))
            }

            secondaryButton(
                onClick = { showResetConfirm = true },
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(Spacing.small))
                Text(stringResource("mcp.servers.reset"))
            }
        }

        // Server List
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = ComponentColors.bannerCardColors(),
        ) {
            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.extraLarge),
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

        // Server count
        Text(
            text = stringResource("mcp.servers.count", servers.size.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }

    // Add/Edit Dialog
    if (showAddDialog || editingServer != null) {
        mcpServerTemplateDialog(
            server = editingServer,
            onDismiss = {
                showAddDialog = false
                editingServer = null
            },
            onSave = { server ->
                McpServersConfig.add(server)
                servers = McpServersConfig.getAll()
                showAddDialog = false
                editingServer = null
            },
        )
    }

    // Delete Confirmation
    deletingServer?.let { server ->
        ComponentColors.themedAlertDialog(
            onDismissRequest = { deletingServer = null },
            title = { Text(stringResource("mcp.servers.delete.confirm.title")) },
            text = { Text(stringResource("mcp.servers.delete.confirm.message", server.name)) },
            confirmButton = {
                dangerButton(
                    onClick = {
                        McpServersConfig.remove(server.id)
                        servers = McpServersConfig.getAll()
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

    // Reset Confirmation
    if (showResetConfirm) {
        ComponentColors.themedAlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource("mcp.servers.reset.confirm.title")) },
            text = { Text(stringResource("mcp.servers.reset.confirm.message")) },
            confirmButton = {
                dangerButton(
                    onClick = {
                        McpServersConfig.resetToDefaults()
                        servers = McpServersConfig.getAll()
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

                Text(
                    text = "${server.transportType} • ${server.parameters.size} parameter(s)",
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
