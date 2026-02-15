/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.askimo.core.config.AppConfig
import io.askimo.core.i18n.LocalizationManager
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.FontSettings
import io.askimo.desktop.common.theme.FontSize
import io.askimo.desktop.common.theme.Spacing
import io.askimo.desktop.common.theme.ThemePreferences
import io.askimo.desktop.common.ui.clickableCard
import io.askimo.desktop.common.ui.themedTooltip

@Composable
fun generalSettingsSection() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
        Text(
            text = stringResource("settings.general"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = Spacing.small),
        )

        // Language Selection
        languageSelectionCard()

        // Font Settings
        fontSettingsCard()

        // AI Sampling Settings
        samplingSettingsCard()
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
                .padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
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
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                ThemePreferences.setLocale(locale)
                                languageDropdownExpanded = false
                            },
                            leadingIcon = if (locale == currentLocale) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            HorizontalDivider()

            // Preferred AI Response Language
            preferredAIResponseLanguageField(availableLanguages)
        }
    }
}

@Composable
private fun preferredAIResponseLanguageField(availableLanguages: Map<java.util.Locale, String>) {
    var aiLanguageDropdownExpanded by remember { mutableStateOf(false) }
    val currentAILocale = AppConfig.chat.defaultResponseAILocale

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource("settings.ai.response.language"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            themedTooltip(
                text = stringResource("settings.ai.response.language.tooltip"),
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Information",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.width(24.dp),
                )
            }
        }
        Text(
            text = stringResource("settings.ai.response.language.description"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableCard { aiLanguageDropdownExpanded = true },
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
                        text = if (currentAILocale == null) {
                            stringResource("settings.ai.response.language.auto")
                        } else {
                            availableLanguages.entries.find { it.key.toString() == currentAILocale }?.value
                                ?: currentAILocale
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Change AI response language",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            ComponentColors.themedDropdownMenu(
                expanded = aiLanguageDropdownExpanded,
                onDismissRequest = { aiLanguageDropdownExpanded = false },
            ) {
                // Auto-detect option
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource("settings.ai.response.language.auto"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        AppConfig.updateField("chat.defaultResponseAILocale", "")
                        aiLanguageDropdownExpanded = false
                    },
                    leadingIcon = if (currentAILocale == null) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        null
                    },
                )

                // Language options
                availableLanguages.forEach { (locale, name) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            AppConfig.updateField("chat.defaultResponseAILocale", locale.toString())
                            aiLanguageDropdownExpanded = false
                        },
                        leadingIcon = if (locale.toString() == currentAILocale) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onSurface,
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
                .padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            // Font Family
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
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
                                            tint = MaterialTheme.colorScheme.onSurface,
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
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
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            contentDescription = "Selected",
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
private fun samplingSettingsCard() {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource("settings.sampling.title"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource("settings.sampling.description"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked = AppConfig.chat.sampling.enabled,
                    onCheckedChange = { newEnabled ->
                        AppConfig.updateField("chat.sampling.enabled", newEnabled)
                    },
                )
            }

            if (AppConfig.chat.sampling.enabled) {
                HorizontalDivider()

                // Creativity Slider (Temperature 0.0 - 1.0)
                var sliderValue by remember { mutableStateOf(AppConfig.chat.sampling.temperature.toFloat()) }

                // Sync slider with AppConfig when AppConfig changes externally
                sliderValue = AppConfig.chat.sampling.temperature.toFloat()

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text(
                        text = stringResource("settings.sampling.creativity"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    Text(
                        text = stringResource("settings.sampling.creativity.description"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            sliderValue = newValue
                            AppConfig.updateField("chat.sampling.temperature", newValue.toDouble())
                            // Keep topP at 1.0
                            AppConfig.updateField("chat.sampling.topP", 1.0)
                        },
                        valueRange = 0f..1f,
                    )

                    // Slider labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource("settings.sampling.creativity.strict"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        )
                        Text(
                            text = stringResource("settings.sampling.creativity.creative"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
