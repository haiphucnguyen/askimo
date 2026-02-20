/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.logging.currentFileLogger
import io.askimo.core.mcp.McpServerDefinition
import io.askimo.core.mcp.StdioConfig
import io.askimo.core.mcp.TransportType
import io.askimo.core.mcp.getFirstError
import io.askimo.core.mcp.mcpServerDefinitionValidator
import io.askimo.desktop.common.components.inlineErrorMessage
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.components.rememberDialogState
import io.askimo.desktop.common.components.secondaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.Spacing

private val log = currentFileLogger()

@Composable
fun mcpServerTemplateDialog(
    server: McpServerDefinition?,
    onDismiss: () -> Unit,
    onSave: (McpServerDefinition) -> Unit,
) {
    val dialogState = rememberDialogState()

    // Edit mode - initialize with existing values
    var id by remember { mutableStateOf(server?.id ?: "") }
    var name by remember { mutableStateOf(server?.name ?: "") }
    var description by remember { mutableStateOf(server?.description ?: "") }
    var commandTemplate by remember { mutableStateOf(server?.stdioConfig?.commandTemplate?.joinToString(" ") ?: "") }
    var envTemplate by remember { mutableStateOf(server?.stdioConfig?.envTemplate?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: "") }
    var tags by remember { mutableStateOf(server?.tags?.joinToString(", ") ?: "") }

    val isEditMode = server != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(700.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.large),
            ) {
                // Dialog Title
                Text(
                    text = if (isEditMode) {
                        stringResource("mcp.template.dialog.title.edit")
                    } else {
                        stringResource("mcp.template.dialog.title.add")
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource("mcp.template.dialog.description"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))

                // ID (read-only in edit mode)
                OutlinedTextField(
                    value = id,
                    onValueChange = { if (!isEditMode) id = it },
                    label = { Text(stringResource("mcp.template.field.id")) },
                    placeholder = { Text(stringResource("mcp.template.field.id.placeholder")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isEditMode,
                    colors = ComponentColors.outlinedTextFieldColors(),
                )

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource("mcp.template.field.name")) },
                    placeholder = { Text(stringResource("mcp.template.field.name.placeholder")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = ComponentColors.outlinedTextFieldColors(),
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource("mcp.template.field.description")) },
                    placeholder = { Text(stringResource("mcp.template.field.description.placeholder")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    colors = ComponentColors.outlinedTextFieldColors(),
                )

                // Command Template
                OutlinedTextField(
                    value = commandTemplate,
                    onValueChange = { commandTemplate = it },
                    label = { Text(stringResource("mcp.template.field.command")) },
                    placeholder = { Text(stringResource("mcp.template.field.command.placeholder")) },
                    supportingText = {
                        Text(
                            stringResource("mcp.template.field.command.hint"),
                            modifier = Modifier.padding(top = Spacing.extraSmall),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = ComponentColors.outlinedTextFieldColors(),
                )

                // Environment Variables
                OutlinedTextField(
                    value = envTemplate,
                    onValueChange = { envTemplate = it },
                    label = { Text(stringResource("mcp.template.env.label")) },
                    placeholder = { Text(stringResource("mcp.template.env.placeholder")) },
                    supportingText = {
                        Text(
                            stringResource("mcp.template.env.hint"),
                            modifier = Modifier.padding(top = Spacing.extraSmall),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = ComponentColors.outlinedTextFieldColors(),
                )

                // Tags
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource("mcp.template.tags.label")) },
                    placeholder = { Text(stringResource("mcp.template.tags.placeholder")) },
                    supportingText = {
                        Text(
                            stringResource("mcp.template.tags.hint"),
                            modifier = Modifier.padding(top = Spacing.extraSmall),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = ComponentColors.outlinedTextFieldColors(),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))

                // Error Message Display
                inlineErrorMessage(errorMessage = dialogState.errorMessage)

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    secondaryButton(
                        onClick = onDismiss,
                    ) {
                        Text(stringResource("dialog.cancel"))
                    }

                    Spacer(modifier = Modifier.width(Spacing.small))

                    primaryButton(
                        onClick = {
                            dialogState.clearError()
                            try {
                                // Parse command template
                                val commandList = commandTemplate.trim().split("\\s+".toRegex())

                                // Parse environment variables
                                val envMap = if (envTemplate.isBlank()) {
                                    emptyMap()
                                } else {
                                    envTemplate.trim().lines()
                                        .filter { it.isNotBlank() }
                                        .associate {
                                            val parts = it.split("=", limit = 2)
                                            parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
                                        }
                                }

                                // Parse tags
                                val tagsList = if (tags.isBlank()) {
                                    emptyList()
                                } else {
                                    tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                }

                                // Create server definition
                                val serverDefinition = McpServerDefinition(
                                    id = id.trim(),
                                    name = name.trim(),
                                    description = description.trim(),
                                    transportType = TransportType.STDIO,
                                    stdioConfig = StdioConfig(
                                        commandTemplate = commandList,
                                        envTemplate = envMap,
                                    ),
                                    tags = tagsList,
                                )

                                // Validate before saving
                                val validationResult = mcpServerDefinitionValidator(serverDefinition)
                                if (validationResult.errors.isNotEmpty()) {
                                    dialogState.setError(validationResult.getFirstError() ?: "Validation failed")
                                    return@primaryButton
                                }

                                onSave(serverDefinition)
                            } catch (e: Exception) {
                                dialogState.setError(e, "Failed to save server template")
                                log.error("Save failed", e)
                            }
                        },
                        enabled = id.isNotBlank() && name.isNotBlank() && commandTemplate.isNotBlank(),
                    ) {
                        Text(stringResource("action.save"))
                    }
                }
            }
        }
    }
}
