/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.exception.ModelNotFoundException
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.repository.ProjectRepository
import io.askimo.core.context.AppContext
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectDeletedEvent
import io.askimo.core.event.internal.ProjectIndexRemovalEvent
import io.askimo.core.event.internal.ProjectIndexingRequestedEvent
import io.askimo.core.event.internal.ProjectReIndexEvent
import io.askimo.core.event.user.IndexingCompletedEvent
import io.askimo.core.event.user.IndexingFailedEvent
import io.askimo.core.event.user.IndexingStartedEvent
import io.askimo.core.logging.logger
import io.askimo.core.rag.indexing.IndexingCoordinator
import io.askimo.core.rag.indexing.IndexingCoordinatorFactory
import io.askimo.core.rag.state.IndexStatus
import io.askimo.core.util.AskimoHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for project indexing with RAG (Retrieval-Augmented Generation).
 * Each project can have multiple coordinators - one per knowledge source.
 */
class ProjectIndexer(
    private val appContext: AppContext,
    private val projectRepository: ProjectRepository,
) {
    private val log = logger<ProjectIndexer>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Map of projectId -> List of coordinators (one per knowledge source)
    private val coordinators = ConcurrentHashMap<String, List<IndexingCoordinator<*>>>()
    init {
        scope.launch {
            EventBus.internalEvents
                .filterIsInstance<ProjectDeletedEvent>()
                .collect { event ->
                    log.info("Project deleted, cleaning up coordinator: ${event.projectId}")
                    removeCoordinator(event.projectId, true)
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

        scope.launch {
            EventBus.internalEvents
                .filterIsInstance<ProjectIndexRemovalEvent>()
                .collect { event ->
                    log.info("Indexing removal requested for project ${event.projectId}")
                    handleRemoveIndexEvent(event)
                }
        }
    }

    /**
     * Remove coordinator and cleanup resources
     * @param projectId The project ID
     */
    private fun removeCoordinator(projectId: String, deleteProjectFolder: Boolean) {
        coordinators.remove(projectId)?.forEach {
            it.clearAll()
            it.close()
        } // Close all coordinators for this project

        if (deleteProjectFolder) {
            try {
                val projectDir = AskimoHome.projectsDir().resolve(projectId)
                if (projectDir.toFile().exists()) {
                    projectDir.toFile().deleteRecursively()
                }
            } catch (e: Exception) {
                log.error("Failed to delete index files for project $projectId", e)
            }
        } else {
            LuceneIndexer.removeInstance(projectId)
            try {
                val indexDir = RagUtils.getProjectIndexDir(projectId, createIfNotExists = false)
                if (indexDir.toFile().exists()) {
                    indexDir.toFile().deleteRecursively()
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
        projectName: String,
        knowledgeSources: List<KnowledgeSourceConfig>,
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel,
        watchForChanges: Boolean,
    ) {
        // Create one coordinator per knowledge source using factory
        val projectCoordinators = try {
            knowledgeSources.map { source ->
                IndexingCoordinatorFactory.createCoordinator(
                    projectId = projectId,
                    projectName = projectName,
                    knowledgeSource = source,
                    embeddingStore = embeddingStore,
                    embeddingModel = embeddingModel,
                    appContext = appContext,
                )
            }
        } catch (e: Exception) {
            log.error("Failed to create indexing coordinators for project $projectId", e)
            EventBus.emit(
                IndexingFailedEvent(
                    projectId = projectId,
                    projectName = projectName,
                    errorMessage = e.message ?: "Failed to create indexing coordinators",
                ),
            )
            return
        }

        coordinators[projectId] = projectCoordinators

        EventBus.emit(
            IndexingStartedEvent(
                projectId = projectId,
                projectName = projectName,
            ),
        )

        // Index all sources in parallel
        val results = coroutineScope {
            projectCoordinators.map { coordinator ->
                async {
                    try {
                        coordinator.startIndexing()
                    } catch (e: Exception) {
                        log.error("Failed to index knowledge source for project $projectId", e)
                        false
                    }
                }
            }.awaitAll()
        }

        val success = results.all { it }

        if (success) {
            if (watchForChanges) {
                projectCoordinators.forEach { coordinator ->
                    try {
                        coordinator.startWatching(scope)
                    } catch (e: Exception) {
                        log.error("Failed to start watching for project $projectId", e)
                    }
                }
            }

            // Sum up total files indexed across all coordinators
            val totalFilesIndexed = projectCoordinators.sumOf { it.progress.value.processedFiles }

            EventBus.emit(
                IndexingCompletedEvent(
                    projectId = projectId,
                    projectName = projectName,
                    filesIndexed = totalFilesIndexed,
                ),
            )
        } else {
            // Collect errors from failed coordinators
            val errors = projectCoordinators
                .mapNotNull { it.progress.value.error }
                .joinToString("; ")
                .takeIf { it.isNotEmpty() } ?: "Unknown error"

            EventBus.emit(
                IndexingFailedEvent(
                    projectId = projectId,
                    projectName = projectName,
                    errorMessage = errors,
                ),
            )
        }

        log.info(
            "Indexing ${if (success) "completed" else "failed"} for project $projectId " +
                "(${projectCoordinators.size} knowledge source(s))",
        )
    }

    /**
     * Handle re-index request event
     */
    private suspend fun handleReIndexRequest(event: ProjectReIndexEvent) {
        try {
            val projectId = event.projectId

            val existingCoordinators = coordinators[projectId]

            if (existingCoordinators != null && existingCoordinators.all { it.progress.value.isComplete }) {
                log.debug("Project $projectId already indexed, removing and re-indexing")
                removeCoordinator(projectId, false)
            }

            if (existingCoordinators != null && existingCoordinators.any { it.progress.value.status == IndexStatus.INDEXING }) {
                log.debug("Project $projectId is currently being indexed, skipping duplicate re-index request")
                return
            }

            removeCoordinator(projectId, false)
            log.info("Removed existing coordinators and deleted index files for project $projectId")

            val project = try {
                projectRepository.getProject(projectId)
            } catch (e: Exception) {
                log.error("Failed to get project $projectId for indexing", e)
                return
            }

            if (project != null) {
                // Create new embedding components for re-indexing
                val embeddingModel = getEmbeddingModel(appContext)
                checkEmbeddingModelAvailable(embeddingModel)

                val embeddingStore = getEmbeddingStore(projectId, embeddingModel)

                performIndexing(
                    projectId = projectId,
                    projectName = project.name,
                    knowledgeSources = project.knowledgeSources,
                    embeddingStore = embeddingStore,
                    embeddingModel = embeddingModel,
                    watchForChanges = true,
                )

                log.info("Re-indexing initiated for project $projectId")
            }
        } catch (e: Exception) {
            log.error("Failed to handle re-index request for project ${event.projectId}", e)
            EventBus.emit(
                IndexingFailedEvent(
                    projectId = event.projectId,
                    projectName = event.projectId,
                    errorMessage = e.message ?: "Unknown error",
                ),
            )
        }
    }

    private fun handleRemoveIndexEvent(event: ProjectIndexRemovalEvent) {
        try {
            val projectId = event.projectId
            val knowledgeSource = event.knowledgeSource

            val projectCoordinators = coordinators[projectId]
            if (projectCoordinators != null) {
                val coordinatorToRemove = projectCoordinators.find {
                    it.knowledgeSourceConfig == event.knowledgeSource
                }
                if (coordinatorToRemove != null) {
                    coordinatorToRemove.clearAll()
                    coordinatorToRemove.close()

                    // Remove from the list of coordinators for this project
                    coordinators[projectId] = projectCoordinators.filterNot {
                        it.knowledgeSourceConfig.resourceIdentifier == event.knowledgeSource.resourceIdentifier
                    }

                    log.info("Removed index for knowledge source ${knowledgeSource.resourceIdentifier} from project $projectId")
                } else {
                    log.warn("No coordinator found for knowledge source ${knowledgeSource.resourceIdentifier} in project $projectId")
                }
            } else {
                log.warn("No coordinators found for project $projectId when trying to remove index for source ${knowledgeSource.resourceIdentifier}")
            }
        } catch (e: Exception) {
            log.error("Failed to handle index removal request for project ${event.projectId}", e)
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
            val existingCoordinators = coordinators[projectId]

            if (existingCoordinators != null && existingCoordinators.all { it.progress.value.isComplete }) {
                log.debug("Project $projectId already indexed, skipping duplicate request")
                return
            }

            if (existingCoordinators != null && existingCoordinators.any { it.progress.value.status == IndexStatus.INDEXING }) {
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

            val project = try {
                projectRepository.getProject(projectId)
            } catch (e: Exception) {
                log.error("Failed to get project $projectId for indexing", e)
                return
            }
            if (project != null) {
                performIndexing(
                    projectId = projectId,
                    projectName = project.name,
                    knowledgeSources = event.knowledgeSources ?: project.knowledgeSources,
                    embeddingStore = embeddingStore,
                    embeddingModel = embeddingModel,
                    watchForChanges = event.watchForChanges,
                )
            }
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
        // Check if coordinators exist and all indexing is complete
        val projectCoordinators = coordinators[projectId]
        if (projectCoordinators != null && projectCoordinators.all { it.progress.value.isComplete }) {
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
}
