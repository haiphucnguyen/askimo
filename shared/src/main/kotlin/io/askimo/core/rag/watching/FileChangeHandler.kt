/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.watching

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.context.AppContext
import io.askimo.core.logging.logger
import io.askimo.core.rag.filter.FilterChain
import io.askimo.core.rag.indexing.HybridIndexer
import io.askimo.core.rag.indexing.ResourceContentProcessor
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Handles file change events from the file watcher
 */
class FileChangeHandler(
    private val projectId: String,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val embeddingModel: EmbeddingModel,
    private val appContext: AppContext,
) {
    private val log = logger<FileChangeHandler>()
    private val resourceContentProcessor = ResourceContentProcessor(appContext)
    private val batchIndexer = HybridIndexer(embeddingStore, embeddingModel, projectId)

    private val filterChain: FilterChain = FilterChain.DEFAULT

    /**
     * Check if a path should be excluded from indexing
     */
    private fun shouldExcludePath(path: Path): Boolean = filterChain.shouldExcludePath(path)

    /**
     * Handle a file change event
     */
    suspend fun handleFileChange(filePath: Path, kind: WatchEvent.Kind<*>) {
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            log.debug("File deleted: {}, removing from index...", filePath.fileName)
            batchIndexer.removeFileFromIndex(filePath)
            return
        }

        // For CREATE/MODIFY events, check if file exists and is not excluded
        if (!filePath.isRegularFile() || shouldExcludePath(filePath)) {
            return
        }

        when (kind) {
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            -> {
                log.debug("File changed: {}, re-indexing...", filePath.fileName)
                reindexFile(filePath)
            }
        }
    }

    /**
     * Re-index a file
     */
    private suspend fun reindexFile(filePath: Path) {
        if (!filePath.exists()) {
            log.warn("Cannot re-index non-existent file: ${filePath.fileName}")
            return
        }

        try {
            batchIndexer.removeFileFromIndex(filePath)

            val text = resourceContentProcessor.extractTextFromFile(filePath) ?: return

            if (text.isBlank()) {
                log.debug("Skipping re-index of file with blank content: ${filePath.fileName}")
                return
            }

            // Check if this is a text file where line numbers are meaningful
            val isTextFile = resourceContentProcessor.isTextFile(filePath)

            if (isTextFile) {
                // For text files, use line-aware chunking
                val chunksWithLineNumbers = resourceContentProcessor.chunkTextWithLineNumbers(text)

                if (chunksWithLineNumbers.isEmpty()) {
                    log.debug("No valid chunks created for file: ${filePath.fileName}")
                    return
                }

                for ((idx, chunkData) in chunksWithLineNumbers.withIndex()) {
                    val segment = resourceContentProcessor.createTextSegmentWithMetadata(
                        chunk = chunkData.text,
                        filePath = filePath,
                        chunkIndex = idx,
                        totalChunks = chunksWithLineNumbers.size,
                        startLine = chunkData.startLine,
                        endLine = chunkData.endLine,
                    )

                    batchIndexer.addSegmentToBatch(segment, filePath)
                }

                batchIndexer.flushRemainingSegments()

                log.debug("Re-indexed {} ({} chunks, lines tracked)", filePath.fileName, chunksWithLineNumbers.size)
            } else {
                val chunks = resourceContentProcessor.chunkText(text)

                if (chunks.isEmpty()) {
                    log.debug("No valid chunks created for file: ${filePath.fileName}")
                    return
                }

                for ((idx, chunk) in chunks.withIndex()) {
                    val segment = resourceContentProcessor.createTextSegmentWithMetadata(
                        chunk = chunk,
                        filePath = filePath,
                        chunkIndex = idx,
                        totalChunks = chunks.size,
                    )

                    batchIndexer.addSegmentToBatch(segment, filePath)
                }

                batchIndexer.flushRemainingSegments()

                log.debug("Re-indexed {} ({} chunks)", filePath.fileName, chunks.size)
            }
        } catch (e: Exception) {
            log.error("Failed to re-index file {}", filePath.fileName, e)
        }
    }
}
