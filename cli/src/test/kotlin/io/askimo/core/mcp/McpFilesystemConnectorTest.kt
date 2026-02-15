/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import io.askimo.core.mcp.config.McpServersConfig
import io.askimo.core.util.ProcessBuilderExt
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("MCP Filesystem Connector Integration Tests")
class McpFilesystemConnectorTest {

    @TempDir
    lateinit var testRootDir: Path

    private lateinit var projectId: String
    private lateinit var mcpInstanceService: ProjectMcpInstanceService

    @BeforeEach
    fun setup() {
        projectId = UUID.randomUUID().toString()
        mcpInstanceService = ProjectMcpInstanceService()

        // Create test directory structure
        testRootDir.resolve("documents").createDirectories()
        testRootDir.resolve("documents/test.txt").writeText("Hello from test file!")
        testRootDir.resolve("documents/subfolder").createDirectories()
        testRootDir.resolve("documents/subfolder/nested.txt").writeText("Nested content")
    }

    @AfterEach
    fun cleanup() {
        mcpInstanceService.deleteAllInstances(projectId)
    }

    @Test
    @DisplayName("Should create filesystem MCP instance with valid path")
    fun testCreateFilesystemInstance() {
        // Given
        val instanceName = "Test Filesystem"
        val serverDefId = "filesystem-mcp-server"
        val rootPath = testRootDir.resolve("documents").toString()

        // When
        val result = mcpInstanceService.createInstance(
            projectId = projectId,
            serverId = serverDefId,
            name = instanceName,
            parameterValues = mapOf("rootPath" to rootPath),
        )

        // Then
        assertTrue(result.isSuccess, "Should successfully create filesystem instance")
        val instance = result.getOrNull()
        assertNotNull(instance)
        assertEquals(instanceName, instance.name)
        assertEquals(serverDefId, instance.serverId)
        assertEquals(rootPath, instance.parameterValues["rootPath"])
        assertTrue(instance.enabled)
    }

    @Test
    @DisplayName("Should create filesystem MCP instance even with non-existent path (validation limitation)")
    fun testCreateInstanceWithInvalidPath() {
        // Given
        val instanceName = "Invalid Filesystem"
        val serverDefId = "filesystem-mcp-server"
        val invalidPath = "/nonexistent/path/to/directory"

        // When
        val result = mcpInstanceService.createInstance(
            projectId = projectId,
            serverId = serverDefId,
            name = instanceName,
            parameterValues = mapOf("rootPath" to invalidPath),
        )

        assertTrue(result.isSuccess, "Instance creation succeeds (validation happens later)")
    }

    @Test
    @DisplayName("Should validate filesystem connector configuration (pattern validation only)")
    fun testValidateConnector() {
        // Given
        val serverDefId = "filesystem-mcp-server"
        val validPath = testRootDir.resolve("documents").toString()

        // When - Valid path format
        val validResult = mcpInstanceService.validateInstance(
            serverId = serverDefId,
            parameterValues = mapOf("rootPath" to validPath),
        )

        // Then
        assertTrue(validResult.isSuccess, "Validation should succeed with valid path format")

        // When - Invalid path format (but note: current validation is pattern-based, not filesystem-based)
        val invalidResult = mcpInstanceService.validateInstance(
            serverId = serverDefId,
            parameterValues = mapOf("rootPath" to "/invalid/path"),
        )

        // Then - Path format is valid, so validation passes
        // NOTE: Actual filesystem existence is checked when connector.validate() is called
        assertTrue(invalidResult.isSuccess, "Validation checks pattern, not filesystem existence")
    }

    @Test
    @DisplayName("Should test connection to filesystem connector")
    fun testConnectionTest() {
        // Given
        val instanceName = "Connection Test"
        val serverDefId = "filesystem-mcp-server"
        val rootPath = testRootDir.resolve("documents").toString()

        val createResult = mcpInstanceService.createInstance(
            projectId = projectId,
            serverId = serverDefId,
            name = instanceName,
            parameterValues = mapOf("rootPath" to rootPath),
        )
        assertTrue(createResult.isSuccess)
        val instance = createResult.getOrThrow()

        // When
        val testResult = mcpInstanceService.testConnection(projectId, instance.id)

        // Then
        assertTrue(testResult.isSuccess, "Connection test should succeed with valid path")
    }

