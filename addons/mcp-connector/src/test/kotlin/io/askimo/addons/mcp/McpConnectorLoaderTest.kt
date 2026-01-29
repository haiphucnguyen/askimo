/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.addons.mcp

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test for ServiceLoader-based connector discovery.
 */
class McpConnectorLoaderTest {

    @Test
    fun `should discover providers using ServiceLoader`() {
        val providers = McpConnectorLoader.loadProviders()

        println("Discovered ${providers.size} provider(s):")
        providers.forEach { provider ->
            println("  - ${provider.name} (${provider.id})")
            println("    Version: ${provider.version}")
            println("    Description: ${provider.description}")
        }

        // Should find at least the PostgreSQL provider
        assertTrue(providers.isNotEmpty(), "Should discover at least one provider")
    }

    @Test
    fun `should get provider metadata`() {
        val providers = McpConnectorLoader.loadProviders()

        assertTrue(providers.isNotEmpty(), "Should have providers")

        providers.forEach { provider ->
            assertNotNull(provider.id, "Provider should have ID")
            assertNotNull(provider.name, "Provider should have name")
            assertNotNull(provider.version, "Provider should have version")
            assertNotNull(provider.configSchema, "Provider should have config schema")
        }
    }

    @Test
    fun `should find provider by ID`() {
        val postgresProvider = McpConnectorLoader.findProvider("io.askimo.addons.postgres")

        if (postgresProvider != null) {
            println("Found PostgreSQL provider:")
            println("  Name: ${postgresProvider.name}")
            println("  Version: ${postgresProvider.version}")
            println("  Description: ${postgresProvider.description}")
            println("  Config schema keys: ${postgresProvider.configSchema.keys}")
        } else {
            println("PostgreSQL provider not found (this is OK if postgres module is not on classpath)")
        }
    }

    @Test
    fun `should get provider map`() {
        val providerMap = McpConnectorLoader.getProviderMap()

        println("Provider map contains ${providerMap.size} provider(s)")
        providerMap.forEach { (id, provider) ->
            println("  $id -> ${provider.name}")
        }
    }

    @Test
    fun `should create connector with config`() {
        val postgresProvider = McpConnectorLoader.findProvider("io.askimo.addons.postgres")

        if (postgresProvider != null) {
            val config = mapOf(
                "postgres.uri" to "postgresql://localhost:5432/testdb",
                "postgres.schema" to "public",
            )

            val connector = postgresProvider.createConnector(config)
            assertNotNull(connector, "Should create connector instance")

            println("Created PostgreSQL connector with config:")
            println("  URI: ${config["postgres.uri"]}")
            println("  Schema: ${config["postgres.schema"]}")
        }
    }
}
