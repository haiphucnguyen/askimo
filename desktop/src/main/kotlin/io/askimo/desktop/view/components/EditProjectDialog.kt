/* SPDX-License-Identifier: AGPLv3
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.Project
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectReIndexEvent
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.util.FileDialogUtils
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.UUID

/**
 * Dialog for editing an existing project.
 */
@Composable
fun editProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (projectId: String, name: String, description: String?, knowledgeSources: List<KnowledgeSourceConfig>) -> Unit,
) {
    var projectName by remember { mutableStateOf(project.name) }
    var projectDescription by remember { mutableStateOf(project.description ?: "") }

    // Parse existing knowledge sources into UI items
    val initialSources = remember {
        parseKnowledgeSourceConfigs(project.knowledgeSources)
    }
    var knowledgeSources by remember { mutableStateOf(initialSources) }
    var showAddSourceMenu by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    val emptyNameError = stringResource("project.new.dialog.name.error.empty")
    val browseFolderTitle = stringResource("project.new.dialog.folder.browse")
    val browseFileTitle = stringResource("project.new.dialog.file.browse")

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
        dialog.setFilenameFilter(FileDialogUtils.createSupportedFileFilter())
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

    // Extract save logic to reuse in button and Enter key handler
    fun performSave() {
        // Validate project name
        if (projectName.trim().isEmpty()) {
            nameError = emptyNameError
            return
        }

        // Build knowledge source configurations from UI items
        val knowledgeSourceConfigs = buildKnowledgeSourceConfigs(knowledgeSources)

        // Check if knowledge sources have changed
        val knowledgeSourceChanged = knowledgeSourceConfigs != project.knowledgeSources

        // Save the project
        onSave(
            project.id,
            projectName.trim(),
            projectDescription.trim().takeIf { it.isNotEmpty() },
            knowledgeSourceConfigs,
        )

        // If knowledge sources changed, emit re-index event
        if (knowledgeSourceChanged) {
            EventBus.post(
                ProjectReIndexEvent(
                    projectId = project.id,
                    reason = "Knowledge sources changed",
                ),
            )
        }
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

/**
 * Composable for displaying a single knowledge source row with remove button
 */
@Composable
fun knowledgeSourceRow(
    source: KnowledgeSourceItem,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                source.icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = source.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.isValid) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Valid",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).padding(start = 4.dp),
                )
            } else {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Invalid",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp).padding(start = 4.dp),
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
