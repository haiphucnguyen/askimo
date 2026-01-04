/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectIndexingRequestedEvent
import io.askimo.core.logging.logger
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

private object NewProjectDialog
private val log = logger<NewProjectDialog>()

/**
 * Dialog for creating a new project with name, description, and optional knowledge sources.
 */
@Composable
fun newProjectDialog(
    onDismiss: () -> Unit,
    onCreateProject: (name: String, description: String?) -> Unit,
) {
    var projectName by remember { mutableStateOf("") }
    var projectDescription by remember { mutableStateOf("") }
    var knowledgeSources by remember { mutableStateOf<List<KnowledgeSourceItem>>(emptyList()) }
    var showAddSourceMenu by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    var createdProjectName by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(5) }

    val scope = rememberCoroutineScope()

    // Retrieve string resources in composable scope
    val errorEmptyName = stringResource("project.new.dialog.error.empty.name")
    val browseFolderTitle = stringResource("project.new.dialog.folder.browse")
    val browseFileTitle = stringResource("project.new.dialog.file.browse")

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

    // Browse for folder using FileDialog
    fun browseForFolder() {
        val dialog = FileDialog(null as Frame?, browseFolderTitle, FileDialog.LOAD)

        // macOS: Enable folder selection
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        dialog.isVisible = true
        System.setProperty("apple.awt.fileDialogForDirectories", "false")

        if (dialog.file != null) {
            val folderPath = File(dialog.directory, dialog.file).absolutePath
            knowledgeSources = knowledgeSources + KnowledgeSourceItem.Folder(
                id = UUID.randomUUID().toString(),
                path = folderPath,
                isValid = validateFolder(folderPath),
            )
        }
    }

    // Browse for files using FileDialog
    fun browseForFiles() {
        val dialog = FileDialog(null as Frame?, browseFileTitle, FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.isVisible = true

        dialog.files?.forEach { file ->
            knowledgeSources = knowledgeSources + KnowledgeSourceItem.File(
                id = UUID.randomUUID().toString(),
                path = file.absolutePath,
                isValid = validateFile(file.absolutePath),
            )
        }
    }

    // Handle adding a source based on type
    fun handleAddSource(type: KnowledgeSourceType) {
        when (type) {
            KnowledgeSourceType.FOLDER -> browseForFolder()
            KnowledgeSourceType.FILE -> browseForFiles()
        }
    }

    // Validate and create project
    fun handleCreate() {
        // Validate project name
        if (projectName.isBlank()) {
            nameError = errorEmptyName
            return
        }

        // Save project to database
        scope.launch {
            try {
                val projectRepository = DatabaseManager.getInstance().getProjectRepository()

                // Build knowledge source configurations from UI items
                val knowledgeSourceConfigs = buildKnowledgeSourceConfigs(knowledgeSources)

                // Create project
                val project = Project(
                    id = "",
                    name = projectName.trim(),
                    description = projectDescription.takeIf { it.isNotBlank() },
                    knowledgeSources = knowledgeSourceConfigs,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                )

                val createdProject = projectRepository.createProject(project)

                // Emit indexing event if the project has knowledge sources
                if (createdProject.knowledgeSources.isNotEmpty()) {
                    EventBus.post(
                        ProjectIndexingRequestedEvent(
                            projectId = createdProject.id,
                            watchForChanges = true,
                        ),
                    )
                    log.debug("Emitted indexing event for project ${createdProject.id}")
                }

                // Show success message
                createdProjectName = projectName.trim()
                showSuccess = true

                // Call callback with project info
                onCreateProject(
                    projectName.trim(),
                    projectDescription.takeIf { it.isNotBlank() },
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

                    // Knowledge Sources Section
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource("project.new.dialog.sources.label"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // List of existing sources
                        knowledgeSources.forEach { source ->
                            knowledgeSourceRow(
                                source = source,
                                onRemove = { knowledgeSources = knowledgeSources - source },
                            )
                        }

                        // Add Source Button with Dropdown
                        Box {
                            OutlinedButton(
                                onClick = { showAddSourceMenu = true },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource("project.new.dialog.sources.add"))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = showAddSourceMenu,
                                onDismissRequest = { showAddSourceMenu = false },
                            ) {
                                KnowledgeSourceType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    type.icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(end = 8.dp).size(20.dp),
                                                )
                                                Text(type.displayName)
                                            }
                                        },
                                        onClick = {
                                            showAddSourceMenu = false
                                            handleAddSource(type)
                                        },
                                    )
                                }
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
