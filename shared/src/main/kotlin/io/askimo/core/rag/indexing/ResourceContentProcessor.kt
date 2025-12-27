/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.segment.TextSegment
import io.askimo.core.context.AppContext
import io.askimo.core.rag.extraction.FileResourceIdentifier
import io.askimo.core.rag.extraction.LocalFileContentExtractor
import java.nio.file.Path
import kotlin.io.path.extension

/**
 * Handles file processing by coordinating content extraction and text processing.
 * Delegates to:
 * - ContentExtractor: for extracting text from files
 * - TextProcessor: for chunking and creating text segments
 *
 * This class acts as a facade that maintains backward compatibility with existing code.
 */
class ResourceContentProcessor(
    private val appContext: AppContext,
) {

    /**
     * Content extractor for extracting text from local files.
     */
    private val contentExtractor = LocalFileContentExtractor()

    /**
     * Text processor for chunking and creating segments.
     */
    private val textProcessor = TextProcessor(appContext)

    /**
     * Extract text from a file using ContentExtractor.
     * Supports text files, PDF, DOCX, and other formats via Apache Tika.
     */
    fun extractTextFromFile(filePath: Path): String? {
        val resourceIdentifier = FileResourceIdentifier(filePath)
        return contentExtractor.extractContent(resourceIdentifier)
    }

    /**
     * Chunk text into segments using dynamically calculated chunk size and overlap.
     * Delegates to TextProcessor.
     */
    fun chunkText(text: String): List<String> = textProcessor.chunkText(text)

    /**
     * Create a TextSegment with file-specific metadata.
     * Note: Caller must ensure chunk is not blank.
     */
    fun createTextSegment(
        chunk: String,
        filePath: Path,
        chunkIndex: Int,
        totalChunks: Int,
    ): TextSegment {
        val absolutePath = filePath.toAbsolutePath()

        // Build file-specific metadata
        val metadata = mapOf(
            "file_path" to absolutePath.toString().replace('\\', '/'),
            "file_name" to filePath.fileName.toString(),
            "extension" to filePath.extension,
            "chunk_index" to chunkIndex.toString(),
            "chunk_total" to totalChunks.toString(),
        )

        // Delegate to TextProcessor for segment creation
        return textProcessor.createTextSegment(chunk, metadata)
    }
}
