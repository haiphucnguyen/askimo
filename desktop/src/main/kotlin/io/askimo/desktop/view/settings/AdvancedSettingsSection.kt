/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.askimo.core.config.AppConfig
import io.askimo.core.logging.LogLevel
import io.askimo.core.logging.LoggingService
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.preferences.DeveloperModePreferences
import io.askimo.desktop.preferences.ThemePreferences
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.util.Platform
import io.askimo.desktop.view.components.clickableCard
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.File

@Composable
fun advancedSettingsSection() {
    var showLogViewerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource("settings.advanced"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Log Level Section
        Text(
            text = stringResource("settings.log.level"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        logLevelCard()

        // Log Viewer Section
        logViewerCard(
            onViewLogs = { showLogViewerDialog = true },
        )

        // RAG Configuration Section
        ragConfigurationSection()

        // Developer Mode Section
        developerModeSection()
    }

    // Log Viewer Dialog
    if (showLogViewerDialog) {
        io.askimo.desktop.view.components.logViewerDialog(
            onDismiss = { showLogViewerDialog = false },
        )
    }
}

@Composable
private fun logLevelCard() {
    val currentLogLevel by ThemePreferences.logLevel.collectAsState()
    var logLevelDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = ComponentColors.bannerCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource("settings.log.level.description"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableCard { logLevelDropdownExpanded = true },
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource("log.level.${currentLogLevel.name.lowercase()}"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource("log.level.${currentLogLevel.name.lowercase()}.description"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Change log level",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                ComponentColors.themedDropdownMenu(
                    expanded = logLevelDropdownExpanded,
                    onDismissRequest = { logLevelDropdownExpanded = false },
                ) {
                    LogLevel.entries.forEach { level ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = stringResource("log.level.${level.name.lowercase()}"),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource("log.level.${level.name.lowercase()}.description"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                ThemePreferences.setLogLevel(level)
                                logLevelDropdownExpanded = false
                            },
                            leadingIcon = if (level == currentLogLevel) {
                                {
                                    Icon(
                                        Icons.Default.Check,
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
    }
}

@Composable
private fun logViewerCard(
    onViewLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = ComponentColors.bannerCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource("settings.log.viewer"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Text(
                text = stringResource("settings.log.viewer.description"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )

            // Buttons Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // View Logs Button
                OutlinedButton(
                    onClick = onViewLogs,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.subtleButtonColors(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource("settings.log.viewer.view"))
                }

                // Open Log Folder Button
                OutlinedButton(
                    onClick = {
                        val logDir = LoggingService.getLogDirectory()
                        openInFileManager(logDir.toFile())
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.subtleButtonColors(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource("settings.log.viewer.open_folder"))
                }
            }

            // Show log file path
            LoggingService.getLogFilePath()?.let { logPath ->
                Text(
                    text = stringResource("settings.log.viewer.path", logPath.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * Opens a file or directory in the system's file manager.
 * Platform-aware implementation for macOS, Linux, and Windows.
 */
private fun openInFileManager(file: File) {
    try {
        when {
            Platform.isMac -> {
                Runtime.getRuntime().exec(arrayOf("open", file.absolutePath))
            }
            Platform.isLinux -> {
                Runtime.getRuntime().exec(arrayOf("xdg-open", file.absolutePath))
            }
            Platform.isWindows -> {
                Runtime.getRuntime().exec(arrayOf("explorer", file.absolutePath))
            }
            else -> {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file)
                }
            }
        }
    } catch (e: Exception) {
        LoggerFactory.getLogger("AdvancedSettingsSection")
            .error("Failed to open log directory: ${file.absolutePath}", e)
    }
}

@Composable
private fun developerModeSection() {
    val isDeveloperModeEnabled = remember { DeveloperModePreferences.isEnabled() }

    // Only show this section if developer mode is enabled in config
    if (!isDeveloperModeEnabled) {
        return
    }

    val isDeveloperModeActive by DeveloperModePreferences.isActive.collectAsState()

    // Developer Mode Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = ComponentColors.bannerCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource("settings.developer.mode"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource("settings.developer.mode.description"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked = isDeveloperModeActive,
                    onCheckedChange = { DeveloperModePreferences.setActive(it) },
                )
            }
        }
    }
}

@Composable
private fun ragConfigurationSection() {
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
            // Title
            Text(
                text = stringResource("settings.rag.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            // Description
            Text(
                text = stringResource("settings.rag.description"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )

            // Vector Search Max Results
            ragIntField(
                label = stringResource("settings.rag.vector.max.results"),
                hint = stringResource("settings.rag.vector.max.results.hint"),
                value = AppConfig.rag.vectorSearchMaxResults,
                onValueChange = { newValue ->
                    AppConfig.updateField("rag.vectorSearchMaxResults", newValue)
                },
            )

            // Vector Search Min Score
            ragDoubleField(
                label = stringResource("settings.rag.vector.min.score"),
                hint = stringResource("settings.rag.vector.min.score.hint"),
                value = AppConfig.rag.vectorSearchMinScore,
                onValueChange = { newValue ->
                    AppConfig.updateField("rag.vectorSearchMinScore", newValue)
                },
            )

            // Hybrid Max Results
            ragIntField(
                label = stringResource("settings.rag.hybrid.max.results"),
                hint = stringResource("settings.rag.hybrid.max.results.hint"),
                value = AppConfig.rag.hybridMaxResults,
                onValueChange = { newValue ->
                    AppConfig.updateField("rag.hybridMaxResults", newValue)
                },
            )

            // Rank Fusion Constant
            ragIntField(
                label = stringResource("settings.rag.rank.fusion.constant"),
                hint = stringResource("settings.rag.rank.fusion.constant.hint"),
                value = AppConfig.rag.rankFusionConstant,
                onValueChange = { newValue ->
                    AppConfig.updateField("rag.rankFusionConstant", newValue)
                },
            )

            // Divider before embedding configuration
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
            )

            // Embedding Configuration Section
            Text(
                text = stringResource("settings.rag.embedding.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Text(
                text = stringResource("settings.rag.embedding.description"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )

            // Max Characters Per Chunk
            ragIntField(
                label = stringResource("settings.rag.embedding.max.chars.per.chunk"),
                hint = stringResource("settings.rag.embedding.max.chars.per.chunk.hint"),
                value = AppConfig.embedding.maxCharsPerChunk,
                onValueChange = { newValue ->
                    AppConfig.updateField("embedding.maxCharsPerChunk", newValue)
                },
            )

            // Chunk Overlap
            ragIntField(
                label = stringResource("settings.rag.embedding.chunk.overlap"),
                hint = stringResource("settings.rag.embedding.chunk.overlap.hint"),
                value = AppConfig.embedding.chunkOverlap,
                onValueChange = { newValue ->
                    AppConfig.updateField("embedding.chunkOverlap", newValue)
                },
            )

            // Preferred Dimension (optional)
            ragOptionalIntField(
                label = stringResource("settings.rag.embedding.preferred.dim"),
                hint = stringResource("settings.rag.embedding.preferred.dim.hint"),
                value = AppConfig.embedding.preferredDim,
                onValueChange = { newValue ->
                    AppConfig.updateField("embedding.preferredDim", newValue ?: "")
                },
            )

            // Divider before embedding models section
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
            )

            // Embedding Models Section
            Text(
                text = stringResource("settings.rag.embedding.models"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Text(
                text = stringResource("settings.rag.embedding.models.description"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )

            // Embedding Model Selector
            ragEmbeddingModelSelector()
        }
    }
}

@Composable
private fun ragIntField(
    label: String,
    hint: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var textValue by remember { mutableStateOf(value.toString()) }
    var lastValidValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        // Update text when external value changes (e.g., reload)
        if (value != lastValidValue) {
            textValue = value.toString()
            lastValidValue = value
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        androidx.compose.material3.OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                // Only save if it's a valid integer
                newValue.toIntOrNull()?.let { validInt ->
                    lastValidValue = validInt
                    onValueChange(validInt)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = textValue.toIntOrNull() == null,
            colors = ComponentColors.outlinedTextFieldColors(),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ragDoubleField(
    label: String,
    hint: String,
    value: Double,
    onValueChange: (Double) -> Unit,
) {
    var textValue by remember { mutableStateOf(value.toString()) }
    var lastValidValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        // Update text when external value changes (e.g., reload)
        if (value != lastValidValue) {
            textValue = value.toString()
            lastValidValue = value
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        androidx.compose.material3.OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                // Only save if it's a valid double
                newValue.toDoubleOrNull()?.let { validDouble ->
                    lastValidValue = validDouble
                    onValueChange(validDouble)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = textValue.toDoubleOrNull() == null,
            colors = ComponentColors.outlinedTextFieldColors(),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ragOptionalIntField(
    label: String,
    hint: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
) {
    var textValue by remember { mutableStateOf(value?.toString() ?: "") }
    var lastValidValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        // Update text when external value changes (e.g., reload)
        if (value != lastValidValue) {
            textValue = value?.toString() ?: ""
            lastValidValue = value
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        androidx.compose.material3.OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                // If empty, set to null
                if (newValue.isBlank()) {
                    lastValidValue = null
                    onValueChange(null)
                } else {
                    // Only save if it's a valid integer
                    newValue.toIntOrNull()?.let { validInt ->
                        lastValidValue = validInt
                        onValueChange(validInt)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = textValue.isNotBlank() && textValue.toIntOrNull() == null,
            colors = ComponentColors.outlinedTextFieldColors(),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ragEmbeddingModelSelector() {
    // AI Provider options
    val providers = listOf(
        "OpenAI" to true,
        "Anthropic" to false,
        "Gemini" to true,
        "X AI" to false,
        "Ollama" to true,
        "Docker" to true,
        "LocalAI" to true,
        "LMStudio" to true,
    )

    var selectedProvider by remember { mutableStateOf(providers[0].first) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Get current value based on selected provider
    val currentValue = when (selectedProvider) {
        "OpenAI" -> AppConfig.embeddingModels.openai
        "Gemini" -> AppConfig.embeddingModels.gemini
        "Ollama" -> AppConfig.embeddingModels.ollama
        "Docker" -> AppConfig.embeddingModels.docker
        "LocalAI" -> AppConfig.embeddingModels.localai
        "LMStudio" -> AppConfig.embeddingModels.lmstudio
        "Anthropic", "X AI" -> "N/A"
        else -> ""
    }

    val isSupported = providers.find { it.first == selectedProvider }?.second ?: false

    var textValue by remember(selectedProvider, currentValue) { mutableStateOf(currentValue) }

    LaunchedEffect(currentValue) {
        textValue = currentValue
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Provider Dropdown and Model Field in a Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Provider Dropdown
            Box(
                modifier = Modifier.weight(1f),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableCard { dropdownExpanded = true },
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
                            text = selectedProvider,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Change provider",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                ComponentColors.themedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    providers.forEach { (providerName, supported) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = providerName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (supported) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        },
                                    )
                                    if (!supported) {
                                        Text(
                                            text = "(Not supported)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            fontStyle = FontStyle.Italic,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                selectedProvider = providerName
                                dropdownExpanded = false
                            },
                            leadingIcon = if (providerName == selectedProvider) {
                                {
                                    Icon(
                                        Icons.Default.Check,
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

            // Embedding Model Text Field
            androidx.compose.material3.OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    if (isSupported) {
                        textValue = newValue
                        // Update the corresponding config field
                        when (selectedProvider) {
                            "OpenAI" -> AppConfig.updateField("embeddingModels.openai", newValue)
                            "Gemini" -> AppConfig.updateField("embeddingModels.gemini", newValue)
                            "Ollama" -> AppConfig.updateField("embeddingModels.ollama", newValue)
                            "Docker" -> AppConfig.updateField("embeddingModels.docker", newValue)
                            "LocalAI" -> AppConfig.updateField("embeddingModels.localai", newValue)
                            "LMStudio" -> AppConfig.updateField("embeddingModels.lmstudio", newValue)
                        }
                    }
                },
                modifier = Modifier.weight(2f),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                enabled = isSupported,
                placeholder = {
                    Text(
                        text = if (isSupported) "Enter embedding model" else "Not supported",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                colors = ComponentColors.outlinedTextFieldColors(),
            )
        }

        // Show not supported message
        if (!isSupported) {
            Text(
                text = stringResource("settings.rag.embedding.not.supported", selectedProvider),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                fontStyle = FontStyle.Italic,
            )
        }
    }
}
