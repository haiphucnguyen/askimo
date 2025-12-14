/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fasterxml.jackson.databind.ObjectMapper
import io.askimo.core.chat.domain.SessionMemory
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors

/**
 * Dialog to display session memory information including memory messages and summary.
 *
 * @param sessionMemory The session memory data to display
 * @param onDismiss Callback when the dialog is dismissed
 */
@Composable
fun sessionMemoryDialog(
    sessionMemory: SessionMemory?,
    onDismiss: () -> Unit,
) {
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
                // Header
                Text(
                    text = stringResource("developer.session.memory.title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (sessionMemory == null) {
                    // No memory found
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ComponentColors.surfaceVariantCardColors(),
                    ) {
                        Text(
                            text = stringResource("developer.session.memory.not.found"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    // Memory Summary Section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource("developer.session.memory.summary"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(
                                    "developer.session.memory.word.count",
                                    countWords(sessionMemory.memorySummary ?: ""),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = ComponentColors.surfaceVariantCardColors(),
                        ) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = formatJson(sessionMemory.memorySummary ?: stringResource("developer.session.memory.empty")),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Memory Messages Section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource("developer.session.memory.messages"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(
                                    "developer.session.memory.word.count",
                                    countWords(sessionMemory.memoryMessages),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            colors = ComponentColors.surfaceVariantCardColors(),
                        ) {
                            val scrollState = rememberScrollState()
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = formatJson(sessionMemory.memoryMessages.ifBlank { stringResource("developer.session.memory.empty") }),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth()
                                        .verticalScroll(scrollState),
                                )
                            }
                        }
                    }
                }

                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ComponentColors.primaryTextButtonColors(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("action.close"))
                    }
                }
            }
        }
    }
}

/**
 * Count words in a text string.
 * Words are separated by whitespace.
 */
private fun countWords(text: String): Int {
    if (text.isBlank()) return 0
    return text.trim().split("\\s+".toRegex()).size
}

/**
 * Format JSON string with proper indentation for better readability.
 * If the string is not valid JSON, returns it as-is.
 */
private fun formatJson(text: String): String {
    if (text.isBlank()) return text

    return try {
        // Try to parse and re-format the JSON with indentation
        val objectMapper = ObjectMapper()
        val jsonNode = objectMapper.readTree(text)
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode)
    } catch (e: Exception) {
        // If it's not valid JSON, return as-is
        text
    }
}
