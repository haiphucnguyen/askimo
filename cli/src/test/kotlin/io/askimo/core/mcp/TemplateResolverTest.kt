/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class TemplateResolverTest {

    // ==================== Simple Placeholder Tests ====================

    @Test
    fun `resolve simple placeholder with value`() {
        val resolver = TemplateResolver(mapOf("name" to "John"))
        val result = resolver.resolve("Hello {{name}}")
        assertEquals("Hello John", result)
    }

    @Test
    fun `resolve simple placeholder without value returns empty`() {
        val resolver = TemplateResolver(emptyMap())
        val result = resolver.resolve("Hello {{name}}")
        assertEquals("Hello ", result)
    }

    @Test
    fun `resolve multiple simple placeholders`() {
        val resolver = TemplateResolver(
            mapOf(
                "firstName" to "John",
                "lastName" to "Doe",
                "age" to "30",
            ),
        )
        val result = resolver.resolve("{{firstName}} {{lastName}} is {{age}} years old")
        assertEquals("John Doe is 30 years old", result)
    }

    @Test
    fun `resolve same placeholder multiple times`() {
        val resolver = TemplateResolver(mapOf("name" to "Alice"))
        val result = resolver.resolve("{{name}} loves {{name}}")
        assertEquals("Alice loves Alice", result)
    }

    @Test
    fun `resolve placeholder with numeric value`() {
        val resolver = TemplateResolver(mapOf("port" to "8080"))
        val result = resolver.resolve("Server running on port {{port}}")
        assertEquals("Server running on port 8080", result)
    }

    @Test
    fun `resolve placeholder with special characters in value`() {
        val resolver = TemplateResolver(mapOf("url" to "https://example.com/path?query=value"))
        val result = resolver.resolve("Visit {{url}}")
        assertEquals("Visit https://example.com/path?query=value", result)
    }

    @Test
    fun `resolve placeholder at start of string`() {
        val resolver = TemplateResolver(mapOf("prefix" to "ERROR"))
        val result = resolver.resolve("{{prefix}}: Something went wrong")
        assertEquals("ERROR: Something went wrong", result)
    }

    @Test
    fun `resolve placeholder at end of string`() {
        val resolver = TemplateResolver(mapOf("suffix" to "END"))
        val result = resolver.resolve("This is the {{suffix}}")
        assertEquals("This is the END", result)
    }

    @Test
    fun `resolve empty template returns empty string`() {
        val resolver = TemplateResolver(mapOf("key" to "value"))
        val result = resolver.resolve("")
        assertEquals("", result)
    }

    @Test
    fun `resolve template with no placeholders returns original`() {
        val resolver = TemplateResolver(mapOf("key" to "value"))
        val result = resolver.resolve("Plain text with no placeholders")
        assertEquals("Plain text with no placeholders", result)
    }

    // ==================== Conditional Placeholder Tests ====================

    @Test
    fun `resolve conditional placeholder when true`() {
        val resolver = TemplateResolver(mapOf("readOnly" to "true"))
        val result = resolver.resolve("docker run {{?readOnly:--read-only}}")
        assertEquals("docker run --read-only", result)
    }

    @Test
    fun `resolve conditional placeholder when false`() {
        val resolver = TemplateResolver(mapOf("readOnly" to "false"))
        val result = resolver.resolve("docker run {{?readOnly:--read-only}}")
        assertEquals("docker run ", result)
    }

    @Test
    fun `resolve conditional placeholder when missing`() {
        val resolver = TemplateResolver(emptyMap())
        val result = resolver.resolve("docker run {{?readOnly:--read-only}}")
        assertEquals("docker run ", result)
    }

    @Test
    fun `resolve conditional placeholder with case insensitive true`() {
        val resolver = TemplateResolver(mapOf("flag" to "TRUE"))
        val result = resolver.resolve("{{?flag:--enabled}}")
        assertEquals("--enabled", result)
    }

    @Test
    fun `resolve conditional placeholder with True capitalized`() {
        val resolver = TemplateResolver(mapOf("flag" to "True"))
        val result = resolver.resolve("{{?flag:--enabled}}")
        assertEquals("--enabled", result)
    }

    @Test
    fun `resolve multiple conditional placeholders`() {
        val resolver = TemplateResolver(
            mapOf(
                "verbose" to "true",
                "debug" to "false",
                "quiet" to "true",
            ),
        )
        val result = resolver.resolve("cmd {{?verbose:-v}} {{?debug:-d}} {{?quiet:-q}}")
        assertEquals("cmd -v  -q", result)
    }

    @Test
    fun `resolve conditional placeholder with complex value`() {
        val resolver = TemplateResolver(mapOf("ssl" to "true"))
        val result = resolver.resolve("{{?ssl:--ssl-mode=REQUIRED}}")
        assertEquals("--ssl-mode=REQUIRED", result)
    }

    @Test
    fun `resolve conditional placeholder with spaces in value`() {
        val resolver = TemplateResolver(mapOf("log" to "true"))
        val result = resolver.resolve("{{?log:--log-level DEBUG}}")
        assertEquals("--log-level DEBUG", result)
    }

    @Test
    fun `resolve conditional placeholder with equals sign in value`() {
        val resolver = TemplateResolver(mapOf("config" to "true"))
        val result = resolver.resolve("{{?config:--config=production.yml}}")
        assertEquals("--config=production.yml", result)
    }

    // ==================== Mixed Placeholder Tests ====================

    @Test
    fun `resolve mix of simple and conditional placeholders`() {
        val resolver = TemplateResolver(
            mapOf(
                "host" to "localhost",
                "port" to "3000",
                "ssl" to "true",
            ),
        )
        val result = resolver.resolve("http://{{host}}:{{port}} {{?ssl:--secure}}")
        assertEquals("http://localhost:3000 --secure", result)
    }

    @Test
    fun `resolve complex command with multiple placeholder types`() {
        val resolver = TemplateResolver(
            mapOf(
                "image" to "mongo:latest",
                "name" to "my-mongo",
                "port" to "27017",
                "detached" to "true",
                "volume" to "true",
            ),
        )
        val result = resolver.resolve(
            "docker run {{?detached:-d}} --name {{name}} -p {{port}}:27017 {{?volume:-v data:/data/db}} {{image}}",
        )
        assertEquals("docker run -d --name my-mongo -p 27017:27017 -v data:/data/db mongo:latest", result)
    }

    // ==================== resolveList Tests ====================

    @Test
    fun `resolveList with all valid templates`() {
        val resolver = TemplateResolver(mapOf("host" to "localhost", "port" to "8080"))
        val templates = listOf("--host", "{{host}}", "--port", "{{port}}")
        val result = resolver.resolveList(templates)
        assertEquals(listOf("--host", "localhost", "--port", "8080"), result)
    }

    @Test
    fun `resolveList filters out empty results from missing values`() {
        val resolver = TemplateResolver(mapOf("host" to "localhost"))
        val templates = listOf("{{host}}", "{{port}}", "{{missing}}")
        val result = resolver.resolveList(templates)
        assertEquals(listOf("localhost"), result)
    }

    @Test
    fun `resolveList filters out empty results from false conditionals`() {
        val resolver = TemplateResolver(
            mapOf(
                "verbose" to "true",
                "debug" to "false",
            ),
        )
        val templates = listOf("{{?verbose:-v}}", "{{?debug:-d}}", "{{?missing:--flag}}")
        val result = resolver.resolveList(templates)
        assertEquals(listOf("-v"), result)
    }

    @Test
    fun `resolveList with mixed placeholder types`() {
        val resolver = TemplateResolver(
            mapOf(
                "command" to "run",
                "name" to "myapp",
                "detached" to "true",
                "interactive" to "false",
            ),
        )
        val templates = listOf(
            "docker",
            "{{command}}",
            "{{?detached:-d}}",
            "{{?interactive:-it}}",
            "--name",
            "{{name}}",
        )
        val result = resolver.resolveList(templates)
        assertEquals(listOf("docker", "run", "-d", "--name", "myapp"), result)
    }

    @Test
    fun `resolveList with empty list returns empty`() {
        val resolver = TemplateResolver(mapOf("key" to "value"))
        val result = resolver.resolveList(emptyList())
        assertEquals(emptyList<String>(), result)
    }

    // ==================== resolveMap Tests ====================

    @Test
    fun `resolveMap with simple key-value pairs`() {
        val resolver = TemplateResolver(
            mapOf(
                "dbHost" to "localhost",
                "dbPort" to "5432",
            ),
        )
        val templates = mapOf(
            "DATABASE_HOST" to "{{dbHost}}",
            "DATABASE_PORT" to "{{dbPort}}",
        )
        val result = resolver.resolveMap(templates)
        assertEquals(
            mapOf(
                "DATABASE_HOST" to "localhost",
                "DATABASE_PORT" to "5432",
            ),
            result,
        )
    }

    @Test
    fun `resolveMap filters out blank values`() {
        val resolver = TemplateResolver(mapOf("host" to "localhost"))
        val templates = mapOf(
            "HOST" to "{{host}}",
            "PORT" to "{{port}}",
            "MISSING" to "{{missing}}",
        )
        val result = resolver.resolveMap(templates)
        assertEquals(mapOf("HOST" to "localhost"), result)
    }

    @Test
    fun `resolveMap splits comma-separated KEY=value pairs`() {
        val resolver = TemplateResolver(
            mapOf(
                "env1" to "value1",
                "env2" to "value2",
            ),
        )
        val templates = mapOf(
            "ENV_VARS" to "KEY1={{env1}},KEY2={{env2}}",
        )
        val result = resolver.resolveMap(templates)
        assertEquals(
            mapOf(
                "KEY1" to "value1",
                "KEY2" to "value2",
            ),
            result,
        )
    }

    @Test
    fun `resolveMap handles comma-separated with spaces`() {
        val resolver = TemplateResolver(mapOf("a" to "1", "b" to "2", "c" to "3"))
        val templates = mapOf(
            "VARS" to "A={{a}}, B={{b}}, C={{c}}",
        )
        val result = resolver.resolveMap(templates)
        assertEquals(
            mapOf(
                "A" to "1",
                "B" to "2",
                "C" to "3",
            ),
            result,
        )
    }

    @Test
    fun `resolveMap with conditional placeholders`() {
        val resolver = TemplateResolver(
            mapOf(
                "prod" to "true",
                "debug" to "false",
                "host" to "prod.example.com",
            ),
        )
        val templates = mapOf(
            "HOST" to "{{host}}",
            "MODE" to "{{?prod:production}}",
            "DEBUG" to "{{?debug:true}}",
        )
        val result = resolver.resolveMap(templates)
        assertEquals(
            mapOf(
                "HOST" to "prod.example.com",
                "MODE" to "production",
            ),
            result,
        )
    }

    @Test
    fun `resolveMap with empty template map returns empty`() {
        val resolver = TemplateResolver(mapOf("key" to "value"))
        val result = resolver.resolveMap(emptyMap())
        assertEquals(emptyMap<String, String>(), result)
    }

    // ==================== Validation Tests ====================

    @Test
    fun `validate returns valid for correct simple placeholder`() {
        val result = TemplateResolver.validate("Hello {{name}}")
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validate returns valid for correct conditional placeholder`() {
        val result = TemplateResolver.validate("{{?flag:--enabled}}")
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validate returns valid for multiple placeholders`() {
        val result = TemplateResolver.validate("{{a}} {{b}} {{?c:value}}")
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validate detects unmatched opening braces`() {
        val result = TemplateResolver.validate("{{name")
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Unmatched opening braces") })
    }

    @Test
    fun `validate detects unmatched closing braces`() {
        val result = TemplateResolver.validate("name}}")
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Unmatched closing braces") })
    }

    @Test
    fun `validate detects empty placeholder`() {
        val result = TemplateResolver.validate("Hello {{}}")
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Empty placeholder") })
    }

    @Test
    fun `validate returns valid for nested braces in value`() {
        val result = TemplateResolver.validate("{{?flag:--config={value}}}")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate returns valid for plain text`() {
        val result = TemplateResolver.validate("Plain text with no placeholders")
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validate returns valid for empty string`() {
        val result = TemplateResolver.validate("")
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    // ==================== Parameter Extraction Tests ====================

    @Test
    fun `extractParameters finds simple placeholders`() {
        val params = TemplateResolver.extractParameters("{{host}} {{port}}")
        assertEquals(setOf("host", "port"), params)
    }

    @Test
    fun `extractParameters finds conditional placeholders`() {
        val params = TemplateResolver.extractParameters("{{?ssl:--secure}} {{?verbose:-v}}")
        assertEquals(setOf("ssl", "verbose"), params)
    }

    @Test
    fun `extractParameters finds both simple and conditional`() {
        val params = TemplateResolver.extractParameters("{{host}}:{{port}} {{?ssl:--secure}}")
        assertEquals(setOf("host", "port", "ssl"), params)
    }

    @Test
    fun `extractParameters handles duplicate parameters`() {
        val params = TemplateResolver.extractParameters("{{name}} loves {{name}}")
        assertEquals(setOf("name"), params)
    }

    @Test
    fun `extractParameters returns empty for plain text`() {
        val params = TemplateResolver.extractParameters("Plain text")
        assertTrue(params.isEmpty())
    }

    @Test
    fun `extractParameters returns empty for empty string`() {
        val params = TemplateResolver.extractParameters("")
        assertTrue(params.isEmpty())
    }

    @Test
    fun `extractParameters from complex template`() {
        val template = "docker run {{?detached:-d}} --name {{name}} -p {{port}}:27017 {{image}}"
        val params = TemplateResolver.extractParameters(template)
        assertEquals(setOf("detached", "name", "port", "image"), params)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `resolve handles adjacent placeholders`() {
        val resolver = TemplateResolver(mapOf("a" to "Hello", "b" to "World"))
        val result = resolver.resolve("{{a}}{{b}}")
        assertEquals("HelloWorld", result)
    }

    @Test
    fun `resolve handles placeholder with underscore`() {
        val resolver = TemplateResolver(mapOf("my_var" to "value"))
        val result = resolver.resolve("{{my_var}}")
        assertEquals("value", result)
    }

    @Test
    fun `resolve handles placeholder with numbers`() {
        val resolver = TemplateResolver(mapOf("var123" to "value"))
        val result = resolver.resolve("{{var123}}")
        assertEquals("value", result)
    }

    @Test
    fun `resolve preserves whitespace around placeholders`() {
        val resolver = TemplateResolver(mapOf("name" to "John"))
        val result = resolver.resolve("  {{name}}  ")
        assertEquals("  John  ", result)
    }

    @Test
    fun `resolve handles empty string value`() {
        val resolver = TemplateResolver(mapOf("key" to ""))
        val result = resolver.resolve("Value: {{key}}")
        assertEquals("Value: ", result)
    }

    @Test
    fun `resolve conditional with non-boolean value treats as false`() {
        val resolver = TemplateResolver(mapOf("flag" to "yes"))
        val result = resolver.resolve("{{?flag:--enabled}}")
        assertEquals("", result)
    }

    @Test
    fun `resolve conditional with empty string treats as false`() {
        val resolver = TemplateResolver(mapOf("flag" to ""))
        val result = resolver.resolve("{{?flag:--enabled}}")
        assertEquals("", result)
    }

    // ==================== Real-world Scenario Tests ====================

    @Test
    fun `resolve MongoDB connection string`() {
        val resolver = TemplateResolver(
            mapOf(
                "mongoUri" to "mongodb://localhost:27017/mydb",
                "readOnly" to "true",
            ),
        )
        val result = resolver.resolve("{{mongoUri}} {{?readOnly:--readOnly}}")
        assertEquals("mongodb://localhost:27017/mydb --readOnly", result)
    }

    @Test
    fun `resolve Docker command with all options`() {
        val resolver = TemplateResolver(
            mapOf(
                "detached" to "true",
                "name" to "web-server",
                "port" to "8080",
                "volume" to "true",
                "volumePath" to "/data:/usr/share/nginx/html",
                "image" to "nginx:latest",
            ),
        )
        val result = resolver.resolve(
            "docker run {{?detached:-d}} --name {{name}} -p {{port}}:80 {{?volume:-v}} {{volumePath}} {{image}}",
        )
        assertEquals(
            "docker run -d --name web-server -p 8080:80 -v /data:/usr/share/nginx/html nginx:latest",
            result,
        )
    }

    @Test
    fun `resolve environment variables for deployment`() {
        val resolver = TemplateResolver(
            mapOf(
                "env" to "production",
                "dbHost" to "db.prod.example.com",
                "dbPort" to "5432",
                "cacheEnabled" to "true",
            ),
        )
        val envVars = resolver.resolveMap(
            mapOf(
                "ENVIRONMENT" to "{{env}}",
                "DATABASE_HOST" to "{{dbHost}}",
                "DATABASE_PORT" to "{{dbPort}}",
                "CACHE_ENABLED" to "{{?cacheEnabled:true}}",
            ),
        )
        assertAll(
            { assertEquals("production", envVars["ENVIRONMENT"]) },
            { assertEquals("db.prod.example.com", envVars["DATABASE_HOST"]) },
            { assertEquals("5432", envVars["DATABASE_PORT"]) },
            { assertEquals("true", envVars["CACHE_ENABLED"]) },
        )
    }

    @Test
    fun `resolve command line arguments with optional flags`() {
        val resolver = TemplateResolver(
            mapOf(
                "config" to "/etc/app/config.yml",
                "verbose" to "true",
                "debug" to "false",
                "port" to "3000",
            ),
        )
        val args = resolver.resolveList(
            listOf(
                "--config",
                "{{config}}",
                "{{?verbose:--verbose}}",
                "{{?debug:--debug}}",
                "--port",
                "{{port}}",
            ),
        )
        assertEquals(
            listOf("--config", "/etc/app/config.yml", "--verbose", "--port", "3000"),
            args,
        )
    }
}
