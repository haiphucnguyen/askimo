/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.context.AppContextParams
import io.askimo.core.logging.logger
import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.anthropic.AnthropicSettings
import io.askimo.core.providers.docker.DockerAiSettings
import io.askimo.core.providers.gemini.GeminiSettings
import io.askimo.core.providers.ollama.OllamaSettings
import io.askimo.core.providers.openai.OpenAiSettings
import io.askimo.core.providers.xai.XAiSettings

/**
 * Test-specific secure session manager that uses safe provider names
 * to avoid overwriting real user keychain data during tests.
 *
 * SAFETY: This class automatically prefixes all provider names with "test_"
 * when storing/retrieving from the keychain. For example:
 * - ModelProvider.OPENAI -> "test_openai"
 * - ModelProvider.GEMINI -> "test_gemini"
 * - etc.
 *
 * This ensures that running tests will never overwrite a developer's real
 * API keys stored in their system keychain.
 */
class TestSecureSessionManager {
    private val log = logger<TestSecureSessionManager>()

    companion object {
        private const val ENCRYPTED_API_KEY_PREFIX = "encrypted:"
        private const val KEYCHAIN_API_KEY_PLACEHOLDER = "***keychain***"
        private const val TEST_PROVIDER_PREFIX = "test_"
    }

    /**
     * Loads session parameters and populates API keys from secure storage using test-safe provider names.
     */
    fun loadSecureSession(appContextParams: AppContextParams): AppContextParams {
        // Clone the session params with deep copy of provider settings
        val secureParams = appContextParams.copy(
            models = appContextParams.models.toMutableMap(),
            providerSettings = appContextParams.providerSettings.mapValues { (provider, settings) ->
                deepCopyProviderSettings(provider, settings)
            }.toMutableMap(),
        )

        // Load API keys from secure storage for each provider
        secureParams.providerSettings.forEach { (provider, settings) ->
            if (settings is HasApiKey) {
                loadApiKeyForProvider(provider, settings)
            }
        }

        return secureParams
    }

    /**
     * Saves session parameters, storing API keys securely using test-safe provider names.
     */
    fun saveSecureSession(appContextParams: AppContextParams): AppContextParams {
        // Clone the session params with deep copy of provider settings
        val sanitizedParams = appContextParams.copy(
            models = appContextParams.models.toMutableMap(),
            providerSettings = appContextParams.providerSettings.mapValues { (provider, settings) ->
                deepCopyProviderSettings(provider, settings)
            }.toMutableMap(),
        )

        // Store API keys securely and replace them with placeholders
        sanitizedParams.providerSettings.forEach { (provider, settings) ->
            if (settings is HasApiKey && settings.apiKey.isNotBlank()) {
                saveApiKeyForProvider(provider, settings)
            }
        }

        return sanitizedParams
    }

    private fun isUsingSecureStorage(apiKey: String): Boolean = apiKey == KEYCHAIN_API_KEY_PLACEHOLDER ||
        apiKey.startsWith(ENCRYPTED_API_KEY_PREFIX)

    private fun loadApiKeyForProvider(provider: ModelProvider, settings: HasApiKey) {
        val currentKey = settings.apiKey

        // Skip if already loaded or empty
        if (currentKey.isBlank() || isActualApiKey(currentKey)) {
            return
        }

        // Use test-safe provider name
        val safeProviderName = getSafeProviderName(provider)

        // Try to load from secure storage
        val secureKey = SecureKeyManager.retrieveSecretKey(safeProviderName)
        if (secureKey != null) {
            settings.apiKey = secureKey
            log.debug("Loaded API key for test provider $safeProviderName from secure storage")
        } else if (currentKey.startsWith(ENCRYPTED_API_KEY_PREFIX)) {
            // Try to decrypt legacy encrypted key
            val encryptedPart = currentKey.removePrefix(ENCRYPTED_API_KEY_PREFIX)
            val decryptedKey = EncryptionManager.decrypt(encryptedPart)
            if (decryptedKey != null) {
                settings.apiKey = decryptedKey
                log.debug("Decrypted legacy API key for test provider $safeProviderName")
            } else {
                log.warn("Failed to decrypt API key for test provider $safeProviderName")
                settings.apiKey = ""
            }
        }
    }

    private fun saveApiKeyForProvider(provider: ModelProvider, settings: HasApiKey) {
        val apiKey = settings.apiKey

        // Skip if it's already a placeholder or empty
        if (!isActualApiKey(apiKey)) {
            return
        }

        // Use test-safe provider name
        val safeProviderName = getSafeProviderName(provider)

        val result = SecureKeyManager.storeSecuredKey(safeProviderName, apiKey)

        if (result.success) {
            // Replace with appropriate placeholder
            updateApiKeyPlaceholder(settings, result.method)

            // Show warning if not using keychain
            result.warningMessage?.let { message -> log.warn(message) }
        } else {
            // Fall back to encryption in the session file
            val encrypted = EncryptionManager.encrypt(apiKey)
            if (encrypted != null) {
                settings.apiKey = "$ENCRYPTED_API_KEY_PREFIX$encrypted"
                log.warn("⚠️ Storing encrypted API key for test provider $safeProviderName in session file (less secure)")
            } else {
                log.warn("❌ Failed to encrypt API key for test provider $safeProviderName - will be stored as plain text")
            }
        }
    }

    private fun getSafeProviderName(provider: ModelProvider): String = "$TEST_PROVIDER_PREFIX${provider.name.lowercase()}"

    private fun updateApiKeyPlaceholder(settings: HasApiKey, method: SecureKeyManager.StorageMethod) {
        settings.apiKey = when (method) {
            SecureKeyManager.StorageMethod.KEYCHAIN -> KEYCHAIN_API_KEY_PLACEHOLDER
            SecureKeyManager.StorageMethod.ENCRYPTED -> KEYCHAIN_API_KEY_PLACEHOLDER
            SecureKeyManager.StorageMethod.INSECURE_FALLBACK -> settings.apiKey // Keep as-is
        }
    }

    private fun isActualApiKey(apiKey: String): Boolean = apiKey.isNotBlank() &&
        apiKey != KEYCHAIN_API_KEY_PLACEHOLDER &&
        !apiKey.startsWith(ENCRYPTED_API_KEY_PREFIX)

    private fun deepCopyProviderSettings(provider: ModelProvider, settings: ProviderSettings): ProviderSettings = when (provider) {
        ModelProvider.OPENAI -> {
            val openAiSettings = settings as OpenAiSettings
            openAiSettings.copy()
        }
        ModelProvider.GEMINI -> {
            val geminiSettings = settings as GeminiSettings
            geminiSettings.copy()
        }
        ModelProvider.XAI -> {
            val xaiSettings = settings as XAiSettings
            xaiSettings.copy()
        }
        ModelProvider.ANTHROPIC -> {
            val anthropicSettings = settings as AnthropicSettings
            anthropicSettings.copy()
        }
        ModelProvider.OLLAMA -> {
            val ollamaSettings = settings as OllamaSettings
            ollamaSettings.copy()
        }
        ModelProvider.DOCKER -> {
            val dockerSettings = settings as DockerAiSettings
            dockerSettings.copy()
        }
        ModelProvider.LOCALAI -> {
            val localAiSettings = settings as OllamaSettings
            localAiSettings.copy()
        }
        ModelProvider.LMSTUDIO -> {
            val lmStudioSettings = settings as OllamaSettings
            lmStudioSettings.copy()
        }
        ModelProvider.UNKNOWN -> settings // Unknown settings, return as-is
    }
}
