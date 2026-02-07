/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectRefreshEvent
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.components.secondaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import java.util.UUID
import kotlin.collections.plus

/**
 * Dialog for adding reference materials to an existing project
 */
@Composable
fun addReferenceMaterialDialog(
    projectId: String,
    onDismiss: () -> Unit,
    onAdd: (List<KnowledgeSourceItem>) -> Unit,
) {
    var knowledgeSources by remember { mutableStateOf<List<KnowledgeSourceItem>>(emptyList()) }
    var showAddSourceMenu by remember { mutableStateOf(false) }
    var showUrlInputDialog by remember { mutableStateOf(false) }

    val browseFolderTitle = stringResource("project.new.dialog.folder.browse")
    val browseFileTitle = stringResource("project.new.dialog.file.browse")

    // Shared knowledge source browser helper
    val sourceBrowser = remember(browseFolderTitle, browseFileTitle) {
        KnowledgeSourceBrowser(
            browseFolderTitle = browseFolderTitle,
            browseFileTitle = browseFileTitle,
        )
    }

    // Handle adding a source based on type
    fun handleAddSource(typeInfo: KnowledgeSourceItem.TypeInfo) {
        val newSources = sourceBrowser.handleAddSource(typeInfo) {
            showUrlInputDialog = true
        }
        knowledgeSources = knowledgeSources + newSources
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource("projects.sources.add.dialog.title"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource("projects.sources.add.dialog.description"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.padding(vertical = 12.dp))

                // Add source dropdown button
                Box {
                    OutlinedButton(
                        onClick = { showAddSourceMenu = true },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource("projects.sources.add.button"))
                    }

                    ComponentColors.themedDropdownMenu(
                        expanded = showAddSourceMenu,
                        onDismissRequest = { showAddSourceMenu = false },
                    ) {
                        KnowledgeSourceItem.availableTypes.forEach { typeInfo ->
                            DropdownMenuItem(
                                text = { Text(stringResource(typeInfo.typeLabelKey)) },
                                onClick = {
                                    handleAddSource(typeInfo)
                                    showAddSourceMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = typeInfo.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            )
                        }
                    }
                }

                // Display selected sources
                if (knowledgeSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.padding(vertical = 8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        knowledgeSources.forEach { source ->
                            knowledgeSourceRow(
                                source = source,
                                onRemove = {
                                    knowledgeSources = knowledgeSources.filter { it.id != source.id }
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 12.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    secondaryButton(
                        onClick = onDismiss,
                    ) {
                        Text(stringResource("project.new.dialog.button.cancel"))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    primaryButton(
                        onClick = {
                            if (knowledgeSources.isNotEmpty()) {
                                onAdd(knowledgeSources)

                                EventBus.post(
                                    ProjectRefreshEvent(
                                        projectId = projectId,
                                        reason = "Knowledge sources added via dialog",
                                    ),
                                )

                                onDismiss()
                            }
                        },
                        enabled = knowledgeSources.isNotEmpty(),
                    ) {
                        Text(stringResource("projects.sources.add.dialog.button.add"))
                    }
                }
            }
        }
    }

    // Show URL input dialog when requested
    if (showUrlInputDialog) {
        urlInputDialog(
            onDismiss = { showUrlInputDialog = false },
            onUrlAdded = { url ->
                knowledgeSources = knowledgeSources + KnowledgeSourceItem.Url(
                    id = UUID.randomUUID().toString(),
                    url = url,
                    isValid = validateUrl(url),
                )
            },
        )
    }
}
