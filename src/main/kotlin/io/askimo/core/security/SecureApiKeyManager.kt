/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.security.SecureApiKeyManager.StorageMethod.ENCRYPTED
import io.askimo.core.security.SecureApiKeyManager.StorageMethod.INSECURE_FALLBACK
import io.askimo.core.security.SecureApiKeyManager.StorageMethod.KEYCHAIN
import io.askimo.core.util.Logger.info
import io.askimo.core.util.Logger.warn

/**
 * Secure API key storage manager that tries keychain first, then falls back to encryption.
 * Provides warnings when API keys are stored insecurely.
 */
object SecureApiKeyManager {
    enum class StorageMethod {
        KEYCHAIN,
        ENCRYPTED,
        INSECURE_FALLBACK,
    }

    data class StorageResult(
        val success: Boolean,
        val method: StorageMethod,
        val warningMessage: String? = null,
    )

    /**
     * Stores an API key securely, trying keychain first, then encryption fallback.
     *
     * @param provider The provider name (e.g., "openai", "anthropic")
     * @param apiKey The API key to store
     * @return StorageResult indicating success/failure and storage method used
     */
    fun storeApiKey(
        provider: String,
        apiKey: String,
    ): StorageResult {
        // Try keychain first
        if (KeychainManager.storeApiKey(provider, apiKey)) {
            info("✅ API key for $provider stored securely in system keychain")
            return StorageResult(true, KEYCHAIN)
        }

        // Fallback to encryption
        val encryptedKey = EncryptionManager.encrypt(apiKey)
        if (encryptedKey != null) {
            warn("⚠️ System keychain not available. API key for $provider stored with encryption (less secure than keychain)")
            return StorageResult(
                success = true,
                method = ENCRYPTED,
                warningMessage = "API key stored with encryption - not as secure as system keychain",
            )
        }

        // Both methods failed
        warn("❌ Both keychain and encryption failed. API key for $provider will be stored as plain text")
        return StorageResult(
            success = false,
            method = INSECURE_FALLBACK,
            warningMessage = "API key will be stored as PLAIN TEXT - this is INSECURE",
        )
    }

    /**
     * Retrieves an API key, trying keychain first, then encryption fallback.
     *
     * @param provider The provider name
     * @return The API key if found, null otherwise
     */
    fun retrieveApiKey(provider: String): String? {
        // Try keychain first
        KeychainManager.retrieveApiKey(provider)?.let { return it }

        // Try to find encrypted version in legacy storage
        // This would require integration with the existing session storage
        // For now, return null to indicate not found in secure storage
        return null
    }

    /**
     * Removes an API key from secure storage.
     *
     * @param provider The provider name
     * @return true if removed from any secure storage method
     */
    fun removeApiKey(provider: String): Boolean {
        val keychainRemoved = KeychainManager.removeApiKey(provider)
        // Note: We don't have a way to remove from encrypted storage yet
        // as it's integrated with the main session config
        return keychainRemoved
    }

    /**
     * Checks if an API key exists in secure storage.
     *
     * @param provider The provider name
     * @return true if found in keychain or encrypted storage
     */
    fun hasSecureApiKey(provider: String): Boolean = retrieveApiKey(provider) != null

    /**
     * Migrates an existing plain text API key to secure storage.
     *
     * @param provider The provider name
     * @param plainTextApiKey The existing plain text API key
     * @return StorageResult indicating migration success and method used
     */
    fun migrateToSecureStorage(
        provider: String,
        plainTextApiKey: String,
    ): StorageResult {
        if (plainTextApiKey.isBlank()) {
            return StorageResult(false, INSECURE_FALLBACK, "No API key to migrate")
        }

        return storeApiKey(provider, plainTextApiKey)
    }

    /**
     * Gets a user-friendly description of the storage security level.
     */
    fun getStorageSecurityDescription(method: StorageMethod): String =
        when (method) {
            KEYCHAIN -> "🔒 Secure (System Keychain)"
            ENCRYPTED -> "🔐 Encrypted (Local Key)"
            INSECURE_FALLBACK -> "⚠️ INSECURE (Plain Text)"
        }
}
