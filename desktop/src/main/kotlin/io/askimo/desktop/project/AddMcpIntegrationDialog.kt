/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.langchain4j.agent.tool.ToolSpecification
import io.askimo.core.intent.ToolCategory
import io.askimo.core.intent.ToolStrategy
import io.askimo.core.mcp.McpServerDefinition
import io.askimo.core.mcp.Parameter
import io.askimo.core.mcp.ParameterType
import io.askimo.core.mcp.ProjectMcpInstanceData
import io.askimo.core.mcp.ProjectMcpInstanceService
import io.askimo.core.mcp.config.McpRuntimeValidator
import io.askimo.core.mcp.config.McpServersConfig
import io.askimo.core.mcp.config.RuntimeValidationResult
import io.askimo.core.mcp.config.ValidationSeverity
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.components.secondaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun addMcpIntegrationDialog(
    projectId: String,
    onDismiss: () -> Unit,
    onSave: (
        serverId: String,
        name: String,
        parameters: Map<String, String>,
        toolCategories: Map<String, ToolCategory>,
        toolStrategies: Map<String, Int>,
    ) -> Unit,
) {
    // Load available server templates
    val serverDefinitions = remember { McpServersConfig.getAll() }
    var selectedServer by remember { mutableStateOf<McpServerDefinition?>(null) }
    var instanceName by remember { mutableStateOf("") }
    val parameterValues = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showServerDropdown by remember { mutableStateOf(false) }

    // Tool configuration state
    var isLoadingTools by remember { mutableStateOf(false) }
    var availableTools by remember { mutableStateOf<List<ToolSpecification>>(emptyList()) }
    var toolCategories by remember { mutableStateOf<Map<String, ToolCategory>>(emptyMap()) }
    var toolStrategies by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var toolLoadError by remember { mutableStateOf<String?>(null) }

    // Validation state
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<RuntimeValidationResult?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(800.dp)
                .height(800.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                // Dialog Title
                Text(
                    text = stringResource("mcp.integrations.add.dialog.title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                HorizontalDivider()

                // Scrollable content area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    Text(
                        text = stringResource("mcp.integrations.add.dialog.description"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))

                    // Step 1: Select Server Template
                    Text(
                        text = stringResource("mcp.integrations.step.select.server"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Server Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerHoverIcon(PointerIcon.Hand),
                            onClick = { showServerDropdown = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedServer?.name
                                            ?: stringResource("mcp.integrations.select.server.placeholder"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (selectedServer != null) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    selectedServer?.description?.let { desc ->
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        ComponentColors.themedDropdownMenu(
                            expanded = showServerDropdown,
                            onDismissRequest = { showServerDropdown = false },
                        ) {
                            serverDefinitions.forEach { server ->
                                DropdownMenuItem(
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    text = {
                                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                            Text(
                                                text = server.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                text = server.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedServer = server
                                        showServerDropdown = false
                                        parameterValues.value = server.parameters.associate { param ->
                                            param.key to (param.defaultValue ?: "")
                                        }
                                        instanceName = server.name
                                    },
                                )
                            }
                        }
                    }

                    // Step 2: Configure Integration (only show if server selected)
                    AnimatedVisibility(visible = selectedServer != null) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                        ) {
                            Spacer(modifier = Modifier.height(Spacing.small))

                            Text(
                                text = stringResource("mcp.integrations.step.configure"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            // Instance Name
                            OutlinedTextField(
                                value = instanceName,
                                onValueChange = { instanceName = it },
                                label = { Text(stringResource("mcp.integrations.instance.name")) },
                                placeholder = { Text(stringResource("mcp.integrations.instance.name.placeholder")) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = ComponentColors.outlinedTextFieldColors(),
                            )

                            // Parameters
                            selectedServer?.parameters?.let { params ->
                                if (params.isNotEmpty()) {
                                    Text(
                                        text = stringResource("mcp.integrations.parameters"),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.small),
                                    )

                                    params.forEach { param ->
                                        parameterField(
                                            parameter = param,
                                            value = parameterValues.value[param.key] ?: "",
                                            onValueChange = { newValue ->
                                                parameterValues.value += (param.key to newValue)
                                            },
                                        )
                                    }
                                }
                            }

                            // Tools Section
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.medium))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Available Tools",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                OutlinedButton(
                                    onClick = {
                                        // Load tools from MCP instance
                                        isLoadingTools = true
                                        toolLoadError = null

                                        scope.launch {
                                            try {
                                                val mcpService = ProjectMcpInstanceService()
                                                // Create temporary instance to test connection
                                                val now = java.time.LocalDateTime.now().toString()
                                                val tempInstanceData = ProjectMcpInstanceData(
                                                    id = "temp-${System.currentTimeMillis()}",
                                                    projectId = projectId,
                                                    serverId = selectedServer!!.id,
                                                    name = instanceName.ifBlank { "Temporary" },
                                                    parameterValues = parameterValues.value,
                                                    enabled = true,
                                                    createdAt = now,
                                                    updatedAt = now,
                                                )

                                                // Convert to domain object for MCP client creation
                                                val tempInstance = tempInstanceData.toDomain()

                                                // Try to fetch tools
                                                val mcpClient = mcpService.createMcpClient(
                                                    tempInstance,
                                                    "test-connection",
                                                )

                                                if (mcpClient != null) {
                                                    val tools = mcpClient.listTools()
                                                    availableTools = tools

                                                    // Auto-infer categories and strategies for each tool
                                                    val inferredCategories = mutableMapOf<String, ToolCategory>()
                                                    val inferredStrategies = mutableMapOf<String, Int>()

                                                    tools.forEach { toolSpec ->
                                                        val category = mcpService.inferToolCategory(toolSpec)
                                                        val strategy = mcpService.inferToolStrategy(toolSpec)

                                                        inferredCategories[toolSpec.name()] = category
                                                        inferredStrategies[toolSpec.name()] = strategy
                                                    }

                                                    toolCategories = inferredCategories
                                                    toolStrategies = inferredStrategies
                                                    toolLoadError = null
                                                } else {
                                                    toolLoadError = "Failed to create MCP client. Check your parameters."
                                                }
                                            } catch (e: Exception) {
                                                toolLoadError = "Error loading tools: ${e.message}"
                                            } finally {
                                                isLoadingTools = false
                                            }
                                        }
                                    },
                                    enabled = selectedServer != null && !isLoadingTools,
                                ) {
                                    if (isLoadingTools) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(end = Spacing.small).width(16.dp).height(16.dp),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Load Tools",
                                            modifier = Modifier.padding(end = Spacing.small),
                                        )
                                    }
                                    Text(if (isLoadingTools) "Loading..." else "Load Tools")
                                }
                            }

                            // Tool load error
                            toolLoadError?.let { error ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                                ) {
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(Spacing.medium),
                                    )
                                }
                            }

                            // Tools list
                            if (availableTools.isNotEmpty()) {
                                Text(
                                    text = "${availableTools.size} tools available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = Spacing.small),
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.medium),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.small),
                                    ) {
                                        availableTools.forEach { toolSpec ->
                                            toolConfigurationRow(
                                                toolSpec = toolSpec,
                                                category = toolCategories[toolSpec.name()] ?: ToolCategory.OTHER,
                                                strategy = toolStrategies[toolSpec.name()] ?: ToolStrategy.FOLLOW_UP_BASED,
                                                onCategoryChange = { newCategory ->
                                                    toolCategories = toolCategories + (toolSpec.name() to newCategory)
                                                },
                                                onStrategyChange = { newStrategy ->
                                                    toolStrategies = toolStrategies + (toolSpec.name() to newStrategy)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } // End of scrollable content area

                HorizontalDivider()

                // Validation Results
                validationResult?.let { result ->
                    if (result.issues.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (result.hasErrors) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                },
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(Spacing.small),
                            ) {
                                Text(
                                    text = if (result.hasErrors) {
                                        stringResource("mcp.validation.errors")
                                    } else {
                                        stringResource("mcp.validation.warnings")
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (result.hasErrors) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                )

                                result.issues.forEach { issue ->
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Text(
                                                text = if (issue.severity == ValidationSeverity.ERROR) "⚠" else "⚡",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                text = issue.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (result.hasErrors) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onTertiaryContainer
                                                },
                                            )
                                        }

                                        issue.fixCommand?.let { fix ->
                                            Text(
                                                text = "Fix: $fix",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                ),
                                                color = if (result.hasErrors) {
                                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                                } else {
                                                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                                },
                                                modifier = Modifier.padding(start = 20.dp),
                                            )
                                        }
                                    }
                                }
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
                    secondaryButton(
                        onClick = onDismiss,
                    ) {
                        Text(stringResource("dialog.cancel"))
                    }

                    Spacer(modifier = Modifier.width(Spacing.small))

                    primaryButton(
                        onClick = {
                            selectedServer?.let { server ->
                                // Start validation
                                isValidating = true
                                validationResult = null

                                scope.launch {
                                    val result = McpRuntimeValidator.validateInstance(
                                        definition = server,
                                        parameters = parameterValues.value,
                                    )

                                    isValidating = false
                                    validationResult = result

                                    // If validation passes, save
                                    if (result.canProceed) {
                                        // Automatically load tools if not already loaded
                                        if (toolCategories.isEmpty() && toolStrategies.isEmpty()) {
                                            try {
                                                val mcpService = ProjectMcpInstanceService()
                                                val now = java.time.LocalDateTime.now().toString()
                                                val tempInstanceData = ProjectMcpInstanceData(
                                                    id = "temp-${System.currentTimeMillis()}",
                                                    projectId = projectId,
                                                    serverId = server.id,
                                                    name = instanceName,
                                                    parameterValues = parameterValues.value,
                                                    enabled = true,
                                                    createdAt = now,
                                                    updatedAt = now,
                                                )
                                                val tempInstance = tempInstanceData.toDomain()
                                                val mcpClient = mcpService.createMcpClient(tempInstance, "auto-load-tools")

                                                if (mcpClient != null) {
                                                    val tools = mcpClient.listTools()
                                                    val inferredCategories = mutableMapOf<String, ToolCategory>()
                                                    val inferredStrategies = mutableMapOf<String, Int>()

                                                    tools.forEach { toolSpec ->
                                                        val category = mcpService.inferToolCategory(toolSpec)
                                                        val strategy = mcpService.inferToolStrategy(toolSpec)
                                                        inferredCategories[toolSpec.name()] = category
                                                        inferredStrategies[toolSpec.name()] = strategy
                                                    }

                                                    toolCategories = inferredCategories
                                                    toolStrategies = inferredStrategies
                                                }
                                            } catch (e: Exception) {
                                                // If auto-load fails, continue without tools
                                                // (tools can still be loaded later via getProjectTools)
                                            }
                                        }

                                        onSave(
                                            server.id,
                                            instanceName,
                                            parameterValues.value,
                                            toolCategories,
                                            toolStrategies,
                                        )
                                        onDismiss()
                                    }
                                }
                            }
                        },
                        enabled = selectedServer != null && instanceName.isNotBlank() && !isValidating,
                    ) {
                        if (isValidating) {
                            Text(stringResource("mcp.validation.validating"))
                        } else {
                            Text(stringResource("mcp.integrations.add.button"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun parameterField(
    parameter: Parameter,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (parameter.type) {
        ParameterType.BOOLEAN -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parameter.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    parameter.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Checkbox(
                    checked = value.lowercase() == "true",
                    onCheckedChange = { checked ->
                        onValueChange(checked.toString())
                    },
                )
            }
        }

        ParameterType.SECRET -> {
            var showPassword by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(parameter.label)
                        if (parameter.required) {
                            Text(
                                text = " *",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                placeholder = { parameter.placeholder?.let { Text(it) } },
                supportingText = { parameter.description?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = if (showPassword) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = if (showPassword) {
                                "Hide password"
                            } else {
                                "Show password"
                            },
                        )
                    }
                },
                colors = ComponentColors.outlinedTextFieldColors(),
            )
        }

        else -> {
            // STRING, NUMBER, URL, PATH
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(parameter.label)
                        if (parameter.required) {
                            Text(
                                text = " *",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                placeholder = { parameter.placeholder?.let { Text(it) } },
                supportingText = { parameter.description?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = ComponentColors.outlinedTextFieldColors(),
            )
        }
    }
}

@Composable
private fun toolConfigurationRow(
    toolSpec: ToolSpecification,
    category: ToolCategory,
    strategy: Int,
    onCategoryChange: (ToolCategory) -> Unit,
    onStrategyChange: (Int) -> Unit,
) {
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showStrategyDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        // Tool name and description
        Text(
            text = toolSpec.name(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        toolSpec.description()?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Category and Strategy selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            // Category dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showCategoryDropdown = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Category: ${category.name}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Category",
                    )
                }

                ComponentColors.themedDropdownMenu(
                    expanded = showCategoryDropdown,
                    onDismissRequest = { showCategoryDropdown = false },
                ) {
                    ToolCategory.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = getCategoryDescription(cat),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                onCategoryChange(cat)
                                showCategoryDropdown = false
                            },
                        )
                    }
                }
            }

            // Strategy dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showStrategyDropdown = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = when (strategy) {
                            ToolStrategy.INTENT_BASED -> "Intent-Based"
                            ToolStrategy.FOLLOW_UP_BASED -> "Follow-Up"
                            ToolStrategy.BOTH -> "Both"
                            else -> "Unknown"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Strategy",
                    )
                }

                ComponentColors.themedDropdownMenu(
                    expanded = showStrategyDropdown,
                    onDismissRequest = { showStrategyDropdown = false },
                ) {
                    listOf(
                        ToolStrategy.INTENT_BASED to "Intent-Based" to "Auto-attach when user keywords match",
                        ToolStrategy.FOLLOW_UP_BASED to "Follow-Up" to "Suggest after AI response",
                        ToolStrategy.BOTH to "Both" to "Available in both stages",
                    ).forEach { (strategyPair, description) ->
                        val (strategyValue, strategyName) = strategyPair
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = strategyName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                onStrategyChange(strategyValue)
                                showStrategyDropdown = false
                            },
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = Spacing.small))
    }
}

private fun getCategoryDescription(category: ToolCategory): String = when (category) {
    ToolCategory.DATABASE -> "Database queries and operations"
    ToolCategory.NETWORK -> "HTTP requests and API calls"
    ToolCategory.FILE_READ -> "File system read operations"
    ToolCategory.FILE_WRITE -> "File system write operations"
    ToolCategory.VISUALIZE -> "Data visualization and charting"
    ToolCategory.EXECUTE -> "Code execution and commands"
    ToolCategory.SEARCH -> "Search and retrieval operations"
    ToolCategory.TRANSFORM -> "Data transformation"
    ToolCategory.VERSION_CONTROL -> "Git and version control"
    ToolCategory.COMMUNICATION -> "Email, messaging, notifications"
    ToolCategory.MONITORING -> "Logging and monitoring"
    ToolCategory.OTHER -> "Unclassified tools"
}
