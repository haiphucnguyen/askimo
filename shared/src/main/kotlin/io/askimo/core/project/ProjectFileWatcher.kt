/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.core.config.AppConfig
import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Watches a project directory for file changes and updates the pgvector indexes accordingly.
 *
 * This class monitors file system events (CREATE, MODIFY, DELETE) for indexable files
 * and automatically updates the corresponding embeddings in the pgvector store.
 */
class ProjectFileWatcher(
    private val projectRoot: Path,
    private val indexer: PgVectorIndexer,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private var watchService: WatchService? = null
    private var watchJob: Job? = null
    private val watchKeys = ConcurrentHashMap<WatchKey, Path>()

    @Volatile
    private var isWatching = false

    /**
     * Gets the project root path being watched.
     */
    val watchedPath: Path get() = projectRoot

    /**
     * Gets the number of directories currently being watched.
     * Useful for testing and debugging.
     */
    fun getWatchedDirectoryCount(): Int = watchKeys.size

    private val supportedExtensions = AppConfig.indexing.supportedExtensions

    /**
     * Starts watching the project directory for file changes.
     */
    fun startWatching() {
        if (isWatching) {
            debug("File watcher is already running for: $projectRoot")
            return
        }

        try {
            watchService = FileSystems.getDefault().newWatchService()
            registerDirectoryTree(projectRoot)

            isWatching = true
            watchJob = coroutineScope.launch {
                watchForChanges()
            }

            debug("📁 Started file watcher for project: $projectRoot")
        } catch (e: Exception) {
            debug("❌ Failed to start file watcher: ${e.message}", e)
        }
    }

    /**
     * Stops watching the project directory.
     */
    fun stopWatching() {
        if (!isWatching) return

        isWatching = false
        watchJob?.cancel()
        watchJob = null

        try {
            watchKeys.keys.forEach { it.cancel() }
            watchKeys.clear()
            watchService?.close()
            watchService = null
            debug("📁 Stopped file watcher for project: $projectRoot")
        } catch (e: Exception) {
            debug("⚠️ Error stopping file watcher: ${e.message}", e)
        }
    }

    private fun registerDirectoryTree(start: Path) {
        try {
            Files.walkFileTree(
                start,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (shouldSkipDirectory(dir)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        registerDirectory(dir)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
            debug("📁 Registered directory tree: $start (${watchKeys.size} total directories)")
        } catch (e: Exception) {
            debug("Error registering directory tree $start: ${e.message}", e)
        }
    }

    private fun registerDirectory(dir: Path) {
        try {
            val ws = watchService ?: return
            val watchKey = dir.register(
                ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
            )
            watchKeys[watchKey] = dir
            debug("📁 Registered directory: $dir")
        } catch (e: Exception) {
            debug("Failed to register directory for watching: $dir - ${e.message}")
        }
    }

    private suspend fun watchForChanges() {
        val ws = watchService ?: return

        while (isWatching && !Thread.currentThread().isInterrupted) {
            val watchKey = getNextWatchKey(ws)
            if (watchKey == null) {
                delay(1000)
                continue
            }

            processWatchKeyEvents(watchKey)
        }
    }

    private suspend fun getNextWatchKey(ws: WatchService): WatchKey? = try {
        withContext(Dispatchers.IO) {
            ws.take()
        }
    } catch (_: InterruptedException) {
        isWatching = false
        null
    } catch (e: Exception) {
        debug("Error getting watch key: ${e.message}")
        null
    }

    private suspend fun processWatchKeyEvents(watchKey: WatchKey) {
        val dir = watchKeys[watchKey] ?: return

        try {
            for (event in watchKey.pollEvents()) {
                val kind = event.kind()

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    info("⚠️ Watch service overflow - some events may have been lost")
                    continue
                }

                @Suppress("UNCHECKED_CAST")
                val fileName = (event as WatchEvent<Path>).context()
                val filePath = dir.resolve(fileName)

                if (kind == StandardWatchEventKinds.ENTRY_CREATE &&
                    Files.isDirectory(filePath) &&
                    !shouldSkipDirectory(filePath)
                ) {
                    debug("📁 Registering new directory for watching: $filePath")
                    registerDirectoryTree(filePath)
                }

                handleFileChange(kind, filePath)
            }

            val valid = watchKey.reset()
            if (!valid) {
                watchKeys.remove(watchKey)
            }
        } catch (e: Exception) {
            debug("Error processing watch key events: ${e.message}")
        }
    }

    /**
     * Handles a file change event by updating the index accordingly.
     */
    private suspend fun handleFileChange(kind: WatchEvent.Kind<*>, filePath: Path) {
        if (!isIndexableFile(filePath)) return

        val relativePath = try {
            projectRoot.relativize(filePath).toString().replace('\\', '/')
        } catch (_: Exception) {
            debug("Could not relativize path: $filePath")
            return
        }

        try {
            when (kind) {
                StandardWatchEventKinds.ENTRY_CREATE -> {
                    debug("📄 File created: $relativePath")
                    indexSingleFileAsync(filePath, relativePath)
                }
                StandardWatchEventKinds.ENTRY_MODIFY -> {
                    debug("📝 File modified: $relativePath")
                    reindexFileAsync(filePath, relativePath)
                }
                StandardWatchEventKinds.ENTRY_DELETE -> {
                    debug("🗑️ File deleted: $relativePath")
                    removeFileFromIndexAsync(relativePath)
                }
            }
        } catch (e: Exception) {
            info("⚠️ Failed to update index for $relativePath: ${e.message}")
            debug(e)
        }
    }

    private suspend fun indexSingleFileAsync(filePath: Path, relativePath: String) {
        withContext(Dispatchers.IO) {
            indexer.indexSingleFile(filePath, relativePath)
        }
    }

    private suspend fun reindexFileAsync(filePath: Path, relativePath: String) {
        withContext(Dispatchers.IO) {
            indexer.removeFileFromIndex(relativePath)
            indexer.indexSingleFile(filePath, relativePath)
        }
    }

    private suspend fun removeFileFromIndexAsync(relativePath: String) {
        withContext(Dispatchers.IO) {
            indexer.removeFileFromIndex(relativePath)
        }
    }

    private fun isIndexableFile(path: Path): Boolean {
        if (!path.isRegularFile()) return false

        val fileName = path.name
        if (fileName.startsWith(".")) return false

        val extension = path.extension.lowercase()
        return extension in supportedExtensions
    }

    private fun shouldSkipDirectory(dir: Path): Boolean {
        val dirName = dir.name

        // Skip hidden directories
        if (dirName.startsWith(".")) return true

        // Use pre-processed skip directory names from AppConfig (computed once at startup)
        return dirName in AppConfig.indexing.skipDirectoryNames
    }
}
