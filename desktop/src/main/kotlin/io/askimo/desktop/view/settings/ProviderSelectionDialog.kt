/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.askimo.core.providers.ProviderConfigField
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.viewmodel.SettingsViewModel
import java.awt.Desktop
import java.net.URI

private fun Modifier.clickableCard(
    onClick: () -> Unit,
): Modifier {
    val shape = RoundedCornerShape(12.dp)
    return this
        .clip(shape)
        .clickable(onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)
}

@Composable
fun providerSelectionDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (viewModel.showModelSelectionInProviderDialog) {
                    stringResource("settings.model.select.title")
                } else if (viewModel.isInitialSetup) {
                    stringResource("provider.select.title")
                } else {
                    stringResource("provider.change.title")
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            if (viewModel.showModelSelectionInProviderDialog) {
                // Step 3: Model Selection with search and list
                var searchQuery by remember { mutableStateOf("") }

                // Filter models based on search query
                val filteredModels = remember(viewModel.availableModels, searchQuery) {
                    if (searchQuery.isBlank()) {
                        viewModel.availableModels
                    } else {
                        viewModel.availableModels.filter { it.contains(searchQuery, ignoreCase = true) }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when {
                        viewModel.isLoadingModels -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource("settings.model.loading"),
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                        viewModel.modelError != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = viewModel.modelError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                viewModel.modelErrorHelp?.let { helpText ->
                                    Card(colors = ComponentColors.surfaceVariantCardColors()) {
                                        Text(
                                            text = helpText,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                        viewModel.availableModels.isEmpty() -> {
                            Text(
                                text = stringResource("settings.model.none"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource("settings.model.select", viewModel.selectedProvider?.name ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )

                            // Selected model display (if any)
                            if (viewModel.pendingModelForNewProvider != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ComponentColors.surfaceVariantCardColors(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource("settings.model.selected"),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = viewModel.pendingModelForNewProvider ?: "",
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        }
                                    }
                                }
                            }

                            // Simple search field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource("settings.model.search.placeholder")) },
                                label = { Text(stringResource("settings.model.search")) },
                                singleLine = true,
                                colors = ComponentColors.outlinedTextFieldColors(),
                            )

                            // Filtered models list
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (filteredModels.isEmpty()) {
                                    Text(
                                        text = stringResource("settings.model.no.match"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                } else {
                                    if (searchQuery.isNotBlank()) {
                                        Text(
                                            text = stringResource("settings.model.filtered", filteredModels.size, viewModel.availableModels.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    filteredModels.forEach { model ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickableCard { viewModel.selectModelForNewProvider(model) },
                                            colors = if (model == viewModel.pendingModelForNewProvider) {
                                                ComponentColors.primaryCardColors()
                                            } else {
                                                ComponentColors.surfaceVariantCardColors()
                                            },
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = model,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                if (model == viewModel.pendingModelForNewProvider) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Selected model",
                                                        tint = MaterialTheme.colorScheme.primary,
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
            } else {
                // Steps 1 & 2: Provider selection and configuration
                var providerDropdownExpanded by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ComponentColors.bannerCardColors(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Step 1: Provider selection dropdown
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource("provider.select.prompt"),
                                style = MaterialTheme.typography.titleMedium,
                            )

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickableCard { providerDropdownExpanded = true },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = viewModel.selectedProvider?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: stringResource("provider.choose.placeholder"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Select provider",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }

                                ComponentColors.themedDropdownMenu(
                                    expanded = providerDropdownExpanded,
                                    onDismissRequest = { providerDropdownExpanded = false },
                                ) {
                                    viewModel.availableProviders.forEach { provider ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = provider.name.lowercase().replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                            },
                                            onClick = {
                                                viewModel.selectProviderForChange(provider)
                                                providerDropdownExpanded = false
                                            },
                                            leadingIcon = if (viewModel.selectedProvider == provider) {
                                                {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // Step 2: Configuration fields (shown after provider is selected)
                        if (viewModel.selectedProvider != null && viewModel.providerConfigFields.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Text(
                                text = stringResource("provider.configure.prompt"),
                                style = MaterialTheme.typography.titleMedium,
                            )

                            viewModel.providerConfigFields.forEach { field ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = field.label + if (field.required) " *" else "",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = field.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    when (field) {
                                        is ProviderConfigField.ApiKeyField -> {
                                            OutlinedTextField(
                                                value = viewModel.providerFieldValues[field.name] ?: "",
                                                onValueChange = { viewModel.updateProviderField(field.name, it) },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                visualTransformation = PasswordVisualTransformation(),
                                                placeholder = {
                                                    Text(
                                                        if (field.hasExistingValue) {
                                                            stringResource("provider.apikey.stored")
                                                        } else {
                                                            stringResource("provider.apikey.enter")
                                                        },
                                                    )
                                                },
                                                trailingIcon = {
                                                    Row {
                                                        if (field.hasExistingValue) {
                                                            Icon(
                                                                Icons.Default.CheckCircle,
                                                                contentDescription = stringResource("provider.apikey.already.stored"),
                                                                tint = MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.Lock, contentDescription = "Password")
                                                    }
                                                },
                                                colors = ComponentColors.outlinedTextFieldColors(),
                                            )

                                            // Security assurance message
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                ),
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource("provider.apikey.security.message"),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    TextButton(
                                                        onClick = {
                                                            try {
                                                                Desktop.getDesktop().browse(
                                                                    URI("https://askimo.chat/blog/securing-user-data-in-askimo/"),
                                                                )
                                                            } catch (_: Exception) {
                                                                // Silently fail if browser cannot be opened
                                                            }
                                                        },
                                                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                                        contentPadding = PaddingValues(0.dp),
                                                    ) {
                                                        Text(
                                                            text = stringResource("provider.apikey.security.link"),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        is ProviderConfigField.BaseUrlField -> {
                                            OutlinedTextField(
                                                value = viewModel.providerFieldValues[field.name] ?: "",
                                                onValueChange = { viewModel.updateProviderField(field.name, it) },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                placeholder = { Text(stringResource("settings.placeholder.baseurl")) },
                                                colors = ComponentColors.outlinedTextFieldColors(),
                                            )
                                        }
                                    }
                                }
                            }

                            // Connection error display
                            if (viewModel.connectionError != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = "Error",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                            Column {
                                                Text(
                                                    text = viewModel.connectionError ?: "",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                )
                                                if (viewModel.connectionErrorHelp != null) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = viewModel.connectionErrorHelp ?: "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
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
            } // End of else block for provider configuration
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (viewModel.showModelSelectionInProviderDialog) {
                    OutlinedButton(
                        onClick = { viewModel.backToProviderConfiguration() },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("action.back"))
                    }
                } else {
                    if (viewModel.selectedProvider != null && viewModel.providerConfigFields.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.testConnection() },
                            enabled = !viewModel.isTestingConnection,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            if (viewModel.isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource("settings.test.connection.testing"))
                            } else {
                                Text(stringResource("settings.test.connection"))
                            }
                        }
                    }
                }

                // Save button
                Button(
                    onClick = onSave,
                    enabled = if (viewModel.showModelSelectionInProviderDialog) {
                        viewModel.pendingModelForNewProvider != null
                    } else {
                        false
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.subtleButtonColors(),
                ) {
                    Text(stringResource("settings.save"))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ComponentColors.primaryTextButtonColors(),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource("settings.cancel"))
            }
        },
    )
}
