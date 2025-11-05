/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.io.IOException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeychainManagerMacOSIntegrationTest {

    private val testProviders = mutableSetOf<String>()

    companion object {
        private const val TEST_PROVIDER_PREFIX = "test-provider"
        private const val SIMPLE_API_KEY = "sk-simple123"
        private const val LONG_API_KEY = "sk-proj-xR7nK4mP9wQ2vE8bF5jL3tY6uA1sD0gH-zX9cV2bN4mK7pQ1wE5rT8yU3iO6aS2dF-vG4hJ8kL1nM0pR7tY2uI9oE3wQ6aS5dF8gH1jK4mP7xZ0cV3bN6mK9pQ2wE5rT-yU8iO1aS4dF7gH0jK3mP6xZ9cV2bN5mK8pQ1wE4rT7yU0iO3aS6dF9gH2jK5mP8xZ1cV4bN7mKpQ0wE3rT6yU9iO2aS5dF8gH1jK4mP7xZ0cV3bN6mKpQ2wE5rT8yU1iO4aS7dF0gH3jK6mP9xZ2cV5bN8mKpQ1wE4rT7yU0iO3aS6dF9gH2jK5mP8xZ1cV4bN7mKpQ0wE3rT6yU9iO2aS5dF8gH1jK4mP7xZ0cV3bN6mKpQ2wE5rT8yU1iO4aS7dF0gH3jK6mP9xZ2cV5bN8mKpQ1wE4rT7yU0iO3aS6dF9gH2jK5mP8xZ1cV4bN7mKpQ0wE3rT6yU9iO2aS5dF8gH1jK4mP7xZ0cV3bN6mKpQ2wE"
        private const val API_KEY_WITH_SPECIAL_CHARS = "sk-test_123-456_789-ABC_DEF"
    }

    @BeforeEach
    fun setUp() {
        // Skip tests if not running on macOS
        assumeTrue(isMacOS(), "Tests only run on macOS")

        // Verify security command is available
        assumeTrue(isSecurityCommandAvailable(), "macOS security command not available")
    }

    @AfterEach
    fun tearDown() {
        // Clean up all test providers that were created during tests
        testProviders.forEach { provider ->
            try {
                KeychainManager.removeApiKey(provider)
            } catch (e: Exception) {
                println("Warning: Failed to clean up test provider $provider: ${e.message}")
            }
        }
        testProviders.clear()
    }

    @Test
    fun `test store and retrieve simple API key`() {
        val provider = generateTestProvider("simple")

        // Store the API key
        val storeResult = KeychainManager.storeApiKey(provider, SIMPLE_API_KEY)
        assertTrue(storeResult, "Failed to store simple API key")

        // Retrieve the API key
        val retrievedKey = KeychainManager.retrieveApiKey(provider)
        assertNotNull(retrievedKey, "Retrieved API key should not be null")
        assertEquals(SIMPLE_API_KEY, retrievedKey, "Retrieved API key should match stored key")
    }

    @Test
    fun `test store and retrieve long OpenAI API key`() {
        val provider = generateTestProvider("openai-long")

        println("Testing with long API key:")
        println("Original length: ${LONG_API_KEY.length}")
        println("Original prefix: ${LONG_API_KEY.take(20)}...")

        // Store the API key
        val storeResult = KeychainManager.storeApiKey(provider, LONG_API_KEY)
        assertTrue(storeResult, "Failed to store long API key")

        // Retrieve the API key
        val retrievedKey = KeychainManager.retrieveApiKey(provider)
        assertNotNull(retrievedKey, "Retrieved API key should not be null")

        println("Retrieved length: ${retrievedKey?.length ?: 0}")
        println("Retrieved prefix: ${retrievedKey?.take(20) ?: "null"}...")

        assertEquals(LONG_API_KEY.length, retrievedKey?.length, "Retrieved API key length should match original")
        assertEquals(LONG_API_KEY, retrievedKey, "Retrieved API key should match stored key exactly")
    }

    @Test
    fun `test store and retrieve API key with special characters`() {
        val provider = generateTestProvider("special-chars")

        // Store the API key
        val storeResult = KeychainManager.storeApiKey(provider, API_KEY_WITH_SPECIAL_CHARS)
        assertTrue(storeResult, "Failed to store API key with special characters")

        // Retrieve the API key
        val retrievedKey = KeychainManager.retrieveApiKey(provider)
        assertNotNull(retrievedKey, "Retrieved API key should not be null")
        assertEquals(API_KEY_WITH_SPECIAL_CHARS, retrievedKey, "Retrieved API key should match stored key")
    }

    @Test
    fun `test update existing API key`() {
        val provider = generateTestProvider("update")
        val firstKey = "sk-first-key-123"
        val secondKey = "sk-second-key-456"

        // Store first API key
        assertTrue(KeychainManager.storeApiKey(provider, firstKey), "Failed to store first API key")
        assertEquals(firstKey, KeychainManager.retrieveApiKey(provider), "First key should be stored correctly")

        // Update with second API key
        assertTrue(KeychainManager.storeApiKey(provider, secondKey), "Failed to update API key")
        assertEquals(secondKey, KeychainManager.retrieveApiKey(provider), "Second key should replace first key")
    }

    @Test
    fun `test remove API key`() {
        val provider = generateTestProvider("remove")

        // Store API key
        assertTrue(KeychainManager.storeApiKey(provider, SIMPLE_API_KEY), "Failed to store API key")
        assertNotNull(KeychainManager.retrieveApiKey(provider), "API key should be retrievable after storing")

        // Remove API key
        assertTrue(KeychainManager.removeApiKey(provider), "Failed to remove API key")
        assertNull(KeychainManager.retrieveApiKey(provider), "API key should not be retrievable after removal")

        // Remove already removed key (should still return true or false gracefully)
        val secondRemoveResult = KeychainManager.removeApiKey(provider)
        // Don't assert the result as it may vary, just ensure it doesn't throw an exception
        println("Second remove attempt result: $secondRemoveResult")
    }

    @Test
    fun `test retrieve non-existent API key`() {
        val provider = generateTestProvider("non-existent")

        // Try to retrieve a key that was never stored
        val retrievedKey = KeychainManager.retrieveApiKey(provider)
        assertNull(retrievedKey, "Non-existent API key should return null")
    }

    @Test
    fun `test multiple providers isolation`() {
        val provider1 = generateTestProvider("isolation1")
        val provider2 = generateTestProvider("isolation2")
        val key1 = "sk-provider1-key"
        val key2 = "sk-provider2-key"

        // Store different keys for different providers
        assertTrue(KeychainManager.storeApiKey(provider1, key1), "Failed to store key for provider1")
        assertTrue(KeychainManager.storeApiKey(provider2, key2), "Failed to store key for provider2")

        // Verify each provider gets its own key
        assertEquals(key1, KeychainManager.retrieveApiKey(provider1), "Provider1 should get its own key")
        assertEquals(key2, KeychainManager.retrieveApiKey(provider2), "Provider2 should get its own key")

        // Remove one provider's key, other should remain
        assertTrue(KeychainManager.removeApiKey(provider1), "Failed to remove provider1 key")
        assertNull(KeychainManager.retrieveApiKey(provider1), "Provider1 key should be removed")
        assertEquals(key2, KeychainManager.retrieveApiKey(provider2), "Provider2 key should still exist")
    }

    @Test
    fun `test edge case empty API key`() {
        val provider = generateTestProvider("empty")
        val emptyKey = ""

        // Store empty API key
        val storeResult = KeychainManager.storeApiKey(provider, emptyKey)
        if (storeResult) {
            val retrievedKey = KeychainManager.retrieveApiKey(provider)
            assertEquals(emptyKey, retrievedKey, "Empty API key should be stored and retrieved correctly")
        } else {
            println("Empty API key storage failed as expected")
        }
    }

    @Test
    fun `test edge case very long API key`() {
        val provider = generateTestProvider("very-long")
        val veryLongKey = "sk-" + "a".repeat(1000) // Very long key

        println("Testing with very long API key (${veryLongKey.length} characters)")

        val storeResult = KeychainManager.storeApiKey(provider, veryLongKey)
        assertTrue(storeResult, "Failed to store very long API key")

        val retrievedKey = KeychainManager.retrieveApiKey(provider)
        assertNotNull(retrievedKey, "Very long API key should be retrievable")
        assertEquals(veryLongKey.length, retrievedKey?.length, "Very long API key length should match")
        assertEquals(veryLongKey, retrievedKey, "Very long API key should match exactly")
    }

    private fun generateTestProvider(suffix: String): String {
        val provider = "$TEST_PROVIDER_PREFIX-$suffix-${System.currentTimeMillis()}"
        testProviders.add(provider)
        return provider
    }

    private fun isMacOS(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        return osName.contains("mac") || osName.contains("darwin")
    }

    private fun isSecurityCommandAvailable(): Boolean = try {
        val process = ProcessBuilder("which", "security").start()
        process.waitFor() == 0
    } catch (e: IOException) {
        false
    }
}
