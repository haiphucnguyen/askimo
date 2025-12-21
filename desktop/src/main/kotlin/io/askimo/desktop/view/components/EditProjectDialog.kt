/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.chat.domain.Project
import io.askimo.core.util.JsonUtils.json
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Dialog for editing an existing project.
 */
@Composable
fun editProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (projectId: String, name: String, description: String?, indexedPaths: String) -> Unit,
) {
    var projectName by remember { mutableStateOf(project.name) }
    var projectDescription by remember { mutableStateOf(project.description ?: "") }

    // Parse existing indexed paths from JSON
    val existingPaths = remember {
        try {
            json.decodeFromString<List<String>>(project.indexedPaths).firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
    var selectedFolder by remember { mutableStateOf(existingPaths) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var folderError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    val emptyNameError = stringResource("project.new.dialog.name.error.empty")
    val errorFolderNotExists = stringResource("project.new.dialog.error.folder.notexists")
    val errorFolderNotFolder = stringResource("project.new.dialog.error.folder.notfolder")
    val errorFolderNotReadable = stringResource("project.new.dialog.error.folder.notreadable")
    val browseFolderTitle = stringResource("project.new.dialog.folder.browse")

    // Validate folder path
    fun validateFolder(path: String?): FolderValidationResult {
        if (path.isNullOrBlank()) return FolderValidationResult.Empty

        val folder = File(path)
        return when {
            !folder.exists() -> FolderValidationResult.NotExists
            !folder.isDirectory -> FolderValidationResult.NotAFolder
            !folder.canRead() -> FolderValidationResult.NotReadable
            else -> FolderValidationResult.Valid
        }
    }

    // Browse for folder using FileDialog
    fun browseForFolder() {
        val dialog = FileDialog(null as Frame?, browseFolderTitle, FileDialog.LOAD)

        // macOS: Enable folder selection
        System.setProperty("apple.awt.fileDialogForDirectories", "true")

        dialog.isVisible = true

        System.setProperty("apple.awt.fileDialogForDirectories", "false")

        if (dialog.file != null) {
            val folderPath = File(dialog.directory, dialog.file).absolutePath
            selectedFolder = folderPath

            // Validate selected folder
            when (validateFolder(folderPath)) {
                FolderValidationResult.Valid -> folderError = null
                FolderValidationResult.NotExists -> folderError = errorFolderNotExists
                FolderValidationResult.NotAFolder -> folderError = errorFolderNotFolder
                FolderValidationResult.NotReadable -> folderError = errorFolderNotReadable
                FolderValidationResult.Empty -> folderError = null
            }
        }
    }

    // Extract save logic to reuse in button and Enter key handler
    fun performSave() {
        // Validate project name
        if (projectName.trim().isEmpty()) {
            nameError = emptyNameError
            return
        }

        // Validate folder if provided
        if (selectedFolder != null) {
            when (validateFolder(selectedFolder)) {
                FolderValidationResult.Valid -> {
                    // Valid, proceed
                }
                FolderValidationResult.Empty -> {
                    selectedFolder = null // Clear if empty
                }
                FolderValidationResult.NotExists -> {
                    folderError = errorFolderNotExists
                    return
                }
                FolderValidationResult.NotAFolder -> {
                    folderError = errorFolderNotFolder
                    return
                }
                FolderValidationResult.NotReadable -> {
                    folderError = errorFolderNotReadable
                    return
                }
            }
        }

        // Convert folder path to JSON array
        val indexedPathsJson = if (selectedFolder != null) {
            json.encodeToString(listOf(selectedFolder))
        } else {
            json.encodeToString(emptyList<String>())
        }

        onSave(
            project.id,
            projectName.trim(),
            projectDescription.trim().takeIf { it.isNotEmpty() },
            indexedPathsJson,
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title
                Text(
                    text = stringResource("project.edit.dialog.title"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Project Name Field
                OutlinedTextField(
                    value = projectName,
                    onValueChange = {
                        projectName = it
                        nameError = null
                    },
                    label = { Text(stringResource("project.new.dialog.name.label")) },
                    placeholder = { Text(stringResource("project.new.dialog.name.placeholder")) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = ComponentColors.outlinedTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                // Description Field (Optional)
                OutlinedTextField(
                    value = projectDescription,
                    onValueChange = { projectDescription = it },
                    label = { Text(stringResource("project.new.dialog.description.label")) },
                    placeholder = { Text(stringResource("project.new.dialog.description.placeholder")) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ComponentColors.outlinedTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                // Folder Selection
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource("project.new.dialog.folder.label"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Selected folder display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = selectedFolder ?: "",
                            onValueChange = { },
                            placeholder = { Text(stringResource("project.new.dialog.folder.placeholder")) },
                            readOnly = true,
                            isError = folderError != null,
                            supportingText = folderError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            trailingIcon = {
                                if (selectedFolder != null) {
                                    val validationResult = validateFolder(selectedFolder)
                                    when (validationResult) {
                                        FolderValidationResult.Valid -> Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Valid",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        else -> Icon(
                                            Icons.Default.Error,
                                            contentDescription = "Invalid",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ComponentColors.outlinedTextFieldColors(),
                        )

                        OutlinedButton(
                            onClick = { browseForFolder() },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Action Buttons
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
                        onClick = ::performSave,
                        enabled = projectName.trim().isNotEmpty(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("action.save"))
                    }
                }
            }
        }
    }
}
