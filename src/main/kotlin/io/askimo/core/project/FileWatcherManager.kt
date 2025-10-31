/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Path

/**
 * Manages the global file watcher instance, ensuring only one project is being watched at a time.
 */
object FileWatcherManager {
    @Volatile
    private var currentWatcher: ProjectFileWatcher? = null

    private val watcherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Starts watching a new project directory, stopping any existing watcher first.
     */
    fun startWatchingProject(projectRoot: Path, indexer: PgVectorIndexer) {
        synchronized(this) {
            // Stop any existing watcher
            stopCurrentWatcher()

            // Start new watcher
            val newWatcher = ProjectFileWatcher(projectRoot, indexer, watcherScope)
            newWatcher.startWatching()
            currentWatcher = newWatcher

            info("🔍 File watcher activated for project: $projectRoot")
        }
    }

    /**
     * Stops the current file watcher if one is active.
     */
    fun stopCurrentWatcher() {
        synchronized(this) {
            currentWatcher?.let { watcher ->
                try {
                    watcher.stopWatching()
                    info("🔍 File watcher stopped")
                } catch (e: Exception) {
                    info("⚠️ Error stopping file watcher: ${e.message}")
                    debug(e)
                }
            }
            currentWatcher = null
        }
    }

    /**
     * Checks if a file watcher is currently active.
     */
    fun isWatching(): Boolean = currentWatcher != null

    /**
     * Gets the path being watched by the current watcher, if any.
     */
    fun getCurrentWatchedPath(): Path? = currentWatcher?.watchedPath
}
