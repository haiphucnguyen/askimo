/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.community.store.embedding.jvector.JVectorEmbeddingStore
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.repository.ResourceSegmentRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import io.askimo.core.rag.LuceneIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Coordinates hybrid indexing of text segments into both:
 * - JVector embedding store (for semantic/vector search)
 * - Lucene keyword index (for BM25/keyword search)
 *
 * Handles batching for efficient embedding generation and tracks
 * file-to-segment mappings for removal support.
 */
class HybridIndexer(
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val embeddingModel: EmbeddingModel,
    private val projectId: String,
) {
    private val log = logger<HybridIndexer>()
    private val luceneIndexer = LuceneIndexer.getInstance(projectId)
    private val segmentRepository: ResourceSegmentRepository by lazy {
        DatabaseManager.getInstance().getResourceSegmentRepository()
    }

    companion object {
        private const val BATCH_SIZE = 100
    }

    // Mutex for thread-safe batch operations
    private val batchMutex = Mutex()

    private val segmentBatch = mutableListOf<Pair<TextSegment, Path>>() // Track file path with segment
    private val pendingMappings = mutableListOf<Triple<Path, String, Int>>() // (filePath, segmentId, chunkIndex)

    /**
     * Add segment to batch and flush if batch is full (thread-safe)
     */
    suspend fun addSegmentToBatch(
        segment: TextSegment,
        filePath: Path,
    ): Boolean = batchMutex.withLock {
        log.trace("Adding segment {} to batch for project {}", segment.metadata().getString("file_name"), projectId)
        segmentBatch.add(segment to filePath)

        if (segmentBatch.size >= BATCH_SIZE) {
            return flushSegmentBatchInternal()
        }

        return true
    }

    /**
     * Internal flush method (must be called with lock held)
     */
    private suspend fun flushSegmentBatchInternal(): Boolean {
        if (segmentBatch.isEmpty()) {
            return true
        }

        val segments = segmentBatch.map { it.first }
        val filePaths = segmentBatch.map { it.second }
        segmentBatch.clear()

        return try {
            withContext(Dispatchers.IO) {
                val embeddings = embeddingModel.embedAll(segments).content()

                val segmentIds = segments.map { generateSegmentId(it) }

                embeddingStore.addAll(embeddings, segments)
                (embeddingStore as JVectorEmbeddingStore).save()

                luceneIndexer.indexSegments(segments)

                // Track segment IDs in database for future removal
                for (i in segments.indices) {
                    val segment = segments[i]
                    val filePath = filePaths[i]
                    val segmentId = segmentIds[i]
                    val chunkIndex = segment.metadata().getInteger("chunk_index") ?: 0

                    pendingMappings.add(Triple(filePath, segmentId, chunkIndex))
                }

                // Save mappings to database
                savePendingMappings()

                log.debug("Hybrid indexed batch of {} segments (vector + keyword) for project {}", segments.size, projectId)
            }

            true
        } catch (e: Exception) {
            // Find the largest segment in the batch to help debug token limit errors
            val maxSegmentChars = segments.maxOfOrNull { it.text().length } ?: 0
            val largestSegmentIndex = segments.indexOfFirst { it.text().length == maxSegmentChars }
            val largestSegment = if (largestSegmentIndex >= 0) segments[largestSegmentIndex] else null

            log.error(
                "Failed to flush segment batch for project {}: {} - " +
                    "Batch size: {} segments, " +
                    "Largest segment: {} chars ({} est. tokens), " +
                    "File: {}, Chunk: {}, Index in batch: {}",
                projectId,
                e.message,
                segments.size,
                maxSegmentChars,
                maxSegmentChars / 2,
                largestSegment?.metadata()?.getString("file_name") ?: "unknown",
                largestSegment?.metadata()?.getInteger("chunk_index") ?: 0,
                largestSegmentIndex,
                e,
            )

            false
        }
    }

    /**
     * Flush any remaining segments in the batch (thread-safe)
     */
    suspend fun flushRemainingSegments(): Boolean = batchMutex.withLock {
        if (segmentBatch.isNotEmpty()) {
            log.debug("Flushing {} remaining segments", segmentBatch.size)
            flushSegmentBatchInternal()
        } else {
            savePendingMappings()
            true
        }
    }

    /**
     * Save pending segment mappings to database
     */
    private fun savePendingMappings() {
        if (pendingMappings.isEmpty()) return

        try {
            // Group by file path for batch insertion
            val groupedByFile = pendingMappings.groupBy { it.first }

            for ((filePath, mappings) in groupedByFile) {
                val segmentData = mappings.map { (_, segmentId, chunkIndex) ->
                    segmentId to chunkIndex
                }
                segmentRepository.saveSegmentMappings(projectId, filePath, segmentData)
            }

            log.debug("Saved {} segment mappings to database", pendingMappings.size)
            pendingMappings.clear()
        } catch (e: Exception) {
            log.error("Failed to save segment mappings: {}", e.message, e)
        }
    }

    /**
     * Remove all segments for a file from both the embedding store, keyword index, and tracking database
     */
    fun removeFileFromIndex(filePath: Path) {
        try {
            val segmentIds = segmentRepository.getSegmentIdsForFile(projectId, filePath)

            if (segmentIds.isNotEmpty()) {
                log.debug("Found {} segments for file {} - removing from hybrid index", segmentIds.size, filePath.fileName)

                // Remove from embedding store
                embeddingStore.removeAll(segmentIds)
                log.debug("Removed {} embeddings from vector store", segmentIds.size)

                // Remove from keyword index
                luceneIndexer.removeFile(filePath.toString())
                log.debug("Removed segments from keyword index for file {}", filePath.fileName)

                // Remove from database
                val removed = segmentRepository.removeSegmentMappingsForFile(projectId, filePath)
                log.debug("Removed {} segment mappings from database for file {}", removed, filePath.fileName)
            } else {
                log.debug("No segments found for file {}", filePath.fileName)
            }
        } catch (e: Exception) {
            log.error("Failed to remove file from hybrid index: {}", filePath.fileName, e)
        }
    }

    private fun generateSegmentId(segment: TextSegment): String {
        val filePath = segment.metadata().getString("file_path") ?: "unknown"
        val chunkIndex = segment.metadata().getInteger("chunk_index") ?: 0

        // Hash the file path to a fixed 8-character hex string for compact, deterministic IDs
        val fileHash = filePath.hashCode().toString(16).padStart(8, '0')

        return "$projectId:$fileHash:$chunkIndex"
    }
}
