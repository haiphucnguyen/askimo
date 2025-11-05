/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.reflect.Method
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
    fun `test encrypted storage methods directly`() {
        val provider = "test-encrypted-direct"
        val testKey = "sk-test-direct-encrypted-123"

        // Use reflection to access private methods for testing
        val secureApiKeyManagerClass = SecureApiKeyManager::class.java
        
        val storeEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("storeEncryptedKey", String::class.java, String::class.java)
        storeEncryptedKeyMethod.isAccessible = true
        
        val retrieveEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("retrieveEncryptedKey", String::class.java)
        retrieveEncryptedKeyMethod.isAccessible = true
        
        val removeEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("removeEncryptedKey", String::class.java)
        removeEncryptedKeyMethod.isAccessible = true

        // First encrypt the key
        val encryptedKey = EncryptionManager.encrypt(testKey)
        assertNotNull(encryptedKey, "Should encrypt successfully")

        // Test storing encrypted key
        val storeResult = storeEncryptedKeyMethod.invoke(SecureApiKeyManager, provider, encryptedKey!!) as Boolean
        assertTrue(storeResult, "Should store encrypted key successfully")

        // Test retrieving encrypted key (this should decrypt it)
        val retrievedKey = retrieveEncryptedKeyMethod.invoke(SecureApiKeyManager, provider) as String?
        assertNotNull(retrievedKey, "Should retrieve and decrypt key")
        assertEquals(testKey, retrievedKey, "Decrypted key should match original")

        // Test removing encrypted key
        val removeResult = removeEncryptedKeyMethod.invoke(SecureApiKeyManager, provider) as Boolean
        assertTrue(removeResult, "Should remove encrypted key successfully")

        // Verify removal
        val retrievedAfterRemoval = retrieveEncryptedKeyMethod.invoke(SecureApiKeyManager, provider) as String?
        assertNull(retrievedAfterRemoval, "Key should not exist after removal")
    }

    @Test
    fun `test encrypted storage when keychain would fail`() {
        // This test simulates what should happen on Windows systems where keychain fails
        val provider = "test-keychain-fail"
        val testKey = "sk-test-keychain-fail-456"

        // Store the key - on systems where keychain works, it will use keychain
        // but on systems where it fails, it should fall back to encrypted storage
        val storeResult = SecureApiKeyManager.storeApiKey(provider, testKey)
        assertTrue(storeResult.success, "Should store successfully even if keychain fails")

        // The method should be either KEYCHAIN or ENCRYPTED
        assertTrue(
            storeResult.method == SecureApiKeyManager.StorageMethod.KEYCHAIN ||
            storeResult.method == SecureApiKeyManager.StorageMethod.ENCRYPTED,
            "Should use KEYCHAIN or ENCRYPTED method"
        )

        // Regardless of method, retrieval should work
        val retrievedKey = SecureApiKeyManager.retrieveApiKey(provider)
        assertNotNull(retrievedKey, "Should retrieve key regardless of storage method")
        assertEquals(testKey, retrievedKey, "Retrieved key should match")

        // Removal should also work
        val removeResult = SecureApiKeyManager.removeApiKey(provider)
        assertTrue(removeResult, "Should remove key")

        // Verify removal
        val afterRemoval = SecureApiKeyManager.retrieveApiKey(provider)
        assertNull(afterRemoval, "Key should not exist after removal")
    }

    @Test
    fun `test encrypted storage file is created when needed`() {
        val provider = "test-file-creation"
        val testKey = "sk-test-file-creation-789"

        // Check if encrypted storage file exists before
        val encryptedFile = AskimoHome.base().resolve(".encrypted-keys")
        val existedBefore = Files.exists(encryptedFile)

        // Force use of encrypted storage by directly calling the method
        val secureApiKeyManagerClass = SecureApiKeyManager::class.java
        val storeEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("storeEncryptedKey", String::class.java, String::class.java)
        storeEncryptedKeyMethod.isAccessible = true

        val encryptedKey = EncryptionManager.encrypt(testKey)!!
        val storeResult = storeEncryptedKeyMethod.invoke(SecureApiKeyManager, provider, encryptedKey) as Boolean
        assertTrue(storeResult, "Should store in encrypted file")

        // Now the file should exist
        assertTrue(Files.exists(encryptedFile), "Encrypted storage file should be created")

        // Verify we can retrieve the key
        val retrieveEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("retrieveEncryptedKey", String::class.java)
        retrieveEncryptedKeyMethod.isAccessible = true
        val retrievedKey = retrieveEncryptedKeyMethod.invoke(SecureApiKeyManager, provider) as String?
        assertEquals(testKey, retrievedKey, "Should retrieve correct key")
    }
}
