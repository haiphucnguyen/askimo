/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import io.askimo.core.chat.util.FileContentExtractor
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.logging.logger
import io.askimo.core.rag.getModelTokenLimit
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * Handles file processing: reading, text extraction, and chunking.
 * Dynamically calculates optimal chunk size based on the embedding model's token limit.
 */
class FileProcessor(
    private val appContext: AppContext,
) {
    private val log = logger<FileProcessor>()

    /**
     * Dynamically calculated maximum characters per chunk based on the embedding model's token limit.
     * Uses 80% of the model's token limit as a safety buffer, with ~4 chars per token as the conversion ratio.
     */
    private val maxCharsPerChunk: Int by lazy {
        calculateSafeMaxChars()
    }

    /**
     * Dynamically calculated chunk overlap (5% of chunk size, between 50 and configured max).
     */
    private val chunkOverlap: Int by lazy {
        val calculatedOverlap = (maxCharsPerChunk * 0.05).toInt()
        val configuredMax = AppConfig.embedding.chunkOverlap
        val minOverlap = 50

        calculatedOverlap.coerceIn(minOverlap, configuredMax).also {
            log.info("Calculated chunk overlap: $it chars (${(it.toFloat() / maxCharsPerChunk * 100).toInt()}% of chunk size)")
        }
    }

    /**
     * Calculate safe maximum characters per chunk based on the embedding model's token limit.
     */
    private fun calculateSafeMaxChars(): Int {
        val tokenLimit = try {
            getModelTokenLimit(appContext)
        } catch (e: Exception) {
            log.warn("Failed to get model token limit, using default from config: ${e.message}")
            return AppConfig.embedding.maxCharsPerChunk
        }

        // Use 80% of token limit as safety buffer
        val safeTokenLimit = (tokenLimit * 0.8).toInt()

        // Convert tokens to characters (~4 chars per token)
        val safeChars = safeTokenLimit * 4

        // Respect configured maximum
        val configuredMax = AppConfig.embedding.maxCharsPerChunk
        val minChars = 500

        val calculated = safeChars.coerceIn(minChars, configuredMax)

        log.info(
            "Calculated chunk size: $calculated chars " +
                "(model limit: $tokenLimit tokens, safe limit: $safeTokenLimit tokens, configured max: $configuredMax)",
        )

        return calculated
    }

    /**
     * Extract text from a file using FileContentExtractor.
     * Supports text files, PDF, DOCX, and other formats via Apache Tika.
     */
    fun extractTextFromFile(filePath: Path): String? {
        return try {
            val file = filePath.toFile()

            if (!FileContentExtractor.isSupported(file)) {
                log.debug("Unsupported file type: {} - {}", filePath.fileName, FileContentExtractor.getUnsupportedMessage(file))
                return null
            }

            FileContentExtractor.extractContent(file)
        } catch (e: Exception) {
            log.warn("Failed to extract content from file {}: {}", filePath.fileName, e.message)
            null
        }
    }

    /**
     * Chunk text into segments using dynamically calculated chunk size and overlap.
     */
    fun chunkText(text: String): List<String> {
        val maxChars = maxCharsPerChunk
        val overlap = chunkOverlap

        if (text.length <= maxChars) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var start = 0

        while (start < text.length) {
            val end = minOf(start + maxChars, text.length)
            val chunk = text.substring(start, end)
            chunks.add(chunk)

            start += (maxChars - overlap)

            if (maxChars <= overlap) {
                log.warn("Invalid chunk configuration: maxChars=$maxChars, overlap=$overlap")
                break
            }
        }

        return chunks
    }

    /**
     * Create a TextSegment with metadata
     */
    fun createTextSegment(
        chunk: String,
        filePath: Path,
        chunkIndex: Int,
        totalChunks: Int,
    ): TextSegment {
        val absolutePath = filePath.toAbsolutePath()

        return TextSegment.from(
            chunk,
            Metadata(
                mapOf(
                    "file_path" to absolutePath.toString().replace('\\', '/'),
                    "file_name" to filePath.fileName.toString(),
                    "extension" to filePath.extension,
                    "chunk_index" to chunkIndex.toString(),
                    "chunk_total" to totalChunks.toString(),
                ),
            ),
        )
    }
}