    @Test
    @DisplayName("Should update filesystem instance parameters")
    fun testUpdateInstanceParameters() {
        // Given
        val initialPath = testRootDir.resolve("documents").toString()
        val createResult = mcpInstanceService.createInstance(
            projectId = projectId,
            serverId = "filesystem-mcp-server",
            name = "Updatable Filesystem",
            parameterValues = mapOf("rootPath" to initialPath),
        )
        val instance = createResult.getOrThrow()

        // When - Update to new valid path
        val newPath = testRootDir.toString()
        val updateResult = mcpInstanceService.updateInstance(
            projectId = projectId,
            instanceId = instance.id,
            parameterValues = mapOf("rootPath" to newPath),
        )

        // Then
        assertTrue(updateResult.isSuccess)
        val updated = updateResult.getOrThrow()
        assertEquals(newPath, updated.parameterValues["rootPath"])
    }

    @Test
    @DisplayName("Should delete filesystem instance")
    fun testDeleteInstance() {
        // Given
        val rootPath = testRootDir.resolve("documents").toString()
        val createResult = mcpInstanceService.createInstance(
            projectId = projectId,
            serverId = "filesystem-mcp-server",
            name = "To Be Deleted",
            parameterValues = mapOf("rootPath" to rootPath),
        )
        val instance = createResult.getOrThrow()

        // When
        val deleteResult = mcpInstanceService.deleteInstance(projectId, instance.id)

        // Then
        assertTrue(deleteResult.isSuccess)
        val retrieved = mcpInstanceService.getInstance(projectId, instance.id)
        assertNull(retrieved, "Instance should be deleted")
    }

    @Test
    @DisplayName("Diagnostic: Check if npx is available")
    fun testNpxAvailability() {
        println("\n=== NPX Availability Check ===")

        // Use 'where' on Windows, 'which' on Unix
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val whichCommand = if (isWindows) "where" else "which"

        try {
            val process = ProcessBuilderExt(whichCommand, "npx")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            println("$whichCommand npx exit code: $exitCode")
            println("npx location: ${output.trim()}")

            if (exitCode == 0) {
                println("✅ npx is available at: ${output.trim()}")
            } else {
                println("⚠️  npx not found in PATH")
            }

            // Try to get npx version using ProcessBuilderExt (handles .cmd files on Windows)
            val versionProcess = ProcessBuilderExt("npx", "--version")
                .redirectErrorStream(true)
                .start()

            val versionOutput = versionProcess.inputStream.bufferedReader().readText()
            val versionExitCode = versionProcess.waitFor()

            println("npx --version exit code: $versionExitCode")
            println("npx version: ${versionOutput.trim()}")
        } catch (e: Exception) {
            println("⚠️  Error checking npx availability: ${e.message}")
            println("This is a diagnostic test - failing here indicates npx is not available or not in PATH")
            throw e
        }

        println("=== End NPX Check ===\n")
    }

    @Test
    @DisplayName("Diagnostic: Try to create MCP transport directly")
    fun testDirectTransportCreation() {
        runBlocking {
            println("\n=== Direct Transport Creation Test ===")
            val rootPath = testRootDir.resolve("documents").toString()
            println("Root path: $rootPath")

            val definition = McpServersConfig.get("filesystem-mcp-server")
            assertNotNull(definition, "Filesystem server definition should exist")
            println("Server definition found: ${definition.name}")

            val instance = ProjectMcpInstance(
                id = "test-id",
                projectId = projectId,
                serverId = "filesystem-mcp-server",
                name = "Direct Test",
                parameterValues = mapOf("rootPath" to rootPath),
                enabled = true,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

            println("Creating connector...")
            val connector = instance.toConnector(definition)
            println("✅ Connector created successfully")

            println("Validating connector...")
            val validationResult = connector.validate()
            println("Validation result: ${validationResult.isValid}")
            if (!validationResult.isValid) {
                println("Validation errors: ${validationResult.errors}")
            }

            println("Creating transport...")

            val transport = connector.createTransport()
            println("✅ Transport created successfully: ${transport.javaClass.simpleName}")

            println("=== End Direct Transport Test ===\n")
        }
    }
}
