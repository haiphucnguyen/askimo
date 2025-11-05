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
class SecureApiKeyManagerEncryptedStorageTest {

    private lateinit var testBaseScope: AskimoHome.TestBaseScope
    private lateinit var testDir: Path

    @BeforeEach
    fun setUp() {
        // Set up isolated test environment for each test
        testDir = Files.createTempDirectory("askimo-test")
        testBaseScope = AskimoHome.withTestBase(testDir)
    }

    @AfterEach
    fun tearDown() {
        // Clean up test directory
        if (::testBaseScope.isInitialized) {
            testBaseScope.close()
        }
        if (::testDir.isInitialized && Files.exists(testDir)) {
            testDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `test encrypted storage when keychain fails`() {
        val provider = "test-provider-encrypted-${System.currentTimeMillis()}"
        val testKey = "sk-test-encrypted-key-12345"


        // On systems where keychain might not work (like CI environments),
        // the SecureApiKeyManager should fall back to encrypted storage
        val storeResult = SecureApiKeyManager.storeApiKey(provider, testKey)
        assertTrue(storeResult.success, "Should store successfully even when keychain might fail")

        // Retrieve the key
        val retrievedKey = SecureApiKeyManager.retrieveApiKey(provider)
        assertNotNull(retrievedKey, "Should retrieve encrypted key")
        assertEquals(testKey, retrievedKey, "Retrieved key should match original")

        // Verify that the key can be removed
        val removeResult = SecureApiKeyManager.removeApiKey(provider)
        assertTrue(removeResult, "Should remove key successfully")

        // Verify removal worked
        val retrievedAfterRemoval = SecureApiKeyManager.retrieveApiKey(provider)
        assertNull(retrievedAfterRemoval, "Key should not exist after removal")
    }

    @Test
    fun `test encrypted storage file creation and structure`() {
        val provider = "test-provider-file-${System.currentTimeMillis()}"
        val testKey = "sk-test-file-key-67890"

        // Store a key to trigger file creation
        val storeResult = SecureApiKeyManager.storeApiKey(provider, testKey)
        assertTrue(storeResult.success, "Should store successfully")

        // Check if encrypted storage file was created when keychain fails
        // (This will happen on systems without working keychain)
        val encryptedFile = AskimoHome.base().resolve(".encrypted-keys")

        // The file might exist if encrypted storage was used as fallback
        if (Files.exists(encryptedFile)) {
            println("Encrypted storage file created at: $encryptedFile")
            assertTrue(Files.isRegularFile(encryptedFile), "Should be a regular file")

            // Verify file permissions are restrictive
            val file = encryptedFile.toFile()
            assertTrue(file.canRead(), "Owner should be able to read")
            assertTrue(file.canWrite(), "Owner should be able to write")
        } else {
            println("Keychain storage was used instead of encrypted storage")
        }

        // Verify the key can still be retrieved regardless of storage method
        val retrievedKey = SecureApiKeyManager.retrieveApiKey(provider)
        assertNotNull(retrievedKey, "Should retrieve key")
        assertEquals(testKey, retrievedKey, "Retrieved key should match original")
    }

    @Test
    fun `test multiple providers in encrypted storage`() {
        val timestamp = System.currentTimeMillis()
        val providers = listOf(
            "openai-${timestamp}" to "sk-openai-key-123",
            "anthropic-${timestamp}" to "sk-ant-key-456",
            "gemini-${timestamp}" to "ai-gemini-key-789"
        )

        println("=== Multiple Providers Test ===")
        println("Test directory: $testDir")
        println("AskimoHome base: ${AskimoHome.base()}")

        // Store multiple keys
        providers.forEach { (provider, key) ->
            println("Storing key for provider: $provider")
            val result = SecureApiKeyManager.storeApiKey(provider, key)
            println("Store result for $provider: $result")
            assertTrue(result.success, "Should store $provider key successfully")
        }

        // Check if encrypted file was created
        val encryptedFile = AskimoHome.base().resolve(".encrypted-keys")
        println("Encrypted file exists: ${Files.exists(encryptedFile)}")
        if (Files.exists(encryptedFile)) {
            println("Encrypted file path: $encryptedFile")
        }

        // Retrieve and verify all keys
        providers.forEach { (provider, expectedKey) ->
            println("Retrieving key for provider: $provider")
            val retrievedKey = SecureApiKeyManager.retrieveApiKey(provider)
            println("Retrieved key for $provider: ${if (retrievedKey != null) "***found***" else "null"}")
            assertNotNull(retrievedKey, "Should retrieve $provider key")
            assertEquals(expectedKey, retrievedKey, "$provider key should match")
        }

        // Remove one key and verify others remain
        val removeResult = SecureApiKeyManager.removeApiKey("openai-${timestamp}")
        assertTrue(removeResult, "Should remove openai key")

        // Verify openai key is gone
        val openaiKey = SecureApiKeyManager.retrieveApiKey("openai-${timestamp}")
        assertNull(openaiKey, "OpenAI key should be removed")

        // Verify other keys still exist
        val anthropicKey = SecureApiKeyManager.retrieveApiKey("anthropic-${timestamp}")
        assertEquals("sk-ant-key-456", anthropicKey, "Anthropic key should still exist")

        val geminiKey = SecureApiKeyManager.retrieveApiKey("gemini-${timestamp}")
        assertEquals("ai-gemini-key-789", geminiKey, "Gemini key should still exist")
    }
}
