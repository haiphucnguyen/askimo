/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.openai.OpenAiSettings
import io.askimo.core.session.SessionParams
import io.askimo.core.util.AskimoHome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SecureApiKeyStorageTest {
    private lateinit var secureSessionManager: SecureSessionManager

    @TempDir
    lateinit var tempHome: Path

    private lateinit var testBaseScope: AskimoHome.TestBaseScope

    @BeforeEach
    fun setUp() {
        // Use AskimoHome's test override instead of affecting real askimo installation
        testBaseScope = AskimoHome.withTestBase(tempHome.resolve(".askimo"))

        secureSessionManager = SecureSessionManager()

        // Clean up any existing test keys
        KeychainManager.removeApiKey("open_ai")

        // Clean up encryption key file if it exists (now points to test directory)
        val keyPath = AskimoHome.encryptionKeyFile()
        if (Files.exists(keyPath)) {
            Files.delete(keyPath)
        }
    }

    @AfterEach
    fun tearDown() {
        // Clean up the test base override
        testBaseScope.close()
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
        // Skip test on non-macOS platforms due to lack of keychain support
        val osName = System.getProperty("os.name").lowercase()
        assumeTrue(osName.contains("mac"), "Keychain only supported on macOS")

        // Create a session with placeholder API key
        val sessionParams = SessionParams()
        val openAiSettings = OpenAiSettings(apiKey = "***keychain***")
        sessionParams.providerSettings[ModelProvider.OPEN_AI] = openAiSettings

        // Store a key in keychain using the correct provider name
        KeychainManager.storeApiKey("open_ai", "sk-actual-key-from-keychain")

        // Load the secure session
        val secureSession = secureSessionManager.loadSecureSession(sessionParams)

        // The key should be loaded from keychain, not remain as placeholder
        val loadedSettings = secureSession.providerSettings[ModelProvider.OPEN_AI] as OpenAiSettings
        assertEquals("sk-actual-key-from-keychain", loadedSettings.apiKey)
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
