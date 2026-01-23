/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.view.components.clickableCard
import io.askimo.desktop.viewmodel.SettingsViewModel

@Composable
fun modelSelectionDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var selectedModel by remember { mutableStateOf<String?>(viewModel.model.takeIf { it.isNotBlank() }) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredModels = remember(viewModel.availableModels, searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.availableModels
        } else {
            viewModel.availableModels.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Group models by family (e.g., gpt-4, gpt-3.5, claude, gemini)
    val groupedModels = remember(filteredModels) {
        filteredModels.groupBy { model ->
            val parts = model.split('-', '.')
            when {
                parts.size >= 2 -> "${parts[0]}-${parts[1]}"
                parts.size == 1 -> parts[0]
                else -> "Other"
            }
        }.toSortedMap()
    }

    ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource("settings.model.select.title"),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Display current model
                if (viewModel.model.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ComponentColors.bannerCardColors(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource("settings.model.current"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = viewModel.model,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

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
                            text = stringResource("settings.model.change.description"),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        // Show selected model if different from current
                        if (selectedModel != null && selectedModel != viewModel.model) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = ComponentColors.primaryCardColors(),
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
                                            text = stringResource("settings.model.new"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Text(
                                            text = selectedModel ?: "",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "New model selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
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
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }

                                // Display grouped models
                                groupedModels.forEach { (category, models) ->
                                    if (groupedModels.size > 1 && models.isNotEmpty()) {
                                        Text(
                                            text = category.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(
                                                horizontal = 0.dp,
                                                vertical = 8.dp,
                                            ),
                                        )
                                    }

                                    // Models in this category
                                    models.forEach { model ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .clickableCard { selectedModel = model },
                                            colors = if (model == selectedModel) {
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
                                                if (model == selectedModel) {
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedModel?.let { onSelect(it) }
                },
                enabled = selectedModel != null && !viewModel.isLoadingModels,
            ) {
                Text(stringResource("action.save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource("action.cancel"))
            }
        },
    )
}
