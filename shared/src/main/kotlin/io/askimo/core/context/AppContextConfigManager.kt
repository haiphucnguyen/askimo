/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.context

import io.askimo.core.logging.display
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.security.SecureSessionManager
import io.askimo.core.util.AskimoHome
import io.askimo.core.util.appJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING

/**
 * Manages the persistence of session configuration with secure API key storage.
 *
 * This singleton object handles loading and saving session parameters to/from a JSON file
 * located in the user's home directory. API keys are stored securely in system keychain
 * or encrypted storage instead of plain text.
 */
object AppContextConfigManager {
    private val log = logger<AppContextConfigManager>()

    /** Path to the configuration file in the user's home directory */
    private val configPath: Path = AskimoHome.sessionFile()

    /** In-memory cache for the loaded session */
    @Volatile
    private var cached: AppContextParams? = null

    /** Secure session manager for handling API keys */
    private val secureSessionManager = SecureSessionManager()

    /**
     * Loads session parameters.
     * - Returns the cached instance if available.
     * - Otherwise loads from disk, migrates API keys to secure storage, and caches the result.
     */
    fun load(): AppContextParams {
        cached?.let {
            return secureSessionManager.loadSecureSession(it)
        }

        val loaded =
            if (Files.exists(configPath)) {
                try {
                    Files.newBufferedReader(configPath).use {
                        appJson.decodeFromString<AppContextParams>(it.readText())
                    }
                } catch (e: Exception) {
                    log.displayError("⚠️ Failed to parse config file at $configPath. Using default configuration.", e)
                    AppContextParams.noOp()
                }
            } else {
                log.display("⚠️ Config file not found at $configPath. Using default configuration.")
                AppContextParams.noOp()
            }

        // Migrate existing API keys to secure storage
        val migrationResult = secureSessionManager.migrateExistingApiKeys(loaded)

        // Show security report if there were API keys to migrate
        if (migrationResult.results.isNotEmpty()) {
            migrationResult.getSecurityReport().forEach { log.debug(it) }
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
    fun save(params: AppContextParams) {
        try {
            Files.createDirectories(configPath.parent)

            // Create sanitized version without API keys for file storage
            val sanitizedParams = secureSessionManager.saveSecureSession(params)

            Files
                .newBufferedWriter(
                    configPath,
                    CREATE,
                    TRUNCATE_EXISTING,
                ).use {
                    it.write(appJson.encodeToString(sanitizedParams))
                }
            cached = params
            log.info("Saving config to: $configPath successfully.")
        } catch (e: Exception) {
            log.displayError("❌ Failed to save session config to $configPath: ${e.message}", e)
        }
    }
}
