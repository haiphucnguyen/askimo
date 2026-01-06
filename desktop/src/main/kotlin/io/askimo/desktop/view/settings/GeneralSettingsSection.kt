/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.askimo.core.context.AppContext
import io.askimo.core.context.AppContextConfigManager
import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.providers.Style
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.preferences.ThemePreferences
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.theme.FontSettings
import io.askimo.desktop.theme.FontSize
import io.askimo.desktop.view.components.clickableCard

@Composable
fun generalSettingsSection() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource("settings.general"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Language Selection
        languageSelectionCard()

        // Font Settings
        fontSettingsCard()

        // Conversation Presets
        conversationPresetsCard()
    }
}

@Composable
private fun languageSelectionCard() {
    val currentLocale by ThemePreferences.locale.collectAsState()
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    val availableLanguages = remember { LocalizationManager.availableLocales }

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
                text = stringResource("settings.app.language"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableCard { languageDropdownExpanded = true },
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
                            text = availableLanguages[currentLocale] ?: currentLocale.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Change language",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                ComponentColors.themedDropdownMenu(
                    expanded = languageDropdownExpanded,
                    onDismissRequest = { languageDropdownExpanded = false },
                ) {
                    availableLanguages.forEach { (locale, name) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (locale == currentLocale) "✓ $name" else name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                ThemePreferences.setLocale(locale)
                                languageDropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun fontSettingsCard() {
    val currentFontSettings by ThemePreferences.fontSettings.collectAsState()
    val availableFonts = remember { ThemePreferences.getAvailableSystemFonts() }
    var fontDropdownExpanded by remember { mutableStateOf(false) }
    var fontSizeDropdownExpanded by remember { mutableStateOf(false) }

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
            // Font Family
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource("settings.font.family"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableCard { fontDropdownExpanded = true },
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
                                text = currentFontSettings.fontFamily,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change font",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    ComponentColors.themedDropdownMenu(
                        expanded = fontDropdownExpanded,
                        onDismissRequest = { fontDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.5f),
                    ) {
                        availableFonts.forEach { fontFamily ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = fontFamily,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = if (fontFamily == FontSettings.SYSTEM_DEFAULT) {
                                                FontFamily.Default
                                            } else {
                                                when (fontFamily.lowercase()) {
                                                    "monospace", "courier", "courier new", "consolas", "monaco", "menlo",
                                                    "dejavu sans mono", "lucida console",
                                                    -> FontFamily.Monospace
                                                    "serif", "times", "times new roman", "georgia", "palatino",
                                                    "garamond", "baskerville", "book antiqua",
                                                    -> FontFamily.Serif
                                                    "cursive", "comic sans ms", "apple chancery", "brush script mt" -> FontFamily.Cursive
                                                    else -> FontFamily.SansSerif
                                                }
                                            },
                                        ),
                                    )
                                },
                                onClick = {
                                    ThemePreferences.setFontSettings(
                                        currentFontSettings.copy(fontFamily = fontFamily),
                                    )
                                    fontDropdownExpanded = false
                                },
                                leadingIcon = if (fontFamily == currentFontSettings.fontFamily) {
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

            HorizontalDivider()

            // Font Size
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource("settings.font.size"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableCard { fontSizeDropdownExpanded = true },
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
                                text = currentFontSettings.fontSize.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change size",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    ComponentColors.themedDropdownMenu(
                        expanded = fontSizeDropdownExpanded,
                        onDismissRequest = { fontSizeDropdownExpanded = false },
                    ) {
                        FontSize.entries.forEach { fontSize ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = fontSize.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                onClick = {
                                    ThemePreferences.setFontSettings(
                                        currentFontSettings.copy(fontSize = fontSize),
                                    )
                                    fontSizeDropdownExpanded = false
                                },
                                leadingIcon = if (fontSize == currentFontSettings.fontSize) {
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
}

@Composable
private fun conversationPresetsCard() {
    val appContext = AppContext.getInstance()
    var currentPresets by remember { mutableStateOf(appContext.params.presets) }
    var styleDropdownExpanded by remember { mutableStateOf(false) }

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
            // Style Setting
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource("settings.conversation.style"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource("settings.conversation.style.description"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableCard { styleDropdownExpanded = true },
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
                            Column {
                                Text(
                                    text = currentPresets.style.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = currentPresets.style.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change style",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    ComponentColors.themedDropdownMenu(
                        expanded = styleDropdownExpanded,
                        onDismissRequest = { styleDropdownExpanded = false },
                    ) {
                        Style.entries.forEach { style ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = style.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = style.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        )
                                    }
                                },
                                onClick = {
                                    val newPresets = currentPresets.copy(style = style)
                                    appContext.params.presets = newPresets
                                    AppContextConfigManager.save(appContext.params)
                                    currentPresets = newPresets
                                    styleDropdownExpanded = false
                                },
                                leadingIcon = if (style == currentPresets.style) {
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
}
