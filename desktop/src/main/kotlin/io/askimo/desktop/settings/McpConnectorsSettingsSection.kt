/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.askimo.addons.mcp.McpConnectorLoader
import io.askimo.addons.mcp.McpConnectorProvider
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.Spacing

@Composable
fun mcpConnectorsSettingsSection() {
    val providers = remember { McpConnectorLoader.loadProviders() }
    var selectedProvider by remember { mutableStateOf<McpConnectorProvider?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        // Header
        Text(
            text = stringResource("settings.mcp.connectors"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = Spacing.small),
        )

        Text(
            text = stringResource("settings.mcp.connectors.description"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )

        if (providers.isEmpty()) {
            // No connectors found
            emptyStateView()
        } else {
            // List of available connectors
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                providers.forEach { provider ->
                    connectorCard(
                        provider = provider,
                        onConfigure = {
                            selectedProvider = provider
                            showConfigDialog = true
                        },
                    )
                }
            }
        }
    }

    // Configuration dialog
    if (showConfigDialog && selectedProvider != null) {
        connectorConfigDialog(
            provider = selectedProvider!!,
            onDismiss = {
                showConfigDialog = false
                selectedProvider = null
            },
        )
    }
}

@Composable
private fun emptyStateView() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
            )
            Spacer(modifier = Modifier.height(Spacing.large))
            Text(
                text = stringResource("settings.mcp.connectors.empty.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                text = stringResource("settings.mcp.connectors.empty.description"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun connectorCard(
    provider: McpConnectorProvider,
    onConfigure: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = ComponentColors.bannerCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            // Header with icon and name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    Column {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "v${provider.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Description
            Text(
                text = provider.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )

            // Configuration requirements
            if (provider.configSchema.isNotEmpty()) {
                Text(
                    text = stringResource("settings.mcp.connectors.configuration.required"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                provider.configSchema.entries.take(3).forEach { (key, field) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (field.required) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                            },
                        )
                        Text(
                            text = field.label + if (field.required) " (required)" else " (optional)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }

                if (provider.configSchema.size > 3) {
                    Text(
                        text = "... and ${provider.configSchema.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                // Configure button
                OutlinedButton(
                    onClick = onConfigure,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.subtleButtonColors(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource("settings.mcp.connectors.configure"))
                }

                // Homepage link
                provider.homepage?.let { homepage ->
                    OutlinedButton(
                        onClick = { uriHandler.openUri(homepage) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        colors = ComponentColors.subtleButtonColors(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource("settings.mcp.connectors.documentation"))
                    }
                }
            }
        }
    }
}

@Composable
private fun connectorConfigDialog(
    provider: McpConnectorProvider,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource("settings.mcp.connectors.configure.title", provider.name),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                Text(
                    text = stringResource("settings.mcp.connectors.configure.schema", provider.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                provider.configSchema.forEach { (key, field) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = field.label + if (field.required) " *" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        field.description?.let { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        Text(
                            text = "Key: $key",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.small))

                Text(
                    text = stringResource("settings.mcp.connectors.configure.note"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource("dialog.close"))
            }
        },
    )
}
