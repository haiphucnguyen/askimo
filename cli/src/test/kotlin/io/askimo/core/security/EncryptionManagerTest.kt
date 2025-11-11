/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(Lifecycle.PER_CLASS)
class EncryptionManagerTest {

    private var originalKeyPath: Path? = null

    @BeforeEach
    fun setUp() {
        // Back up the original key file if it exists
        val keyPath = AskimoHome.encryptionKeyFile()
        if (Files.exists(keyPath)) {
            originalKeyPath = keyPath
            // Move the original key to a backup location
            Files.move(keyPath, keyPath.resolveSibling(".key.backup"))
        }

        // Ensure no key file exists for testing
        EncryptionManager.deleteKey()
    }

    @AfterEach
    fun tearDown() {
        // Clean up test key
        EncryptionManager.deleteKey()

        // Restore the original key file if it existed
        originalKeyPath?.let { originalPath ->
            val backupPath = originalPath.resolveSibling(".key.backup")
            if (Files.exists(backupPath)) {
                Files.move(backupPath, originalPath)
            }
        }
    }

    @Test
    @DisplayName("Should successfully encrypt and decrypt a simple string")
    fun testBasicEncryptionDecryption() {
        val plaintext = "test-api-key-12345"

        val encrypted = EncryptionManager.encrypt(plaintext)
        assertNotNull(encrypted, "Encryption should succeed")
        assertNotEquals(plaintext, encrypted, "Encrypted text should be different from plaintext")

        val decrypted = EncryptionManager.decrypt(encrypted)
        assertNotNull(decrypted, "Decryption should succeed")
        assertEquals(plaintext, decrypted, "Decrypted text should match original plaintext")
    }

    @Test
    @DisplayName("Should handle empty strings correctly")
    fun testEmptyStringEncryption() {
        val plaintext = ""

        val encrypted = EncryptionManager.encrypt(plaintext)
        assertNotNull(encrypted, "Should be able to encrypt empty string")

        val decrypted = EncryptionManager.decrypt(encrypted)
        assertNotNull(decrypted, "Should be able to decrypt empty string")
        assertEquals(plaintext, decrypted, "Decrypted empty string should match original")
    }

    @Test
    @DisplayName("Should handle long API keys correctly")
    fun testLongApiKeyEncryption() {
        val longApiKey = "sk-" + "a".repeat(100) + "very-long-api-key-with-lots-of-characters"

        val encrypted = EncryptionManager.encrypt(longApiKey)
        assertNotNull(encrypted, "Should be able to encrypt long API key")

        val decrypted = EncryptionManager.decrypt(encrypted)
        assertNotNull(decrypted, "Should be able to decrypt long API key")
        assertEquals(longApiKey, decrypted, "Decrypted long API key should match original")
    }

    @Test
    @DisplayName("Should handle special characters and Unicode correctly")
    fun testSpecialCharactersEncryption() {
        val specialKey = "api-key-with-特殊字符-emojis-🔑🔐-symbols-!@#$%^&*()[]{}|"

        val encrypted = EncryptionManager.encrypt(specialKey)
        assertNotNull(encrypted, "Should be able to encrypt special characters")

        val decrypted = EncryptionManager.decrypt(encrypted)
        assertNotNull(decrypted, "Should be able to decrypt special characters")
        assertEquals(specialKey, decrypted, "Decrypted special characters should match original")
    }

    @Test
    @DisplayName("Should produce different encrypted output for same input (due to random IV)")
    fun testRandomIVGeneration() {
        val plaintext = "same-api-key"

        val encrypted1 = EncryptionManager.encrypt(plaintext)
        val encrypted2 = EncryptionManager.encrypt(plaintext)

        assertNotNull(encrypted1, "First encryption should succeed")
        assertNotNull(encrypted2, "Second encryption should succeed")
        assertNotEquals(encrypted1, encrypted2, "Two encryptions of same text should produce different ciphertext due to random IV")

        // But both should decrypt to the same plaintext
        val decrypted1 = EncryptionManager.decrypt(encrypted1)
        val decrypted2 = EncryptionManager.decrypt(encrypted2)

        assertEquals(plaintext, decrypted1, "First decryption should match original")
        assertEquals(plaintext, decrypted2, "Second decryption should match original")
    }

    @Test
    @DisplayName("Should reuse existing key file for consistent decryption")
    fun testKeyPersistence() {
        val plaintext = "persistent-key-test"

        // First encryption creates the key
        val encrypted = EncryptionManager.encrypt(plaintext)
        assertNotNull(encrypted, "First encryption should succeed")

        // Verify key file exists
        val keyPath = AskimoHome.encryptionKeyFile()
        assertTrue(Files.exists(keyPath), "Key file should be created")

        // Second encryption should use the same key
        val decrypted = EncryptionManager.decrypt(encrypted)
        assertNotNull(decrypted, "Decryption with existing key should succeed")
        assertEquals(plaintext, decrypted, "Decryption should work with persisted key")
    }

