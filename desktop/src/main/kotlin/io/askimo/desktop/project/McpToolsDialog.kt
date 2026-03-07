/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.project

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.askimo.core.mcp.ProjectMcpInstance
import io.askimo.core.mcp.ProjectMcpInstanceService
import io.askimo.desktop.common.components.inlineErrorMessage
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.get
import java.util.concurrent.TimeoutException

@Composable
fun mcpToolsDialog(
    instance: ProjectMcpInstance,
    onDismiss: () -> Unit,
) {
    val mcpService = get<ProjectMcpInstanceService>(ProjectMcpInstanceService::class.java)
    var tools by remember { mutableStateOf<List<dev.langchain4j.agent.tool.ToolSpecification>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(instance.id) {
        isLoading = true
        errorMessage = null

        try {
            val toolsList = withContext(Dispatchers.IO) {
                mcpService.listTools(instance.projectId, instance.id)
            }
            tools = toolsList
        } catch (e: Exception) {
            val isTimeout = e is TimeoutException ||
                e.cause is TimeoutException ||
                e.message?.contains("TimeoutException", ignoreCase = true) == true

            errorMessage = if (isTimeout) {
                "Connection timeout: Unable to connect to MCP server. Please check if the server is running and accessible."
            } else {
                "Failed to load tools: ${e.message ?: "Unknown error occurred"}"
            }
        } finally {
            isLoading = false
        }
    }

    ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource("mcp.tools.dialog.title", instance.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            // Scrollable container with controlled height and visible scrollbar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Instance Parameters Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ComponentColors.secondaryCardColors(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource("mcp.tools.dialog.instance.info"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                            )

                            // Server ID
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource("mcp.instance.field.serverId"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                )
                                SelectionContainer {
                                    Text(
                                        text = instance.serverId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }

                            // Parameters
                            if (instance.parameterValues.isNotEmpty()) {
                                Text(
                                    text = stringResource("mcp.tools.dialog.parameters"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp),
                                )

                                instance.parameterValues.forEach { (key, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                                            )
                                        }
                                        SelectionContainer {
                                            Text(
                                                text = value,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when {
                        isLoading -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource("mcp.tools.dialog.loading"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        errorMessage != null -> {
                            inlineErrorMessage(errorMessage = errorMessage)
                        }

                        tools.isNullOrEmpty() -> {
                            Text(
                                text = stringResource("mcp.tools.dialog.empty"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource("mcp.tools.dialog.count", tools!!.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            tools!!.forEach { tool ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = tool.name(),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }

                                        tool.description()?.let { desc ->
                                            SelectionContainer {
                                                Text(
                                                    text = desc,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Visible scrollbar
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    style = defaultScrollbarStyle().copy(
                        unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    ),
                )
            }
        },
        confirmButton = {
            primaryButton(onClick = onDismiss) {
                Text(stringResource("dialog.close"))
            }
        },
    )
}
