/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import io.askimo.core.mcp.config.McpServersConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
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
    @Tag("slow")
    @Tag("integration")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("Should get MCP tool provider with filesystem connectors")
    fun testGetToolProvider() {
        runBlocking {
            // Given
            val rootPath = testRootDir.resolve("documents").toString()
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")

            println("Test root path: $rootPath")
            println("Operating System: ${System.getProperty("os.name")}")
            println("⚠️  This test may timeout on first run if npx needs to download @modelcontextprotocol/server-filesystem")
            println("   To avoid timeout, pre-install: npm install -g @modelcontextprotocol/server-filesystem")

            if (isWindows) {
                println("⚠️  Running on Windows - NPX behavior may differ from Unix systems")
            }

            val createResult = mcpInstanceService.createInstance(
                projectId = projectId,
                serverId = "filesystem-mcp-server",
                name = "Test Filesystem",
                parameterValues = mapOf("rootPath" to rootPath),
            )
            println("Instance created: ${createResult.isSuccess}")

            if (createResult.isFailure) {
                println("ERROR creating instance: ${createResult.exceptionOrNull()?.message}")
                createResult.exceptionOrNull()?.printStackTrace()
                throw AssertionError("Failed to create instance", createResult.exceptionOrNull())
            }

            // When
            println("Calling getToolProvider for project: $projectId")
            val result = mcpInstanceService.getToolProvider(projectId)

            // Then
            println("getToolProvider result: success=${result.isSuccess}, hasValue=${result.getOrNull() != null}")

            if (result.isFailure) {
                println("ERROR: ${result.exceptionOrNull()?.message}")
                result.exceptionOrNull()?.printStackTrace()

                if (isWindows) {
                    println("\n⚠️  This failure occurred on Windows.")
                    println("   Common Windows issues:")
                    println("   1. NPX may not be in PATH or may use .cmd/.bat extension")
                    println("   2. Node.js/npm may not be properly installed")
                    println("   3. MCP server may fail to start due to Windows-specific issues")
                    println("   4. Path separators (backslash vs forward slash)")
                }

                throw AssertionError("Failed to get tool provider: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
            }

            assertTrue(result.isSuccess, "Should successfully return result")
            val toolProvider = result.getOrNull()

            // FAIL the test if tool provider is null (timeout or initialization failure)
            assertNotNull(
                toolProvider,
                """
                Tool provider creation failed (returned null).
                This likely means:
                  1. MCP server initialization timed out (npx downloading package on first run)
                  2. MCP server failed to start or crashed
                  3. Transport communication failed
                ${if (isWindows) "\n  4. Windows-specific NPX or path issues\n" else ""}
                To fix: Pre-install the MCP server globally:
                  npm install -g @modelcontextprotocol/server-filesystem

                Then re-run the test.
                """.trimIndent(),
            )

            println("✅ SUCCESS: Tool provider created successfully")
        }
    }

    @Test
    @DisplayName("Should return null tool provider when no instances exist")
    fun testGetToolProviderWithNoInstances() {
        runBlocking {
            // Given - no instances created

            // When
            val result = mcpInstanceService.getToolProvider(projectId)

            // Then
            assertTrue(result.isSuccess)
            assertNull(result.getOrNull(), "Tool provider should be null when no instances")
        }
    }

    @Test
    @DisplayName("Should return null tool provider when instance is disabled")
    fun testGetToolProviderWithDisabledInstance() {
        runBlocking {
            // Given
            val rootPath = testRootDir.resolve("documents").toString()

            val createResult = mcpInstanceService.createInstance(
                projectId = projectId,
                serverId = "filesystem-mcp-server",
                name = "Disabled Filesystem",
                parameterValues = mapOf("rootPath" to rootPath),
            )
            val instance = createResult.getOrThrow()

            // Disable the instance
            mcpInstanceService.setInstanceEnabled(projectId, instance.id, false)

            // When
            val result = mcpInstanceService.getToolProvider(projectId)

            // Then
            assertTrue(result.isSuccess)
            assertNull(result.getOrNull(), "Tool provider should be null when all instances disabled")
        }
    }

    @Test
    @Tag("slow")
    @Tag("integration")
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @DisplayName("Should skip invalid instances and create tool provider with valid ones")
    fun testGetToolProviderWithMixedValidation() {
        runBlocking {
            // Given - One valid and one invalid instance
            val validPath = testRootDir.resolve("documents").toString()
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")

            println("Operating System: ${System.getProperty("os.name")}")
            if (isWindows) {
                println("⚠️  Running on Windows - NPX behavior may differ from Unix systems")
            }

            // Valid instance
            mcpInstanceService.createInstance(
                projectId = projectId,
                serverId = "filesystem-mcp-server",
                name = "Valid Filesystem",
                parameterValues = mapOf("rootPath" to validPath),
            )

            // Create an instance and then delete the directory to make it invalid
            val tempPath = testRootDir.resolve("temp-dir")
            tempPath.createDirectories()

            mcpInstanceService.createInstance(
                projectId = projectId,
                serverId = "filesystem-mcp-server",
                name = "Invalid Filesystem",
                parameterValues = mapOf("rootPath" to tempPath.toString()),
            )

            // Delete the directory to make it invalid
            tempPath.toFile().deleteRecursively()

            // When
            val result = mcpInstanceService.getToolProvider(projectId)

            // Then
            assertTrue(result.isSuccess, "Should successfully return result")
            val toolProvider = result.getOrNull()

            assertNotNull(
                toolProvider,
                """
                Tool provider should be created with at least the valid instance.
                This test validates that invalid instances are skipped, not that all fail.

                Null result likely means:
                  1. MCP server initialization timed out
                  2. Valid instance also failed (unexpected)
                ${if (isWindows) "  3. Windows-specific NPX or path issues\n" else ""}
                Pre-install the MCP server: npm install -g @modelcontextprotocol/server-filesystem
                """.trimIndent(),
            )

            println("✅ SUCCESS: Tool provider created, invalid instance was skipped")
        }
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
    @DisplayName("Should get instance statistics")
    fun testGetInstanceStats() {
        // Given - Create 3 instances, 2 enabled, 1 disabled
        val rootPath = testRootDir.resolve("documents").toString()

        mcpInstanceService.createInstance(
            projectId = projectId,
            serverId = "filesystem-mcp-server",
            name = "Filesystem 1",
            parameterValues = mapOf("rootPath" to rootPath),
        ).getOrThrow()

        mcpInstanceService.createInstance(
            projectId = projectId,
            serverId = "filesystem-mcp-server",
            name = "Filesystem 2",
            parameterValues = mapOf("rootPath" to rootPath),
        )

        val instance3 = mcpInstanceService.createInstance(
            projectId = projectId,
            serverId = "filesystem-mcp-server",
            name = "Filesystem 3",
            parameterValues = mapOf("rootPath" to rootPath),
        ).getOrThrow()

        // Disable one instance
        mcpInstanceService.setInstanceEnabled(projectId, instance3.id, false)

        // When
        val stats = mcpInstanceService.getInstanceStats(projectId)

        // Then
        assertEquals(3, stats.total)
        assertEquals(2, stats.enabled)
        assertEquals(1, stats.disabled)
        assertEquals(3, stats.byServer["filesystem-mcp-server"])
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
    @DisplayName("Should handle connector validation errors gracefully")
    fun testConnectorValidationErrorHandling() {
        runBlocking {
            // Given - Create instance with valid path initially
            val tempPath = testRootDir.resolve("temp-valid")
            tempPath.createDirectories()

            val createResult = mcpInstanceService.createInstance(
                projectId = projectId,
                serverId = "filesystem-mcp-server",
                name = "Soon Invalid",
                parameterValues = mapOf("rootPath" to tempPath.toString()),
            )
            assertTrue(createResult.isSuccess)

            // Delete the directory after creation to simulate validation failure
            tempPath.toFile().deleteRecursively()

            // When - Try to get tool provider
            val result = mcpInstanceService.getToolProvider(projectId)

            // Then - Should return null (all connectors failed validation)
            assertTrue(result.isSuccess)
            assertNull(result.getOrNull(), "Should return null when connector validation fails")
        }
    }

    @Test
    @DisplayName("Diagnostic: Check if npx is available")
    fun testNpxAvailability() {
        println("\n=== NPX Availability Check ===")

        // Use 'where' on Windows, 'which' on Unix
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val whichCommand = if (isWindows) "where" else "which"

        try {
            val process = ProcessBuilder(whichCommand, "npx")
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

            // Try to get npx version
            val versionProcess = ProcessBuilder("npx", "--version")
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
