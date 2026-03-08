/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.askimo.core.config.AppConfig
import io.askimo.core.providers.ModelProvider
import io.askimo.desktop.common.components.secondaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun aiProviderSettingsSection(viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Text(
            text = stringResource("settings.ai.provider"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = Spacing.small),
        )

        // Chat Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = ComponentColors.bannerCardColors(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                // Provider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource("settings.provider"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = viewModel.provider?.name ?: stringResource("provider.not.set"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    secondaryButton(
                        onClick = { viewModel.onChangeProvider() },
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text(
                            stringResource("settings.provider.change.button"),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                HorizontalDivider()

                // Model
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource("settings.model"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = viewModel.model,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    secondaryButton(
                        onClick = { viewModel.onChangeModel() },
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text(
                            stringResource("settings.model.change.button"),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                // Settings
                if (viewModel.settingsDescription.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                        ) {
                            Text(
                                text = stringResource("settings.title"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            viewModel.settingsDescription.forEach { setting ->
                                Text(
                                    text = setting,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        secondaryButton(
                            onClick = { viewModel.onChangeSettings() },
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text(
                                stringResource("settings.change.button"),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        // Model Config Card — only shown when a provider is selected
        viewModel.provider?.let { provider ->
            providerModelConfigCard(provider)
        }
    }
}

@Composable
private fun providerModelConfigCard(provider: ModelProvider) {
    val isLocalProvider = provider in setOf(
        ModelProvider.OLLAMA,
        ModelProvider.DOCKER,
        ModelProvider.LOCALAI,
        ModelProvider.LMSTUDIO,
    )
    val supportsEmbedding = provider in setOf(
        ModelProvider.OPENAI,
        ModelProvider.GEMINI,
        ModelProvider.OLLAMA,
        ModelProvider.DOCKER,
        ModelProvider.LOCALAI,
        ModelProvider.LMSTUDIO,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = ComponentColors.bannerCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            Text(
                text = stringResource("settings.provider.model.config.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource("settings.provider.model.config.description"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )

            // Utility model
            providerModelField(
                label = stringResource("settings.utility.models.title"),
                hint = if (isLocalProvider) {
                    stringResource("settings.utility.models.local.note", provider.name)
                } else {
                    stringResource("settings.provider.model.utility.hint")
                },
                value = getProviderUtilityModel(provider),
                placeholder = if (isLocalProvider) {
                    stringResource("settings.utility.models.uses.selected")
                } else {
                    stringResource("settings.model.placeholder.enter", "utility")
                },
                onSave = { AppConfig.updateField("models.${provider.name.lowercase()}.utilityModel", it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f))

            // Vision model
            providerModelField(
                label = stringResource("settings.vision.models.title"),
                hint = stringResource("settings.provider.model.vision.hint"),
                value = getProviderVisionModel(provider),
                placeholder = stringResource("settings.model.placeholder.enter", "vision"),
                onSave = { AppConfig.updateField("models.${provider.name.lowercase()}.visionModel", it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f))

            // Image model
            providerModelField(
                label = stringResource("settings.image.models.title"),
                hint = stringResource("settings.provider.model.image.hint"),
                value = getProviderImageModel(provider),
                placeholder = stringResource("settings.model.placeholder.enter", "image"),
                onSave = { AppConfig.updateField("models.${provider.name.lowercase()}.imageModel", it) },
            )

            // Embedding model — only for supported providers
            if (supportsEmbedding) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f))

                providerModelField(
                    label = stringResource("settings.rag.embedding.models"),
                    hint = stringResource("settings.provider.model.embedding.hint"),
                    value = getProviderEmbeddingModel(provider),
                    placeholder = stringResource("settings.model.placeholder.enter", "embedding"),
                    onSave = { AppConfig.updateField("models.${provider.name.lowercase()}.embeddingModel", it) },
                )
            }
        }
    }
}

private fun getProviderUtilityModel(provider: ModelProvider): String = AppConfig.models[provider].utilityModel

private fun getProviderVisionModel(provider: ModelProvider): String = AppConfig.models[provider].visionModel

private fun getProviderImageModel(provider: ModelProvider): String = AppConfig.models[provider].imageModel

private fun getProviderEmbeddingModel(provider: ModelProvider): String = AppConfig.models[provider].embeddingModel

@Composable
private fun providerModelField(
    label: String,
    hint: String,
    value: String,
    placeholder: String,
    onSave: (String) -> Unit,
) {
    var textValue by remember(value) { mutableStateOf(value) }
    var lastSavedValue by remember(value) { mutableStateOf(value) }
    var showSavedIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(showSavedIndicator) {
        if (showSavedIndicator) {
            delay(2000)
            showSavedIndicator = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && textValue != lastSavedValue) {
                        onSave(textValue)
                        lastSavedValue = textValue
                        showSavedIndicator = true
                    }
                },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (value.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                )
            },
            trailingIcon = {
                AnimatedVisibility(visible = showSavedIndicator, enter = fadeIn(), exit = fadeOut()) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            colors = ComponentColors.outlinedTextFieldColors(),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
        )
    }
}
