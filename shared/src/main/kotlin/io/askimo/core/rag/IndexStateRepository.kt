/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import com.zaxxer.hikari.HikariDataSource
import io.askimo.core.db.SQLiteDataSourceFactory
import io.askimo.core.logging.logger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * Information about an indexed file.
 */
data class IndexedFileInfo(
    val path: String,
    val lastModified: Long,
    val indexedAt: LocalDateTime,
    val size: Long = 0,
    val checksum: String? = null,
)

/**
 * Represents changes detected between current file system and persisted index.
 */
data class FileChanges(
    val toAdd: List<IndexedFileInfo>,
    val toUpdate: List<IndexedFileInfo>,
    val toRemove: List<String>,
) {
    val hasChanges: Boolean
        get() = toAdd.isNotEmpty() || toUpdate.isNotEmpty() || toRemove.isNotEmpty()

    val totalChanges: Int
        get() = toAdd.size + toUpdate.size + toRemove.size

    override fun toString(): String = buildString {
        append("FileChanges(")
        append("add: ${toAdd.size}, ")
        append("update: ${toUpdate.size}, ")
        append("remove: ${toRemove.size}")
        append(")")
    }
}

/**
 * Repository for managing RAG index state in SQLite.
 * Each project has its own database file stored alongside the index files.
 *
 *
 * This provides:
 * - Persistent tracking of indexed files
 * - Efficient change detection on app restart
 * - Incremental indexing support
 * - Streaming/batched updates for large projects
 */
