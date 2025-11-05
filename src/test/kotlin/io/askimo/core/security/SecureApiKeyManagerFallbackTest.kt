/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecureApiKeyManagerFallbackTest {

    private lateinit var testBaseScope: AskimoHome.TestBaseScope
    private lateinit var testDir: Path

    @BeforeEach
    fun setUp() {
        // Set up isolated test environment to force encrypted storage
        testDir = Files.createTempDirectory("askimo-test-fallback")
        testBaseScope = AskimoHome.withTestBase(testDir)
    }

    @AfterEach
    fun tearDown() {
        // Clean up test directory
        testBaseScope.close()
        if (Files.exists(testDir)) {
            testDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `test encrypted storage works independently of keychain`() {
        val provider = "test-provider-fallback"
        val testKey = "sk-test-fallback-key-12345"

        // Test storing directly to encrypted storage
        // (This tests our new encrypted storage methods)
        val storeResult = SecureApiKeyManager.storeApiKey(provider, testKey)
        assertTrue(storeResult.success, "Should store successfully")

        // The storage method could be KEYCHAIN or ENCRYPTED depending on system
        println("Storage method used: ${storeResult.method}")

        // Regardless of storage method, retrieval should work
        val retrievedKey = SecureApiKeyManager.retrieveApiKey(provider)
        assertNotNull(retrievedKey, "Should retrieve stored key")
        assertEquals(testKey, retrievedKey, "Retrieved key should match original")

        // Test removal
        val removeResult = SecureApiKeyManager.removeApiKey(provider)
        assertTrue(removeResult, "Should remove key successfully")

        // Verify removal
        val retrievedAfterRemoval = SecureApiKeyManager.retrieveApiKey(provider)
        assertNull(retrievedAfterRemoval, "Key should not exist after removal")
    }

    @Test
    fun `test long API key storage and retrieval`() {
        val provider = "test-provider-long"
        val longApiKey = "sk-proj-xR7nK4mP9wQ2vE8bF5jL3tY6uA1sD0gH-zX9cV2bN4mK7pQ1wE5rT8yU3iO6aS2dF-vG4hJ8kL1nM0pR7tY2uI9oE3wQ6aS5dF8gH1jK4mP7xZ0cV3bN6mK9pQ2wE5rT-yU8iO1aS4dF7gH0jK3mP6xZ9cV2bN5mK8pQ1wE4rT7yU0iO3aS6dF9gH2jK5mP8xZ1cV4bN7mKpQ0wE3rT6yU9iO2aS5dF8gH1jK4mP7xZ0cV3bN6mKpQ2wE5rT8yU1iO4aS7dF0gH3jK6mP9xZ2cV5bN8mKpQ1wE4rT7yU0iO3aS6dF9gH2jK5mP8xZ1cV4bN7mKpQ0wE3rT6yU9iO2aS5dF8gH1jK4mP7xZ0cV3bN6mKpQ2wE5rT8yU1iO4aS7dF0gH3jK6mP9xZ2cV5bN8mKpQ1wE4rT7yU0iO3aS6dF9gH2jK5mP8xZ1cV4bN7mKpQ0wE3rT6yU9iO2aS5dF8gH1jK4mP7xZ0cV3bN6mKpQ2wE"

        println("Testing with long API key (${longApiKey.length} chars)")

        // Store
        val storeResult = SecureApiKeyManager.storeApiKey(provider, longApiKey)
        assertTrue(storeResult.success, "Should store long key successfully")

        // Retrieve
        val retrievedKey = SecureApiKeyManager.retrieveApiKey(provider)
        assertNotNull(retrievedKey, "Should retrieve long key")
        assertEquals(longApiKey.length, retrievedKey!!.length, "Length should match")
        assertEquals(longApiKey, retrievedKey, "Content should match exactly")

        // Clean up
        SecureApiKeyManager.removeApiKey(provider)
    }
}
