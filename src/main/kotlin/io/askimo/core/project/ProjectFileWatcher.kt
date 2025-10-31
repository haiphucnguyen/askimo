/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

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

    private val supportedExtensions = setOf(
        "java", "kt", "kts", "py", "js", "ts", "jsx", "tsx", "go", "rs", "c", "cpp", "h", "hpp",
        "cs", "rb", "php", "swift", "scala", "groovy", "sh", "bash", "yaml", "yml", "json",
        "xml", "md", "txt", "gradle", "properties", "toml",
    )

    /**
     * Starts watching the project directory for file changes.
     */
    fun startWatching() {
        if (isWatching) {
            info("File watcher is already running for: $projectRoot")
            return
        }

        try {
            watchService = FileSystems.getDefault().newWatchService()
            registerDirectoryTree(projectRoot)

            isWatching = true
            watchJob = coroutineScope.launch {
                watchForChanges()
            }

            info("📁 Started file watcher for project: $projectRoot")
        } catch (e: Exception) {
            info("❌ Failed to start file watcher: ${e.message}")
            debug(e)
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
            info("📁 Stopped file watcher for project: $projectRoot")
        } catch (e: Exception) {
            info("⚠️ Error stopping file watcher: ${e.message}")
            debug(e)
        }
    }

    /**
     * Registers a directory and all its subdirectories for watching.
     */
    private fun registerDirectoryTree(start: Path) {
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
    }

    /**
     * Registers a single directory for watching.
     */
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
        } catch (e: Exception) {
            debug("Failed to register directory for watching: $dir - ${e.message}")
        }
    }

    /**
     * Main watching loop that processes file system events.
     */
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

    /**
     * Gets the next watch key from the watch service, handling interruptions gracefully.
     */
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

    /**
     * Processes events for a single watch key.
     */
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

                // Handle the file change
                handleFileChange(kind, filePath)

                // If a new directory was created, register it for watching
                if (kind == StandardWatchEventKinds.ENTRY_CREATE &&
                    Files.isDirectory(filePath) &&
                    !shouldSkipDirectory(filePath)
                ) {
                    registerDirectoryTree(filePath)
                }
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
                    info("📄 File created: $relativePath")
                    indexSingleFileAsync(filePath, relativePath)
                }
                StandardWatchEventKinds.ENTRY_MODIFY -> {
                    info("📝 File modified: $relativePath")
                    reindexFileAsync(filePath, relativePath)
                }
                StandardWatchEventKinds.ENTRY_DELETE -> {
                    info("🗑️ File deleted: $relativePath")
                    removeFileFromIndexAsync(relativePath)
                }
            }
        } catch (e: Exception) {
            info("⚠️ Failed to update index for $relativePath: ${e.message}")
            debug(e)
        }
    }

    /**
     * Asynchronously indexes a single file.
     */
    private suspend fun indexSingleFileAsync(filePath: Path, relativePath: String) {
        withContext(Dispatchers.IO) {
            indexer.indexSingleFile(filePath, relativePath)
        }
    }

    /**
     * Asynchronously re-indexes a file by removing old entries and creating new ones.
     */
    private suspend fun reindexFileAsync(filePath: Path, relativePath: String) {
        withContext(Dispatchers.IO) {
            // Remove old entries and re-index
            indexer.removeFileFromIndex(relativePath)
            indexer.indexSingleFile(filePath, relativePath)
        }
    }

    /**
     * Asynchronously removes a file from the index.
     */
    private suspend fun removeFileFromIndexAsync(relativePath: String) {
        withContext(Dispatchers.IO) {
            indexer.removeFileFromIndex(relativePath)
        }
    }

    /**
     * Checks if a file should be indexed based on its extension and other criteria.
     */
    private fun isIndexableFile(path: Path): Boolean {
        if (!path.isRegularFile()) return false

        val fileName = path.name
        if (fileName.startsWith(".")) return false

        val extension = path.extension.lowercase()
        return extension in supportedExtensions
    }

    /**
     * Checks if a directory should be skipped from watching.
     */
    private fun shouldSkipDirectory(dir: Path): Boolean {
        val dirName = dir.name

        // Skip hidden directories
        if (dirName.startsWith(".")) return true

        // Skip common build/output directories
        val skipDirs = setOf(
            "build", "target", "dist", "out", "bin", "node_modules", "__pycache__",
            ".gradle", ".mvn", ".idea", ".vscode", ".git", ".svn", ".hg",
        )

        return dirName in skipDirs
    }
}
