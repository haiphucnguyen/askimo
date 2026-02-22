/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.connectors

import dev.langchain4j.mcp.client.transport.McpTransport
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport
import io.askimo.core.logging.logger
import io.askimo.core.mcp.McpConnector
import io.askimo.core.mcp.StdioMcpTransportConfig
import io.askimo.core.mcp.ValidationResult
import io.askimo.core.util.ExecutableResolver
import java.io.File

/**
 * Connector for stdio-based MCP servers (local processes)
 */
class StdioMcpConnector(
    private val config: StdioMcpTransportConfig,
) : McpConnector() {

    private val log = logger<StdioMcpConnector>()

    override suspend fun createTransport(): McpTransport {
        // Resolve executable paths (handles Windows .cmd/.bat extensions, macOS PATH issues, etc.)
        val command = ExecutableResolver.resolveCommand(config.command)

        val builder = StdioMcpTransport.builder()
            .logger(log)
            .logEvents(log.isTraceEnabled)
            .command(command)

        // Add environment variables if any
        if (config.env.isNotEmpty()) {
            builder.environment(config.env.toMutableMap())
        }

        return builder.build()
    }

    override fun validate(): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate command
        if (config.command.isEmpty()) {
            errors.add("Command cannot be empty")
        }

        // Check if command executable exists (basic check for first element)
        if (config.command.isNotEmpty()) {
            val executable = config.command[0]
            // Skip validation for well-known commands that should be in PATH
            val skipValidation = listOf("npx", "node", "python", "python3", "java", "sh", "bash")
            if (!skipValidation.contains(executable)) {
                val execFile = File(executable)
                // Only check if it's an absolute path
                if (execFile.isAbsolute && !execFile.exists()) {
                    errors.add("Executable not found: $executable")
                }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
        )
    }
}
