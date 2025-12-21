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
import io.askimo.core.rag.state.IndexStatus
import io.askimo.core.rag.watching.FileChangeHandler
import io.askimo.core.rag.watching.FileWatcher
import io.askimo.core.util.JsonUtils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.nio.file.Path
import java.nio.file.Paths
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

    private val indexers = ConcurrentHashMap<String, ProjectIndexInstance>()
    init {
        scope.launch {
            EventBus.internalEvents
                .filterIsInstance<ProjectDeletedEvent>()
                .collect { event ->
                    log.info("Project deleted, cleaning up indexer: ${event.projectId}")
                    removeIndexer(event.projectId, deleteIndexFiles = true)
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
     * Remove indexer and cleanup resources
     * @param projectId The project ID
     * @param deleteIndexFiles If true, also delete the index files from disk
     */
    fun removeIndexer(projectId: String, deleteIndexFiles: Boolean = false) {
        indexers.remove(projectId)?.close()

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
        projectName: String,
        paths: List<Path>,
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel,
        watchForChanges: Boolean,
    ) {
        val coordinator = IndexingCoordinator(
            projectId = projectId,
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            appContext = appContext,
        )

        val instance = ProjectIndexInstance(
            projectId = projectId,
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            coordinator = coordinator,
            appContext = appContext,
        )

        indexers[projectId] = instance

        EventBus.emit(
            IndexingStartedEvent(
                projectId = projectId,
                projectName = projectName,
                estimatedFiles = paths.size,
            ),
        )

        val success = coordinator.indexPathsWithProgress(paths)

        if (success) {
            if (watchForChanges) {
                instance.startWatching(paths)
            }

            EventBus.emit(
                IndexingCompletedEvent(
                    projectId = projectId,
                    projectName = projectName,
                    filesIndexed = coordinator.progress.value.processedFiles,
                ),
            )
        } else {
            val error = coordinator.progress.value.error ?: "Unknown error"
            EventBus.emit(
                IndexingFailedEvent(
                    projectId = projectId,
                    projectName = projectName,
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

            val existingInstance = indexers[projectId]

            if (existingInstance != null && existingInstance.coordinator.progress.value.isComplete) {
                log.debug("Project $projectId already indexed, removing and re-indexing")
                removeIndexer(projectId, deleteIndexFiles = true)
            }

            if (existingInstance != null && existingInstance.coordinator.progress.value.status == IndexStatus.INDEXING) {
                log.debug("Project $projectId is currently being indexed, skipping duplicate re-index request")
                return
            }

            // Get project to retrieve indexed paths
            val project = try {
                projectRepository.getProject(projectId)
            } catch (e: Exception) {
                log.error("Failed to get project $projectId for re-indexing", e)
                return
            }

            if (project == null) {
                log.warn("Project $projectId not found, cannot re-index")
                return
            }

            // Parse indexed paths from project configuration
            val indexedPaths = try {
                json.decodeFromString<List<String>>(project.indexedPaths)
                    .map { Paths.get(it) }
            } catch (e: Exception) {
                log.error("Failed to parse indexed paths for project $projectId", e)
                return
            }

            if (indexedPaths.isEmpty()) {
                log.warn("No indexed paths found for project $projectId, skipping re-index")
                return
            }

            // Remove existing indexer and delete all index files for a clean re-index
            removeIndexer(projectId, deleteIndexFiles = true)
            log.info("Removed existing indexer and deleted index files for project $projectId")

            // Create new embedding components for re-indexing
            val embeddingModel = getEmbeddingModel(appContext)
            checkEmbeddingModelAvailable(embeddingModel)

            val embeddingStore = getEmbeddingdtore(projectId, embeddingModel)

            performIndexing(
                projectId = projectId,
                projectName = project.name,
                paths = indexedPaths,
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
            val existingInstance = indexers[projectId]

            if (existingInstance != null && existingInstance.coordinator.progress.value.isComplete) {
                log.debug("Project $projectId already indexed, skipping duplicate request")
                return
            }

            if (existingInstance != null && existingInstance.coordinator.progress.value.status == IndexStatus.INDEXING) {
                log.debug("Project $projectId is currently being indexed, skipping duplicate request")
                return
            }

            // Get project name
            val projectName = try {
                projectRepository.getProject(projectId)?.name ?: projectId
            } catch (e: Exception) {
                log.warn("Failed to get project name for $projectId, using ID as fallback", e)
                projectId
            }

            // Create embedding model and store if not provided
            val embeddingModel = event.embeddingModel ?: run {
                val model = getEmbeddingModel(appContext)
                checkEmbeddingModelAvailable(model)
                model
            }

            val embeddingStore = event.embeddingStore ?: run {
                getEmbeddingdtore(projectId, embeddingModel)
            }

            performIndexing(
                projectId = projectId,
                projectName = projectName,
                paths = event.paths,
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
        log.info("Closing ProjectIndexer, cleaning up ${indexers.size} indexers")
        indexers.values.forEach { it.close() }
        indexers.clear()
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

/**
 * Instance of an indexer for a specific project
 */
private class ProjectIndexInstance(
    val projectId: String,
    val embeddingStore: EmbeddingStore<TextSegment>,
    val embeddingModel: EmbeddingModel,
    val coordinator: IndexingCoordinator,
    val appContext: AppContext,
) : Closeable {
    private val log = logger<ProjectIndexInstance>()
    private var fileWatcher: FileWatcher? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start watching for file changes
     */
    fun startWatching(paths: List<Path>) {
        if (fileWatcher != null) {
            log.debug("File watcher already active for project $projectId")
            return
        }

        val changeHandler = FileChangeHandler(
            projectId = projectId,
            embeddingStore = embeddingStore,
            embeddingModel = embeddingModel,
            appContext = appContext,
        )

        fileWatcher = FileWatcher(
            projectId = projectId,
            onFileChange = { path, kind ->
                changeHandler.handleFileChange(path, kind)
            },
        )

        fileWatcher?.startWatching(paths, scope)
        log.info("Started file watching for project $projectId")
    }

    override fun close() {
        log.debug("Closing indexer instance for project $projectId")
        fileWatcher?.stopWatching()
        fileWatcher = null
    }
}
