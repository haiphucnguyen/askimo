/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.viewmodel.SettingsViewModel

@Composable
fun aiProviderSettingsSection(viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource("settings.ai.provider"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Chat Configuration Card
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
                        Text(
                            stringResource("settings.provider.change.button"),
                            modifier = Modifier.padding(start = 8.dp),
                        )
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
                        Text(
                            stringResource("settings.model.change.button"),
                            modifier = Modifier.padding(start = 8.dp),
                        )
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
                            Text(
                                stringResource("settings.change.button"),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
