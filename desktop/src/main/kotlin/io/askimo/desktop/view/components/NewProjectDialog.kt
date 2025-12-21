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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.chat.domain.Project
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import io.askimo.core.util.JsonUtils.json
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDateTime

/**
 * Validation result for folder path
 */
sealed class FolderValidationResult {
    data object Valid : FolderValidationResult()
    data object Empty : FolderValidationResult()
    data object NotExists : FolderValidationResult()
    data object NotAFolder : FolderValidationResult()
    data object NotReadable : FolderValidationResult()
}

private object NewProjectDialog
private val log = logger<NewProjectDialog>()

/**
 * Dialog for creating a new project with name, description, and optional folder path.
 */
@Composable
fun newProjectDialog(
    onDismiss: () -> Unit,
    onCreateProject: (name: String, description: String?, folderPath: String?) -> Unit,
) {
    var projectName by remember { mutableStateOf("") }
    var projectDescription by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    var createdProjectName by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(5) }

    val scope = rememberCoroutineScope()

    // Retrieve string resources in composable scope
    val errorEmptyName = stringResource("project.new.dialog.error.empty.name")
    val errorFolderNotExists = stringResource("project.new.dialog.error.folder.notexists")
    val errorFolderNotFolder = stringResource("project.new.dialog.error.folder.notfolder")
    val errorFolderNotReadable = stringResource("project.new.dialog.error.folder.notreadable")
    val browseFolderTitle = stringResource("project.new.dialog.folder.browse")

    // Countdown and auto-dismiss when success is shown
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            countdown = 5
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            onDismiss()
        }
    }

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

    // Validate and create project
    fun handleCreate() {
        // Validate project name
        if (projectName.isBlank()) {
            nameError = errorEmptyName
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

        // Save project to database
        scope.launch {
            try {
                val projectRepository = DatabaseManager.getInstance().getProjectRepository()

                // Convert folder path to JSON array
                val indexedPathsJson = if (selectedFolder != null) {
                    json.encodeToString(listOf(selectedFolder))
                } else {
                    json.encodeToString(emptyList<String>())
                }

                // Create project
                val project = Project(
                    id = "",
                    name = projectName.trim(),
                    description = projectDescription.takeIf { it.isNotBlank() },
                    indexedPaths = indexedPathsJson,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                )

                projectRepository.createProject(project)

                // Show success message
                createdProjectName = projectName.trim()
                showSuccess = true

                // Call callback
                onCreateProject(
                    projectName.trim(),
                    projectDescription.takeIf { it.isNotBlank() },
                    selectedFolder,
                )
            } catch (e: Exception) {
                log.error("Failed to create project", e)
                onDismiss()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(650.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            if (showSuccess) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    )

                    Text(
                        text = stringResource("project.new.dialog.success.title"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        text = stringResource("project.new.dialog.success.message", createdProjectName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = stringResource(
                            "project.new.dialog.success.countdown",
                            countdown,
                            if (countdown != 1) "s" else "",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("project.new.dialog.success.close"))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Title
                    Text(
                        text = stringResource("project.new.dialog.title"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Project Name Field
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = {
                            projectName = it
                            nameError = null // Clear error on change
                        },
                        label = { Text(stringResource("project.new.dialog.name.label")) },
                        placeholder = { Text(stringResource("project.new.dialog.name.placeholder")) },
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { handleCreate() }),
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
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(stringResource("project.new.dialog.folder.browse"))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                            Text(stringResource("project.new.dialog.button.cancel"))
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { handleCreate() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text(stringResource("project.new.dialog.button.create"))
                        }
                    }
                }
            }
        }
    }
}
