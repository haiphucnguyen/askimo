/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp.config

import io.askimo.core.logging.logger
import io.askimo.core.mcp.McpServerDefinition
import io.askimo.core.mcp.TransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.time.Duration.Companion.seconds

/**
 * Severity level for validation errors
 */
enum class ValidationSeverity {
    ERROR, // Blocks creation - cannot proceed
    WARNING, // Allows creation but shows warning
}

/**
 * Single validation issue with severity and optional fix command
 */
data class ValidationIssue(
    val severity: ValidationSeverity,
    val message: String,
    val fixCommand: String? = null,
)

/**
 * Result of runtime validation
 */
data class RuntimeValidationResult(
    val canProceed: Boolean,
    val issues: List<ValidationIssue>,
) {
    val hasErrors: Boolean get() = issues.any { it.severity == ValidationSeverity.ERROR }
    val hasWarnings: Boolean get() = issues.any { it.severity == ValidationSeverity.WARNING }
}

/**
 * Validates MCP server instances at runtime
 */
object McpRuntimeValidator {
    private val log = logger<McpRuntimeValidator>()

    /**
     * Validate an MCP server instance before creation
     */
    suspend fun validateInstance(
        definition: McpServerDefinition,
        parameters: Map<String, String>,
    ): RuntimeValidationResult = withContext(Dispatchers.IO) {
        val issues = mutableListOf<ValidationIssue>()

        // Only validate STDIO transport for now
        if (definition.transportType != TransportType.STDIO) {
            return@withContext RuntimeValidationResult(canProceed = true, issues = emptyList())
        }

        val stdioConfig = definition.stdioConfig
        if (stdioConfig == null) {
            issues.add(
                ValidationIssue(
                    severity = ValidationSeverity.ERROR,
                    message = "STDIO configuration is missing",
                ),
            )
            return@withContext RuntimeValidationResult(canProceed = false, issues = issues)
        }

        // Check executables in command template
        val commandTemplate = stdioConfig.commandTemplate
        if (commandTemplate.isEmpty()) {
            issues.add(
                ValidationIssue(
                    severity = ValidationSeverity.ERROR,
                    message = "Command template is empty",
                ),
            )
            return@withContext RuntimeValidationResult(canProceed = false, issues = issues)
        }

        val executable = commandTemplate.first()

        // Check if executable exists on system
        val executableExists = checkExecutableExists(executable)
        if (!executableExists) {
            issues.add(
                ValidationIssue(
                    severity = ValidationSeverity.ERROR,
                    message = "'$executable' is not installed or not in PATH",
                    fixCommand = when (executable) {
                        "npx" -> "npm install -g npm"
                        "node" -> "Install Node.js from https://nodejs.org"
                        else -> null
                    },
                ),
            )
            return@withContext RuntimeValidationResult(canProceed = false, issues = issues)
        }

        // For npx-based servers, check if package might be available
        if (executable == "npx" && commandTemplate.size >= 3) {
            val packageName = commandTemplate.getOrNull(2) // After "npx -y"
            if (packageName != null && packageName.startsWith("@")) {
                // This is informational - npx will auto-install with -y flag
                log.debug("MCP server uses npm package: $packageName")
            }
        }

        // Try to spawn the process with a quick timeout
        val spawnResult = trySpawnProcess(commandTemplate, parameters)
        if (!spawnResult.success) {
            issues.add(
                ValidationIssue(
                    severity = ValidationSeverity.WARNING,
                    message = spawnResult.error ?: "Failed to start MCP server process",
                    fixCommand = spawnResult.fixCommand,
                ),
            )
        }

        RuntimeValidationResult(
            canProceed = !issues.any { it.severity == ValidationSeverity.ERROR },
            issues = issues,
        )
    }

    /**
     * Check if an executable exists in PATH
     */
    private fun checkExecutableExists(executable: String): Boolean = try {
        val command = if (System.getProperty("os.name").lowercase().contains("windows")) {
            listOf("where", executable)
        } else {
            listOf("which", executable)
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        exitCode == 0
    } catch (e: Exception) {
        log.debug("Failed to check executable '$executable': ${e.message}")
        false
    }

    /**
     * Try to spawn the MCP server process briefly to see if it works
     */
    private suspend fun trySpawnProcess(
        commandTemplate: List<String>,
        parameters: Map<String, String>,
    ): SpawnResult = withTimeoutOrNull(5.seconds) {
        try {
            // Replace parameters in command
            val command = commandTemplate.map { part ->
                if (part.startsWith("{{") && part.endsWith("}}")) {
                    val paramKey = part.substring(2, part.length - 2)
                    parameters[paramKey] ?: part
                } else {
                    part
                }
            }

            log.debug("Testing MCP server spawn with command: ${command.joinToString(" ")}")

            val processBuilder = ProcessBuilder(command)
                .redirectErrorStream(true)

            val process = processBuilder.start()

            // Read a bit of output to see if process starts
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var linesRead = 0
            while (linesRead < 5 && reader.ready()) {
                val line = reader.readLine() ?: break
                output.appendLine(line)
                linesRead++
            }

            // Kill the process
            process.destroy()

            // Wait a bit for clean shutdown
            withTimeoutOrNull(1.seconds) {
                process.waitFor()
            }

            SpawnResult(success = true)
        } catch (e: Exception) {
            log.debug("Failed to spawn MCP server process: ${e.message}")
            SpawnResult(
                success = false,
                error = "Failed to start server: ${e.message}",
                fixCommand = null,
            )
        }
    } ?: SpawnResult(
        success = false,
        error = "Server process did not respond within 5 seconds",
    )

    private data class SpawnResult(
        val success: Boolean,
        val error: String? = null,
        val fixCommand: String? = null,
    )
}
