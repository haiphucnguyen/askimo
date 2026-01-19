/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.watching

import io.askimo.core.logging.logger
import io.askimo.core.rag.filter.BinaryFileFilter
import io.askimo.core.rag.filter.CustomPatternFilter
import io.askimo.core.rag.filter.FileSizeFilter
import io.askimo.core.rag.filter.FilterChain
import io.askimo.core.rag.filter.GitignoreFilter
import io.askimo.core.rag.filter.IndexingFilter
import io.askimo.core.rag.filter.ProjectTypeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * Watches file system for changes
 */
class FileWatcher(
    private val projectId: String,
    private val onFileChange: suspend (Path, WatchEvent.Kind<*>) -> Unit,
) {
    private val log = logger<FileWatcher>()
    private var watchService: WatchService? = null
    private val watchKeys = mutableMapOf<WatchKey, Path>()

    @Volatile
    private var isShuttingDown = false

    // Build filter chain once - works for all paths
    private val filterChain: FilterChain by lazy {
        buildFilterChain()
    }

    /**
     * Build a filter chain with all necessary filters.
     * No project root needed - filters detect their context per-path.
     */
    private fun buildFilterChain(): FilterChain {
        val filters = mutableListOf<IndexingFilter>()

        // Add gitignore filter (detects Git repos automatically)
        filters.add(GitignoreFilter())

        // Add project type filter
        filters.add(ProjectTypeFilter())

        // Add binary file filter
        filters.add(BinaryFileFilter())

        // Add file size filter
        filters.add(FileSizeFilter())

        // Add custom pattern filter
        filters.add(CustomPatternFilter(emptyList()))

        return FilterChain(filters)
    }

    /**
     * Check if a directory should be excluded from watching
     */
    private fun shouldExcludeDirectory(dir: Path): Boolean = filterChain.shouldExcludePath(dir)

    /**
     * Start watching for file changes
     */
    fun startWatching(paths: List<Path>, scope: CoroutineScope) {
        try {
            watchService = FileSystems.getDefault().newWatchService()

            // Register directories
            for (path in paths) {
                if (path.isDirectory()) {
                    registerDirectoryTree(path)
                }
            }

            log.info("Started watching ${watchKeys.size} directories for project $projectId")

            // Launch watch loop in coroutine
            scope.launch(Dispatchers.IO) {
                watchForChanges()
            }
        } catch (e: Exception) {
            log.error("Failed to start file watching for project $projectId", e)
        }
    }

    /**
     * Register a directory and its subdirectories
     */
    private fun registerDirectoryTree(dir: Path) {
        if (shouldExcludeDirectory(dir)) {
            return
        }

        try {
            val watchService = watchService ?: return
            val key = dir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
            )
            watchKeys[key] = dir

            // Register subdirectories
            try {
                dir.listDirectoryEntries().forEach { entry ->
                    if (entry.isDirectory()) {
                        registerDirectoryTree(entry)
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to list directory ${dir.fileName}: ${e.message}")
            }
        } catch (e: Exception) {
            log.warn("Failed to register directory ${dir.fileName}: ${e.message}")
        }
    }

    /**
     * Watch for file system changes
     */
    private suspend fun watchForChanges() {
        val watchService = watchService ?: return

        log.debug("File watcher active for project $projectId")

        try {
            while (!isShuttingDown) {
                val key = try {
                    watchService.take()
                } catch (e: InterruptedException) {
                    log.debug("File watcher interrupted for project $projectId")
                    break
                } catch (e: ClosedWatchServiceException) {
                    // WatchService was closed (project deleted or stopped watching)
                    log.debug("File watcher closed for project $projectId")
                    break
                }

                val dir = watchKeys[key] ?: continue

                for (event in key.pollEvents()) {
                    @Suppress("UNCHECKED_CAST")
                    val ev = event as WatchEvent<Path>
                    val kind = ev.kind()

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        log.warn("Watch event overflow for project $projectId")
                        continue
                    }

                    val filename = ev.context()
                    val filePath = dir.resolve(filename)

                    // Handle new directories
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && filePath.isDirectory()) {
                        registerDirectoryTree(filePath)
                    }

                    // Notify change handler
                    try {
                        onFileChange(filePath, kind)
                    } catch (e: Exception) {
                        log.error("Error handling file change for ${filePath.fileName}", e)
                    }
                }

                if (!key.reset()) {
                    watchKeys.remove(key)
                    if (watchKeys.isEmpty()) {
                        log.info("No more directories to watch for project $projectId")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is ClosedWatchServiceException) {
                log.error("File watcher error for project $projectId", e)
            } else {
                log.debug("File watcher closed for project $projectId")
            }
        }
    }

    /**
     * Stop watching
     */
    fun stopWatching() {
        try {
            isShuttingDown = true

            watchService?.close()
            watchKeys.clear()

            log.info("Stopped file watching for project $projectId")
        } catch (e: Exception) {
            log.error("Failed to stop file watching for project $projectId", e)
        }
    }
}
