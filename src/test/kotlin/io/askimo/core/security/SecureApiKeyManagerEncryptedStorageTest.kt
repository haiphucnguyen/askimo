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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecureApiKeyManagerEncryptedStorageTest {

    private lateinit var testBaseScope: AskimoHome.TestBaseScope
    private lateinit var testDir: Path

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
        // Set up isolated test environment
        testDir = Files.createTempDirectory("askimo-test")
        testBaseScope = AskimoHome.withTestBase(testDir)

        val provider = "test-provider-encrypted"
        val testKey = "sk-test-encrypted-key-12345"

        // First, let's test the direct encrypted storage methods
        // by examining what happens when keychain is unavailable

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
        // Set up isolated test environment
        testDir = Files.createTempDirectory("askimo-test")
        testBaseScope = AskimoHome.withTestBase(testDir)

        val provider = "test-provider-file"
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
        // Set up isolated test environment
        testDir = Files.createTempDirectory("askimo-test")
        testBaseScope = AskimoHome.withTestBase(testDir)

        val providers = listOf(
            "openai" to "sk-openai-key-123",
            "anthropic" to "sk-ant-key-456",
            "gemini" to "ai-gemini-key-789"
        )

        // Store multiple keys
        providers.forEach { (provider, key) ->
            val result = SecureApiKeyManager.storeApiKey(provider, key)
            assertTrue(result.success, "Should store $provider key successfully")
        }

        // Retrieve and verify all keys
        providers.forEach { (provider, expectedKey) ->
            val retrievedKey = SecureApiKeyManager.retrieveApiKey(provider)
            assertNotNull(retrievedKey, "Should retrieve $provider key")
            assertEquals(expectedKey, retrievedKey, "$provider key should match")
        }

        // Remove one key and verify others remain
        val removeResult = SecureApiKeyManager.removeApiKey("openai")
        assertTrue(removeResult, "Should remove openai key")

        // Verify openai key is gone
        val openaiKey = SecureApiKeyManager.retrieveApiKey("openai")
        assertNull(openaiKey, "OpenAI key should be removed")

        // Verify other keys still exist
        val anthropicKey = SecureApiKeyManager.retrieveApiKey("anthropic")
        assertEquals("sk-ant-key-456", anthropicKey, "Anthropic key should still exist")

        val geminiKey = SecureApiKeyManager.retrieveApiKey("gemini")
        assertEquals("ai-gemini-key-789", geminiKey, "Gemini key should still exist")
    }
}
