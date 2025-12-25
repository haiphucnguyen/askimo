/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.preferences.ThemePreferences
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.viewmodel.SettingsViewModel
import org.jetbrains.skia.Image
import java.awt.Cursor

enum class SettingsSection {
    GENERAL,
    AI_PROVIDER,
    APPEARANCE,
    SHORTCUTS,
    ADVANCED,
    ABOUT,
}

@Composable
fun settingsViewWithSidebar(
    onClose: () -> Unit,
    settingsViewModel: SettingsViewModel,
    initialSection: SettingsSection = SettingsSection.GENERAL,
) {
    var selectedSection by remember { mutableStateOf(initialSection) }
    // Sidebar width as a fraction of screen width (0.0 to 1.0) - load from preferences
    var sidebarWidthFraction by remember { mutableStateOf(ThemePreferences.getSettingsSidebarWidthFraction()) }

    // Settings view - full screen replacement with top header bar
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top Header Bar (full width)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComponentColors.sidebarHeaderColor())
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = remember {
                        BitmapPainter(
                            Image.makeFromEncoded(
                                object {}.javaClass.getResourceAsStream("/images/askimo_logo_64.png")?.readBytes()
                                    ?: throw IllegalStateException("Icon not found"),
                            ).toComposeImageBitmap(),
                        )
                    },
                    contentDescription = "Askimo",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Askimo AI",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource("settings.title"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource("action.close"),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        HorizontalDivider()

        // Content area: Sidebar + Main Content
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Calculate actual sidebar width from fraction
            // Min 180dp, max 30% of screen width, default ~18%
            val minSidebarWidth = 180.dp
            val maxSidebarWidthFraction = 0.30f
            val calculatedWidth = (maxWidth * sidebarWidthFraction).coerceIn(
                minSidebarWidth,
                maxWidth * maxSidebarWidthFraction,
            )

            // Capture maxWidth for use in pointerInput
            val containerWidth = maxWidth

            Row(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Left Sidebar
                Column(
                    modifier = Modifier
                        .width(calculatedWidth)
                        .fillMaxHeight()
                        .background(ComponentColors.sidebarSurfaceColor()),
                ) {
                    settingsSidebarItem(
                        title = stringResource("settings.general"),
                        icon = Icons.Default.Settings,
                        isSelected = selectedSection == SettingsSection.GENERAL,
                        onClick = { selectedSection = SettingsSection.GENERAL },
                    )
                    settingsSidebarItem(
                        title = stringResource("settings.ai.provider"),
                        icon = Icons.Default.SmartToy,
                        isSelected = selectedSection == SettingsSection.AI_PROVIDER,
                        onClick = { selectedSection = SettingsSection.AI_PROVIDER },
                    )
                    settingsSidebarItem(
                        title = stringResource("settings.appearance"),
                        icon = Icons.Default.Palette,
                        isSelected = selectedSection == SettingsSection.APPEARANCE,
                        onClick = { selectedSection = SettingsSection.APPEARANCE },
                    )
                    settingsSidebarItem(
                        title = stringResource("settings.shortcuts"),
                        icon = Icons.Outlined.Keyboard,
                        isSelected = selectedSection == SettingsSection.SHORTCUTS,
                        onClick = { selectedSection = SettingsSection.SHORTCUTS },
                    )
                    settingsSidebarItem(
                        title = stringResource("settings.advanced"),
                        icon = Icons.Outlined.Tune,
                        isSelected = selectedSection == SettingsSection.ADVANCED,
                        onClick = { selectedSection = SettingsSection.ADVANCED },
                    )
                    settingsSidebarItem(
                        title = stringResource("settings.about"),
                        icon = Icons.Default.Info,
                        isSelected = selectedSection == SettingsSection.ABOUT,
                        onClick = { selectedSection = SettingsSection.ABOUT },
                    )
                } // End Left Sidebar Column

                // Draggable divider
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(containerWidth) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // Calculate new fraction based on drag
                                val dragWidthDp = (dragAmount.x / density).dp
                                val newFraction = sidebarWidthFraction + (dragWidthDp / containerWidth)
                                // Min 10%, max 30%
                                val coercedFraction = newFraction.coerceIn(0.10f, 0.30f)
                                sidebarWidthFraction = coercedFraction
                                // Save preference
                                ThemePreferences.setSettingsSidebarWidthFraction(coercedFraction)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight(0.1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }

                // Main Content Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    when (selectedSection) {
                        SettingsSection.GENERAL -> generalSettingsSection()
                        SettingsSection.AI_PROVIDER -> aiProviderSettingsSection(settingsViewModel)
                        SettingsSection.APPEARANCE -> appearanceSettingsSection()
                        SettingsSection.SHORTCUTS -> shortcutsSettingsSection()
                        SettingsSection.ADVANCED -> advancedSettingsSection()
                        SettingsSection.ABOUT -> aboutSettingsSection()
                    }
                } // End Box (main content)
            } // End Row (sidebar + content)
        }
    }

    // Dialogs
    if (settingsViewModel.showModelDialog) {
        modelSelectionDialog(
            viewModel = settingsViewModel,
            onDismiss = { settingsViewModel.closeModelDialog() },
            onSelect = { model -> settingsViewModel.selectModel(model) },
        )
    }

    if (settingsViewModel.showProviderDialog) {
        providerSelectionDialog(
            viewModel = settingsViewModel,
            onDismiss = { settingsViewModel.closeProviderDialog() },
            onSave = { settingsViewModel.saveProvider() },
        )
    }

    if (settingsViewModel.showSettingsDialog) {
        settingsConfigDialog(
            viewModel = settingsViewModel,
            onDismiss = { settingsViewModel.closeSettingsDialog() },
        )
    }
} // End settingsViewWithSidebar function

@Composable
private fun settingsSidebarItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Transparent
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
