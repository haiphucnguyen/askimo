/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.directive.ChatDirective
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.ui.theme.ComponentColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun manageDirectivesDialog(
    directives: List<ChatDirective>,
    onDismiss: () -> Unit,
    onUpdate: (id: String, newName: String, newContent: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    var editingDirective by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    // Pre-load error messages in composable context
    val nameRequiredError = stringResource("directive.edit.name.required")
    val contentRequiredError = stringResource("directive.edit.content.required")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(700.dp)
                .height(600.dp)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource("directive.manage.title"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource("action.close"),
                        )
                    }
                }

                HorizontalDivider()

                // Directives list
                if (directives.isEmpty()) {
                    Text(
                        text = stringResource("directive.manage.empty"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(directives, key = { it.id }) { directive ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = ComponentColors.sidebarSurfaceColor(),
                                ),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // Directive name and action buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = directive.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )

                                        if (editingDirective != directive.id) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        editingDirective = directive.id
                                                        editName = directive.name
                                                        editContent = directive.content
                                                        editError = null
                                                    },
                                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = stringResource("action.edit"),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDelete(directive.id) },
                                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = stringResource("action.delete"),
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Directive content or edit mode
                                    if (editingDirective == directive.id) {
                                        // Inline edit mode
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            OutlinedTextField(
                                                value = editName,
                                                onValueChange = {
                                                    editName = it
                                                    editError = null
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text(stringResource("directive.edit.name.label")) },
                                                placeholder = { Text(stringResource("directive.edit.name.placeholder")) },
                                                singleLine = true,
                                                isError = editError != null && editName.isBlank(),
                                                colors = ComponentColors.outlinedTextFieldColors(),
                                            )
                                            OutlinedTextField(
                                                value = editContent,
                                                onValueChange = {
                                                    editContent = it
                                                    editError = null
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp),
                                                label = { Text(stringResource("directive.edit.content.label")) },
                                                placeholder = { Text(stringResource("directive.edit.content.placeholder")) },
                                                maxLines = 10,
                                                isError = editError != null,
                                                supportingText = editError?.let { { Text(it) } },
                                                colors = ComponentColors.outlinedTextFieldColors(),
                                            )

                                            // Action buttons for edit mode
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        editingDirective = null
                                                        editName = ""
                                                        editContent = ""
                                                        editError = null
                                                    },
                                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Text(stringResource("settings.cancel"))
                                                }

                                                TextButton(
                                                    onClick = {
                                                        if (editName.isBlank()) {
                                                            editError = nameRequiredError
                                                        } else if (editContent.isBlank()) {
                                                            editError = contentRequiredError
                                                        } else {
                                                            onUpdate(directive.id, editName.trim(), editContent.trim())
                                                            editingDirective = null
                                                            editName = ""
                                                            editContent = ""
                                                            editError = null
                                                        }
                                                    },
                                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                                    colors = ComponentColors.primaryTextButtonColors(),
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Text(stringResource("settings.save"))
                                                }
                                            }
                                        }
                                    } else {
                                        // Display mode with truncated content and tooltip
                                        val truncatedContent = if (directive.content.length > 150) {
                                            directive.content.take(150) + "..."
                                        } else {
                                            directive.content
                                        }

                                        TooltipArea(
                                            tooltip = {
                                                Surface(
                                                    modifier = Modifier.width(400.dp),
                                                    shadowElevation = 4.dp,
                                                    shape = MaterialTheme.shapes.small,
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                    ) {
                                                        Text(
                                                            text = stringResource("directive.tooltip.full.content"),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                        )
                                                        Text(
                                                            text = directive.content,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            modifier = Modifier.padding(top = 8.dp),
                                                        )
                                                    }
                                                }
                                            },
                                        ) {
                                            Text(
                                                text = truncatedContent,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                            )
                                        }

                                        // Created date
                                        Text(
                                            text = stringResource("directive.created", directive.createdAt.toLocalDate()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Close button at bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("action.close"))
                    }
                }
            }
        }
    }
}
