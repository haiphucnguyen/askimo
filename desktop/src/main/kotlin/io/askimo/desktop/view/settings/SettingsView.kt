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
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.viewmodel.SettingsViewModel
import org.jetbrains.skia.Image

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
) {
    var selectedSection by remember { mutableStateOf(SettingsSection.GENERAL) }
    var sidebarWidth by remember { mutableStateOf(240.dp) }

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
                    text = "— ${stringResource("settings.title")}",
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Left Sidebar
            Column(
                modifier = Modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .background(ComponentColors.sidebarSurfaceColor()),
            ) {
                settingsSidebarItem(
                    title = stringResource("settings.general"),
                    isSelected = selectedSection == SettingsSection.GENERAL,
                    onClick = { selectedSection = SettingsSection.GENERAL },
                )
                settingsSidebarItem(
                    title = stringResource("settings.ai.provider"),
                    isSelected = selectedSection == SettingsSection.AI_PROVIDER,
                    onClick = { selectedSection = SettingsSection.AI_PROVIDER },
                )
                settingsSidebarItem(
                    title = stringResource("settings.appearance"),
                    isSelected = selectedSection == SettingsSection.APPEARANCE,
                    onClick = { selectedSection = SettingsSection.APPEARANCE },
                )
                settingsSidebarItem(
                    title = stringResource("settings.shortcuts"),
                    isSelected = selectedSection == SettingsSection.SHORTCUTS,
                    onClick = { selectedSection = SettingsSection.SHORTCUTS },
                )
                settingsSidebarItem(
                    title = stringResource("settings.advanced"),
                    isSelected = selectedSection == SettingsSection.ADVANCED,
                    onClick = { selectedSection = SettingsSection.ADVANCED },
                )
                settingsSidebarItem(
                    title = stringResource("settings.about"),
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
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newWidth = (sidebarWidth.value + dragAmount.x / density).dp
                            sidebarWidth = newWidth.coerceIn(200.dp, 400.dp)
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
    } // End Column (settings view with top header)
} // End settingsViewWithSidebar function

@Composable
private fun settingsSidebarItem(
    title: String,
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
        Text(
            text = title,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
