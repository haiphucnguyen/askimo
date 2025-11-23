/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.askimo.core.session.ProviderConfigField
import io.askimo.core.session.SettingField
import io.askimo.desktop.i18n.LocalizationManager
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.keymap.KeyMapManager
import io.askimo.desktop.model.AccentColor
import io.askimo.desktop.model.FontSettings
import io.askimo.desktop.model.FontSize
import io.askimo.desktop.model.ThemeMode
import io.askimo.desktop.service.ThemePreferences
import io.askimo.desktop.ui.theme.ComponentColors
import io.askimo.desktop.viewmodel.SettingsViewModel

@Composable
private fun Modifier.clickableCard(
    onClick: () -> Unit,
): Modifier {
    val shape = MaterialTheme.shapes.medium
    return this
        .clip(shape) // Clip to rounded shape first
        .clickable(onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)
}

@Composable
fun settingsView(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val currentThemeMode by ThemePreferences.themeMode.collectAsState()
    val currentAccentColor by ThemePreferences.accentColor.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success message
    LaunchedEffect(viewModel.showSuccessMessage) {
        if (viewModel.showSuccessMessage) {
            snackbarHostState.showSnackbar(viewModel.successMessage)
            viewModel.dismissSuccessMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource("settings.title"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            HorizontalDivider()

            // Chat Configuration Section
            Text(
                text = stringResource("settings.chat.config"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )

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
                        Button(
                            onClick = { viewModel.onChangeProvider() },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            colors = ComponentColors.subtleButtonColors(),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text(stringResource("settings.provider.change.button"), modifier = Modifier.padding(start = 8.dp))
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
                        Button(
                            onClick = { viewModel.onChangeModel() },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            colors = ComponentColors.subtleButtonColors(),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text(stringResource("settings.model.change.button"), modifier = Modifier.padding(start = 8.dp))
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
                                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                            Button(
                                onClick = { viewModel.onChangeSettings() },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                colors = ComponentColors.subtleButtonColors(),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Text(stringResource("settings.change.button"), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            // Appearance Section
            Text(
                text = stringResource("settings.appearance"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = stringResource("settings.theme"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )

            // Theme options
            themeOption(
                title = stringResource("theme.light"),
                description = stringResource("theme.light.description"),
                icon = { Icon(Icons.Default.LightMode, contentDescription = null) },
                selected = currentThemeMode == ThemeMode.LIGHT,
                onClick = { ThemePreferences.setThemeMode(ThemeMode.LIGHT) },
            )

            themeOption(
                title = stringResource("theme.dark"),
                description = stringResource("theme.dark.description"),
                icon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                selected = currentThemeMode == ThemeMode.DARK,
                onClick = { ThemePreferences.setThemeMode(ThemeMode.DARK) },
            )

            themeOption(
                title = stringResource("theme.system"),
                description = stringResource("theme.system.description"),
                icon = { Icon(Icons.Default.Contrast, contentDescription = null) },
                selected = currentThemeMode == ThemeMode.SYSTEM,
                onClick = { ThemePreferences.setThemeMode(ThemeMode.SYSTEM) },
            )

            // Language Section
            Text(
                text = stringResource("settings.language"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp),
            )

            languageSelectionCard()

            // Accent Color Section
            Text(
                text = stringResource("settings.accent.color"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp),
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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

            // Font Settings Section
            Text(
                text = stringResource("settings.font"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp),
            )

            fontSettingsCard()

            // Keyboard Shortcuts Section
            Text(
                text = stringResource("settings.shortcuts"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 24.dp),
            )

            keyboardShortcutsCard()
        }

        // Snackbar for success messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) { data ->
            Snackbar(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Text(data.visuals.message)
                }
            }
        }
    }

    // Model selection dialog
    if (viewModel.showModelDialog) {
        modelSelectionDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModelDialog() },
            onSelectModel = { viewModel.selectModel(it) },
        )
    }

    // Settings configuration dialog
    if (viewModel.showSettingsDialog) {
        settingsConfigurationDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeSettingsDialog() },
        )
    }

    // Provider selection dialog
    if (viewModel.showProviderDialog) {
        providerSelectionDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeProviderDialog() },
            onSave = { viewModel.saveProvider() },
        )
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
                                    // Apply the font style directly to the font name
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

@Composable
private fun keyboardShortcutsCard() {
    val shortcutsByCategory = remember {
        KeyMapManager.getAllShortcuts().mapValues { (_, shortcuts) ->
            shortcuts.map { shortcut ->
                shortcut.description to shortcut.getDisplayString()
            }
        }
    }

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
            Text(
                text = stringResource("settings.shortcuts.available"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            // Convert to list for easier column distribution
            val categoriesList = shortcutsByCategory.entries.toList()
            // Use 2 columns if we have 4 or fewer categories, otherwise 3 columns for larger lists
            val columnsCount = if (categoriesList.size <= 4) 2 else 3

            // Distribute categories across columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                for (columnIndex in 0 until columnsCount) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        categoriesList.filterIndexed { index, _ ->
                            index % columnsCount == columnIndex
                        }.forEach { (category, shortcuts) ->
                            shortcutCategory(category)
                            shortcuts.forEach { (description, shortcut) ->
                                shortcutRowCompact(description, shortcut)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun shortcutCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun shortcutRowCompact(description: String, shortcut: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ),
        ) {
            Text(
                text = shortcut,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun modelSelectionDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onSelectModel: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource("settings.model.select.title")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                Card(
                                    colors = ComponentColors.surfaceVariantCardColors(),
                                ) {
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
                            text = stringResource("settings.model.select", viewModel.provider?.name ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        viewModel.availableModels.forEach { model ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickableCard { onSelectModel(model) },
                                colors = if (model == viewModel.model) {
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
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (model == viewModel.model) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    if (model == viewModel.model) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                        )
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
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource("action.close"))
            }
        },
    )
}

@Composable
private fun settingsConfigurationDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource("settings.configure.title", viewModel.provider?.name ?: "")) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                viewModel.settingsFields.forEach { field ->
                    when (field) {
                        is SettingField.TextField -> {
                            textFieldSetting(
                                field = field,
                                onValueChange = { viewModel.updateSettingsField(field.name, it) },
                            )
                        }
                        is SettingField.EnumField -> {
                            enumFieldSetting(
                                field = field,
                                onValueChange = { viewModel.updateSettingsField(field.name, it) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource("action.ok"))
            }
        },
    )
}

@Composable
private fun textFieldSetting(
    field: SettingField.TextField,
    onValueChange: (String) -> Unit,
) {
    var textValue by remember { mutableStateOf(field.value) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = field.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                onValueChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (field.isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (field.isPassword) {
                {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = if (passwordVisible) "Hide" else "Show",
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            colors = ComponentColors.outlinedTextFieldColors(),
        )
    }
}

@Composable
private fun enumFieldSetting(
    field: SettingField.EnumField,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = field.options.find { it.value == field.value }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = field.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableCard { expanded = true },
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
                            text = selectedOption?.label ?: field.value,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        selectedOption?.description?.let { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            ComponentColors.themedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                field.options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onValueChange(option.value)
                            expanded = false
                        },
                        leadingIcon = if (option.value == field.value) {
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

@Composable
private fun providerSelectionDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource("provider.change.title"),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
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
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Test Connection button
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

                // Save button
                Button(
                    onClick = onSave,
                    enabled = viewModel.selectedProvider != null && !viewModel.isTestingConnection,
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
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource("settings.cancel"))
            }
        },
    )
}
