/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.session.SessionParams
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.warn

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
    companion object {
        private const val ENCRYPTED_API_KEY_PREFIX = "encrypted:"
        private const val KEYCHAIN_API_KEY_PLACEHOLDER = "***keychain***"
        private const val TEST_PROVIDER_PREFIX = "test_"
    }

    /**
     * Loads session parameters and populates API keys from secure storage using test-safe provider names.
     */
    fun loadSecureSession(sessionParams: SessionParams): SessionParams {
        // Clone the session params with deep copy of provider settings
        val secureParams = sessionParams.copy(
            models = sessionParams.models.toMutableMap(),
            providerSettings = sessionParams.providerSettings.mapValues { (provider, settings) ->
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
    fun saveSecureSession(sessionParams: SessionParams): SessionParams {
        // Clone the session params with deep copy of provider settings
        val sanitizedParams = sessionParams.copy(
            models = sessionParams.models.toMutableMap(),
            providerSettings = sessionParams.providerSettings.mapValues { (provider, settings) ->
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

    /**
     * Migrates existing plain text API keys to secure storage using test-safe provider names.
     */
    fun migrateExistingApiKeys(sessionParams: SessionParams): MigrationResult {
        val results = mutableMapOf<io.askimo.core.providers.ModelProvider, SecureApiKeyManager.StorageResult>()
        var hasInsecureKeys = false

        sessionParams.providerSettings.forEach { (provider, settings) ->
            if (settings is HasApiKey && settings.apiKey.isNotBlank()) {
                if (isUsingSecureStorage(settings.apiKey)) {
                    return@forEach
                }

                val safeProviderName = getSafeProviderName(provider)
                val result = SecureApiKeyManager.migrateToSecureStorage(safeProviderName, settings.apiKey)
                results[provider] = result

                if (!result.success) {
                    hasInsecureKeys = true
                    warn("Failed to migrate API key for test provider $safeProviderName to secure storage")
                } else {
                    debug("Migrated API key for test provider $safeProviderName to ${result.method.name}")
                    updateApiKeyPlaceholder(settings, result.method)
                }
            }
        }

        return MigrationResult(results, hasInsecureKeys)
    }

    private fun isUsingSecureStorage(apiKey: String): Boolean = apiKey == KEYCHAIN_API_KEY_PLACEHOLDER ||
        apiKey.startsWith(ENCRYPTED_API_KEY_PREFIX)

    private fun loadApiKeyForProvider(provider: io.askimo.core.providers.ModelProvider, settings: HasApiKey) {
        val currentKey = settings.apiKey

        // Skip if already loaded or empty
        if (currentKey.isBlank() || isActualApiKey(currentKey)) {
            return
        }

        // Use test-safe provider name
        val safeProviderName = getSafeProviderName(provider)

        // Try to load from secure storage
        val secureKey = SecureApiKeyManager.retrieveApiKey(safeProviderName)
        if (secureKey != null) {
            settings.apiKey = secureKey
            debug("Loaded API key for test provider $safeProviderName from secure storage")
        } else if (currentKey.startsWith(ENCRYPTED_API_KEY_PREFIX)) {
            // Try to decrypt legacy encrypted key
            val encryptedPart = currentKey.removePrefix(ENCRYPTED_API_KEY_PREFIX)
            val decryptedKey = EncryptionManager.decrypt(encryptedPart)
            if (decryptedKey != null) {
                settings.apiKey = decryptedKey
                debug("Decrypted legacy API key for test provider $safeProviderName")
            } else {
                warn("Failed to decrypt API key for test provider $safeProviderName")
                settings.apiKey = ""
            }
        }
    }

    private fun saveApiKeyForProvider(provider: io.askimo.core.providers.ModelProvider, settings: HasApiKey) {
        val apiKey = settings.apiKey

        // Skip if it's already a placeholder or empty
        if (!isActualApiKey(apiKey)) {
            return
        }

        // Use test-safe provider name
        val safeProviderName = getSafeProviderName(provider)

        val result = SecureApiKeyManager.storeApiKey(safeProviderName, apiKey)

        if (result.success) {
            // Replace with appropriate placeholder
            updateApiKeyPlaceholder(settings, result.method)

            // Show warning if not using keychain
            result.warningMessage?.let { message -> warn(message) }
        } else {
            // Fall back to encryption in the session file
            val encrypted = EncryptionManager.encrypt(apiKey)
            if (encrypted != null) {
                settings.apiKey = "$ENCRYPTED_API_KEY_PREFIX$encrypted"
                warn("⚠️ Storing encrypted API key for test provider $safeProviderName in session file (less secure)")
            } else {
                warn("❌ Failed to encrypt API key for test provider $safeProviderName - will be stored as plain text")
            }
        }
    }

    private fun getSafeProviderName(provider: io.askimo.core.providers.ModelProvider): String = "$TEST_PROVIDER_PREFIX${provider.name.lowercase()}"

    private fun updateApiKeyPlaceholder(settings: HasApiKey, method: SecureApiKeyManager.StorageMethod) {
        settings.apiKey = when (method) {
            SecureApiKeyManager.StorageMethod.KEYCHAIN -> KEYCHAIN_API_KEY_PLACEHOLDER
            SecureApiKeyManager.StorageMethod.ENCRYPTED -> KEYCHAIN_API_KEY_PLACEHOLDER
            SecureApiKeyManager.StorageMethod.INSECURE_FALLBACK -> settings.apiKey // Keep as-is
        }
    }

    private fun isActualApiKey(apiKey: String): Boolean = apiKey.isNotBlank() &&
        apiKey != KEYCHAIN_API_KEY_PLACEHOLDER &&
        !apiKey.startsWith(ENCRYPTED_API_KEY_PREFIX)

    private fun deepCopyProviderSettings(provider: io.askimo.core.providers.ModelProvider, settings: ProviderSettings): ProviderSettings = when (provider) {
        io.askimo.core.providers.ModelProvider.OPENAI -> {
            val openAiSettings = settings as io.askimo.core.providers.openai.OpenAiSettings
            openAiSettings.copy()
        }
        io.askimo.core.providers.ModelProvider.GEMINI -> {
            val geminiSettings = settings as io.askimo.core.providers.gemini.GeminiSettings
            geminiSettings.copy()
        }
        io.askimo.core.providers.ModelProvider.XAI -> {
            val xaiSettings = settings as io.askimo.core.providers.xai.XAiSettings
            xaiSettings.copy()
        }
        io.askimo.core.providers.ModelProvider.ANTHROPIC -> {
            val anthropicSettings = settings as io.askimo.core.providers.anthropic.AnthropicSettings
            anthropicSettings.copy()
        }
        io.askimo.core.providers.ModelProvider.OLLAMA -> {
            val ollamaSettings = settings as io.askimo.core.providers.ollama.OllamaSettings
            ollamaSettings.copy()
        }
        io.askimo.core.providers.ModelProvider.UNKNOWN -> settings // Unknown settings, return as-is
    }

    data class MigrationResult(
        val results: Map<io.askimo.core.providers.ModelProvider, SecureApiKeyManager.StorageResult>,
        val hasInsecureKeys: Boolean,
    ) {
        fun getSecurityReport(): List<String> {
            val report = mutableListOf<String>()

            if (results.isEmpty()) {
                report.add("No API keys found to migrate")
                return report
            }

            report.add("API Key Security Report:")
            results.forEach { (provider, result) ->
                val security = SecureApiKeyManager.getStorageSecurityDescription(result.method)
                report.add("  ${provider.name}: $security")
                result.warningMessage?.let {
                    report.add("    ⚠️ $it")
                }
            }

            if (hasInsecureKeys) {
                report.add("")
                report.add("⚠️ Some API keys could not be stored securely!")
                report.add("Consider:")
                report.add("  - Installing system keychain utilities")
                report.add("  - Setting proper file permissions")
                report.add("  - Using environment variables instead")
            }

            return report
        }
    }
}
