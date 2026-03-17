/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.mcp

import io.askimo.core.mcp.config.GlobalMcpInstancesConfig
import io.askimo.core.mcp.config.McpServersConfig
import io.askimo.test.extensions.AskimoTestHome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AskimoTestHome
class GlobalMcpInstanceServiceTest {

    @Test
    fun `deleteInstance removes both instance and associated server definition`() {
        val service = GlobalMcpInstanceService()

        // Create a global server definition (simulating what AddGlobalMcpInstanceDialog does)
        val serverId = "global-test-delete-server-123"
        val serverDef = McpServerDefinition(
            id = serverId,
            name = "Test Global Server",
            description = "Test server for deletion",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = mapOf("Authorization" to "Bearer test-token"),
                timeoutMs = 30000,
            ),
            tags = listOf("global"), // Important: Tagged as global
        )

        // Save the server definition
        McpServersConfig.add(serverDef)

        // Verify server definition exists
        assertNotNull(McpServersConfig.get(serverId), "Server definition should exist before deletion")

        // Create an instance using this server
        val createResult = service.createInstance(
            serverId = serverId,
            name = "Test Global Instance",
            parameterValues = emptyMap(),
        )

        assertTrue(createResult.isSuccess, "Instance creation should succeed")
        val instance = createResult.getOrNull()!!

        // Verify instance exists in mcp-instances.yml
        assertNotNull(
            GlobalMcpInstancesConfig.get(instance.id),
            "Instance should exist in mcp-instances.yml before deletion",
        )

        // Delete the instance
        val deleteResult = service.deleteInstance(instance.id)

        assertTrue(deleteResult.isSuccess, "Instance deletion should succeed")

        // Verify instance is removed from mcp-instances.yml
        assertNull(
            GlobalMcpInstancesConfig.get(instance.id),
            "Instance should be removed from mcp-instances.yml after deletion",
        )

        // Verify associated server definition is removed from mcp-servers.yml
        assertNull(
            McpServersConfig.get(serverId),
            "Server definition should be removed from mcp-servers.yml after deletion",
        )
    }

    @Test
    fun `deleteInstance does not remove server definitions without global tag`() {
        val service = GlobalMcpInstanceService()

        // Create a regular server definition (NOT tagged as global)
        val serverId = "regular-server-not-global"
        val serverDef = McpServerDefinition(
            id = serverId,
            name = "Regular Server Template",
            description = "This is a template, not a global instance",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = emptyMap(),
                timeoutMs = 30000,
            ),
            tags = listOf("template"), // NOT tagged as global
        )

        // Save the server definition
        McpServersConfig.add(serverDef)

        // Create an instance using this template (hypothetically)
        val createResult = service.createInstance(
            serverId = serverId,
            name = "Instance from Template",
            parameterValues = emptyMap(),
        )

        assertTrue(createResult.isSuccess)
        val instance = createResult.getOrNull()!!

        // Delete the instance
        val deleteResult = service.deleteInstance(instance.id)

        assertTrue(deleteResult.isSuccess, "Instance deletion should succeed")

        // Verify instance is removed
        assertNull(GlobalMcpInstancesConfig.get(instance.id))

        // Verify server definition is NOT removed (it's a template, not a global instance)
        assertNotNull(
            McpServersConfig.get(serverId),
            "Template server definition should NOT be removed when instance deleted",
        )

        // Cleanup
        McpServersConfig.remove(serverId)
    }

    @Test
    fun `deleteInstance handles non-existent instance gracefully`() {
        val service = GlobalMcpInstanceService()

        // Try to delete non-existent instance
        val result = service.deleteInstance("non-existent-instance-id")

        assertFalse(result.isSuccess, "Deleting non-existent instance should fail")
    }

    @Test
    fun `createInstance successfully creates both server definition and instance`() {
        val service = GlobalMcpInstanceService()

        // Create a global server definition first
        val serverId = "global-test-create-123"
        val serverDef = McpServerDefinition(
            id = serverId,
            name = "Test Create Server",
            description = "Test server for creation",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = emptyMap(),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        McpServersConfig.add(serverDef)

        // Create instance
        val result = service.createInstance(
            serverId = serverId,
            name = "Test Instance",
            parameterValues = emptyMap(),
        )

        assertTrue(result.isSuccess, "Instance creation should succeed")
        val instance = result.getOrNull()!!

        // Verify instance exists
        assertNotNull(GlobalMcpInstancesConfig.get(instance.id))
        assertEquals("Test Instance", instance.name)
        assertEquals(serverId, instance.serverId)
        assertEquals(GLOBAL_MCP_SCOPE_ID, instance.projectId)

        // Cleanup
        service.deleteInstance(instance.id)
    }

    @Test
    fun `getInstances returns all global instances`() {
        val service = GlobalMcpInstanceService()

        // Create multiple instances
        val serverId = "global-test-list-123"
        val serverDef = McpServerDefinition(
            id = serverId,
            name = "Test List Server",
            description = "Test server for listing",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = emptyMap(),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        McpServersConfig.add(serverDef)

        val instance1 = service.createInstance(serverId, "Instance 1", emptyMap()).getOrNull()!!
        val instance2 = service.createInstance(serverId, "Instance 2", emptyMap()).getOrNull()!!

        val instances = service.getInstances()

        assertTrue(instances.size >= 2, "Should have at least 2 instances")
        assertTrue(instances.any { it.id == instance1.id }, "Instance 1 should be in list")
        assertTrue(instances.any { it.id == instance2.id }, "Instance 2 should be in list")

        // Cleanup
        service.deleteInstance(instance1.id)
        service.deleteInstance(instance2.id)
    }

    @Test
    fun `updateInstance modifies instance properties`() {
        val service = GlobalMcpInstanceService()

        // Create server and instance
        val serverId = "global-test-update-123"
        val serverDef = McpServerDefinition(
            id = serverId,
            name = "Test Update Server",
            description = "Test server for updates",
            transportType = TransportType.HTTP,
            httpConfig = HttpConfig(
                urlTemplate = "https://api.example.com",
                headersTemplate = emptyMap(),
                timeoutMs = 30000,
            ),
            tags = listOf("global"),
        )

        McpServersConfig.add(serverDef)

        val instance = service.createInstance(serverId, "Original Name", emptyMap()).getOrNull()!!

        // Update the instance
        val updateResult = service.updateInstance(
            instanceId = instance.id,
            name = "Updated Name",
            enabled = false,
        )

        assertTrue(updateResult.isSuccess, "Update should succeed")
        val updated = updateResult.getOrNull()!!

        assertEquals("Updated Name", updated.name, "Name should be updated")
        assertFalse(updated.enabled, "Should be disabled")

        // Cleanup
        service.deleteInstance(instance.id)
    }
}
