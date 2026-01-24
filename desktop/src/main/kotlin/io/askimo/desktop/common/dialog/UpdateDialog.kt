/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.common.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.askimo.core.service.UpdateInfo
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.ui.markdownText
import io.askimo.desktop.shell.UpdateViewModel

@Composable
fun updateCheckDialog(
    viewModel: UpdateViewModel,
    onDismiss: () -> Unit,
) {
    when {
        viewModel.showUpdateDialog && viewModel.releaseInfo?.isNewVersion == true -> {
            newVersionDialog(
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
            upToDateDialog(
                currentVersion = viewModel.getCurrentVersion(),
                onDismiss = onDismiss,
            )
        }
        viewModel.errorMessage != null -> {
            errorDialog(
                message = viewModel.errorMessage!!,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun checkingForUpdatesDialog(onDismiss: () -> Unit) {
    ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource("update.dialog.title"),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                Text(
                    text = stringResource("update.check.checking"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun newVersionDialog(
    releaseInfo: UpdateInfo,
    currentVersion: String,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    ComponentColors.themedAlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(
                text = stringResource("update.dialog.title"),
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
                    text = stringResource("update.dialog.new.version.available"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Version info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ComponentColors.bannerCardColors(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = stringResource("update.dialog.current.version.label"),
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
                                text = stringResource("update.dialog.new.version.label"),
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
                        text = stringResource("update.dialog.release.date"),
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
                        text = stringResource("update.dialog.release.notes"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ComponentColors.bannerCardColors(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        ) {
                            markdownText(
                                markdown = releaseInfo.releaseNotes,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource("update.dialog.download"))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onLater,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource("update.dialog.later"))
            }
        },
    )
}

@Composable
private fun upToDateDialog(
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource("update.dialog.title"),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource("update.check.up.to.date"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ComponentColors.bannerCardColors(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource("update.dialog.current.version.label"),
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
            Button(
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource("action.ok"))
            }
        },
    )
}

@Composable
private fun errorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource("update.check.failed"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = ComponentColors.bannerCardColors(),
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
            Button(
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource("action.ok"))
            }
        },
    )
}
