/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.chat.domain.ChatDirective
import io.askimo.core.util.TimeUtil
import io.askimo.ui.common.components.primaryButton
import io.askimo.ui.common.components.secondaryButton
import io.askimo.ui.common.i18n.stringResource
import io.askimo.ui.common.theme.AppComponents
import io.askimo.ui.common.ui.themedTooltip

@Composable
fun manageDirectivesDialog(
    directives: List<ChatDirective>,
    onDismiss: () -> Unit,
    onAdd: (name: String, content: String, applyToCurrent: Boolean) -> Unit,
    onUpdate: (id: String, newName: String, newContent: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    var editingDirective by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    var isAdding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var newApplyToCurrent by remember { mutableStateOf(false) }
    var newNameError by remember { mutableStateOf<String?>(null) }
    var newContentError by remember { mutableStateOf<String?>(null) }

    // Pre-load error messages in composable context
    val nameRequiredError = stringResource("directive.edit.name.required")
    val contentRequiredError = stringResource("directive.edit.content.required")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(700.dp)
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
                if (directives.isEmpty() && !isAdding) {
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
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(directives, key = { it.id }) { directive ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = AppComponents.sidebarSurfaceColor(),
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
                                            color = MaterialTheme.colorScheme.onSurface,
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
                                                        isAdding = false
                                                    },
                                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = stringResource("action.edit"),
                                                        tint = MaterialTheme.colorScheme.onSurface,
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

                                    if (editingDirective == directive.id) {
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
                                                colors = AppComponents.outlinedTextFieldColors(),
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
                                                colors = AppComponents.outlinedTextFieldColors(),
                                            )

                                            // Action buttons for edit mode
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                secondaryButton(
                                                    onClick = {
                                                        editingDirective = null
                                                        editName = ""
                                                        editContent = ""
                                                        editError = null
                                                    },
                                                ) {
                                                    Text(stringResource("settings.cancel"))
                                                }

                                                primaryButton(
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
                                                ) {
                                                    Text(stringResource("settings.save"))
                                                }
                                            }
                                        }
                                    } else {
                                        // Display mode: show truncated content, full text in tooltip
                                        themedTooltip(text = directive.content) {
                                            Text(
                                                text = directive.content,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                            )
                                        }

                                        // Created date
                                        Text(
                                            text = stringResource("directive.created", TimeUtil.formatDisplay(directive.createdAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Add directive section
                if (isAdding) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource("directive.new.title"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource("directive.new.guidelines"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = newName,
                            onValueChange = {
                                newName = it
                                newNameError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource("directive.edit.name.label")) },
                            placeholder = { Text(stringResource("directive.new.name.placeholder")) },
                            singleLine = true,
                            isError = newNameError != null,
                            supportingText = newNameError?.let { { Text(it) } },
                            colors = AppComponents.outlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = newContent,
                            onValueChange = {
                                newContent = it
                                newContentError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            label = { Text(stringResource("directive.edit.content.label")) },
                            placeholder = { Text(stringResource("directive.new.content.placeholder")) },
                            maxLines = 8,
                            isError = newContentError != null,
                            supportingText = newContentError?.let { { Text(it) } },
                            colors = AppComponents.outlinedTextFieldColors(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = newApplyToCurrent,
                                onCheckedChange = { newApplyToCurrent = it },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            )
                            Text(
                                text = stringResource("directive.new.apply.current"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            secondaryButton(
                                onClick = {
                                    isAdding = false
                                    newName = ""
                                    newContent = ""
                                    newApplyToCurrent = false
                                    newNameError = null
                                    newContentError = null
                                },
                            ) {
                                Text(stringResource("settings.cancel"))
                            }
                            primaryButton(
                                onClick = {
                                    var hasError = false
                                    if (newName.isBlank()) {
                                        newNameError = nameRequiredError
                                        hasError = true
                                    }
                                    if (newContent.isBlank()) {
                                        newContentError = contentRequiredError
                                        hasError = true
                                    }
                                    if (!hasError) {
                                        onAdd(newName.trim(), newContent.trim(), newApplyToCurrent)
                                        isAdding = false
                                        newName = ""
                                        newContent = ""
                                        newApplyToCurrent = false
                                        newNameError = null
                                        newContentError = null
                                    }
                                },
                            ) {
                                Text(stringResource("directive.new.create"))
                            }
                        }
                    }
                } else {
                    // Bottom bar: Add button + Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryButton(
                            onClick = {
                                isAdding = true
                                editingDirective = null
                            },
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(stringResource("directive.new.title"))
                        }
                        secondaryButton(onClick = onDismiss) {
                            Text(stringResource("action.close"))
                        }
                    }
                }
            }
        }
    }
}
