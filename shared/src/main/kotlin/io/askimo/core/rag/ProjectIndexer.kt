/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.exception.ModelNotFoundException
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.repository.ProjectRepository
import io.askimo.core.context.AppContext
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectDeletedEvent
import io.askimo.core.event.internal.ProjectIndexingRequestedEvent
import io.askimo.core.event.internal.ProjectReIndexEvent
import io.askimo.core.event.user.IndexingCompletedEvent
import io.askimo.core.event.user.IndexingFailedEvent
import io.askimo.core.event.user.IndexingStartedEvent
import io.askimo.core.logging.logger
import io.askimo.core.rag.indexing.IndexingCoordinator
import io.askimo.core.rag.indexing.IndexingCoordinatorFactory
import io.askimo.core.rag.state.IndexStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for project indexing with RAG (Retrieval-Augmented Generation).
 */
class ProjectIndexer(
    private val appContext: AppContext,
    private val projectRepository: ProjectRepository,
) : Closeable {
    private val log = logger<ProjectIndexer>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val coordinators = ConcurrentHashMap<String, IndexingCoordinator>()
    init {
        scope.launch {
            EventBus.internalEvents
                .filterIsInstance<ProjectDeletedEvent>()
                .collect { event ->
                    log.info("Project deleted, cleaning up coordinator: ${event.projectId}")
                    removeCoordinator(event.projectId, deleteIndexFiles = true)
                }
        }

        scope.launch {
            EventBus.internalEvents
                .filterIsInstance<ProjectReIndexEvent>()
                .collect { event ->
                    log.info("Re-index requested for project ${event.projectId}: ${event.reason}")
                    handleReIndexRequest(event)
                }
        }

        scope.launch {
            EventBus.internalEvents
                .filterIsInstance<ProjectIndexingRequestedEvent>()
                .collect { event ->
                    log.info("Indexing requested for project ${event.projectId}")
                    handleIndexingRequest(event)
                }
        }
    }

    /**
     * Remove coordinator and cleanup resources
     * @param projectId The project ID
     * @param deleteIndexFiles If true, also delete the index files from disk
     */
    fun removeCoordinator(projectId: String, deleteIndexFiles: Boolean = false) {
        coordinators.remove(projectId)?.close() // Coordinator handles cleanup including file watcher

        LuceneIndexer.removeInstance(projectId)

        if (deleteIndexFiles) {
            try {
                val indexDir = RagUtils.getProjectIndexDir(projectId, createIfNotExists = false)
                if (indexDir.toFile().exists()) {
                    indexDir.toFile().deleteRecursively()
                    log.info("Deleted index files for project $projectId at $indexDir")
                }
            } catch (e: Exception) {
                log.error("Failed to delete index files for project $projectId", e)
            }
        }
    }

    /**
     * Common indexing logic used by both initial indexing and re-indexing.
     */
    private suspend fun performIndexing(
        projectId: String,
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel,
        watchForChanges: Boolean,
    ) {
        val project = try {
            projectRepository.getProject(projectId)
        } catch (e: Exception) {
            log.error("Failed to get project $projectId for indexing", e)
            return
        }

        if (project == null) {
            log.warn("Project $projectId not found, cannot index")
            return
        }

        // Create coordinator using factory with typed knowledge sources
        val coordinator = try {
            IndexingCoordinatorFactory.createCoordinator(
                projectId = projectId,
                projectName = project.name,
                knowledgeSources = project.knowledgeSources,
                embeddingStore = embeddingStore,
                embeddingModel = embeddingModel,
                appContext = appContext,
            )
        } catch (e: Exception) {
            log.error("Failed to create indexing coordinator for project $projectId", e)
            EventBus.emit(
                IndexingFailedEvent(
                    projectId = projectId,
                    projectName = project.name,
                    errorMessage = e.message ?: "Failed to create indexing coordinator",
                ),
            )
            return
        }

        coordinators[projectId] = coordinator

        EventBus.emit(
            IndexingStartedEvent(
                projectId = projectId,
                projectName = project.name,
            ),
        )

        val success = coordinator.startIndexing()

        if (success) {
            if (watchForChanges) {
                coordinator.startWatching(scope)
            }

            EventBus.emit(
                IndexingCompletedEvent(
                    projectId = projectId,
                    projectName = project.name,
                    filesIndexed = coordinator.progress.value.processedFiles,
                ),
            )
        } else {
            val error = coordinator.progress.value.error ?: "Unknown error"
            EventBus.emit(
                IndexingFailedEvent(
                    projectId = projectId,
                    projectName = project.name,
                    errorMessage = error,
                ),
            )
        }

        log.info("Indexing ${if (success) "completed" else "failed"} for project $projectId")
    }

    /**
     * Handle re-index request event
     */
    private suspend fun handleReIndexRequest(event: ProjectReIndexEvent) {
        try {
            val projectId = event.projectId

            val existingCoordinator = coordinators[projectId]

            if (existingCoordinator != null && existingCoordinator.progress.value.isComplete) {
                log.debug("Project $projectId already indexed, removing and re-indexing")
                removeCoordinator(projectId, deleteIndexFiles = true)
            }

            if (existingCoordinator != null && existingCoordinator.progress.value.status == IndexStatus.INDEXING) {
                log.debug("Project $projectId is currently being indexed, skipping duplicate re-index request")
                return
            }

            removeCoordinator(projectId, deleteIndexFiles = true)
            log.info("Removed existing coordinator and deleted index files for project $projectId")

            // Create new embedding components for re-indexing
            val embeddingModel = getEmbeddingModel(appContext)
            checkEmbeddingModelAvailable(embeddingModel)

            val embeddingStore = getEmbeddingStore(projectId, embeddingModel)

            performIndexing(
                projectId = projectId,
                embeddingStore = embeddingStore,
                embeddingModel = embeddingModel,
                watchForChanges = true,
            )

            log.info("Re-indexing initiated for project $projectId")
        } catch (e: Exception) {
            log.error("Failed to handle re-index request for project ${event.projectId}", e)
        }
    }

    /**
     * Handle project indexing request event.
     * This method receives embedding components from the requester (e.g., ChatSessionService)
     * and uses them directly to create the indexer instance, avoiding duplicate initialization.
     * Uses the same duplicate prevention logic as handleReIndexRequest to prevent race conditions.
     */
    private suspend fun handleIndexingRequest(event: ProjectIndexingRequestedEvent) {
        try {
            val projectId = event.projectId

            // Check for duplicate/in-progress indexing (same as handleReIndexRequest)
            val existingCoordinator = coordinators[projectId]

            if (existingCoordinator != null && existingCoordinator.progress.value.isComplete) {
                log.debug("Project $projectId already indexed, skipping duplicate request")
                return
            }

            if (existingCoordinator != null && existingCoordinator.progress.value.status == IndexStatus.INDEXING) {
                log.debug("Project $projectId is currently being indexed, skipping duplicate request")
                return
            }

            // Create embedding model and store if not provided
            val embeddingModel = event.embeddingModel ?: run {
                val model = getEmbeddingModel(appContext)
                checkEmbeddingModelAvailable(model)
                model
            }

            val embeddingStore = event.embeddingStore ?: run {
                getEmbeddingStore(projectId, embeddingModel)
            }

            performIndexing(
                projectId = projectId,
                embeddingStore = embeddingStore,
                embeddingModel = embeddingModel,
                watchForChanges = event.watchForChanges,
            )
        } catch (e: Exception) {
            log.error("Failed to handle indexing request for project ${event.projectId}", e)

            EventBus.emit(
                IndexingFailedEvent(
                    projectId = event.projectId,
                    projectName = event.projectId,
                    errorMessage = e.message ?: "Unknown error",
                ),
            )
        }
    }

    /**
     * Check if a project has been successfully indexed
     * @param projectId The project ID to check
     * @return true if the project has a valid index, false otherwise
     */
    fun isProjectIndexed(projectId: String): Boolean {
        // Check if coordinator exists and indexing is complete
        val coordinator = coordinators[projectId]
        if (coordinator != null && coordinator.progress.value.isComplete) {
            return true
        }

        // Check if index files exist on disk (for projects indexed in previous sessions)
        val indexDir = RagUtils.getProjectIndexDir(projectId, createIfNotExists = false)
        if (!indexDir.toFile().exists()) {
            return false
        }

        // Verify the index is valid by checking for essential files
        val indexFiles = indexDir.toFile().listFiles() ?: return false
        return indexFiles.any { it.name.startsWith("segments_") } // Lucene segment files indicate valid index
    }

    /**
     * Get the current indexing status for a project
     * @param projectId The project ID to check
     * @return IndexStatus (NOT_STARTED, INDEXING, READY, WATCHING, FAILED)
     */
    fun getProjectIndexStatus(projectId: String): IndexStatus {
        val coordinator = coordinators[projectId]
        if (coordinator != null) {
            return coordinator.progress.value.status
        }

        // Check disk if no active coordinator
        return if (isProjectIndexed(projectId)) {
            IndexStatus.READY
        } else {
            IndexStatus.NOT_STARTED
        }
    }

    /**
     * Check if embedding model is available and functional
     * @throws ModelNotFoundException if the model is not found or unavailable
     */
    private fun checkEmbeddingModelAvailable(embeddingModel: EmbeddingModel) {
        try {
            embeddingModel.embed(TextSegment.from("ping"))
        } catch (e: Exception) {
            if (e is ModelNotFoundException ||
                e.message?.contains("model not found", ignoreCase = true) == true
            ) {
                log.error("Embedding model not found: ${e.message}")
                throw ModelNotFoundException("Embedding model not found: ${e.message}")
            } else {
                throw e
            }
        }
    }

    override fun close() {
        log.info("Closing ProjectIndexer, cleaning up ${coordinators.size} coordinators")
        coordinators.values.forEach { it.close() }
        coordinators.clear()
    }

    /**
     * Shutdown hook for cleanup
     */
    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runBlocking {
                    close()
                }
            },
        )
    }
}
