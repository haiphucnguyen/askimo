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
import io.askimo.core.security.SecureKeyManager.StorageMethod

/**
 * Secure wrapper for AppContextParams that handles API key storage/retrieval transparently.
 * API keys are stored in system keychain or encrypted storage instead of plain text.
 */
class SecureSessionManager {
    private val log = logger<SecureSessionManager>()

    companion object {
        private const val ENCRYPTED_API_KEY_PREFIX = "encrypted:"
        private const val KEYCHAIN_API_KEY_PLACEHOLDER = "***keychain***"
    }

    /**
     * Loads session parameters and populates API keys from secure storage.
     */
    fun loadSecureSession(appContextParams: AppContextParams): AppContextParams {
        // Clone the session params with deep copy of provider settings
        val secureParams = appContextParams.copy(
            models = appContextParams.models.toMutableMap(),
            providerSettings = appContextParams.providerSettings.mapValues { (_, settings) ->
                deepCopyProviderSettings(settings)
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
     * Saves session parameters, storing API keys securely and removing them from the session file.
     */
    fun saveSecureSession(appContextParams: AppContextParams): AppContextParams {
        // Clone the session params with deep copy of provider settings
        val sanitizedParams = appContextParams.copy(
            models = appContextParams.models.toMutableMap(),
            providerSettings = appContextParams.providerSettings.mapValues { (_, settings) ->
                deepCopyProviderSettings(settings)
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

    private fun loadApiKeyForProvider(
        provider: ModelProvider,
        settings: HasApiKey,
    ) {
        val currentKey = settings.apiKey

        // Skip if already loaded or empty
        if (currentKey.isBlank() || isActualApiKey(currentKey)) {
            return
        }

        // Try to load from secure storage
        val secureKey = SecureKeyManager.retrieveSecretKey(provider.name.lowercase())
        if (secureKey != null) {
            settings.apiKey = secureKey
            log.trace("Loaded API key for ${provider.name} from secure storage")
        } else if (currentKey.startsWith(ENCRYPTED_API_KEY_PREFIX)) {
            // Try to decrypt legacy encrypted key
            val encryptedPart = currentKey.removePrefix(ENCRYPTED_API_KEY_PREFIX)
            val decryptedKey = EncryptionManager.decrypt(encryptedPart)
            if (decryptedKey != null) {
                settings.apiKey = decryptedKey
                log.debug("Decrypted legacy API key for ${provider.name}")
            } else {
                log.warn("Failed to decrypt API key for ${provider.name}")
                settings.apiKey = ""
            }
        }
    }

    private fun saveApiKeyForProvider(
        provider: ModelProvider,
        settings: HasApiKey,
    ) {
        val apiKey = settings.apiKey

        // Skip if it's already a placeholder or empty
        if (!isActualApiKey(apiKey)) {
            return
        }

        val result = SecureKeyManager.storeSecuredKey(provider.name.lowercase(), apiKey)

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
                log.warn("⚠️ Storing encrypted API key for ${provider.name} in session file (less secure)")
            } else {
                log.warn("❌ Failed to encrypt API key for ${provider.name} - will be stored as plain text")
            }
        }
    }

    private fun updateApiKeyPlaceholder(
        settings: HasApiKey,
        method: StorageMethod,
    ) {
        settings.apiKey =
            when (method) {
                StorageMethod.KEYCHAIN -> KEYCHAIN_API_KEY_PLACEHOLDER
                StorageMethod.ENCRYPTED -> KEYCHAIN_API_KEY_PLACEHOLDER
                StorageMethod.INSECURE_FALLBACK -> settings.apiKey // Keep as-is
            }
    }

    private fun isActualApiKey(apiKey: String): Boolean = apiKey.isNotBlank() &&
        apiKey != KEYCHAIN_API_KEY_PLACEHOLDER &&
        !apiKey.startsWith(ENCRYPTED_API_KEY_PREFIX)

    /**
     * Creates a deep copy of provider settings to avoid shared mutable state.
     */
    private fun deepCopyProviderSettings(settings: ProviderSettings): ProviderSettings = settings.deepCopy()
}
