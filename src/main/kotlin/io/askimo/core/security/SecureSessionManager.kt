/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.ModelProvider
import io.askimo.core.session.SessionParams
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.warn

/**
 * Secure wrapper for SessionParams that handles API key storage/retrieval transparently.
 * API keys are stored in system keychain or encrypted storage instead of plain text.
 */
class SecureSessionManager {
    companion object {
        private const val ENCRYPTED_API_KEY_PREFIX = "encrypted:"
        private const val KEYCHAIN_API_KEY_PLACEHOLDER = "***keychain***"
    }

    /**
     * Loads session parameters and populates API keys from secure storage.
     */
    fun loadSecureSession(sessionParams: SessionParams): SessionParams {
        // Clone the session params to avoid modifying the original
        val secureParams =
            sessionParams.copy(
                providerSettings = sessionParams.providerSettings.toMutableMap(),
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
    fun saveSecureSession(sessionParams: SessionParams): SessionParams {
        // Clone the session params
        val sanitizedParams =
            sessionParams.copy(
                providerSettings = sessionParams.providerSettings.toMutableMap(),
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
     * Migrates existing plain text API keys to secure storage.
     */
    fun migrateExistingApiKeys(sessionParams: SessionParams): MigrationResult {
        val results = mutableMapOf<ModelProvider, SecureApiKeyManager.StorageResult>()
        var hasInsecureKeys = false

        sessionParams.providerSettings.forEach { (provider, settings) ->
            if (settings is HasApiKey && settings.apiKey.isNotBlank()) {
                // Skip if already using secure storage
                if (isUsingSecureStorage(settings.apiKey)) {
                    return@forEach
                }

                val result =
                    SecureApiKeyManager.migrateToSecureStorage(
                        provider.name.lowercase(),
                        settings.apiKey,
                    )
                results[provider] = result

                if (!result.success) {
                    hasInsecureKeys = true
                    warn("Failed to migrate API key for ${provider.name} to secure storage")
                } else {
                    debug("Migrated API key for ${provider.name} to ${result.method.name}")
                    // Update the settings to use placeholder
                    updateApiKeyPlaceholder(settings, result.method)
                }
            }
        }

        return MigrationResult(results, hasInsecureKeys)
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
        val secureKey = SecureApiKeyManager.retrieveApiKey(provider.name.lowercase())
        if (secureKey != null) {
            settings.apiKey = secureKey
            debug("Loaded API key for ${provider.name} from secure storage")
        } else if (currentKey.startsWith(ENCRYPTED_API_KEY_PREFIX)) {
            // Try to decrypt legacy encrypted key
            val encryptedPart = currentKey.removePrefix(ENCRYPTED_API_KEY_PREFIX)
            val decryptedKey = EncryptionManager.decrypt(encryptedPart)
            if (decryptedKey != null) {
                settings.apiKey = decryptedKey
                debug("Decrypted legacy API key for ${provider.name}")
            } else {
                warn("Failed to decrypt API key for ${provider.name}")
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

        val result = SecureApiKeyManager.storeApiKey(provider.name.lowercase(), apiKey)

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
                warn("⚠️ Storing encrypted API key for ${provider.name} in session file (less secure)")
            } else {
                warn("❌ Failed to encrypt API key for ${provider.name} - will be stored as plain text")
            }
        }
    }

    private fun updateApiKeyPlaceholder(
        settings: HasApiKey,
        method: SecureApiKeyManager.StorageMethod,
    ) {
        settings.apiKey =
            when (method) {
                SecureApiKeyManager.StorageMethod.KEYCHAIN -> KEYCHAIN_API_KEY_PLACEHOLDER
                SecureApiKeyManager.StorageMethod.ENCRYPTED -> KEYCHAIN_API_KEY_PLACEHOLDER
                SecureApiKeyManager.StorageMethod.INSECURE_FALLBACK -> settings.apiKey // Keep as-is
            }
    }

    private fun isUsingSecureStorage(apiKey: String): Boolean =
        apiKey == KEYCHAIN_API_KEY_PLACEHOLDER ||
            apiKey.startsWith(ENCRYPTED_API_KEY_PREFIX)

    private fun isActualApiKey(apiKey: String): Boolean =
        apiKey.isNotBlank() &&
            apiKey != KEYCHAIN_API_KEY_PLACEHOLDER &&
            !apiKey.startsWith(ENCRYPTED_API_KEY_PREFIX)

    data class MigrationResult(
        val results: Map<ModelProvider, SecureApiKeyManager.StorageResult>,
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
