/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import io.askimo.core.mcp.config.McpServersConfig
import io.askimo.core.security.SecureKeyManager
import io.askimo.test.extensions.AskimoTestHome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AskimoTestHome
class ServerDefinitionSecretManagerTest {

    @AfterEach
    fun cleanup() {
        // Clean up any test secrets created during tests
        listOf(
            "askimo.mcp.server.test-http-server.header.Authorization",
            "askimo.mcp.server.test-http-server.header.X-API-Key",
            "askimo.mcp.server.test-stdio-server.env.GITHUB_TOKEN",
            "askimo.mcp.server.test-stdio-server.env.DATABASE_PASSWORD",
            "askimo.mcp.server.test-mixed-server.header.Authorization",
        ).forEach { key ->
            SecureKeyManager.removeSecretKey(key)
        }
    }

    // ── HTTP Transport Tests ─────────────────────────────────────────────

    @Test
    fun `protectSecrets detects and protects HTTP header secrets`() {
        val definition = McpServerDefinition(
            id = "test-http-server",
            name = "Test HTTP Server",
            description = "Test server with secrets",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "Authorization" to "Bearer secret-token-12345",
                    "X-API-Key" to "sk-test-api-key-67890",
                    "Content-Type" to "application/json",
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        // Should protect 2 secrets (Authorization and X-API-Key)
        assertEquals(2, secretCount, "Expected 2 secrets to be protected")

        // Check Authorization is replaced with reference
        val authHeader = protectedDef.httpConfig?.headersTemplate?.get("Authorization")
        assertTrue(
            ServerDefinitionSecretManager.isSecretReference(authHeader ?: ""),
            "Authorization should be a secret reference",
        )
        assertTrue(
            authHeader?.contains("askimo.mcp.server.test-http-server.header.Authorization") == true,
            "Authorization reference should contain proper key",
        )

        // Check X-API-Key is replaced with reference
        val apiKeyHeader = protectedDef.httpConfig?.headersTemplate?.get("X-API-Key")
        assertTrue(
            ServerDefinitionSecretManager.isSecretReference(apiKeyHeader ?: ""),
            "X-API-Key should be a secret reference",
        )
        assertTrue(
            apiKeyHeader?.contains("askimo.mcp.server.test-http-server.header.X-API-Key") == true,
            "X-API-Key reference should contain proper key",
        )

        // Check Content-Type is NOT replaced (not a secret)
        val contentType = protectedDef.httpConfig?.headersTemplate?.get("Content-Type")
        assertFalse(
            ServerDefinitionSecretManager.isSecretReference(contentType ?: ""),
            "Content-Type should not be a secret reference",
        )
        assertEquals("application/json", contentType, "Content-Type should remain unchanged")

        // Verify secrets are stored in SecureKeyManager
        val retrievedAuth = SecureKeyManager.retrieveSecretKey("askimo.mcp.server.test-http-server.header.Authorization")
        assertEquals("Bearer secret-token-12345", retrievedAuth, "Authorization secret should be retrievable")

        val retrievedApiKey = SecureKeyManager.retrieveSecretKey("askimo.mcp.server.test-http-server.header.X-API-Key")
        assertEquals("sk-test-api-key-67890", retrievedApiKey, "X-API-Key secret should be retrievable")
    }

    @Test
    fun `protectSecrets handles HTTP server with no secrets`() {
        val definition = McpServerDefinition(
            id = "test-http-no-secrets",
            name = "Test HTTP Server",
            description = "Test server without secrets",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                    "User-Agent" to "Askimo/1.0",
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        // Should protect 0 secrets
        assertEquals(0, secretCount, "Expected no secrets to be protected")

        // All headers should remain unchanged
        assertEquals("application/json", protectedDef.httpConfig?.headersTemplate?.get("Content-Type"))
        assertEquals("application/json", protectedDef.httpConfig?.headersTemplate?.get("Accept"))
        assertEquals("Askimo/1.0", protectedDef.httpConfig?.headersTemplate?.get("User-Agent"))
    }

    // ── STDIO Transport Tests ────────────────────────────────────────────

    @Test
    fun `protectSecrets detects and protects STDIO env var secrets`() {
        val definition = McpServerDefinition(
            id = "test-stdio-server",
            name = "Test STDIO Server",
            description = "Test server with env secrets",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("node", "server.js"),
                envTemplate = mapOf(
                    "GITHUB_TOKEN" to "ghp_1234567890abcdefghij",
                    "DATABASE_PASSWORD" to "super-secret-password",
                    "NODE_ENV" to "production",
                ),
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        // Should protect 2 secrets (GITHUB_TOKEN and DATABASE_PASSWORD)
        assertEquals(2, secretCount, "Expected 2 secrets to be protected")

        // Check GITHUB_TOKEN is replaced with reference
        val githubToken = protectedDef.stdioConfig?.envTemplate?.get("GITHUB_TOKEN")
        assertTrue(
            ServerDefinitionSecretManager.isSecretReference(githubToken ?: ""),
            "GITHUB_TOKEN should be a secret reference",
        )

        // Check DATABASE_PASSWORD is replaced with reference
        val dbPassword = protectedDef.stdioConfig?.envTemplate?.get("DATABASE_PASSWORD")
        assertTrue(
            ServerDefinitionSecretManager.isSecretReference(dbPassword ?: ""),
            "DATABASE_PASSWORD should be a secret reference",
        )

        // Check NODE_ENV is NOT replaced (not a secret)
        val nodeEnv = protectedDef.stdioConfig?.envTemplate?.get("NODE_ENV")
        assertFalse(
            ServerDefinitionSecretManager.isSecretReference(nodeEnv ?: ""),
            "NODE_ENV should not be a secret reference",
        )
        assertEquals("production", nodeEnv, "NODE_ENV should remain unchanged")

        // Verify secrets are stored in SecureKeyManager
        val retrievedToken = SecureKeyManager.retrieveSecretKey("askimo.mcp.server.test-stdio-server.env.GITHUB_TOKEN")
        assertEquals("ghp_1234567890abcdefghij", retrievedToken)

        val retrievedPassword = SecureKeyManager.retrieveSecretKey("askimo.mcp.server.test-stdio-server.env.DATABASE_PASSWORD")
        assertEquals("super-secret-password", retrievedPassword)
    }

    @Test
    fun `protectSecrets handles STDIO server with no secrets`() {
        val definition = McpServerDefinition(
            id = "test-stdio-no-secrets",
            name = "Test STDIO Server",
            description = "Test server without secrets",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("node", "server.js"),
                envTemplate = mapOf(
                    "NODE_ENV" to "production",
                    "PORT" to "3000",
                    "LOG_LEVEL" to "info",
                ),
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        // Should protect 0 secrets
        assertEquals(0, secretCount, "Expected no secrets to be protected")

        // All env vars should remain unchanged
        assertEquals("production", protectedDef.stdioConfig?.envTemplate?.get("NODE_ENV"))
        assertEquals("3000", protectedDef.stdioConfig?.envTemplate?.get("PORT"))
        assertEquals("info", protectedDef.stdioConfig?.envTemplate?.get("LOG_LEVEL"))
    }

    // ── Secret Reference Detection Tests ─────────────────────────────────

    @Test
    fun `isSecretReference correctly identifies secret references`() {
        assertTrue(
            ServerDefinitionSecretManager.isSecretReference("{{secret:askimo.mcp.server.abc.header.Auth}}"),
            "Should detect valid secret reference",
        )

        assertFalse(
            ServerDefinitionSecretManager.isSecretReference("Bearer token-12345"),
            "Should not detect plain value as reference",
        )

        assertFalse(
            ServerDefinitionSecretManager.isSecretReference("{{secret:incomplete"),
            "Should not detect incomplete reference",
        )

        assertFalse(
            ServerDefinitionSecretManager.isSecretReference("incomplete}}"),
            "Should not detect incomplete reference",
        )

        assertFalse(
            ServerDefinitionSecretManager.isSecretReference(""),
            "Should not detect empty string as reference",
        )
    }

    // ── Already Protected Secrets Tests ──────────────────────────────────

    @Test
    fun `protectSecrets preserves existing secret references`() {
        // Simulate a definition that already has protected secrets
        val existingReference = "{{secret:askimo.mcp.server.test-mixed-server.header.Authorization}}"

        val definition = McpServerDefinition(
            id = "test-mixed-server",
            name = "Test Mixed Server",
            description = "Server with mix of protected and unprotected secrets",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "Authorization" to existingReference, // Already protected
                    "X-New-Secret" to "new-secret-value", // Needs protection
                    "Content-Type" to "application/json", // Not a secret
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        // Should only protect 1 new secret (X-New-Secret)
        assertEquals(1, secretCount, "Expected only 1 new secret to be protected")

        // Existing reference should be preserved
        assertEquals(
            existingReference,
            protectedDef.httpConfig?.headersTemplate?.get("Authorization"),
            "Existing secret reference should be preserved",
        )

        // New secret should be protected
        val newSecretRef = protectedDef.httpConfig?.headersTemplate?.get("X-New-Secret")
        assertTrue(
            ServerDefinitionSecretManager.isSecretReference(newSecretRef ?: ""),
            "X-New-Secret should be protected with reference",
        )

        // Content-Type should remain unchanged
        assertEquals("application/json", protectedDef.httpConfig?.headersTemplate?.get("Content-Type"))
    }

    // ── Resolve Secrets Tests ────────────────────────────────────────────

    @Test
    fun `resolveSecrets replaces references with actual values for HTTP`() {
        // First protect secrets
        val definition = McpServerDefinition(
            id = "test-resolve-http",
            name = "Test Resolve HTTP",
            description = "Test resolving secrets",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "Authorization" to "Bearer test-token",
                    "Content-Type" to "application/json",
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, _) = ServerDefinitionSecretManager.protectSecrets(definition)

        // Now resolve the secrets
        val resolvedDef = ServerDefinitionSecretManager.resolveSecrets(protectedDef)

        // Authorization should be resolved back to original value
        assertEquals(
            "Bearer test-token",
            resolvedDef.httpConfig?.headersTemplate?.get("Authorization"),
            "Authorization should be resolved to original value",
        )

        // Content-Type should remain unchanged
        assertEquals("application/json", resolvedDef.httpConfig?.headersTemplate?.get("Content-Type"))

        // Cleanup
        SecureKeyManager.removeSecretKey("askimo.mcp.server.test-resolve-http.header.Authorization")
    }

    @Test
    fun `resolveSecrets replaces references with actual values for STDIO`() {
        // First protect secrets
        val definition = McpServerDefinition(
            id = "test-resolve-stdio",
            name = "Test Resolve STDIO",
            description = "Test resolving secrets",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("node", "server.js"),
                envTemplate = mapOf(
                    "API_TOKEN" to "secret-token-xyz",
                    "NODE_ENV" to "production",
                ),
            ),
            tags = listOf("global"),
        )

        val (protectedDef, _) = ServerDefinitionSecretManager.protectSecrets(definition)

        // Now resolve the secrets
        val resolvedDef = ServerDefinitionSecretManager.resolveSecrets(protectedDef)

        // API_TOKEN should be resolved back to original value
        assertEquals(
            "secret-token-xyz",
            resolvedDef.stdioConfig?.envTemplate?.get("API_TOKEN"),
            "API_TOKEN should be resolved to original value",
        )

        // NODE_ENV should remain unchanged
        assertEquals("production", resolvedDef.stdioConfig?.envTemplate?.get("NODE_ENV"))

        // Cleanup
        SecureKeyManager.removeSecretKey("askimo.mcp.server.test-resolve-stdio.env.API_TOKEN")
    }

    @Test
    fun `resolveSecrets handles missing secrets gracefully`() {
        // Create a definition with a reference to a non-existent secret
        val definition = McpServerDefinition(
            id = "test-missing-secret",
            name = "Test Missing Secret",
            description = "Test with missing secret",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "Authorization" to "{{secret:askimo.mcp.server.nonexistent.header.Auth}}",
                    "Content-Type" to "application/json",
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val resolvedDef = ServerDefinitionSecretManager.resolveSecrets(definition)

        // Should return empty string for missing secret (graceful degradation)
        assertEquals(
            "",
            resolvedDef.httpConfig?.headersTemplate?.get("Authorization"),
            "Missing secret should resolve to empty string",
        )

        // Non-secret should remain unchanged
        assertEquals("application/json", resolvedDef.httpConfig?.headersTemplate?.get("Content-Type"))
    }

    // ── Edge Cases Tests ─────────────────────────────────────────────────

    @Test
    fun `protectSecrets handles empty headers`() {
        val definition = McpServerDefinition(
            id = "test-empty-headers",
            name = "Test Empty Headers",
            description = "Test with empty headers",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = emptyMap(),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        assertEquals(0, secretCount, "Expected no secrets for empty headers")
        assertTrue(protectedDef.httpConfig?.headersTemplate?.isEmpty() == true, "Headers should remain empty")
    }

    @Test
    fun `protectSecrets handles empty env vars`() {
        val definition = McpServerDefinition(
            id = "test-empty-env",
            name = "Test Empty Env",
            description = "Test with empty env",
            transportType = TransportType.STDIO,
            stdioConfig = StdioConfig(
                commandTemplate = listOf("node", "server.js"),
                envTemplate = emptyMap(),
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        assertEquals(0, secretCount, "Expected no secrets for empty env")
        assertTrue(protectedDef.stdioConfig?.envTemplate?.isEmpty() == true, "Env vars should remain empty")
    }

    @Test
    fun `protectSecrets handles various secret keyword patterns`() {
        val definition = McpServerDefinition(
            id = "test-patterns",
            name = "Test Secret Patterns",
            description = "Test various secret naming patterns",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "Authorization" to "Bearer token1", // Contains "auth"
                    "X-API-Key" to "key1", // Contains "key"
                    "Bearer-Token" to "token2", // Contains "bearer" and "token"
                    "Access-Token" to "token3", // Contains "access_token"
                    "Private-Key" to "key2", // Contains "private" and "key"
                    "GITHUB_PAT" to "pat1", // Contains "pat" as segment
                    "Secret-Value" to "value1", // Contains "secret"
                    "User-Password" to "pass1", // Contains "password"
                    "Credentials" to "creds1", // Contains "credential"
                    "Regular-Header" to "value2", // No secret keywords
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        // Should detect 9 secrets (all except Regular-Header)
        assertEquals(9, secretCount, "Expected 9 secrets to be detected")

        // Verify all secrets are protected
        val headers = protectedDef.httpConfig?.headersTemplate ?: emptyMap()
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["Authorization"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["X-API-Key"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["Bearer-Token"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["Access-Token"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["Private-Key"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["GITHUB_PAT"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["Secret-Value"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["User-Password"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["Credentials"] ?: ""))

        // Regular-Header should NOT be protected
        assertFalse(ServerDefinitionSecretManager.isSecretReference(headers["Regular-Header"] ?: ""))
        assertEquals("value2", headers["Regular-Header"])

        // Cleanup all test secrets
        listOf(
            "Authorization", "X-API-Key", "Bearer-Token", "Access-Token",
            "Private-Key", "GITHUB_PAT", "Secret-Value", "User-Password", "Credentials",
        ).forEach { headerName ->
            SecureKeyManager.removeSecretKey("askimo.mcp.server.test-patterns.header.$headerName")
        }
    }

    // ── Round-trip Tests ─────────────────────────────────────────────────

    @Test
    fun `protect and resolve round-trip maintains original values`() {
        val original = McpServerDefinition(
            id = "test-roundtrip",
            name = "Test Roundtrip",
            description = "Test protect/resolve cycle",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "Authorization" to "Bearer original-token-12345",
                    "X-Custom-Secret" to "custom-secret-value",
                    "Content-Type" to "application/json",
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        // Protect secrets
        val (protected, secretCount) = ServerDefinitionSecretManager.protectSecrets(original)
        assertEquals(2, secretCount)

        // Resolve secrets
        val resolved = ServerDefinitionSecretManager.resolveSecrets(protected)

        // Verify values match original
        assertEquals(
            original.httpConfig?.headersTemplate?.get("Authorization"),
            resolved.httpConfig?.headersTemplate?.get("Authorization"),
            "Authorization should match original after round-trip",
        )
        assertEquals(
            original.httpConfig?.headersTemplate?.get("X-Custom-Secret"),
            resolved.httpConfig?.headersTemplate?.get("X-Custom-Secret"),
            "X-Custom-Secret should match original after round-trip",
        )
        assertEquals(
            original.httpConfig?.headersTemplate?.get("Content-Type"),
            resolved.httpConfig?.headersTemplate?.get("Content-Type"),
            "Content-Type should match original after round-trip",
        )

        // Cleanup
        SecureKeyManager.removeSecretKey("askimo.mcp.server.test-roundtrip.header.Authorization")
        SecureKeyManager.removeSecretKey("askimo.mcp.server.test-roundtrip.header.X-Custom-Secret")
    }

    // ── Cleanup Tests ────────────────────────────────────────────────────

    @Test
    fun `cleanupSecrets removes all secrets for a server`() {
        // Create and protect a definition
        val definition = McpServerDefinition(
            id = "test-cleanup",
            name = "Test Cleanup",
            description = "Test secret cleanup",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "Authorization" to "Bearer cleanup-token",
                    "X-API-Key" to "cleanup-api-key",
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)
        assertEquals(2, secretCount)

        // Verify secrets are stored
        val authKey = "askimo.mcp.server.test-cleanup.header.Authorization"
        val apiKeyKey = "askimo.mcp.server.test-cleanup.header.X-API-Key"

        assertEquals("Bearer cleanup-token", SecureKeyManager.retrieveSecretKey(authKey))
        assertEquals("cleanup-api-key", SecureKeyManager.retrieveSecretKey(apiKeyKey))

        // Add to McpServersConfig so cleanupSecrets can load it
        McpServersConfig.add(protectedDef)

        // Clean up secrets
        ServerDefinitionSecretManager.cleanupSecrets("test-cleanup")

        // Verify secrets are removed
        assertEquals(null, SecureKeyManager.retrieveSecretKey(authKey), "Authorization secret should be removed")
        assertEquals(null, SecureKeyManager.retrieveSecretKey(apiKeyKey), "X-API-Key secret should be removed")

        // Cleanup test definition
        McpServersConfig.remove("test-cleanup")
    }

    @Test
    fun `cleanupSecrets handles non-existent server gracefully`() {
        // Should not throw exception for non-existent server
        ServerDefinitionSecretManager.cleanupSecrets("non-existent-server-id")
        // Test passes if no exception thrown
    }

    // ── Secret Key Format Tests ──────────────────────────────────────────

    @Test
    fun `secret key format matches expected namespace pattern`() {
        val definition = McpServerDefinition(
            id = "test-key-format",
            name = "Test Key Format",
            description = "Test secret key formatting",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "X-Custom-Auth" to "test-value",
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, _) = ServerDefinitionSecretManager.protectSecrets(definition)

        val reference = protectedDef.httpConfig?.headersTemplate?.get("X-Custom-Auth") ?: ""
        assertTrue(reference.contains("askimo.mcp.server.test-key-format.header.X-Custom-Auth"))

        // Verify the key format structure: askimo.mcp.server.{id}.{type}.{name}
        val parts = reference
            .removePrefix("{{secret:")
            .removeSuffix("}}")
            .split(".")

        assertEquals("askimo", parts[0], "First part should be 'askimo'")
        assertEquals("mcp", parts[1], "Second part should be 'mcp'")
        assertEquals("server", parts[2], "Third part should be 'server'")
        assertEquals("test-key-format", parts[3], "Fourth part should be server ID")
        assertEquals("header", parts[4], "Fifth part should be type (header/env)")
        assertEquals("X-Custom-Auth", parts[5], "Sixth part should be header name")

        // Cleanup
        SecureKeyManager.removeSecretKey("askimo.mcp.server.test-key-format.header.X-Custom-Auth")
    }

    // ── Case Sensitivity Tests ───────────────────────────────────────────

    @Test
    fun `protectSecrets is case-insensitive for secret detection`() {
        val definition = McpServerDefinition(
            id = "test-case-sensitivity",
            name = "Test Case Sensitivity",
            description = "Test case-insensitive detection",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "authorization" to "lowercase-auth", // lowercase
                    "AUTHORIZATION" to "uppercase-auth", // uppercase
                    "Authorization" to "mixedcase-auth", // mixed case
                    "x-api-key" to "lowercase-key", // lowercase with hyphens
                    "X-API-KEY" to "uppercase-key", // uppercase with hyphens
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        // All 5 should be detected as secrets
        assertEquals(5, secretCount, "All case variations should be detected as secrets")

        val headers = protectedDef.httpConfig?.headersTemplate ?: emptyMap()
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["authorization"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["AUTHORIZATION"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["Authorization"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["x-api-key"] ?: ""))
        assertTrue(ServerDefinitionSecretManager.isSecretReference(headers["X-API-KEY"] ?: ""))

        // Cleanup
        listOf("authorization", "AUTHORIZATION", "Authorization", "x-api-key", "X-API-KEY").forEach { headerName ->
            SecureKeyManager.removeSecretKey("askimo.mcp.server.test-case-sensitivity.header.$headerName")
        }
    }

    // ── Empty/Null Value Tests ───────────────────────────────────────────

    @Test
    fun `protectSecrets handles empty secret values`() {
        val definition = McpServerDefinition(
            id = "test-empty-values",
            name = "Test Empty Values",
            description = "Test with empty secret values",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf(
                    "Authorization" to "", // Empty value but secret key
                    "Content-Type" to "application/json",
                ),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        val (protectedDef, secretCount) = ServerDefinitionSecretManager.protectSecrets(definition)

        // Should still protect even if empty (user might fill later)
        assertEquals(1, secretCount, "Empty secret value should still be protected")

        val authRef = protectedDef.httpConfig?.headersTemplate?.get("Authorization")
        assertTrue(ServerDefinitionSecretManager.isSecretReference(authRef ?: ""))

        // Verify empty value is stored
        val retrieved = SecureKeyManager.retrieveSecretKey("askimo.mcp.server.test-empty-values.header.Authorization")
        assertEquals("", retrieved, "Empty secret should be retrievable")

        // Cleanup
        SecureKeyManager.removeSecretKey("askimo.mcp.server.test-empty-values.header.Authorization")
    }
}
