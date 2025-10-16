/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import io.askimo.cli.Logger.log
import io.askimo.core.util.appJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Manages the persistence of session configuration.
 *
 * This singleton object handles loading and saving session parameters to/from a JSON file
 * located in the user's home directory. It uses Gson for JSON serialization and deserialization.
 */
object SessionConfigManager {
    /** Path to the configuration file in the user's home directory */
    private val configPath: Path = Paths.get(System.getProperty("user.home"), ".askimo", "session")

    /** In-memory cache for the loaded session */
    @Volatile
    private var cached: SessionParams? = null

    /**
     * Loads session parameters.
     * - Returns the cached instance if available.
     * - Otherwise loads from disk and caches the result.
     */
    fun load(): SessionParams {
        cached?.let { return it }

        val loaded =
            if (Files.exists(configPath)) {
                try {
                    Files.newBufferedReader(configPath).use {
                        appJson.decodeFromString<SessionParams>(it.readText())
                    }
                } catch (e: Exception) {
                    println("⚠️ Failed to parse config file at $configPath. Using default configuration.")
                    SessionParams.noOp()
                }
            } else {
                println("⚠️ Config file not found at $configPath. Using default configuration.")
                SessionParams.noOp()
            }

        cached = loaded
        return loaded
    }

    /**
     * Saves the session parameters to the configuration file and updates the cache.
     */
    fun save(params: SessionParams) {
        try {
            Files.createDirectories(configPath.parent)
            Files
                .newBufferedWriter(
                    configPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                ).use {
                    it.write(appJson.encodeToString(params))
                }
            cached = params // update cache immediately
            log { "Saving config to: $configPath successfully." }
        } catch (e: Exception) {
            e.printStackTrace()
            System.err.println("❌ Failed to save session config to $configPath: ${e.message}")
        }
    }

    /**
     * Clears the in-memory cache (e.g., if user manually edits the config on disk).
     */
    fun clearCache() {
        cached = null
    }
}
