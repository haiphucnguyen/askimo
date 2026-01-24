/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.askimo.core.VersionInfo
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import java.awt.Desktop
import java.net.URI
import java.time.Year

@Composable
fun aboutSettingsSection() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource("settings.about"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Application Info Card
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = VersionInfo.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "${stringResource("about.version")} ${VersionInfo.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                HorizontalDivider()

                // Build Information
                infoRow(
                    icon = Icons.Default.Build,
                    label = stringResource("about.buildDate"),
                    value = VersionInfo.buildDate,
                )

                infoRow(
                    icon = Icons.Default.Code,
                    label = stringResource("about.buildJdk"),
                    value = VersionInfo.buildJdk,
                )

                // Website Link
                TextButton(
                    onClick = {
                        openUrl("https://askimo.chat")
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "askimo.chat",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Description Card
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
                    text = stringResource("about.description"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource("about.description.text"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        // License Card
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
                    text = stringResource("about.license"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = VersionInfo.license,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource("about.copyright", Year.now().value, VersionInfo.author),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }

        // Runtime Information Card
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
                    text = stringResource("about.runtime.info"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                infoRow(
                    icon = Icons.Default.Update,
                    label = "Runtime VM",
                    value = VersionInfo.runtimeVm,
                    useMonospace = true,
                )

                infoRow(
                    icon = Icons.Default.Code,
                    label = "Runtime Version",
                    value = VersionInfo.runtimeVersion,
                    useMonospace = true,
                )
            }
        }

        // Links Section
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
                    text = stringResource("about.links"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                TextButton(
                    onClick = { openUrl("https://github.com/haiphucnguyen/askimo") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text("GitHub Repository")
                }

                TextButton(
                    onClick = { openUrl("https://github.com/haiphucnguyen/askimo/issues") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text("Report Issues")
                }

                TextButton(
                    onClick = { openUrl("https://github.com/haiphucnguyen/askimo/blob/main/LICENSE") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text("View License")
                }
            }
        }
    }
}

@Composable
private fun infoRow(
    icon: ImageVector,
    label: String,
    value: String,
    useMonospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

private fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (e: Exception) {
        // Silently fail if browser cannot be opened
    }
}
