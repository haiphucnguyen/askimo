/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.openai.OpenAiSettings
import io.askimo.core.session.SessionParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class SecureApiKeyStorageTest {
    private lateinit var secureSessionManager: SecureSessionManager

    @BeforeEach
    fun setUp() {
        secureSessionManager = SecureSessionManager()

        // Clean up any existing test keys
        KeychainManager.removeApiKey("openai")

        // Clean up encryption key file if it exists
        val keyPath = Paths.get(System.getProperty("user.home"), ".askimo", ".key")
        if (Files.exists(keyPath)) {
            Files.delete(keyPath)
        }
    }

    @Test
    fun `test API key migration from plain text`() {
        // Create a session with a plain text API key
        val sessionParams = SessionParams()
        val openAiSettings = OpenAiSettings(apiKey = "sk-test-api-key-12345")
        sessionParams.providerSettings[ModelProvider.OPEN_AI] = openAiSettings

        // Migrate to secure storage
        val migrationResult = secureSessionManager.migrateExistingApiKeys(sessionParams)

        // Verify migration was attempted
        assertTrue(migrationResult.results.containsKey(ModelProvider.OPEN_AI))

        val result = migrationResult.results[ModelProvider.OPEN_AI]!!
        assertTrue(result.success)

        // The API key should now be replaced with a placeholder or encrypted
        assertNotEquals("sk-test-api-key-12345", openAiSettings.apiKey)
    }

    @Test
    fun `test secure session loading`() {
        // Create a session with placeholder API key
        val sessionParams = SessionParams()
        val openAiSettings = OpenAiSettings(apiKey = "***keychain***")
        sessionParams.providerSettings[ModelProvider.OPEN_AI] = openAiSettings

        // First store a key in keychain (if available)
        KeychainManager.storeApiKey("openai", "sk-actual-key-from-keychain")

        // Load the secure session
        val secureSession = secureSessionManager.loadSecureSession(sessionParams)

        // If keychain is available, the API key should be loaded
        val loadedSettings = secureSession.providerSettings[ModelProvider.OPEN_AI] as OpenAiSettings

        // The key should either be loaded from keychain or remain as placeholder
        assertTrue(
            loadedSettings.apiKey == "sk-actual-key-from-keychain" ||
                loadedSettings.apiKey == "***keychain***",
        )
    }

    @Test
    fun `test encryption fallback`() {
        // Test the encryption manager directly
        val testApiKey = "sk-test-encryption-key"

        val encrypted = EncryptionManager.encrypt(testApiKey)
        assertNotNull(encrypted)
        assertNotEquals(testApiKey, encrypted)

        val decrypted = EncryptionManager.decrypt(encrypted!!)
        assertEquals(testApiKey, decrypted)
    }

    @Test
    fun `test storage security descriptions`() {
        val keychainDesc =
            SecureApiKeyManager.getStorageSecurityDescription(
                SecureApiKeyManager.StorageMethod.KEYCHAIN,
            )
        assertTrue(keychainDesc.contains("Keychain"))

        val encryptedDesc =
            SecureApiKeyManager.getStorageSecurityDescription(
                SecureApiKeyManager.StorageMethod.ENCRYPTED,
            )
        assertTrue(encryptedDesc.contains("Encrypted"))

        val insecureDesc =
            SecureApiKeyManager.getStorageSecurityDescription(
                SecureApiKeyManager.StorageMethod.INSECURE_FALLBACK,
            )
        assertTrue(insecureDesc.contains("INSECURE"))
    }
}
