/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.data.segment.TextSegment
import io.askimo.core.logging.logger
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.store.Directory
import org.apache.lucene.store.NIOFSDirectory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Lucene-based indexer for managing keyword search indices.
 * Handles indexing, updating, and deleting documents in the Lucene index.
 *
 * GraalVM native-image notes:
 * - Disable Lucene MemorySegments (prevents native-image link errors on some setups).
 * - Use NIOFSDirectory to avoid mmap/MemorySegment-related paths.
 */
class LuceneIndexer(
    private val projectId: String,
) {

    private val log = logger<LuceneIndexer>()
    private val analyzer = StandardAnalyzer()
    private val directory: Directory

    // Reuse single IndexWriter instance (thread-safe for concurrent writes)
    private val indexWriter: IndexWriter by lazy {
        val config = IndexWriterConfig(analyzer)
            .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
        IndexWriter(directory, config)
    }

    private val indexPath: Path
        get() = RagUtils.getProjectLuceneIndexDir(projectId)

    companion object {
        const val FIELD_CONTENT = "content"
        const val FIELD_META_PREFIX = "m_" // prevent collisions with Lucene internal/your own fields
    }

    init {
        // 1) Disable Lucene's MemorySegment-backed mmap optimizations (native-image linker friendly)
        // Must be set BEFORE any Lucene directory/index classes are initialized.
        System.setProperty("org.apache.lucene.store.MMapDirectory.enableMemorySegments", "false")

        Files.createDirectories(indexPath)

        // 2) Avoid mmap entirely: NIOFSDirectory uses NIO FileChannel read (portable & native-image friendly)
        directory = NIOFSDirectory(indexPath)
    }

    /**
     * Index multiple text segments in batch.
     * Thread-safe: IndexWriter handles concurrent addDocument calls.
     */
    fun indexSegments(textSegments: List<TextSegment>) {
        for (textSegment in textSegments) {
            val doc = Document().apply {
                // Store + index content for BM25
                add(TextField(FIELD_CONTENT, textSegment.text(), Field.Store.YES))

                // Store metadata (stored-only fields). Prefix keys to avoid conflicts.
                textSegment.metadata().toMap().forEach { (key, value) ->
                    val safeKey = FIELD_META_PREFIX + key
                    add(StoredField(safeKey, value.toString()))
                }
            }
            indexWriter.addDocument(doc)
        }
        indexWriter.commit()
        log.debug("Indexed ${textSegments.size} text segments for keyword search at $indexPath")
    }

    /**
     * Clear the keyword index.
     */
    fun clearIndex() {
        indexWriter.deleteAll()
        indexWriter.commit()
        log.debug("Cleared keyword index at $indexPath")
    }

    /**
     * Remove all documents for a specific file from the index.
     * @param filePath The absolute path of the file to remove
     */
    fun removeFile(filePath: String) {
        try {
            val queryParser = QueryParser(FIELD_META_PREFIX + "file_path", analyzer)
            val query = queryParser.parse(QueryParser.escape(filePath))
            indexWriter.deleteDocuments(query)
            indexWriter.commit()
            log.debug("Removed file from keyword index: $filePath")
        } catch (e: Exception) {
            log.debug("Failed to remove file from keyword index: $filePath", e)
        }
    }

    /**
     * Commit any pending changes to the index.
     * This makes recent writes visible to readers.
     */
    fun commit() {
        indexWriter.commit()
    }

    /**
     * Close the indexer and release resources.
     */
    fun close() {
        try {
            indexWriter.commit()
            indexWriter.close()
            directory.close()
        } catch (e: Exception) {
            log.error("Failed to close Lucene resources", e)
        }
    }
}
