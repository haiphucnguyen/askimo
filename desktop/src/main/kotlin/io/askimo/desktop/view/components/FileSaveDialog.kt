/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import java.io.File

/**
 * A styled file save dialog that matches the application's Material Design 3 theme.
 *
 * @param title The dialog title
 * @param defaultFilename The default filename to suggest
 * @param onDismiss Callback when the dialog is dismissed
 * @param onSave Callback when a file is selected, receives the full path
 */
@Composable
fun fileSaveDialog(
    title: String,
    defaultFilename: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var currentDirectory by remember { mutableStateOf(File(System.getProperty("user.home"))) }
    var filename by remember { mutableStateOf(defaultFilename) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }

    // Pre-load string resources to avoid composable invocation in onClick
    val selectFolderTitle = stringResource("file.dialog.select.folder")

    // Load files in current directory
    LaunchedEffect(currentDirectory) {
        files = try {
            currentDirectory.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(600.dp)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Current path - Editable with browse button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = currentDirectory.absolutePath,
                        onValueChange = { newPath ->
                            val newDir = File(newPath)
                            if (newDir.exists() && newDir.isDirectory) {
                                currentDirectory = newDir
                            }
                        },
                        label = { Text(stringResource("file.dialog.path")) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = ComponentColors.outlinedTextFieldColors(),
                    )

                    Button(
                        onClick = {
                            // Open native folder picker
                            val folderChooser = java.awt.FileDialog(
                                null as java.awt.Frame?,
                                selectFolderTitle,
                                java.awt.FileDialog.LOAD,
                            )
                            // Set to directory mode (works on some platforms)
                            System.setProperty("apple.awt.fileDialogForDirectories", "true")
                            folderChooser.isVisible = true
                            System.setProperty("apple.awt.fileDialogForDirectories", "false")

                            val selectedDir = folderChooser.directory
                            if (selectedDir != null) {
                                val dir = File(selectedDir)
                                if (dir.exists() && dir.isDirectory) {
                                    currentDirectory = dir
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("file.dialog.browse"))
                    }
                }

                // Quick access - Home button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            currentDirectory = File(System.getProperty("user.home"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(stringResource("file.dialog.home"))
                    }

                    // Parent directory button
                    if (currentDirectory.parent != null) {
                        Button(
                            onClick = {
                                currentDirectory.parentFile?.let {
                                    currentDirectory = it
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text("↑ ${stringResource("file.dialog.parent")}")
                        }
                    }
                }

                // Directory list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.small,
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (files.isEmpty()) {
                        Text(
                            text = stringResource("file.dialog.empty"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    } else {
                        files.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (file.isDirectory) {
                                            currentDirectory = file
                                        }
                                    }
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        MaterialTheme.shapes.small,
                                    )
                                    .padding(8.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // Filename input
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text(stringResource("file.dialog.filename")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = ComponentColors.outlinedTextFieldColors(),
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ComponentColors.primaryTextButtonColors(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("action.cancel"))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (filename.isNotBlank()) {
                                val fullPath = File(currentDirectory, filename).absolutePath
                                onSave(fullPath)
                            }
                        },
                        enabled = filename.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("action.save"))
                    }
                }
            }
        }
    }
}
