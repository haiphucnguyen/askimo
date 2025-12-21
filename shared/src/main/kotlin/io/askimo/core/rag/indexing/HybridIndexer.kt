/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import io.askimo.core.rag.FileSegmentRepository
import io.askimo.core.rag.LuceneKeywordRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.UUID

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
    private val keywordRetriever: LuceneKeywordRetriever,
    private val projectId: String,
) {
    private val log = logger<HybridIndexer>()
    private val segmentRepository: FileSegmentRepository by lazy {
        DatabaseManager.getInstance().getFileSegmentRepository()
    }

    companion object {
        private const val BATCH_SIZE = 50
    }

    private val segmentBatch = mutableListOf<Pair<TextSegment, Path>>() // Track file path with segment
    private val pendingMappings = mutableListOf<Triple<Path, String, Int>>() // (filePath, segmentId, chunkIndex)

    /**
     * Index a batch of text segments into both stores (legacy method for compatibility):
     * 1. Generate embeddings and store in JVector (semantic search)
     * 2. Index text content in Lucene (keyword search)
     *
     * @param textSegments List of text segments to index
     */
    fun indexSegments(textSegments: List<TextSegment>) {
        if (textSegments.isEmpty()) {
            log.debug("No segments to index")
            return
        }

        try {
            // 1. Batch embed all segments in a single API call
            val embeddings = embeddingModel.embedAll(textSegments).content()

            // 2. Store embeddings in JVector (vector store)
            embeddings.forEachIndexed { index, embedding ->
                embeddingStore.add(embedding, textSegments[index])
            }

            // 3. Index in Lucene (keywords for BM25 search)
            keywordRetriever.indexSegments(textSegments)

            log.debug("Hybrid indexed ${textSegments.size} segments (vector + keyword) in batch")
        } catch (e: Exception) {
            log.error("Failed to hybrid index ${textSegments.size} segments", e)
            throw e
        }
    }

    /**
     * Add segment to batch and flush if batch is full
     */
    suspend fun addSegmentToBatch(
        segment: TextSegment,
        filePath: Path,
    ): Boolean {
        segmentBatch.add(segment to filePath)

        if (segmentBatch.size >= BATCH_SIZE) {
            return flushSegmentBatch()
        }

        return true
    }

    /**
     * Flush the current batch of segments to both embedding store and keyword index
     */
    suspend fun flushSegmentBatch(): Boolean {
        if (segmentBatch.isEmpty()) {
            return true
        }

        return try {
            val segments = segmentBatch.map { it.first }
            val filePaths = segmentBatch.map { it.second }
            segmentBatch.clear()

            withContext(Dispatchers.IO) {
                val embeddings = mutableListOf<Embedding>()
                val segmentIds = mutableListOf<String>()

                // Generate embeddings for all segments
                for (segment in segments) {
                    try {
                        val embedding = embeddingModel.embed(segment).content()
                        embeddings.add(embedding)

                        // Generate unique segment ID
                        val segmentId = generateSegmentId(segment)
                        segmentIds.add(segmentId)
                    } catch (e: Exception) {
                        log.error("Failed to generate embedding for segment: ${e.message}", e)
                        throw e
                    }
                }

                // Add to embedding store with IDs
                embeddingStore.addAll(embeddings, segments)

                // Index in Lucene (keywords for BM25 search)
                keywordRetriever.indexSegments(segments)

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
            log.error("Failed to flush segment batch for project {}: {}", projectId, e.message, e)
            false
        }
    }

    /**
     * Flush any remaining segments in the batch
     */
    suspend fun flushRemainingSegments(): Boolean = if (segmentBatch.isNotEmpty()) {
        log.debug("Flushing {} remaining segments", segmentBatch.size)
        flushSegmentBatch()
    } else {
        savePendingMappings()
        true
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
                keywordRetriever.removeFile(filePath.toString())
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

    /**
     * Generate a unique segment ID
     */
    private fun generateSegmentId(segment: TextSegment): String {
        val filePath = segment.metadata().getString("file_path") ?: "unknown"
        val chunkIndex = segment.metadata().getInteger("chunk_index") ?: 0
        return "$projectId:$filePath:$chunkIndex:${UUID.randomUUID()}"
    }
}
