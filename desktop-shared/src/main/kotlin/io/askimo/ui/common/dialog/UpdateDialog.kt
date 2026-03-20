/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.common.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.askimo.core.service.UpdateInfo
import io.askimo.ui.common.components.primaryButton
import io.askimo.ui.common.components.secondaryButton
import io.askimo.ui.common.i18n.stringResource
import io.askimo.ui.common.theme.ComponentColors
import io.askimo.ui.common.ui.markdownText
import io.askimo.ui.shell.UpdateViewModel

@Composable
fun updateCheckDialog(
    viewModel: io.askimo.ui.shell.UpdateViewModel,
    onDismiss: () -> Unit,
) {
    when {
        viewModel.showUpdateDialog && viewModel.releaseInfo?.isNewVersion == true -> {
            _root_ide_package_.io.askimo.ui.common.dialog.newVersionDialog(
                releaseInfo = viewModel.releaseInfo!!,
                currentVersion = viewModel.getCurrentVersion(),
                onDownload = {
                    viewModel.openDownloadPage()
                    onDismiss()
                },
                onLater = onDismiss,
            )
        }
        viewModel.releaseInfo != null && !viewModel.releaseInfo!!.isNewVersion -> {
            _root_ide_package_.io.askimo.ui.common.dialog.upToDateDialog(
                currentVersion = viewModel.getCurrentVersion(),
                onDismiss = onDismiss,
            )
        }
        viewModel.errorMessage != null -> {
            _root_ide_package_.io.askimo.ui.common.dialog.errorDialog(
                message = viewModel.errorMessage!!,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun newVersionDialog(
    releaseInfo: UpdateInfo,
    currentVersion: String,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    _root_ide_package_.io.askimo.ui.common.theme.ComponentColors.themedAlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(
                text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.title"),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header message
                Text(
                    text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.new.version.available"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Version info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = _root_ide_package_.io.askimo.ui.common.theme.ComponentColors.bannerCardColors(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.current.version.label"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                text = currentVersion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.new.version.label"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                text = releaseInfo.latestVersion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Release info
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.release.date"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        text = releaseInfo.releaseDate,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Release notes
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.release.notes"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = _root_ide_package_.io.askimo.ui.common.theme.ComponentColors.bannerCardColors(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        ) {
                            _root_ide_package_.io.askimo.ui.common.ui.markdownText(
                                markdown = releaseInfo.releaseNotes,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            _root_ide_package_.io.askimo.ui.common.components.primaryButton(
                onClick = onDownload,
            ) {
                Text(_root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.download"))
            }
        },
        dismissButton = {
            _root_ide_package_.io.askimo.ui.common.components.secondaryButton(
                onClick = onLater,
            ) {
                Text(_root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.later"))
            }
        },
    )
}

@Composable
private fun upToDateDialog(
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    _root_ide_package_.io.askimo.ui.common.theme.ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.title"),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.check.up.to.date"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = _root_ide_package_.io.askimo.ui.common.theme.ComponentColors.bannerCardColors(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.dialog.current.version.label"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                        Text(
                            text = currentVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        },
        confirmButton = {
            _root_ide_package_.io.askimo.ui.common.components.primaryButton(
                onClick = onDismiss,
            ) {
                Text(_root_ide_package_.io.askimo.ui.common.i18n.stringResource("action.ok"))
            }
        },
    )
}

@Composable
private fun errorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    _root_ide_package_.io.askimo.ui.common.theme.ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = _root_ide_package_.io.askimo.ui.common.i18n.stringResource("update.check.failed"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = _root_ide_package_.io.askimo.ui.common.theme.ComponentColors.bannerCardColors(),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        confirmButton = {
            _root_ide_package_.io.askimo.ui.common.components.primaryButton(
                onClick = onDismiss,
            ) {
                Text(_root_ide_package_.io.askimo.ui.common.i18n.stringResource("action.ok"))
            }
        },
    )
}
