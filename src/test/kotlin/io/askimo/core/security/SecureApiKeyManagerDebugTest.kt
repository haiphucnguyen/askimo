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
class SecureApiKeyManagerDebugTest {

    private lateinit var testBaseScope: AskimoHome.TestBaseScope
    private lateinit var testDir: Path

    @BeforeEach
    fun setUp() {
        // Set up isolated test environment for each test
        testDir = Files.createTempDirectory("askimo-debug-test")
        testBaseScope = AskimoHome.withTestBase(testDir)

        println("=== Test Setup ===")
        println("Test directory: $testDir")
        println("AskimoHome base: ${AskimoHome.base()}")
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
    fun `debug encrypted storage issue`() {
        val provider = "debug-test-${System.currentTimeMillis()}"
        val testKey = "sk-debug-test-123"

        println("=== Debug Test ===")
        println("Provider: $provider")
        println("Test key: $testKey")
        println("Test directory: $testDir")
        println("AskimoHome base: ${AskimoHome.base()}")

        // Step 1: Store the key
        println("\n=== Step 1: Storing Key ===")
        val storeResult = SecureApiKeyManager.storeApiKey(provider, testKey)
        println("Store result: $storeResult")
        assertTrue(storeResult.success, "Should store successfully")

        // Step 2: Check what files exist
        println("\n=== Step 2: Checking Files ===")
        val encryptedFile = AskimoHome.base().resolve(".encrypted-keys")
        println("Encrypted file path: $encryptedFile")
        println("Encrypted file exists: ${Files.exists(encryptedFile)}")

        if (Files.exists(encryptedFile)) {
            val content = Files.readString(encryptedFile)
            println("Encrypted file content length: ${content.length}")
            println("Encrypted file contains provider: ${content.contains(provider)}")
        }

        // Step 3: Try to retrieve the key
        println("\n=== Step 3: Retrieving Key ===")
        val retrievedKey = SecureApiKeyManager.retrieveApiKey(provider)
        println("Retrieved key: ${if (retrievedKey != null) "***found*** (length=${retrievedKey.length})" else "null"}")

        // This is the assertion that should pass
        assertNotNull(retrievedKey, "Should retrieve key - Storage method was: ${storeResult.method}")
        assertEquals(testKey, retrievedKey, "Keys should match")

        println("\n=== Test Completed Successfully ===")
    }

    @Test
    fun `test encrypted storage methods directly`() {
        val provider = "direct-test-${System.currentTimeMillis()}"
        val testKey = "sk-direct-test-456"

        println("=== Direct Encrypted Storage Test ===")

        // Use reflection to directly test the encrypted storage methods
        val secureApiKeyManagerClass = SecureApiKeyManager::class.java

        val storeEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("storeEncryptedKey", String::class.java, String::class.java)
        storeEncryptedKeyMethod.isAccessible = true

        val retrieveEncryptedKeyMethod = secureApiKeyManagerClass.getDeclaredMethod("retrieveEncryptedKey", String::class.java)
        retrieveEncryptedKeyMethod.isAccessible = true

        // Encrypt the key manually
        val encryptedKey = EncryptionManager.encrypt(testKey)
        assertNotNull(encryptedKey, "Should encrypt successfully")
        println("Encrypted key: ${encryptedKey!!.take(20)}...")

        // Store using encrypted storage directly
        val storeResult = storeEncryptedKeyMethod.invoke(SecureApiKeyManager, provider, encryptedKey) as Boolean
        assertTrue(storeResult, "Should store encrypted key successfully")
        println("Stored encrypted key successfully")

        // Retrieve using encrypted storage directly
        val retrievedKey = retrieveEncryptedKeyMethod.invoke(SecureApiKeyManager, provider) as String?
        assertNotNull(retrievedKey, "Should retrieve and decrypt key")
        assertEquals(testKey, retrievedKey, "Decrypted key should match original")
        println("Retrieved and decrypted key successfully")

        // Test the full API
        val retrievedViaApi = SecureApiKeyManager.retrieveApiKey(provider)
        assertNotNull(retrievedViaApi, "API should find key in encrypted storage")
        assertEquals(testKey, retrievedViaApi, "API retrieved key should match")
        println("API retrieval works correctly")
    }
}
