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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.UUID

/**
 * Handles batching of segments for efficient embedding generation and tracks
 * file-to-segment mappings for removal support.
 */
class BatchIndexer(
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val embeddingModel: EmbeddingModel,
    private val projectId: String,
) {
    private val log = logger<BatchIndexer>()
    private val segmentRepository: FileSegmentRepository by lazy {
        DatabaseManager.getInstance().getFileSegmentRepository()
    }

    companion object {
        private const val BATCH_SIZE = 50
    }

    private val segmentBatch = mutableListOf<Pair<TextSegment, Path>>() // Track file path with segment
    private val pendingMappings = mutableListOf<Triple<Path, String, Int>>() // (filePath, segmentId, chunkIndex)

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
     * Flush the current batch of segments
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

                log.debug("Indexed batch of {} segments for project {}", segments.size, projectId)
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
     * Remove all segments for a file from both the embedding store and tracking database
     */
    fun removeFileFromIndex(filePath: Path) {
        try {
            val segmentIds = segmentRepository.getSegmentIdsForFile(projectId, filePath)

            if (segmentIds.isNotEmpty()) {
                log.debug("Found {} segments for file {} - removing from index", segmentIds.size, filePath.fileName)
                embeddingStore.removeAll(segmentIds)
                log.debug("Removed {} embeddings from vector store", segmentIds.size)
                val removed = segmentRepository.removeSegmentMappingsForFile(projectId, filePath)
                log.debug("Removed {} segment mappings from database for file {}", removed, filePath.fileName)
            } else {
                log.debug("No segments found for file {}", filePath.fileName)
            }
        } catch (e: Exception) {
            log.error("Failed to remove file from index: {}", filePath.fileName, e)
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
