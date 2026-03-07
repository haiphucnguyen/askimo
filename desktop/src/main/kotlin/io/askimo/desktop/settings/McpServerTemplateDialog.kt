/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.logging.currentFileLogger
import io.askimo.core.mcp.HttpConfig
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
    val isEditMode = server != null

    // Shared fields
    var id by remember { mutableStateOf(server?.id ?: "") }
    var name by remember { mutableStateOf(server?.name ?: "") }
    var description by remember { mutableStateOf(server?.description ?: "") }
    var tags by remember { mutableStateOf(server?.tags?.joinToString(", ") ?: "") }

    // Transport type tab: 0 = STDIO, 1 = HTTP
    var selectedTab by remember {
        mutableStateOf(if (server?.transportType == TransportType.HTTP) 1 else 0)
    }

    // STDIO fields
    var commandTemplate by remember {
        mutableStateOf(server?.stdioConfig?.commandTemplate?.joinToString(" ") ?: "")
    }
    var envTemplate by remember {
        mutableStateOf(
            server?.stdioConfig?.envTemplate?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: "",
        )
    }

    // HTTP fields
    var urlTemplate by remember { mutableStateOf(server?.httpConfig?.urlTemplate ?: "") }
    var headersTemplate by remember {
        mutableStateOf(
            server?.httpConfig?.headersTemplate?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: "",
        )
    }
    var timeoutMs by remember { mutableStateOf(server?.httpConfig?.timeoutMs?.toString() ?: "60000") }

    val transportType = if (selectedTab == 0) TransportType.STDIO else TransportType.HTTP
    val saveEnabled = id.isNotBlank() && name.isNotBlank() &&
        if (transportType == TransportType.STDIO) commandTemplate.isNotBlank() else urlTemplate.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(700.dp)
                .heightIn(min = 400.dp, max = 700.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            // Outer column: scrollable body takes all remaining space, buttons stay pinned
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                // ── Scrollable body ──────────────────────────────────────────
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 32.dp, top = 24.dp, bottom = 16.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(Spacing.large),
                    ) {
                        // ── Title ────────────────────────────────────────────────────
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

                        // ── Shared metadata ──────────────────────────────────────────
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
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource("mcp.template.field.name")) },
                            placeholder = { Text(stringResource("mcp.template.field.name.placeholder")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = ComponentColors.outlinedTextFieldColors(),
                        )
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

                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))

                        // ── Transport type selector ───────────────────────────────────
                        Text(
                            text = stringResource("mcp.template.field.transport"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { if (!isEditMode) selectedTab = 0 },
                                enabled = !isEditMode,
                                text = { Text(stringResource("mcp.template.transport.stdio")) },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { if (!isEditMode) selectedTab = 1 },
                                enabled = !isEditMode,
                                text = { Text(stringResource("mcp.template.transport.http")) },
                            )
                        }

                        // ── STDIO fields ─────────────────────────────────────────────
                        AnimatedVisibility(visible = selectedTab == 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
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
                            }
                        }

                        // ── HTTP fields ───────────────────────────────────────────────
                        AnimatedVisibility(visible = selectedTab == 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
                                OutlinedTextField(
                                    value = urlTemplate,
                                    onValueChange = { urlTemplate = it },
                                    label = { Text(stringResource("mcp.template.http.url.label")) },
                                    placeholder = { Text(stringResource("mcp.template.http.url.placeholder")) },
                                    supportingText = {
                                        Text(
                                            stringResource("mcp.template.http.url.hint"),
                                            modifier = Modifier.padding(top = Spacing.extraSmall),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )
                                OutlinedTextField(
                                    value = headersTemplate,
                                    onValueChange = { headersTemplate = it },
                                    label = { Text(stringResource("mcp.template.http.headers.label")) },
                                    placeholder = { Text(stringResource("mcp.template.http.headers.placeholder")) },
                                    supportingText = {
                                        Text(
                                            stringResource("mcp.template.http.headers.hint"),
                                            modifier = Modifier.padding(top = Spacing.extraSmall),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )

                                OutlinedTextField(
                                    value = timeoutMs,
                                    onValueChange = { if (it.all(Char::isDigit)) timeoutMs = it },
                                    label = { Text(stringResource("mcp.template.http.timeout.label")) },
                                    supportingText = {
                                        Text(
                                            stringResource("mcp.template.http.timeout.hint"),
                                            modifier = Modifier.padding(top = Spacing.extraSmall),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )
                            }
                        }

                        // ── Tags ──────────────────────────────────────────────────────
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
                    } // end scrollable Column

                    // ── Vertical scrollbar ─────────────────────────────────
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                } // end Box (scrollable body)

                // ── Sticky footer ────────────────────────────────────────────
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    inlineErrorMessage(errorMessage = dialogState.errorMessage)

                    // ── Action buttons ────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        secondaryButton(onClick = onDismiss) {
                            Text(stringResource("dialog.cancel"))
                        }
                        Spacer(modifier = Modifier.width(Spacing.small))
                        primaryButton(
                            onClick = {
                                dialogState.clearError()
                                try {
                                    val tagsList = tags.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }

                                    val serverDefinition = when (transportType) {
                                        TransportType.STDIO -> {
                                            val commandList = commandTemplate.trim().split("\\s+".toRegex())
                                            val envMap = parseKeyValueLines(envTemplate)
                                            McpServerDefinition(
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
                                        }
                                        TransportType.HTTP -> {
                                            val headersMap = parseKeyValueLines(headersTemplate)
                                            McpServerDefinition(
                                                id = id.trim(),
                                                name = name.trim(),
                                                description = description.trim(),
                                                transportType = TransportType.HTTP,
                                                httpConfig = HttpConfig(
                                                    urlTemplate = urlTemplate.trim(),
                                                    headersTemplate = headersMap,
                                                    timeoutMs = timeoutMs.toLongOrNull() ?: 60_000L,
                                                ),
                                                tags = tagsList,
                                            )
                                        }
                                    }

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
                            enabled = saveEnabled,
                        ) {
                            Text(stringResource("action.save"))
                        }
                    }
                } // end sticky footer Column
            } // end outer Column
        } // end Surface
    } // end Dialog
}

/** Parse "KEY=value" lines into a map, skipping blank lines. */
private fun parseKeyValueLines(raw: String): Map<String, String> = if (raw.isBlank()) {
    emptyMap()
} else {
    raw.trim().lines()
        .filter { it.isNotBlank() }
        .associate {
            val parts = it.split("=", limit = 2)
            parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
        }
}
