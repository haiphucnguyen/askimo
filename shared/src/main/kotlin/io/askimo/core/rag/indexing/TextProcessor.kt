/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.logging.logger
import io.askimo.core.rag.getModelTokenLimit

/**
 * Handles generic text processing operations: chunking and segment creation.
 * This class is resource-agnostic - it doesn't care where the text came from
 * (file, web page, SEC filing, etc.). It only processes text.
 *
 * Dynamically calculates optimal chunk size based on the embedding model's token limit.
 */
class TextProcessor(
    private val appContext: AppContext,
) {
    private val log = logger<TextProcessor>()

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
     * Uses conservative ratios that work across all embedding models and languages.
     */
    private fun calculateSafeMaxChars(): Int {
        val tokenLimit = try {
            getModelTokenLimit(appContext)
        } catch (e: Exception) {
            log.warn("Failed to get model token limit, using default from config: ${e.message}")
            return AppConfig.embedding.maxCharsPerChunk
        }

        // Apply 30% safety buffer to handle:
        // - Tokenization overhead (special tokens, padding)
        // - Variability across languages (CJK, emoji, etc.)
        // - Different encoding schemes per model
        // This ultra-conservative approach ensures we NEVER exceed token limits
        val safetyFactor = 0.3
        val safeTokenLimit = (tokenLimit * safetyFactor).toInt()

        // Conservative estimate: 2 characters per token
        // Works for worst-case scenarios:
        // - Non-Latin scripts (CJK: 1-2 chars/token)
        // - Mixed languages and special characters
        // - Code with lots of symbols
        // - Different tokenizers (BPE, WordPiece, SentencePiece)
        val charsPerToken = 2
        val safeChars = safeTokenLimit * charsPerToken

        // Respect configured bounds
        val configuredMax = AppConfig.embedding.maxCharsPerChunk
        val minChars = 500

        val calculated = safeChars.coerceIn(minChars, configuredMax)

        log.debug(
            "Calculated chunk size: $calculated chars " +
                "(model limit: $tokenLimit tokens, " +
                "safe limit: $safeTokenLimit tokens @ ${(safetyFactor * 100).toInt()}% safety, " +
                "ratio: $charsPerToken:1, " +
                "configured max: $configuredMax)",
        )

        return calculated
    }

    /**
     * Chunk text into segments using dynamically calculated chunk size and overlap.
     * Filters out blank chunks to avoid validation errors.
     *
     * This method is resource-agnostic - it works with any text regardless of source.
     */
    fun chunkText(text: String): List<String> {
        if (text.isBlank()) {
            return emptyList()
        }

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

            if (chunk.isNotBlank()) {
                chunks.add(chunk)
            }

            start += (maxChars - overlap)

            if (maxChars <= overlap) {
                log.warn("Invalid chunk configuration: maxChars=$maxChars, overlap=$overlap")
                break
            }
        }

        return chunks
    }

    /**
     * Data class to hold chunk text with line number metadata.
     */
    data class ChunkWithLineNumbers(
        val text: String,
        val startLine: Int,
        val endLine: Int,
    )

    /**
     * Chunk text with line number tracking for text files.
     * This method reads the text line-by-line to track line numbers.
     *
     * @param text The text to chunk
     * @return List of chunks with line number information
     */
    fun chunkTextWithLineNumbers(text: String): List<ChunkWithLineNumbers> {
        if (text.isBlank()) {
            return emptyList()
        }

        val maxChars = maxCharsPerChunk
        val overlap = chunkOverlap

        val lines = text.lines()
        val chunks = mutableListOf<ChunkWithLineNumbers>()

        var currentChunk = StringBuilder()
        var currentStartLine = 1
        var currentLine = 1

        for (line in lines) {
            val lineWithNewline = line + "\n"

            // Handle long lines that exceed maxChars by splitting them
            if (lineWithNewline.length > maxChars) {
                // Save current chunk if it has content
                if (currentChunk.isNotEmpty()) {
                    val chunkText = currentChunk.toString()
                    if (chunkText.isNotBlank()) {
                        chunks.add(ChunkWithLineNumbers(chunkText, currentStartLine, currentLine - 1))
                    }
                    currentChunk.clear()
                }

                // Split the long line into multiple chunks
                var lineStart = 0
                while (lineStart < lineWithNewline.length) {
                    val lineEnd = minOf(lineStart + maxChars, lineWithNewline.length)
                    val linePart = lineWithNewline.substring(lineStart, lineEnd)

                    if (linePart.isNotBlank()) {
                        chunks.add(ChunkWithLineNumbers(linePart, currentLine, currentLine))
                        log.debug(
                            "Split long line {} into chunk of {} chars (line length: {})",
                            currentLine,
                            linePart.length,
                            lineWithNewline.length,
                        )
                    }

                    lineStart += maxChars
                }

                // Move to next line and reset for a fresh chunk
                currentLine++
                currentStartLine = currentLine
                continue
            }

            // If adding this line would exceed max chars, save current chunk and start new one
            if (currentChunk.isNotEmpty() && currentChunk.length + lineWithNewline.length > maxChars) {
                val chunkText = currentChunk.toString()
                if (chunkText.isNotBlank()) {
                    chunks.add(ChunkWithLineNumbers(chunkText, currentStartLine, currentLine - 1))
                }

                // Start new chunk with overlap
                // Calculate how many chars of overlap we need
                val overlapText = if (overlap > 0) {
                    // Take last few lines that fit in overlap size
                    val chunkLines = chunkText.lines()
                    val overlapLines = mutableListOf<String>()
                    var overlapSize = 0

                    for (i in chunkLines.indices.reversed()) {
                        val testLine = chunkLines[i] + "\n"
                        if (overlapSize + testLine.length <= overlap) {
                            overlapLines.add(0, chunkLines[i])
                            overlapSize += testLine.length
                        } else {
                            break
                        }
                    }

                    if (overlapLines.isNotEmpty()) {
                        currentStartLine = currentLine - overlapLines.size
                        overlapLines.joinToString("\n") + "\n"
                    } else {
                        currentStartLine = currentLine
                        ""
                    }
                } else {
                    currentStartLine = currentLine
                    ""
                }

                currentChunk = StringBuilder(overlapText)
            }

            currentChunk.append(lineWithNewline)
            currentLine++
        }

        // Add final chunk
        if (currentChunk.isNotEmpty()) {
            val chunkText = currentChunk.toString()
            if (chunkText.isNotBlank()) {
                chunks.add(ChunkWithLineNumbers(chunkText, currentStartLine, currentLine - 1))
            }
        }

        return chunks
    }

    /**
     * Create a TextSegment with metadata.
     *
     * This is a generic method that creates segments with arbitrary metadata.
     * Note: Caller must ensure chunk is not blank.
     *
     * @param chunk The text chunk
     * @param metadata Map of metadata key-value pairs
     * @return TextSegment with the provided metadata
     */
    fun createTextSegment(
        chunk: String,
        metadata: Map<String, String>,
    ): TextSegment = TextSegment.from(
        chunk,
        Metadata(metadata),
    )
}
