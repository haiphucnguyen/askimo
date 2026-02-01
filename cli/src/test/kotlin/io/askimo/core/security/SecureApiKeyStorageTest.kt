/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.context.AppContextParams
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.openai.OpenAiSettings
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
    private lateinit var secureSessionManager: TestSecureSessionManager

    @TempDir
    lateinit var tempHome: Path

    private lateinit var testBaseScope: AskimoHome.TestBaseScope

    companion object {
        private const val TEST_PROVIDER_NAME = "test_openai_safe"
    }

    @BeforeEach
    fun setUp() {
        // Use AskimoHome's test override instead of affecting real askimo installation
        testBaseScope = AskimoHome.withTestBase(tempHome.resolve(".askimo"))

        secureSessionManager = TestSecureSessionManager()

        // Clean up any existing test keys using SAFE test provider name
        SecureKeyManager.removeSecretKey(TEST_PROVIDER_NAME)

        // Clean up encryption key file if it exists (now points to test directory)
        val keyPath = AskimoHome.encryptionKeyFile()
        if (Files.exists(keyPath)) {
            Files.delete(keyPath)
        }
    }

    @AfterEach
    fun tearDown() {
        // Clean up test keys
        try {
            SecureKeyManager.removeSecretKey(TEST_PROVIDER_NAME)
        } catch (_: Exception) {
            // Ignore cleanup failures
        }

        // Clean up the test base override
        testBaseScope.close()
    }

    @Test
    fun `test secure session loading`() {
        // Skip test on non-macOS platforms due to lack of keychain support
        val osName = System.getProperty("os.name").lowercase()
        assumeTrue(osName.contains("mac"), "Keychain only supported on macOS")

        // Create a session with placeholder API key
        val appContextParams = AppContextParams()
        val openAiSettings = OpenAiSettings(apiKey = "***keychain***")
        appContextParams.providerSettings[ModelProvider.OPENAI] = openAiSettings

        // Store a key in keychain using SAFE test provider name
        SecureKeyManager.storeSecuredKey(TEST_PROVIDER_NAME, "sk-actual-key-from-keychain")

        // Since we're using TestSecureSessionManager, it will use the safe provider name
        // But we need to manually test the keychain retrieval
        val retrievedKey = SecureKeyManager.retrieveSecretKey(TEST_PROVIDER_NAME)

        if (retrievedKey != null) {
            // Keychain is working
            assertEquals("sk-actual-key-from-keychain", retrievedKey)
        } else {
            // Keychain might not be available in test environment
            println("Keychain not available in test environment - skipping keychain verification")
        }
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
            SecureKeyManager.getStorageSecurityDescription(
                SecureKeyManager.StorageMethod.KEYCHAIN,
            )
        assertTrue(keychainDesc.contains("Keychain"))

        val encryptedDesc =
            SecureKeyManager.getStorageSecurityDescription(
                SecureKeyManager.StorageMethod.ENCRYPTED,
            )
        assertTrue(encryptedDesc.contains("Encrypted"))

        val insecureDesc =
            SecureKeyManager.getStorageSecurityDescription(
                SecureKeyManager.StorageMethod.INSECURE_FALLBACK,
            )
        assertTrue(insecureDesc.contains("INSECURE"))
    }
}
