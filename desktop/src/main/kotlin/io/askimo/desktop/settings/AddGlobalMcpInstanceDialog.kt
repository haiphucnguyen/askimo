/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.langchain4j.agent.tool.ToolSpecification
import io.askimo.core.mcp.GLOBAL_MCP_SCOPE_ID
import io.askimo.core.mcp.HttpConfig
import io.askimo.core.mcp.McpClientFactory
import io.askimo.core.mcp.McpInstance
import io.askimo.core.mcp.McpServerDefinition
import io.askimo.core.mcp.ServerDefinitionSecretManager
import io.askimo.core.mcp.StdioConfig
import io.askimo.core.mcp.TransportType
import io.askimo.core.mcp.config.McpServersConfig
import io.askimo.desktop.common.components.inlineErrorMessage
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.components.rememberDialogState
import io.askimo.desktop.common.components.secondaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.get
import java.time.LocalDateTime
import java.util.UUID

/**
 * Simplified one-step dialog for creating/editing global MCP instances directly.
 * No template selection - user enters configuration directly.
 */
@Composable
fun addGlobalMcpInstanceDialog(
    existingInstance: McpInstance? = null,
    onDismiss: () -> Unit,
    onSave: (serverId: String, name: String, parameters: Map<String, String>) -> Unit,
) {
    val dialogState = rememberDialogState()
    val scope = rememberCoroutineScope()

    // Get server definition if editing
    val existingServerDef = remember(existingInstance) {
        existingInstance?.let { McpServersConfig.get(it.serverId) }
    }

    // Basic fields
    var instanceName by remember { mutableStateOf(existingInstance?.name ?: "") }
    var description by remember { mutableStateOf(existingServerDef?.description ?: "") }

    // Transport type: 0 = STDIO, 1 = HTTP
    var selectedTab by remember {
        mutableStateOf(
            if (existingServerDef?.transportType == TransportType.HTTP) 1 else 0,
        )
    }

    // STDIO fields
    var command by remember {
        mutableStateOf(
            existingServerDef?.stdioConfig?.commandTemplate?.firstOrNull() ?: "",
        )
    }
    var args by remember {
        mutableStateOf(
            existingServerDef?.stdioConfig?.commandTemplate?.drop(1)?.joinToString(" ") ?: "",
        )
    }
    var workingDir by remember {
        mutableStateOf(existingServerDef?.stdioConfig?.workingDirectory ?: "")
    }
    var envVars by remember {
        mutableStateOf(
            existingServerDef?.stdioConfig?.envTemplate?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: "",
        )
    }

    // HTTP fields
    var url by remember { mutableStateOf(existingServerDef?.httpConfig?.urlTemplate ?: "") }
    var headers by remember {
        mutableStateOf(
            existingServerDef?.httpConfig?.headersTemplate?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: "",
        )
    }
    var timeoutMs by remember {
        mutableStateOf(existingServerDef?.httpConfig?.timeoutMs?.toString() ?: "60000")
    }

    // Connection test state
    var isTesting by remember { mutableStateOf(false) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }
    var availableTools by remember { mutableStateOf<List<ToolSpecification>>(emptyList()) }

    val transportType = if (selectedTab == 0) TransportType.STDIO else TransportType.HTTP

    // Helper function to parse environment variables
    fun parseEnvironmentVariables(envVarsString: String): Map<String, String> {
        if (envVarsString.isBlank()) return emptyMap()
        return envVarsString
            .split("\n")
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .toMap()
    }

    // Helper function to parse command line string into list of arguments
    // Handles spaces and quoted strings properly
    fun parseCommandLine(commandLine: String): List<String> {
        if (commandLine.isBlank()) return emptyList()

        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar: Char? = null
        var i = 0

        while (i < commandLine.length) {
            val char = commandLine[i]

            when {
                // Handle quotes
                (char == '"' || char == '\'') && !inQuotes -> {
                    inQuotes = true
                    quoteChar = char
                }
                char == quoteChar && inQuotes -> {
                    inQuotes = false
                    quoteChar = null
                }
                // Handle spaces outside quotes
                char.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                // Regular character
                else -> {
                    current.append(char)
                }
            }
            i++
        }

        // Add the last argument
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    // Helper function to build server definition from current form state
    fun buildServerDefinition(serverId: String): McpServerDefinition = if (transportType == TransportType.STDIO) {
        // Parse the command field as a complete command line
        val commandList = buildList {
            addAll(parseCommandLine(command))
            // If args field is also provided, parse and add those too
            if (args.isNotBlank()) {
                addAll(parseCommandLine(args))
            }
        }
        val envMap = parseEnvironmentVariables(envVars)

        McpServerDefinition(
            id = serverId,
            name = instanceName,
            description = description,
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = commandList,
                envTemplate = envMap,
                workingDirectory = workingDir.ifBlank { null },
            ),
            httpConfig = null,
            tags = listOf("global"),
        )
    } else {
        val headersMap = parseEnvironmentVariables(headers)
        val timeout = timeoutMs.toLongOrNull() ?: 60000L

        McpServerDefinition(
            id = serverId,
            name = instanceName,
            description = description,
            transportType = TransportType.HTTP,
            stdioConfig = null,
            httpConfig = HttpConfig(
                urlTemplate = url,
                headersTemplate = headersMap,
                timeoutMs = timeout,
            ),
            tags = listOf("global"),
        )
    }

    // Test connection method - can be called independently or as part of save
    suspend fun testConnection(): Pair<Boolean, String?> {
        return withContext(Dispatchers.IO) {
            try {
                // Use existing serverId if editing, otherwise generate new one
                val serverId = existingInstance?.serverId ?: "global-${UUID.randomUUID()}"

                // Build server definition
                val serverDef = buildServerDefinition(serverId)

                // Protect secrets before saving
                val (protectedDef, _) = ServerDefinitionSecretManager.protectSecrets(serverDef)

                // Save server definition temporarily (or update if editing)
                McpServersConfig.add(protectedDef)

                try {
                    // Test connection
                    val mcpClientFactory = get<McpClientFactory>(McpClientFactory::class.java)

                    val now = LocalDateTime.now()
                    val instance = McpInstance(
                        id = serverId,
                        projectId = GLOBAL_MCP_SCOPE_ID,
                        serverId = serverId,
                        name = instanceName,
                        parameterValues = emptyMap(),
                        enabled = true,
                        createdAt = now,
                        updatedAt = now,
                    )

                    val clientResult = mcpClientFactory.createMcpClient(
                        instance,
                        "test-connection-$serverId",
                    )

                    clientResult.fold(
                        onSuccess = { mcpClient ->
                            val tools = mcpClient.listTools()
                            availableTools = tools
                        },
                        onFailure = { e ->
                            return@withContext false to e.message
                        },
                    )
                    return@withContext true to serverId
                } finally {
                    // Clean up temporary server definition only if not editing
                    if (existingInstance == null) {
                        McpServersConfig.remove(serverId)
                    }
                }
            } catch (e: Exception) {
                return@withContext false to null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier
                .width(800.dp)
                .heightIn(min = 600.dp, max = 800.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Scrollable content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 32.dp, top = 24.dp, bottom = 16.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(Spacing.large),
                    ) {
                        // Title
                        Text(
                            text = stringResource(
                                if (existingInstance != null) {
                                    "mcp.global.instance.edit.dialog.title"
                                } else {
                                    "mcp.global.instance.add.dialog.title"
                                },
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                if (existingInstance != null) {
                                    "mcp.global.instance.edit.dialog.description"
                                } else {
                                    "mcp.global.instance.add.dialog.description"
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))

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

                        // Description (optional)
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

                        // Transport Type Selector
                        Text(
                            text = stringResource("mcp.template.field.transport"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        SecondaryTabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                text = {
                                    Text(
                                        text = stringResource("mcp.template.transport.stdio"),
                                        color = if (selectedTab == 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                text = {
                                    Text(
                                        text = stringResource("mcp.template.transport.http"),
                                        color = if (selectedTab == 1) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                            )
                        }

                        // STDIO Configuration
                        AnimatedVisibility(visible = selectedTab == 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                                OutlinedTextField(
                                    value = command,
                                    onValueChange = { command = it },
                                    label = { Text(stringResource("mcp.template.field.command")) },
                                    placeholder = { Text(stringResource("mcp.template.field.command.placeholder")) },
                                    supportingText = { Text(stringResource("mcp.template.field.command.hint")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )

                                OutlinedTextField(
                                    value = args,
                                    onValueChange = { args = it },
                                    label = { Text(stringResource("mcp.global.instance.field.args")) },
                                    placeholder = { Text(stringResource("mcp.global.instance.field.args.placeholder")) },
                                    supportingText = { Text(stringResource("mcp.global.instance.field.args.hint")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )

                                OutlinedTextField(
                                    value = workingDir,
                                    onValueChange = { workingDir = it },
                                    label = { Text(stringResource("mcp.global.instance.field.workingdir")) },
                                    placeholder = { Text(System.getProperty("user.home")) },
                                    supportingText = { Text(stringResource("mcp.global.instance.field.workingdir.hint")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )

                                OutlinedTextField(
                                    value = envVars,
                                    onValueChange = { envVars = it },
                                    label = { Text(stringResource("mcp.template.env.label")) },
                                    placeholder = { Text(stringResource("mcp.template.env.placeholder")) },
                                    supportingText = { Text(stringResource("mcp.template.env.hint")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 5,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )
                            }
                        }

                        // HTTP Configuration
                        AnimatedVisibility(visible = selectedTab == 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                                OutlinedTextField(
                                    value = url,
                                    onValueChange = { url = it },
                                    label = { Text(stringResource("mcp.template.http.url.label")) },
                                    placeholder = { Text(stringResource("mcp.template.http.url.placeholder")) },
                                    supportingText = { Text(stringResource("mcp.template.http.url.hint")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )

                                OutlinedTextField(
                                    value = headers,
                                    onValueChange = { headers = it },
                                    label = { Text(stringResource("mcp.template.http.headers.label")) },
                                    placeholder = { Text(stringResource("mcp.template.http.headers.placeholder")) },
                                    supportingText = { Text(stringResource("mcp.template.http.headers.hint")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 5,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )

                                OutlinedTextField(
                                    value = timeoutMs,
                                    onValueChange = { timeoutMs = it },
                                    label = { Text(stringResource("mcp.template.http.timeout.label")) },
                                    placeholder = { Text("60000") },
                                    supportingText = { Text(stringResource("mcp.template.http.timeout.hint")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.medium))

                        // Connection Status Section
                        Text(
                            text = stringResource("mcp.integrations.connection.status"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Text(
                            text = stringResource("mcp.integrations.connection.info"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Show test result
                        testSuccess?.let { success ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                modifier = Modifier.padding(vertical = Spacing.small),
                            ) {
                                Text(
                                    text = if (success) "✅" else "❌",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(
                                        if (success) {
                                            "mcp.integrations.test.success"
                                        } else {
                                            "mcp.integrations.test.failed"
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (success) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        // Display available tools
                        if (availableTools.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.medium))

                            Text(
                                text = stringResource("mcp.integrations.tools.title"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Text(
                                text = stringResource("mcp.integrations.tools.count", availableTools.size.toString()),
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
                                        .padding(Spacing.medium)
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                                ) {
                                    availableTools.forEach { toolSpec ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
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
                                            }
                                        }
                                        if (toolSpec != availableTools.last()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.extraSmall))
                                        }
                                    }
                                }
                            }
                        }

                        // Show error message if any
                        dialogState.errorMessage?.let { error ->
                            inlineErrorMessage(errorMessage = error)
                        }
                    }
                }

                // Action buttons (pinned at bottom)
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    secondaryButton(
                        onClick = onDismiss,
                    ) {
                        Text(stringResource("dialog.cancel"))
                    }
                    Spacer(Modifier.width(Spacing.small))
                    secondaryButton(
                        onClick = {
                            dialogState.clearError()
                            isTesting = true
                            scope.launch {
                                try {
                                    val (success, _) = testConnection()
                                    testSuccess = success
                                    if (!success) {
                                        dialogState.setError("Failed to connect to MCP server. Check your configuration.")
                                    }
                                } catch (e: Exception) {
                                    testSuccess = false
                                    dialogState.setError(e, "Connection test failed")
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource("mcp.integrations.test.connection"))
                    }
                    Spacer(Modifier.width(Spacing.small))
                    primaryButton(
                        onClick = {
                            dialogState.clearError()
                            isTesting = true
                            scope.launch {
                                try {
                                    // Always test connection first, then save on success
                                    val (success, serverId) = testConnection()
                                    testSuccess = success

                                    if (success && serverId != null) {
                                        // Connection successful, save permanently with protected secrets
                                        val serverDef = buildServerDefinition(serverId)
                                        val (protectedDef, _) = ServerDefinitionSecretManager.protectSecrets(serverDef)
                                        McpServersConfig.add(protectedDef)
                                        val parameters = emptyMap<String, String>()
                                        onSave(serverId, instanceName, parameters)
                                    } else {
                                        dialogState.setError("Failed to connect to MCP server. Check your configuration.")
                                    }
                                } catch (e: Exception) {
                                    testSuccess = false
                                    dialogState.setError(e, "Failed to save MCP instance")
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource("action.save"))
                    }
                }
            }
        }
    }
}
