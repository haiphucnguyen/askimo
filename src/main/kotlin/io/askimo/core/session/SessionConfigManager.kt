/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.core.security.SecureSessionManager
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import io.askimo.core.util.appJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Manages the persistence of session configuration with secure API key storage.
 *
 * This singleton object handles loading and saving session parameters to/from a JSON file
 * located in the user's home directory. API keys are stored securely in system keychain
 * or encrypted storage instead of plain text.
 */
object SessionConfigManager {
    /** Path to the configuration file in the user's home directory */
    private val configPath: Path = Paths.get(System.getProperty("user.home"), ".askimo", "session")

    /** In-memory cache for the loaded session */
    @Volatile
    private var cached: SessionParams? = null

    /** Secure session manager for handling API keys */
    private val secureSessionManager = SecureSessionManager()

    /**
     * Loads session parameters.
     * - Returns the cached instance if available.
     * - Otherwise loads from disk, migrates API keys to secure storage, and caches the result.
     */
    fun load(): SessionParams {
        cached?.let {
            // Load API keys from secure storage into the cached session
            return secureSessionManager.loadSecureSession(it)
        }

        val loaded =
            if (Files.exists(configPath)) {
                try {
                    Files.newBufferedReader(configPath).use {
                        appJson.decodeFromString<SessionParams>(it.readText())
                    }
                } catch (e: Exception) {
                    info("⚠️ Failed to parse config file at $configPath. Using default configuration.")
                    debug(e)
                    SessionParams.noOp()
                }
            } else {
                info("⚠️ Config file not found at $configPath. Using default configuration.")
                SessionParams.noOp()
            }

        // Migrate existing API keys to secure storage
        val migrationResult = secureSessionManager.migrateExistingApiKeys(loaded)

        // Show security report if there were API keys to migrate
        if (migrationResult.results.isNotEmpty()) {
            migrationResult.getSecurityReport().forEach { info(it) }
        }

        // Load API keys from secure storage
        val secureLoaded = secureSessionManager.loadSecureSession(loaded)

        cached = secureLoaded
        return secureLoaded
    }

    /**
     * Saves the session parameters to the configuration file and updates the cache.
     * API keys are stored securely and removed from the session file.
     */
    fun save(params: SessionParams) {
        try {
            Files.createDirectories(configPath.parent)

            // Create sanitized version without API keys for file storage
            val sanitizedParams = secureSessionManager.saveSecureSession(params)

            Files
                .newBufferedWriter(
                    configPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                ).use {
                    it.write(appJson.encodeToString(sanitizedParams))
                }
            cached = params
            info("Saving config to: $configPath successfully.")
        } catch (e: Exception) {
            info("❌ Failed to save session config to $configPath: ${e.message}")
            debug(e)
        }
    }

    /**
     * Clears the in-memory cache (e.g., if user manually edits the config on disk).
     */
    fun clearCache() {
        cached = null
    }
}
