/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.service

import io.askimo.core.logging.logger
import io.askimo.core.util.ProcessBuilderExt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

/**
 * Exception thrown when Mermaid CLI is not available on the system.
 */
class MermaidCliNotAvailableException(message: String) : Exception(message)

/**
 * Service for converting Mermaid diagrams to SVG using local Mermaid CLI.
 *
 * This service uses the locally installed mermaid-cli (mmdc) to render Mermaid diagrams
 * as SVG images. This approach ensures privacy by keeping all data local and works offline.
 */
class MermaidSvgService {
    private val log = logger<MermaidSvgService>()

    companion object {
        private val log = logger<MermaidSvgService>()

        // Cache the availability check result to avoid multiple checks
        @Volatile
        private var cachedAvailability: Boolean? = null

        // Lock for synchronizing the availability check
        private val availabilityLock = Any()

        /**
         * Checks if Mermaid CLI is available on the system.
         * This result is cached after the first check to avoid multiple expensive checks.
         *
         * @return true if mermaid-cli is installed and accessible
         */
        fun isMermaidCliAvailable(): Boolean {
            cachedAvailability?.let { return it }

            return synchronized(availabilityLock) {
                cachedAvailability?.let { return@synchronized it }

                val result = try {
                    log.debug("Checking if Mermaid CLI is available...")

                    val nodeCheck = ProcessBuilderExt("node", "--version")
                        .redirectErrorStream(true)
                        .start()

                    val nodeAvailable = nodeCheck.waitFor(5, TimeUnit.SECONDS) && nodeCheck.exitValue() == 0

                    if (!nodeAvailable) {
                        log.debug("Node.js not found - Mermaid CLI unavailable")
                        cachedAvailability = false
                        return@synchronized false
                    }

                    log.debug("Node.js found, checking mmdc (Mermaid CLI)...")

                    val process = ProcessBuilderExt("mmdc", "--version")
                        .redirectErrorStream(true)
                        .start()

                    // Read output in a separate thread to avoid blocking
                    val outputBuilder = StringBuilder()
                    val outputReader = Thread {
                        process.inputStream.bufferedReader().use { reader ->
                            reader.lineSequence().forEach { line ->
                                outputBuilder.append(line).append("\n")
                            }
                        }
                    }
                    outputReader.start()

                    val completed = process.waitFor(10, TimeUnit.SECONDS)
                    outputReader.join(1000)

                    if (!completed) {
                        log.warn("Mermaid CLI check timed out")
                        process.destroyForcibly()
                        cachedAvailability = false
                        return@synchronized false
                    }

                    val exitCode = process.exitValue()
                    val output = outputBuilder.toString().trim()
                    val available = exitCode == 0

                    if (available) {
                        log.info("Mermaid CLI (mmdc) is available. Version: {}", output)
                    } else {
                        log.warn("Mermaid CLI (mmdc) check failed with exit code: {}. Output: {}", exitCode, output)
                    }
                    available
                } catch (e: Exception) {
                    log.debug("Mermaid CLI not available: {}", e.message)
                    false
                }

                cachedAvailability = result
                result
            }
        }
    }

    /**
     * Checks if Mermaid CLI is available on the system.
     *
     * @return true if mermaid-cli is installed and accessible
     */
    fun isMermaidCliAvailable(): Boolean = Companion.isMermaidCliAvailable()

    /**
     * Converts a Mermaid diagram to PNG using local Mermaid CLI.
     *
     * @param diagram The Mermaid diagram definition
     * @param theme The theme to use (default, dark, forest, neutral)
     * @param backgroundColor The background color as hex string (e.g., "#ffffff")
     * @return The PNG content as a byte array
     * @throws MermaidCliNotAvailableException if mermaid-cli is not installed
     * @throws IOException if the conversion fails
     */
    suspend fun convertToPng(diagram: String, theme: String = "default", backgroundColor: String = "#ffffff"): ByteArray = withContext(Dispatchers.IO) {
        if (!isMermaidCliAvailable()) {
            throw MermaidCliNotAvailableException(
                "Mermaid CLI (mmdc) is not available. Please install it globally: npm install -g @mermaid-js/mermaid-cli",
            )
        }

        val tempDir = Files.createTempDirectory("askimo-mermaid")
        val inputFile = tempDir.resolve("diagram.mmd")
        val outputFile = tempDir.resolve("diagram.png")

        try {
            // Replace literal escape sequences with actual characters
            // This handles cases where the diagram comes from JSON with escaped characters
            val normalizedDiagram = diagram
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")

            val diagramWithTheme = "$normalizedDiagram"

            Files.writeString(inputFile, diagramWithTheme, StandardOpenOption.CREATE)

            // Run mmdc with command-line parameters instead of config file
            val process = ProcessBuilderExt(
                "mmdc",
                "-i", inputFile.toString(),
                "-o", outputFile.toString(),
                "-b", backgroundColor,
                "-s", "2",
            ).redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()

            val exitCode = process.waitFor(30, TimeUnit.SECONDS)

            if (!exitCode || process.exitValue() != 0) {
                log.error("Mermaid CLI failed with exit code: {}, output: {}", process.exitValue(), output)
                throw IOException("Failed to convert Mermaid diagram: CLI returned exit code ${process.exitValue()}")
            }

            if (!Files.exists(outputFile)) {
                throw IOException("Mermaid CLI did not produce output file")
            }

            val pngData = Files.readAllBytes(outputFile)
            log.debug("Successfully converted diagram to PNG (size: {} bytes)", pngData.size)

            pngData
        } catch (e: MermaidCliNotAvailableException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to convert Mermaid diagram to PNG", e)
            throw IOException("Failed to convert Mermaid diagram: ${e.message}", e)
        } finally {
            try {
                Files.deleteIfExists(inputFile)
                Files.deleteIfExists(outputFile)
                Files.deleteIfExists(tempDir)
            } catch (e: Exception) {
                log.warn("Failed to clean up temp files", e)
            }
        }
    }
}
