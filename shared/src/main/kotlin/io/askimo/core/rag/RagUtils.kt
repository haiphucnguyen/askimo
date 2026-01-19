/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import io.askimo.core.logging.logger
import io.askimo.core.util.AskimoHome
import java.nio.file.Path

/**
 * Utility functions for RAG (Retrieval-Augmented Generation) module.
 */
object RagUtils {
    private val log = logger<RagUtils>()

    /**
     * Get the index directory path for a project.
     * Creates the directory if it doesn't exist.
     *
     * @param projectId The project ID
     * @param createIfNotExists Whether to create the directory if it doesn't exist (default: true)
     * @return Path to the project's index directory
     */
    fun getProjectIndexDir(projectId: String, createIfNotExists: Boolean = true): Path {
        val indexDir = AskimoHome.projectsDir().resolve(projectId).resolve("index")

        if (createIfNotExists) {
            indexDir.toFile().mkdirs()
        }

        return indexDir
    }

    fun getProjectJVectorIndexDir(projectId: String): Path = getProjectIndexDir(projectId, true).resolve("jvector")

    fun getProjectLuceneIndexDir(projectId: String): Path = getProjectIndexDir(projectId, true).resolve("lucene")

    /**
     * Get embedding dimension for the model by testing it with a sample text.
     * Falls back to 384 (common dimension for many models) if detection fails.
     *
     * @param embeddingModel The embedding model to test
     * @return The dimension of the embedding vectors
     */
    fun getDimensionForModel(embeddingModel: EmbeddingModel): Int = try {
        val testSegment = TextSegment.from("test")
        val embedding = embeddingModel.embed(testSegment).content()
        embedding.vector().size
    } catch (e: Exception) {
        log.warn("Failed to detect embedding dimension, using default 384", e)
        384
    }
}
