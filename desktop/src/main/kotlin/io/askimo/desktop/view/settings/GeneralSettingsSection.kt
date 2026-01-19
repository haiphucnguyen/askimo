/* SPDX-License-Identifier: AGPLv3
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.askimo.core.config.AppConfig
import io.askimo.core.i18n.LocalizationManager
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun samplingSettingsCard() {
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

                // Temperature Setting
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource("settings.sampling.temperature"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "(${stringResource("settings.sampling.range")}: 0.0 - 2.0)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        text = stringResource("settings.sampling.temperature.description"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )

                    doubleValueControl(
                        value = AppConfig.chat.sampling.temperature,
                        onValueChange = { newValue ->
                            AppConfig.updateField("chat.sampling.temperature", newValue.coerceIn(0.0, 2.0))
                        },
                        min = 0.0,
                        max = 2.0,
                        step = 0.1,
                    )
                }

                HorizontalDivider()

                // TopP Setting
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource("settings.sampling.topP"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "(${stringResource("settings.sampling.range")}: 0.0 - 1.0)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        text = stringResource("settings.sampling.topP.description"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )

                    doubleValueControl(
                        value = AppConfig.chat.sampling.topP,
                        onValueChange = { newValue ->
                            AppConfig.updateField("chat.sampling.topP", newValue.coerceIn(0.0, 1.0))
                        },
                        min = 0.0,
                        max = 1.0,
                        step = 0.1,
                    )
                }
            }
        }
    }
}

@Composable
private fun doubleValueControl(
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Double,
    max: Double,
    step: Double,
) {
    // Use value as key to recreate state when external value changes
    var internalValue by remember(value) { mutableStateOf(value) }
    var textValue by remember(value) { mutableStateOf(String.format("%.1f", value)) }
    var isEditing by remember { mutableStateOf(false) }

    // Compact stepper: Decrement button - Text field - Increment button
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decrement button
        IconButton(
            onClick = {
                val newValue = ((internalValue - step) * 10 + 0.5).toLong() / 10.0
                val clamped = newValue.coerceIn(min, max)
                internalValue = clamped
                onValueChange(clamped)
            },
            enabled = internalValue > min,
            modifier = Modifier.width(36.dp),
        ) {
            Text(
                text = "−",
                style = MaterialTheme.typography.titleMedium,
                color = if (internalValue > min) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }

        // Editable text field
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            if (isEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    TextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.width(60.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    IconButton(
                        onClick = {
                            val parsed = textValue.toDoubleOrNull()
                            if (parsed != null) {
                                val rounded = ((parsed * 10) + 0.5).toLong() / 10.0
                                val clamped = rounded.coerceIn(min, max)
                                internalValue = clamped
                                onValueChange(clamped)
                            }
                            isEditing = false
                        },
                        modifier = Modifier.width(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Apply",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                Text(
                    text = String.format("%.1f", internalValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickableCard {
                            isEditing = true
                            textValue = String.format("%.1f", internalValue)
                        },
                )
            }
        }

        // Increment button
        IconButton(
            onClick = {
                val newValue = ((internalValue + step) * 10 + 0.5).toLong() / 10.0
                val clamped = newValue.coerceIn(min, max)
                internalValue = clamped
                onValueChange(clamped)
            },
            enabled = internalValue < max,
            modifier = Modifier.width(36.dp),
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.titleMedium,
                color = if (internalValue < max) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}