    @Test
    @DisplayName("Should handle invalid ciphertext gracefully")
    fun testInvalidCiphertextHandling() {
        val invalidInputs = listOf(
            "invalid-base64-!@#$%",
            "dGhpcyBpcyBub3QgdmFsaWQgZW5jcnlwdGVkIGRhdGE=", // Valid base64 but invalid encrypted data
            "",
            "short",
            "QQ==", // Too short for IV + encrypted data
        )

        invalidInputs.forEach { invalidInput ->
            val result = EncryptionManager.decrypt(invalidInput)
            assertNull(result, "Decryption of invalid input '$invalidInput' should return null")
        }
    }

    @Test
    @DisplayName("Should successfully delete encryption key")
    fun testKeyDeletion() {
        // Create a key by performing encryption
        val plaintext = "test-for-deletion"
        val encrypted = EncryptionManager.encrypt(plaintext)
        assertNotNull(encrypted, "Encryption should succeed")

        // Verify key file exists
        val keyPath = AskimoHome.encryptionKeyFile()
        assertTrue(Files.exists(keyPath), "Key file should exist after encryption")

        // Delete the key
        val deleteResult = EncryptionManager.deleteKey()
        assertTrue(deleteResult, "Key deletion should succeed")
        assertFalse(Files.exists(keyPath), "Key file should be deleted")

        // Try to delete again (should return false)
        val secondDeleteResult = EncryptionManager.deleteKey()
        assertFalse(secondDeleteResult, "Second deletion attempt should return false")
    }

    @Test
    @DisplayName("Should handle multiple sequential encryptions correctly")
    fun testMultipleEncryptions() {
        val testData = listOf(
            "api-key-1",
            "different-api-key-2",
            "yet-another-key-3",
            "sk-1234567890abcdef",
            "very-secure-key-with-numbers-123456",
        )

        val encryptedData = mutableListOf<String>()

        // Encrypt all test data
        testData.forEach { plaintext ->
            val encrypted = EncryptionManager.encrypt(plaintext)
            assertNotNull(encrypted, "Encryption should succeed for '$plaintext'")
            encryptedData.add(encrypted)
        }

        // Decrypt all test data and verify
        testData.forEachIndexed { index, expectedPlaintext ->
            val decrypted = EncryptionManager.decrypt(encryptedData[index])
            assertNotNull(decrypted, "Decryption should succeed for encrypted data at index $index")
            assertEquals(expectedPlaintext, decrypted, "Decrypted data should match original at index $index")
        }
    }

    @Test
    @DisplayName("Should handle corrupted key file gracefully")
    fun testCorruptedKeyFileHandling() {
        // First create a valid key
        val plaintext = "test-before-corruption"
        val encrypted = EncryptionManager.encrypt(plaintext)
        assertNotNull(encrypted, "Initial encryption should succeed")

        // Corrupt the key file
        val keyPath = AskimoHome.encryptionKeyFile()
        Files.write(keyPath, "corrupted-key-data".toByteArray())

        // Try to encrypt/decrypt - should handle gracefully
        val encryptResult = EncryptionManager.encrypt("test-after-corruption")
        // The behavior may vary - either create a new key or fail gracefully
        // We just verify it doesn't throw an unhandled exception

        val decryptResult = EncryptionManager.decrypt(encrypted)
        // Similarly, this may fail due to the corrupted key, but should handle gracefully
    }

    @Test
    @DisplayName("Should create key directory if it doesn't exist")
    fun testKeyDirectoryCreation() {
        // Delete the key and its parent directory if possible
        val keyPath = AskimoHome.encryptionKeyFile()
        EncryptionManager.deleteKey()

        // Try to delete parent directory (may not be possible if other files exist)
        try {
            if (Files.list(keyPath.parent).use { it.count() } == 0L) {
                Files.deleteIfExists(keyPath.parent)
            }
        } catch (e: Exception) {
            // Ignore - parent directory might not be empty or deletable
        }

        // Perform encryption which should create the directory structure
        val plaintext = "test-directory-creation"
        val encrypted = EncryptionManager.encrypt(plaintext)
        assertNotNull(encrypted, "Encryption should succeed and create directories")

        // Verify key file and directory exist
        assertTrue(Files.exists(keyPath), "Key file should be created")
        assertTrue(Files.exists(keyPath.parent), "Key directory should be created")

        // Verify decryption works
        val decrypted = EncryptionManager.decrypt(encrypted)
        assertEquals(plaintext, decrypted, "Decryption should work with newly created key")
    }
}
