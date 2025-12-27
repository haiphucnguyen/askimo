/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.indexing

import io.askimo.core.rag.state.IndexProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

/**
 * Coordinator for indexing knowledge sources.
 * Each knowledge source type (files, web pages, databases) implements this interface
 * to handle indexing and watching for changes in its own way.
 */
interface IndexingCoordinator : Closeable {
    /**
     * Progress of the indexing operation.
     */
    val progress: StateFlow<IndexProgress>

    /**
     * Start indexing with progress tracking.
     * @return true if successful, false otherwise
     */
    suspend fun startIndexing(): Boolean

    /**
     * Start watching for changes (if applicable for this knowledge source type).
     * @param scope The coroutine scope for watching
     */
    fun startWatching(scope: CoroutineScope)

    /**
     * Stop watching for changes.
     */
    fun stopWatching()
}
