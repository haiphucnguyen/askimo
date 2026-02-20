/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariableExtractorTest {

    // ==================== extractFromString Tests ====================

    @Test
    fun `extractFromString should find simple placeholder`() {
        val result = VariableExtractor.extractFromString("Hello {{name}}")
        assertEquals(listOf("name"), result)
    }

    @Test
    fun `extractFromString should find multiple placeholders`() {
        val result = VariableExtractor.extractFromString("{{host}}:{{port}}/{{database}}")
        assertEquals(listOf("host", "port", "database"), result)
    }

    @Test
    fun `extractFromString should find conditional placeholder`() {
        val result = VariableExtractor.extractFromString("{{?debug:--verbose}}")
        assertEquals(listOf("debug"), result)
    }

    @Test
    fun `extractFromString should find both simple and conditional placeholders`() {
        val result = VariableExtractor.extractFromString("node {{script}} {{?debug:--verbose}}")
        assertEquals(listOf("script", "debug"), result)
    }

    @Test
    fun `extractFromString should return empty for no placeholders`() {
        val result = VariableExtractor.extractFromString("plain text with no variables")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractFromString should return empty for empty string`() {
        val result = VariableExtractor.extractFromString("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractFromString should handle adjacent placeholders`() {
        val result = VariableExtractor.extractFromString("{{host}}{{port}}")
        assertEquals(listOf("host", "port"), result)
    }

    @Test
    fun `extractFromString should remove duplicates`() {
        val result = VariableExtractor.extractFromString("{{name}} loves {{name}}")
        assertEquals(listOf("name"), result)
    }

    @Test
    fun `extractFromString should trim whitespace in variable keys`() {
        val result = VariableExtractor.extractFromString("{{ host }} {{ port }}")
        assertEquals(listOf("host", "port"), result)
    }

    @Test
    fun `extractFromString should handle complex conditional with value containing special chars`() {
        val result = VariableExtractor.extractFromString("{{?ssl:--ssl-mode=REQUIRED}}")
        assertEquals(listOf("ssl"), result)
    }

    @Test
    fun `extractFromString should handle conditional with spaces in value`() {
        val result = VariableExtractor.extractFromString("{{?verbose:--log-level DEBUG}}")
        assertEquals(listOf("verbose"), result)
    }

    // ==================== extract from StdioConfig Tests ====================

    @Test
    fun `extract should find variables from command template only`() {
        val config = StdioConfig(
            commandTemplate = listOf("node", "{{script}}", "--port", "{{port}}"),
        )

        val result = VariableExtractor.extract(config)

        assertEquals(2, result.size)

        val scriptVar = result.find { it.key == "script" }!!
        assertEquals(setOf(VariableExtractor.VariableLocation.COMMAND), scriptVar.locations)
        assertFalse(scriptVar.isConditional)

        val portVar = result.find { it.key == "port" }!!
        assertEquals(setOf(VariableExtractor.VariableLocation.COMMAND), portVar.locations)
        assertFalse(portVar.isConditional)
    }

    @Test
    fun `extract should find variables from environment template only`() {
        val config = StdioConfig(
            commandTemplate = listOf("node", "server.js"),
            envTemplate = mapOf(
                "API_KEY" to "{{apiKey}}",
                "DB_HOST" to "{{dbHost}}",
            ),
        )

        val result = VariableExtractor.extract(config)

        assertEquals(2, result.size)

        val apiKeyVar = result.find { it.key == "apiKey" }!!
        assertEquals(setOf(VariableExtractor.VariableLocation.ENVIRONMENT), apiKeyVar.locations)

        val dbHostVar = result.find { it.key == "dbHost" }!!
        assertEquals(setOf(VariableExtractor.VariableLocation.ENVIRONMENT), dbHostVar.locations)
    }

    @Test
    fun `extract should find variables from working directory`() {
        val config = StdioConfig(
            commandTemplate = listOf("node", "server.js"),
            workingDirectory = "{{projectRoot}}/scripts",
        )

        val result = VariableExtractor.extract(config)

        assertEquals(1, result.size)
        val projectRootVar = result.first()
        assertEquals("projectRoot", projectRootVar.key)
        assertEquals(setOf(VariableExtractor.VariableLocation.WORKING_DIR), projectRootVar.locations)
    }

    @Test
    fun `extract should identify conditional variables`() {
        val config = StdioConfig(
            commandTemplate = listOf(
                "node",
                "server.js",
                "{{?debug:--verbose}}",
                "{{?production:--prod}}",
            ),
        )

        val result = VariableExtractor.extract(config)

        assertEquals(2, result.size)
        assertTrue(result.all { it.isConditional })
    }

    @Test
    fun `extract should track variable in multiple locations`() {
        val config = StdioConfig(
            commandTemplate = listOf("node", "{{script}}"),
            envTemplate = mapOf("SCRIPT_PATH" to "{{script}}"),
        )

        val result = VariableExtractor.extract(config)

        assertEquals(1, result.size)
        val scriptVar = result.first()
        assertEquals("script", scriptVar.key)
        assertEquals(
            setOf(
                VariableExtractor.VariableLocation.COMMAND,
                VariableExtractor.VariableLocation.ENVIRONMENT,
            ),
            scriptVar.locations,
        )
    }

    @Test
    fun `extract should handle empty config`() {
        val config = StdioConfig(
            commandTemplate = listOf("node", "server.js"),
        )

        val result = VariableExtractor.extract(config)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extract should return sorted results by key`() {
        val config = StdioConfig(
            commandTemplate = listOf("{{zebra}}", "{{apple}}", "{{monkey}}"),
        )

        val result = VariableExtractor.extract(config)

        assertEquals(listOf("apple", "monkey", "zebra"), result.map { it.key })
    }

    // ==================== Real-world Scenario Tests ====================

    @Test
    fun `extract MongoDB MCP server variables`() {
        val config = StdioConfig(
            commandTemplate = listOf(
                "npx",
                "-y",
                "mongodb-mcp-server@latest",
                "{{?readOnly:--readOnly}}",
            ),
            envTemplate = mapOf(
                "MONGODB_VARS" to "{{mongoEnv}}",
            ),
        )

        val result = VariableExtractor.extract(config)

        assertEquals(2, result.size)

        val mongoEnvVar = result.find { it.key == "mongoEnv" }!!
        assertEquals(setOf(VariableExtractor.VariableLocation.ENVIRONMENT), mongoEnvVar.locations)
        assertFalse(mongoEnvVar.isConditional)

        val readOnlyVar = result.find { it.key == "readOnly" }!!
        assertEquals(setOf(VariableExtractor.VariableLocation.COMMAND), readOnlyVar.locations)
        assertTrue(readOnlyVar.isConditional)
    }

    @Test
    fun `extract Filesystem MCP server variables`() {
        val config = StdioConfig(
            commandTemplate = listOf(
                "npx",
                "-y",
                "@modelcontextprotocol/server-filesystem",
                "{{rootPath}}",
            ),
        )

        val result = VariableExtractor.extract(config)

        assertEquals(1, result.size)
        val rootPathVar = result.first()
        assertEquals("rootPath", rootPathVar.key)
        assertEquals(setOf(VariableExtractor.VariableLocation.COMMAND), rootPathVar.locations)
        assertFalse(rootPathVar.isConditional)
    }

    @Test
    fun `extract complex server with multiple variable types`() {
        val config = StdioConfig(
            commandTemplate = listOf(
                "docker", "run",
                "{{?detached:-d}}",
                "--name", "{{containerName}}",
                "-p", "{{port}}:8080",
                "{{?volume:-v}}",
                "{{volumePath}}",
                "{{image}}",
            ),
            envTemplate = mapOf(
                "API_KEY" to "{{apiKey}}",
                "DB_CONNECTION" to "postgres://{{dbHost}}:{{dbPort}}/{{dbName}}",
            ),
            workingDirectory = "{{projectRoot}}/docker",
        )

        val result = VariableExtractor.extract(config)

        // Should find: detached, containerName, port, volume, volumePath, image,
        //              apiKey, dbHost, dbPort, dbName, projectRoot
        assertEquals(11, result.size)

        // Check conditional variables
        val conditionalVars = result.filter { it.isConditional }.map { it.key }
        assertTrue(conditionalVars.containsAll(listOf("detached", "volume")))

        // Check multi-location variables (if any)
        result.forEach { variable ->
            when (variable.key) {
                "detached", "containerName", "port", "volume", "volumePath", "image" ->
                    assertTrue(variable.locations.contains(VariableExtractor.VariableLocation.COMMAND))
                "apiKey", "dbHost", "dbPort", "dbName" ->
                    assertTrue(variable.locations.contains(VariableExtractor.VariableLocation.ENVIRONMENT))
                "projectRoot" ->
                    assertTrue(variable.locations.contains(VariableExtractor.VariableLocation.WORKING_DIR))
            }
        }
    }

    @Test
    fun `resolve extension should produce resolved config`() {
        val config = StdioConfig(
            commandTemplate = listOf("node", "{{script}}", "{{?debug:--verbose}}"),
            envTemplate = mapOf("API_KEY" to "{{apiKey}}"),
            workingDirectory = "{{projectRoot}}",
        )

        val variables = mapOf(
            "script" to "server.js",
            "debug" to "true",
            "apiKey" to "secret123",
            "projectRoot" to "/home/user/project",
        )

        val resolved = config.resolve(variables)

        assertEquals(listOf("node", "server.js", "--verbose"), resolved.command)
        assertEquals(mapOf("API_KEY" to "secret123"), resolved.environment)
        assertEquals("/home/user/project", resolved.workingDirectory)
    }

    @Test
    fun `resolve extension should handle missing optional variables`() {
        val config = StdioConfig(
            commandTemplate = listOf("node", "{{script}}", "{{?debug:--verbose}}"),
        )

        val variables = mapOf("script" to "server.js")

        val resolved = config.resolve(variables)

        // debug is conditional and missing, so --verbose should not appear
        assertEquals(listOf("node", "server.js"), resolved.command)
    }

    @Test
    fun `resolve extension should filter out blank working directory`() {
        val config = StdioConfig(
            commandTemplate = listOf("node", "server.js"),
            workingDirectory = "{{workDir}}",
        )

        val variables = mapOf("workDir" to "")

        val resolved = config.resolve(variables)

        // Blank working directory should become null
        assertEquals(null, resolved.workingDirectory)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `extract should handle malformed placeholders gracefully`() {
        val config = StdioConfig(
            commandTemplate = listOf("node", "{{incomplete", "{{}}"),
        )

        // Should not crash, just ignore malformed placeholders
        val result = VariableExtractor.extract(config)

        // Empty placeholder {{}} should be ignored
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extract should handle nested braces in conditional value`() {
        val config = StdioConfig(
            commandTemplate = listOf("{{?config:--config={value}}}"),
        )

        val result = VariableExtractor.extract(config)

        assertEquals(1, result.size)
        assertEquals("config", result.first().key)
    }

    @Test
    fun `extractFromString should handle unicode characters`() {
        val result = VariableExtractor.extractFromString("Hello {{名前}}")
        assertEquals(listOf("名前"), result)
    }

    @Test
    fun `extract should handle very long variable names`() {
        val longVarName = "a".repeat(100)
        val config = StdioConfig(
            commandTemplate = listOf("node", "{{$longVarName}}"),
        )

        val result = VariableExtractor.extract(config)

        assertEquals(1, result.size)
        assertEquals(longVarName, result.first().key)
    }
}
