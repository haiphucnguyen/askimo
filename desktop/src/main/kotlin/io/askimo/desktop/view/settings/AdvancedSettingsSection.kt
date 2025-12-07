/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.askimo.core.logging.LogLevel
import io.askimo.core.logging.LoggingService
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.preferences.DeveloperModePreferences
import io.askimo.desktop.preferences.ThemePreferences
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.util.Platform
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.File

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
