/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.askimo.core.config.AppConfig
import io.askimo.core.logging.LogLevel
import io.askimo.core.logging.LoggingService
import io.askimo.core.logging.logger
import io.askimo.core.providers.ModelProvider
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.preferences.DeveloperModePreferences
import io.askimo.desktop.preferences.ThemePreferences
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.util.Platform
import io.askimo.desktop.view.components.clickableCard
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.io.File

private val log = logger("AdvancedSettingsSection")

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

        // Vision Models Section
        visionModelsSection()

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
        log.error("Failed to open log directory: ${file.absolutePath}", e)
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

            // Use Absolute Paths in Citations
            ragBooleanField(
                label = stringResource("settings.rag.use.absolute.paths"),
                hint = stringResource("settings.rag.use.absolute.paths.hint"),
                value = AppConfig.rag.useAbsolutePathInCitations,
                onValueChange = { newValue ->
                    AppConfig.updateField("rag.useAbsolutePathInCitations", newValue)
                },
            )

            // Divider before embedding configuration
            HorizontalDivider(
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
            HorizontalDivider(
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
private fun visionModelsSection() {
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
                text = stringResource("settings.vision.models.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            // Description
            Text(
                text = stringResource("settings.vision.models.description"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )

            visionModelSelector()
        }
    }
}

@Composable
private fun ragBooleanField(
    label: String,
    hint: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                )
            }
            Switch(
                checked = value,
                onCheckedChange = onValueChange,
            )
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
    var showSavedIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        // Update text when external value changes (e.g., reload)
        if (value != lastValidValue) {
            textValue = value.toString()
            lastValidValue = value
        }
    }

    LaunchedEffect(showSavedIndicator) {
        if (showSavedIndicator) {
            delay(2000)
            showSavedIndicator = false
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
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        // Only save when losing focus if value is valid and changed
                        textValue.toIntOrNull()?.let { validInt ->
                            if (validInt != lastValidValue) {
                                lastValidValue = validInt
                                onValueChange(validInt)
                                showSavedIndicator = true
                            }
                        }
                    }
                },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = textValue.toIntOrNull() == null,
            trailingIcon = {
                AnimatedVisibility(
                    visible = showSavedIndicator,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
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

@Composable
private fun ragDoubleField(
    label: String,
    hint: String,
    value: Double,
    onValueChange: (Double) -> Unit,
) {
    var textValue by remember { mutableStateOf(value.toString()) }
    var lastValidValue by remember { mutableStateOf(value) }
    var showSavedIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        // Update text when external value changes (e.g., reload)
        if (value != lastValidValue) {
            textValue = value.toString()
            lastValidValue = value
        }
    }

    LaunchedEffect(showSavedIndicator) {
        if (showSavedIndicator) {
            delay(2000)
            showSavedIndicator = false
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
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        // Only save when losing focus if value is valid and changed
                        textValue.toDoubleOrNull()?.let { validDouble ->
                            if (validDouble != lastValidValue) {
                                lastValidValue = validDouble
                                onValueChange(validDouble)
                                showSavedIndicator = true
                            }
                        }
                    }
                },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = textValue.toDoubleOrNull() == null,
            trailingIcon = {
                AnimatedVisibility(
                    visible = showSavedIndicator,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
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

@Composable
private fun ragOptionalIntField(
    label: String,
    hint: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
) {
    var textValue by remember { mutableStateOf(value?.toString() ?: "") }
    var lastValidValue by remember { mutableStateOf(value) }
    var showSavedIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        // Update text when external value changes (e.g., reload)
        if (value != lastValidValue) {
            textValue = value?.toString() ?: ""
            lastValidValue = value
        }
    }

    LaunchedEffect(showSavedIndicator) {
        if (showSavedIndicator) {
            delay(2000)
            showSavedIndicator = false
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
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        // Only save when losing focus if value changed
                        val newValidValue = if (textValue.isBlank()) {
                            null
                        } else {
                            textValue.toIntOrNull()
                        }

                        if (newValidValue != lastValidValue && (textValue.isBlank() || textValue.toIntOrNull() != null)) {
                            lastValidValue = newValidValue
                            onValueChange(newValidValue)
                            showSavedIndicator = true
                        }
                    }
                },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            isError = textValue.isNotBlank() && textValue.toIntOrNull() == null,
            trailingIcon = {
                AnimatedVisibility(
                    visible = showSavedIndicator,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
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

/**
 * Get display name for ModelProvider enum
 */
private fun getProviderDisplayName(provider: ModelProvider): String = when (provider) {
    ModelProvider.OPENAI -> "OpenAI"
    ModelProvider.ANTHROPIC -> "Anthropic"
    ModelProvider.GEMINI -> "Gemini"
    ModelProvider.XAI -> "X AI"
    ModelProvider.OLLAMA -> "Ollama"
    ModelProvider.DOCKER -> "Docker"
    ModelProvider.LOCALAI -> "LocalAI"
    ModelProvider.LMSTUDIO -> "LMStudio"
    ModelProvider.UNKNOWN -> "Unknown"
}

@Composable
private fun ragEmbeddingModelSelector() {
    // AI Provider options - only providers that support embedding models
    val providers = listOf(
        ModelProvider.OPENAI to true,
        ModelProvider.ANTHROPIC to false,
        ModelProvider.GEMINI to true,
        ModelProvider.XAI to false,
        ModelProvider.OLLAMA to true,
        ModelProvider.DOCKER to true,
        ModelProvider.LOCALAI to true,
        ModelProvider.LMSTUDIO to true,
    )

    var selectedProvider by remember { mutableStateOf(providers[0].first) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Get current value based on selected provider
    val currentValue = when (selectedProvider) {
        ModelProvider.OPENAI -> AppConfig.models.openai.embeddingModel
        ModelProvider.GEMINI -> AppConfig.models.gemini.embeddingModel
        ModelProvider.OLLAMA -> AppConfig.models.ollama.embeddingModel
        ModelProvider.DOCKER -> AppConfig.models.docker.embeddingModel
        ModelProvider.LOCALAI -> AppConfig.models.localai.embeddingModel
        ModelProvider.LMSTUDIO -> AppConfig.models.lmstudio.embeddingModel
        ModelProvider.ANTHROPIC, ModelProvider.XAI -> "N/A"
        else -> ""
    }

    val isSupported = providers.find { it.first == selectedProvider }?.second ?: false

    var textValue by remember(selectedProvider, currentValue) { mutableStateOf(currentValue) }
    var lastSavedValue by remember(selectedProvider) { mutableStateOf(currentValue) }
    var showSavedIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(currentValue) {
        textValue = currentValue
        lastSavedValue = currentValue
    }

    // Auto-hide the saved indicator after 2 seconds
    LaunchedEffect(showSavedIndicator) {
        if (showSavedIndicator) {
            delay(2000)
            showSavedIndicator = false
        }
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
                            text = getProviderDisplayName(selectedProvider),
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
                    providers.forEach { (provider, supported) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = getProviderDisplayName(provider),
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
                                selectedProvider = provider
                                dropdownExpanded = false
                            },
                            leadingIcon = if (provider == selectedProvider) {
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
            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    if (isSupported) {
                        textValue = newValue
                    }
                },
                modifier = Modifier
                    .weight(2f)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && isSupported) {
                            // Only save when losing focus if value changed
                            if (textValue != lastSavedValue) {
                                when (selectedProvider) {
                                    ModelProvider.OPENAI -> AppConfig.updateField("models.openai.embeddingModel", textValue)
                                    ModelProvider.GEMINI -> AppConfig.updateField("models.gemini.embeddingModel", textValue)
                                    ModelProvider.OLLAMA -> AppConfig.updateField("models.ollama.embeddingModel", textValue)
                                    ModelProvider.DOCKER -> AppConfig.updateField("models.docker.embeddingModel", textValue)
                                    ModelProvider.LOCALAI -> AppConfig.updateField("models.localai.embeddingModel", textValue)
                                    ModelProvider.LMSTUDIO -> AppConfig.updateField("models.lmstudio.embeddingModel", textValue)
                                    else -> {}
                                }
                                lastSavedValue = textValue
                                showSavedIndicator = true
                            }
                        }
                    },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                enabled = isSupported,
                placeholder = {
                    Text(
                        text = if (isSupported) {
                            stringResource("settings.model.placeholder.enter", "embedding")
                        } else {
                            stringResource("settings.model.placeholder.not.supported")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = showSavedIndicator,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
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
        }

        // Show not supported message
        if (!isSupported) {
            Text(
                text = stringResource("settings.rag.embedding.not.supported", getProviderDisplayName(selectedProvider)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

@Composable
private fun visionModelSelector() {
    // AI Provider options - all providers support vision models
    val providers = listOf(
        ModelProvider.OPENAI to true,
        ModelProvider.ANTHROPIC to true,
        ModelProvider.GEMINI to true,
        ModelProvider.XAI to true,
        ModelProvider.OLLAMA to true,
        ModelProvider.DOCKER to true,
        ModelProvider.LOCALAI to true,
        ModelProvider.LMSTUDIO to true,
    )

    var selectedProvider by remember { mutableStateOf(providers[0].first) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Get current value based on selected provider
    val currentValue = when (selectedProvider) {
        ModelProvider.OPENAI -> AppConfig.models.openai.visionModel
        ModelProvider.ANTHROPIC -> AppConfig.models.anthropic.visionModel
        ModelProvider.GEMINI -> AppConfig.models.gemini.visionModel
        ModelProvider.XAI -> AppConfig.models.xai.visionModel
        ModelProvider.OLLAMA -> AppConfig.models.ollama.visionModel
        ModelProvider.DOCKER -> AppConfig.models.docker.visionModel
        ModelProvider.LOCALAI -> AppConfig.models.localai.visionModel
        ModelProvider.LMSTUDIO -> AppConfig.models.lmstudio.visionModel
        else -> ""
    }

    val isSupported = providers.find { it.first == selectedProvider }?.second ?: false

    var textValue by remember(selectedProvider, currentValue) { mutableStateOf(currentValue) }
    var lastSavedValue by remember(selectedProvider) { mutableStateOf(currentValue) }
    var showSavedIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(currentValue) {
        textValue = currentValue
        lastSavedValue = currentValue
    }

    LaunchedEffect(showSavedIndicator) {
        if (showSavedIndicator) {
            delay(2000)
            showSavedIndicator = false
        }
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
                            text = getProviderDisplayName(selectedProvider),
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
                    providers.forEach { (provider, supported) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = getProviderDisplayName(provider),
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
                                selectedProvider = provider
                                dropdownExpanded = false
                            },
                            leadingIcon = if (provider == selectedProvider) {
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

            // Vision Model Text Field
            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    if (isSupported) {
                        textValue = newValue
                    }
                },
                modifier = Modifier
                    .weight(2f)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && isSupported) {
                            // Only save when losing focus if value changed
                            if (textValue != lastSavedValue) {
                                when (selectedProvider) {
                                    ModelProvider.OPENAI -> AppConfig.updateField("models.openai.visionModel", textValue)
                                    ModelProvider.ANTHROPIC -> AppConfig.updateField("models.anthropic.visionModel", textValue)
                                    ModelProvider.GEMINI -> AppConfig.updateField("models.gemini.visionModel", textValue)
                                    ModelProvider.XAI -> AppConfig.updateField("models.xai.visionModel", textValue)
                                    ModelProvider.OLLAMA -> AppConfig.updateField("models.ollama.visionModel", textValue)
                                    ModelProvider.DOCKER -> AppConfig.updateField("models.docker.visionModel", textValue)
                                    ModelProvider.LOCALAI -> AppConfig.updateField("models.localai.visionModel", textValue)
                                    ModelProvider.LMSTUDIO -> AppConfig.updateField("models.lmstudio.visionModel", textValue)
                                    else -> {}
                                }
                                lastSavedValue = textValue
                                showSavedIndicator = true
                            }
                        }
                    },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                enabled = isSupported,
                placeholder = {
                    Text(
                        text = if (isSupported) {
                            stringResource("settings.model.placeholder.enter", "vision")
                        } else {
                            stringResource("settings.model.placeholder.not.supported")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = showSavedIndicator,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
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
        }

        // Show not supported message
        if (!isSupported) {
            Text(
                text = stringResource("settings.vision.models.not.supported", getProviderDisplayName(selectedProvider)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                fontStyle = FontStyle.Italic,
            )
        }
    }
}
