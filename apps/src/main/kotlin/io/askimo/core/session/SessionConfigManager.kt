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

    /**
     * Loads session parameters from the configuration file.
     *
     * @return The loaded session parameters, or default parameters if the file doesn't exist or can't be parsed
     */
    fun load(): SessionParams =
        if (Files.exists(configPath)) {
            try {
                Files.newBufferedReader(configPath).use {
                    appJson.decodeFromString<SessionParams>(it.readText())
                }
            } catch (_: Exception) {
                println("⚠️ Failed to parse config file at $configPath. Using default configuration.")
                SessionParams.noOp()
            }
        } else {
            println("⚠️ Config file not found at $configPath. Using default configuration.")
            SessionParams.noOp()
        }

    /**
     * Saves session parameters to the configuration file.
     *
     * Creates the file if it doesn't exist or truncates it if it does.
     *
     * @param params The session parameters to save
     */
    fun save(params: SessionParams) {
        try {
            Files
                .newBufferedWriter(
                    configPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                ).use {
                    it.write(appJson.encodeToString(params))
                }
            log { "Saving config to: $configPath successfully." }
        } catch (e: Exception) {
            System.err.println("❌ Failed to save session config to $configPath: ${e.message}")
        }
    }
}
