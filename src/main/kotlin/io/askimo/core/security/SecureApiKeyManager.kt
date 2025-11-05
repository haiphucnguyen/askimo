/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.security.SecureApiKeyManager.StorageMethod.ENCRYPTED
import io.askimo.core.security.SecureApiKeyManager.StorageMethod.INSECURE_FALLBACK
import io.askimo.core.security.SecureApiKeyManager.StorageMethod.KEYCHAIN
import io.askimo.core.util.AskimoHome
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.warn
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Secure API key storage manager that tries keychain first, then falls back to encryption.
 * Provides warnings when API keys are stored insecurely.
 */
object SecureApiKeyManager {
    private fun encryptedStorageFile(): Path = AskimoHome.base().resolve(".encrypted-keys")

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
            debug("✅ API key for $provider stored securely in system keychain")
            return StorageResult(true, KEYCHAIN)
        }

        // Fallback to encryption
        val encryptedKey = EncryptionManager.encrypt(apiKey)
        if (encryptedKey != null) {
            if (storeEncryptedKey(provider, encryptedKey)) {
                warn("⚠️ System keychain not available. API key for $provider stored with encryption (less secure than keychain)")
                return StorageResult(
                    success = true,
                    method = ENCRYPTED,
                    warningMessage = "API key stored with encryption - not as secure as system keychain",
                )
            }
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

        // Try encrypted storage
        return retrieveEncryptedKey(provider)
    }

    /**
     * Removes an API key from secure storage.
     *
     * @param provider The provider name
     * @return true if removed from any secure storage method
     */
    fun removeApiKey(provider: String): Boolean {
        val keychainRemoved = KeychainManager.removeApiKey(provider)
        val encryptedRemoved = removeEncryptedKey(provider)
        return keychainRemoved || encryptedRemoved
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
     * Stores an encrypted key to the encrypted storage file.
     */
    private fun storeEncryptedKey(provider: String, encryptedKey: String): Boolean = try {
        val properties = loadEncryptedStorage()
        properties.setProperty(provider, encryptedKey)
        saveEncryptedStorage(properties)
        true
    } catch (e: Exception) {
        debug("Failed to store encrypted key for $provider: ${e.message}")
        false
    }

    /**
     * Retrieves and decrypts a key from the encrypted storage file.
     */
    private fun retrieveEncryptedKey(provider: String): String? = try {
        val properties = loadEncryptedStorage()
        val encryptedKey = properties.getProperty(provider) ?: return null
        EncryptionManager.decrypt(encryptedKey)
    } catch (e: Exception) {
        debug("Failed to retrieve encrypted key for $provider: ${e.message}")
        null
    }

    /**
     * Removes an encrypted key from the storage file.
     */
    private fun removeEncryptedKey(provider: String): Boolean = try {
        val properties = loadEncryptedStorage()
        val removed = properties.remove(provider) != null
        if (removed) {
            saveEncryptedStorage(properties)
        }
        removed
    } catch (e: Exception) {
        debug("Failed to remove encrypted key for $provider: ${e.message}")
        false
    }

    /**
     * Loads the encrypted storage properties file.
     */
    private fun loadEncryptedStorage(): Properties {
        val properties = Properties()
        val storageFile = encryptedStorageFile()
        if (Files.exists(storageFile)) {
            Files.newInputStream(storageFile).use { input ->
                properties.load(input)
            }
        }
        return properties
    }

    /**
     * Saves the encrypted storage properties file with restrictive permissions.
     */
    private fun saveEncryptedStorage(properties: Properties) {
        val storageFile = encryptedStorageFile()
        // Create directory if it doesn't exist
        Files.createDirectories(storageFile.parent)

        // Save the properties
        Files.newOutputStream(storageFile).use { output ->
            properties.store(output, "Encrypted API Keys - Do Not Edit")
        }

        // Set restrictive permissions (owner only)
        try {
            val file = storageFile.toFile()
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        } catch (e: Exception) {
            debug("Failed to set restrictive permissions on encrypted storage file: ${e.message}")
        }
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
