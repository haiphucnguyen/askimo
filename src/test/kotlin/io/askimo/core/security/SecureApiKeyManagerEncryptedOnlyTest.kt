/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecureApiKeyManagerEncryptedOnlyTest {

    private lateinit var testBaseScope: AskimoHome.TestBaseScope
    private lateinit var testDir: Path

    @BeforeEach
    fun setUp() {
        // Set up isolated test environment
        testDir = Files.createTempDirectory("askimo-test-encrypted-only")
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
    fun `test encrypted storage directly`() {
        // Test the encrypted storage functionality directly
        val provider = "test-encrypted-direct-${System.currentTimeMillis()}"
        val testKey = "sk-test-encrypted-direct-123"

        println("=== Testing Encrypted Storage Directly ===")
        println("Provider: $provider")
        println("Test key: ${testKey.take(10)}...")

        // Use reflection to directly test encrypted storage methods
        val secureApiKeyManagerClass = SecureApiKeyManager::class.java
        
        val storeEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("storeEncryptedKey", String::class.java, String::class.java)
        storeEncryptedKeyMethod.isAccessible = true
        
        val retrieveEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("retrieveEncryptedKey", String::class.java)
        retrieveEncryptedKeyMethod.isAccessible = true

        // Encrypt the key first
        val encryptedKey = EncryptionManager.encrypt(testKey)
        assertNotNull(encryptedKey, "Should encrypt successfully")
        println("Encrypted key: ${encryptedKey!!.take(20)}...")

        // Store the encrypted key
        val storeResult = storeEncryptedKeyMethod.invoke(SecureApiKeyManager, provider, encryptedKey) as Boolean
        assertTrue(storeResult, "Should store encrypted key successfully")

        // Verify the encrypted file was created
        val encryptedFile = AskimoHome.base().resolve(".encrypted-keys")
        assertTrue(Files.exists(encryptedFile), "Encrypted storage file should exist")
        println("Encrypted file created at: $encryptedFile")

        // Retrieve the key (should be decrypted)
        val retrievedKey = retrieveEncryptedKeyMethod.invoke(SecureApiKeyManager, provider) as String?
        assertNotNull(retrievedKey, "Should retrieve and decrypt key")
        assertEquals(testKey, retrievedKey, "Decrypted key should match original")
        println("Successfully retrieved and decrypted key")
    }

    @Test
    fun `test simple encrypted storage flow`() {
        // Simple test to verify encrypted storage works
        val provider = "simple-test-${System.currentTimeMillis()}-${(Math.random() * 1000).toInt()}"
        val testKey = "sk-simple-test-123"

        try {
            // Store and retrieve a key
            val storeResult = SecureApiKeyManager.storeApiKey(provider, testKey)
            assertTrue(storeResult.success, "Should store successfully")

            val retrievedKey = SecureApiKeyManager.retrieveApiKey(provider)
            assertNotNull(retrievedKey, "Should retrieve key - Storage method was: ${storeResult.method}")
            assertEquals(testKey, retrievedKey, "Keys should match")
        } finally {
            // Clean up
            SecureApiKeyManager.removeApiKey(provider)
        }
    }

    @Test
    fun `test encrypted storage file is created when needed`() {
        val provider = "test-file-creation"
        val testKey = "sk-test-file-creation-789"

        // Force use of encrypted storage by directly calling the method
        val secureApiKeyManagerClass = SecureApiKeyManager::class.java
        val storeEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("storeEncryptedKey", String::class.java, String::class.java)
        storeEncryptedKeyMethod.isAccessible = true

        val encryptedKey = EncryptionManager.encrypt(testKey)!!
        val storeResult = storeEncryptedKeyMethod.invoke(SecureApiKeyManager, provider, encryptedKey) as Boolean
        assertTrue(storeResult, "Should store in encrypted file")

        // Now the file should exist (using the current AskimoHome.base())
        val encryptedFile = AskimoHome.base().resolve(".encrypted-keys")
        assertTrue(Files.exists(encryptedFile), "Encrypted storage file should be created")

        // Verify we can retrieve the key
        val retrieveEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("retrieveEncryptedKey", String::class.java)
        retrieveEncryptedKeyMethod.isAccessible = true
        val retrievedKey = retrieveEncryptedKeyMethod.invoke(SecureApiKeyManager, provider) as String?
        assertEquals(testKey, retrievedKey, "Should retrieve correct key")
    }
}
