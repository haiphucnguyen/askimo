/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.telemetry

import io.askimo.core.logging.logger
import io.askimo.core.util.AskimoHome
import io.askimo.core.util.appJson
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Manages persistence of telemetry data to disk.
 * Saves and loads telemetry metrics to/from a JSON file in the user's home directory.
 */
object TelemetryPersistenceManager {
    private val log = logger<TelemetryPersistenceManager>()

    /** Path to the telemetry data file */
    private val telemetryPath: Path = AskimoHome.base().resolve("telemetry.json")

    /**
     * Saves telemetry metrics to disk.
     *
     * @param metrics The metrics to save
     * @return true if save was successful, false otherwise
     */
    fun save(metrics: TelemetryMetrics): Boolean = try {
        Files.createDirectories(telemetryPath.parent)

        val json = appJson.encodeToString(TelemetryMetrics.serializer(), metrics)
        Files.writeString(
            telemetryPath,
            json,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        log.debug("Telemetry saved to $telemetryPath")
        true
    } catch (e: Exception) {
        log.warn("Failed to save telemetry to $telemetryPath: ${e.message}", e)
        false
    }

    /**
     * Loads telemetry metrics from disk.
     *
     * @return Loaded metrics, or empty metrics if file doesn't exist or fails to load
     */
    fun load(): TelemetryMetrics {
        try {
            if (!Files.exists(telemetryPath)) {
                log.debug("Telemetry file not found at $telemetryPath. Starting with empty metrics.")
                return TelemetryMetrics.empty()
            }

            val json = Files.readString(telemetryPath)
            val loaded = appJson.decodeFromString<TelemetryMetrics>(json)

            log.debug("Telemetry loaded from $telemetryPath: ${loaded.ragClassificationTotal} classifications, ${loaded.totalTokensUsed} tokens")
            return loaded
        } catch (e: SerializationException) {
            log.warn("Failed to parse telemetry file at $telemetryPath. Using empty metrics.", e)
            return TelemetryMetrics.empty()
        } catch (e: Exception) {
            log.warn("Failed to load telemetry from $telemetryPath: ${e.message}", e)
            return TelemetryMetrics.empty()
        }
    }

    /**
     * Deletes the telemetry file from disk.
     *
     * @return true if deletion was successful, false otherwise
     */
    fun delete(): Boolean = try {
        if (Files.exists(telemetryPath)) {
            Files.delete(telemetryPath)
            log.debug("Telemetry file deleted: $telemetryPath")
            true
        } else {
            log.debug("Telemetry file does not exist: $telemetryPath")
            false
        }
    } catch (e: Exception) {
        log.warn("Failed to delete telemetry file at $telemetryPath: ${e.message}", e)
        false
    }
}