class IndexStateRepository(
    indexPath: Path,
) : AutoCloseable {
    private val log = logger<IndexStateRepository>()

    // Ensure directory exists before creating database file
    private val dbFile: Path = indexPath.resolve("index-state.db").also {
        Files.createDirectories(indexPath)
    }

    private val dataSource: DataSource by lazy {
        SQLiteDataSourceFactory.createSingleConnection(
            dbPath = dbFile,
            enableForeignKeys = false,
        )
    }

    private val database: Database by lazy {
        Database.connect(dataSource)
    }

    @Volatile
    private var tablesInitialized = false

    private fun ensureTablesInitialized() {
        if (!tablesInitialized) {
            synchronized(this) {
                if (!tablesInitialized) {
                    transaction(database) {
                        SchemaUtils.create(IndexedFilesTable, IndexMetadataTable, FileSegmentsTable)
                    }
                    log.debug("Initialized index state database at: $dbFile")
                    tablesInitialized = true
                }
            }
        }
    }

    /**
     * Save or update a file entry in the index.
     */
    fun upsertFile(fileInfo: IndexedFileInfo) {
        ensureTablesInitialized()
        transaction(database) {
            val existing = IndexedFilesTable
                .selectAll()
                .where { IndexedFilesTable.path eq fileInfo.path }
                .singleOrNull()

            if (existing != null) {
                IndexedFilesTable.update({ IndexedFilesTable.path eq fileInfo.path }) {
                    it[lastModified] = fileInfo.lastModified
                    it[indexedAt] = fileInfo.indexedAt
                    it[size] = fileInfo.size
                    it[checksum] = fileInfo.checksum
                }
            } else {
                IndexedFilesTable.insert {
                    it[path] = fileInfo.path
                    it[lastModified] = fileInfo.lastModified
                    it[indexedAt] = fileInfo.indexedAt
                    it[size] = fileInfo.size
                    it[checksum] = fileInfo.checksum
                }
            }
        }
    }

    /**
     * Batch upsert multiple files efficiently.
     */
    fun upsertFiles(files: List<IndexedFileInfo>) {
        if (files.isEmpty()) return

        ensureTablesInitialized()
        transaction(database) {
            files.forEach { fileInfo ->
                val existing = IndexedFilesTable
                    .selectAll()
                    .where { IndexedFilesTable.path eq fileInfo.path }
                    .singleOrNull()

                if (existing != null) {
                    IndexedFilesTable.update({ IndexedFilesTable.path eq fileInfo.path }) {
                        it[lastModified] = fileInfo.lastModified
                        it[indexedAt] = fileInfo.indexedAt
                        it[size] = fileInfo.size
                        it[checksum] = fileInfo.checksum
                    }
                } else {
                    IndexedFilesTable.insert {
                        it[path] = fileInfo.path
                        it[lastModified] = fileInfo.lastModified
                        it[indexedAt] = fileInfo.indexedAt
                        it[size] = fileInfo.size
                        it[checksum] = fileInfo.checksum
                    }
                }
            }
        }
        log.debug("Upserted ${files.size} file entries")
    }

    /**
     * Get all indexed files.
     */
    fun getAllIndexedFiles(): Map<String, IndexedFileInfo> {
        ensureTablesInitialized()
        return transaction(database) {
            IndexedFilesTable
                .selectAll()
                .associate { row ->
                    val path = row[IndexedFilesTable.path]
                    path to IndexedFileInfo(
                        path = path,
                        lastModified = row[IndexedFilesTable.lastModified],
                        indexedAt = row[IndexedFilesTable.indexedAt],
                        size = row[IndexedFilesTable.size],
                        checksum = row[IndexedFilesTable.checksum],
                    )
                }
        }
    }

    /**
     * Get indexed file info by path.
     */
    fun getFileInfo(path: String): IndexedFileInfo? {
        ensureTablesInitialized()
        return transaction(database) {
            IndexedFilesTable
                .selectAll()
                .where { IndexedFilesTable.path eq path }
                .singleOrNull()
                ?.let { row ->
                    IndexedFileInfo(
                        path = row[IndexedFilesTable.path],
                        lastModified = row[IndexedFilesTable.lastModified],
                        indexedAt = row[IndexedFilesTable.indexedAt],
                        size = row[IndexedFilesTable.size],
                        checksum = row[IndexedFilesTable.checksum],
                    )
                }
        }
    }

    /**
     * Remove a file from the index.
     */
    fun removeFile(path: String): Int {
        ensureTablesInitialized()
        return transaction(database) {
            IndexedFilesTable.deleteWhere { IndexedFilesTable.path eq path }
        }
    }

    /**
     * Remove multiple files from the index.
     */
    fun removeFiles(paths: List<String>): Int {
        if (paths.isEmpty()) return 0

        ensureTablesInitialized()
        return transaction(database) {
            var deleted = 0
            paths.forEach { path ->
                deleted += IndexedFilesTable.deleteWhere { IndexedFilesTable.path eq path }
            }
            deleted
        }
    }

    /**
     * Get the count of indexed files.
     */
    fun getIndexedFileCount(): Int {
        ensureTablesInitialized()
        return transaction(database) {
            IndexedFilesTable.selectAll().count().toInt()
        }
    }

    /**
     * Set a metadata key-value pair.
     */
    fun setMetadata(key: String, value: String) {
        ensureTablesInitialized()
        transaction(database) {
            val existing = IndexMetadataTable
                .selectAll()
                .where { IndexMetadataTable.key eq key }
                .singleOrNull()

            if (existing != null) {
                IndexMetadataTable.update({ IndexMetadataTable.key eq key }) {
                    it[IndexMetadataTable.value] = value
                }
            } else {
                IndexMetadataTable.insert {
                    it[IndexMetadataTable.key] = key
                    it[IndexMetadataTable.value] = value
                }
            }
        }
    }

    /**
     * Get a metadata value by key.
     */
    fun getMetadata(key: String): String? {
        ensureTablesInitialized()
        return transaction(database) {
            IndexMetadataTable
                .selectAll()
                .where { IndexMetadataTable.key eq key }
                .singleOrNull()
                ?.get(IndexMetadataTable.value)
        }
    }

    /**
     * Get all metadata as a map.
     */
    fun getAllMetadata(): Map<String, String> {
        ensureTablesInitialized()
        return transaction(database) {
            IndexMetadataTable
                .selectAll()
                .associate { row ->
                    row[IndexMetadataTable.key] to row[IndexMetadataTable.value]
                }
        }
    }

    /**
     * Clear all indexed files and metadata.
     */
    fun clearAll() {
        ensureTablesInitialized()
        transaction(database) {
            exec("DELETE FROM indexed_files")
            exec("DELETE FROM index_metadata")
        }
        log.debug("Cleared all index state")
    }

    /**
     * Detect changes between current file system state and persisted state.
     * Returns files that need to be added, updated, or removed from the index.
     */
    fun detectChanges(currentFiles: Map<String, IndexedFileInfo>): FileChanges {
        ensureTablesInitialized()
        val persistedFiles = getAllIndexedFiles()

        val toAdd = mutableListOf<IndexedFileInfo>()
        val toUpdate = mutableListOf<IndexedFileInfo>()
        val toRemove = mutableListOf<String>()

        // Check for new and modified files
        currentFiles.forEach { (path, info) ->
            val persisted = persistedFiles[path]
            when {
                persisted == null -> toAdd.add(info)
                persisted.lastModified != info.lastModified -> toUpdate.add(info)
                // File exists and hasn't changed - no action needed
            }
        }

        // Check for deleted files
        persistedFiles.keys.forEach { path ->
            if (path !in currentFiles) {
                toRemove.add(path)
            }
        }

        return FileChanges(
            toAdd = toAdd,
            toUpdate = toUpdate,
            toRemove = toRemove,
        )
    }

    override fun close() {
        try {
            val ds = dataSource
            if (ds is HikariDataSource) {
                ds.close()
            }
            log.debug("Closed index state repository")
        } catch (e: Exception) {
            log.error("Failed to close index state repository", e)
        }
    }
}
