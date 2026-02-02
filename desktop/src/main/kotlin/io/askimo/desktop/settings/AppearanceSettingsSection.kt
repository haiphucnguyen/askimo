/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.AccentColor
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.Spacing
import io.askimo.desktop.common.theme.ThemeMode
import io.askimo.desktop.common.theme.ThemePreferences
import io.askimo.desktop.common.ui.asyncImage
import io.askimo.desktop.common.ui.clickableCard
import io.askimo.desktop.service.AvatarService
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun appearanceSettingsSection() {
    val currentThemeMode by ThemePreferences.themeMode.collectAsState()
    val currentAccentColor by ThemePreferences.accentColor.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Text(
            text = stringResource("settings.appearance"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = Spacing.small),
        )

        // Theme Mode Section
        Text(
            text = stringResource("settings.theme"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // Light Mode
        themeOption(
            title = stringResource("theme.light"),
            description = stringResource("theme.light.description"),
            icon = { Icon(Icons.Default.LightMode, contentDescription = null) },
            selected = currentThemeMode == ThemeMode.LIGHT,
            onClick = { ThemePreferences.setThemeMode(ThemeMode.LIGHT) },
        )

        // Dark Mode
        themeOption(
            title = stringResource("theme.dark"),
            description = stringResource("theme.dark.description"),
            icon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
            selected = currentThemeMode == ThemeMode.DARK,
            onClick = { ThemePreferences.setThemeMode(ThemeMode.DARK) },
        )

        // System Mode
        themeOption(
            title = stringResource("theme.system"),
            description = stringResource("theme.system.description"),
            icon = { Icon(Icons.Default.Contrast, contentDescription = null) },
            selected = currentThemeMode == ThemeMode.SYSTEM,
            onClick = { ThemePreferences.setThemeMode(ThemeMode.SYSTEM) },
        )

        // Accent Color Section
        Text(
            text = stringResource("settings.accent.color"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = Spacing.small),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            AccentColor.entries.forEach { accentColor ->
                Box(
                    modifier = Modifier.widthIn(min = 120.dp, max = 160.dp),
                ) {
                    accentColorOption(
                        accentColor = accentColor,
                        selected = currentAccentColor == accentColor,
                        onClick = { ThemePreferences.setAccentColor(accentColor) },
                    )
                }
            }
        }

        // AI Avatar Section (User avatar moved to User Profile)
        aiAvatarSettingsSection()
    }
}

@Composable
private fun themeOption(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickableCard(onClick = onClick),
        colors = if (selected) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                icon()
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun aiAvatarSettingsSection() {
    val avatarService = remember { AvatarService() }
    var aiAvatarPath by remember { mutableStateOf(ThemePreferences.getAIAvatarPath()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Spacer(modifier = Modifier.height(Spacing.small))

        Text(
            text = stringResource("settings.appearance.avatar.ai"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = stringResource("settings.appearance.avatar.ai.description"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // AI avatar only (user avatar is in User Profile now)
        avatarSetting(
            label = stringResource("settings.appearance.avatar.ai.label"),
            currentAvatar = aiAvatarPath,
            defaultIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
            onSelectAvatar = { path ->
                val savedPath = avatarService.saveAIAvatar(path)
                if (savedPath != null) {
                    ThemePreferences.setAIAvatarPath(savedPath)
                    aiAvatarPath = savedPath
                }
            },
            onRemoveAvatar = {
                avatarService.removeAIAvatar()
                ThemePreferences.setAIAvatarPath(null)
                aiAvatarPath = null
            },
        )
    }
}

@Composable
private fun avatarSetting(
    label: String,
    currentAvatar: String?,
    defaultIcon: @Composable () -> Unit,
    onSelectAvatar: (String) -> Unit,
    onRemoveAvatar: () -> Unit,
) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Avatar preview
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (currentAvatar != null) {
                        // Load and display avatar image
                        asyncImage(
                            imagePath = currentAvatar,
                            contentDescription = label,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    } else {
                        // Default icon
                        defaultIcon()
                    }
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Select button
                TextButton(
                    onClick = {
                        val fileDialog = FileDialog(
                            null as Frame?,
                            "Select Avatar",
                            FileDialog.LOAD,
                        )
                        fileDialog.setFilenameFilter { _, name ->
                            name.lowercase().endsWith(".png") ||
                                name.lowercase().endsWith(".jpg") ||
                                name.lowercase().endsWith(".jpeg") ||
                                name.lowercase().endsWith(".gif") ||
                                name.lowercase().endsWith(".bmp")
                        }
                        fileDialog.isVisible = true
                        val selectedFile = fileDialog.file
                        val selectedDir = fileDialog.directory
                        if (selectedFile != null && selectedDir != null) {
                            onSelectAvatar(File(selectedDir, selectedFile).absolutePath)
                        }
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource("settings.appearance.avatar.select"))
                }

                // Remove button (only show if avatar exists)
                if (currentAvatar != null) {
                    TextButton(
                        onClick = onRemoveAvatar,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("action.remove"))
                    }
                }
            }
        }
    }
}

@Composable
private fun accentColorOption(
    accentColor: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickableCard(onClick = onClick),
        colors = if (selected) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Color preview circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = accentColor.lightColor,
                            shape = MaterialTheme.shapes.small,
                        ),
                )
                Text(
                    text = accentColor.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
