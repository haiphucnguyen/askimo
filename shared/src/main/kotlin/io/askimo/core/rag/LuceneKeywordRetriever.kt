/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.query.Query
import io.askimo.core.logging.logger
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.Directory
import org.apache.lucene.store.NIOFSDirectory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Lucene-based keyword search retriever using BM25 ranking.
 * Complements vector similarity search by catching exact keyword matches.
 *
 * NOTE: This retriever indexes *text + metadata only*. Vector embeddings are stored elsewhere (e.g., JVector).
 *
 * GraalVM native-image notes:
 * - Disable Lucene MemorySegments (prevents native-image link errors on some setups).
 * - Use NIOFSDirectory to avoid mmap/MemorySegment-related paths.
 */
class LuceneKeywordRetriever(
    private val indexPath: Path,
    private val maxResults: Int = 10,
) : ContentRetriever {

    private val log = logger<LuceneKeywordRetriever>()
    private val analyzer = StandardAnalyzer()
    private val directory: Directory

    private val FIELD_CONTENT = "content"
    private val FIELD_META_PREFIX = "m_" // prevent collisions with Lucene internal/your own fields

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
     */
    fun indexSegments(textSegments: List<TextSegment>) {
        val config = IndexWriterConfig(analyzer)
        IndexWriter(directory, config).use { writer ->
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
                writer.addDocument(doc)
            }
            writer.commit()
        }
        log.debug("Indexed ${textSegments.size} text segments for keyword search at $indexPath")
    }

    /**
     * Clear the keyword index.
     */
    fun clearIndex() {
        val config = IndexWriterConfig(analyzer)
        IndexWriter(directory, config).use { writer ->
            writer.deleteAll()
            writer.commit()
        }
        log.debug("Cleared keyword index at $indexPath")
    }

    override fun retrieve(query: Query): List<Content> {
        if (!DirectoryReader.indexExists(directory)) {
            log.debug("Keyword index does not exist yet at $indexPath")
            return emptyList()
        }

        DirectoryReader.open(directory).use { reader ->
            val searcher = IndexSearcher(reader)

            val queryParser = QueryParser(FIELD_CONTENT, analyzer)
            val luceneQuery = try {
                queryParser.parse(query.text())
            } catch (e: Exception) {
                log.warn("Failed to parse query: ${query.text()} - falling back to escaped query", e)
                queryParser.parse(QueryParser.escape(query.text()))
            }

            val topDocs = searcher.search(luceneQuery, maxResults)
            log.debug("Keyword search found ${topDocs.scoreDocs.size} results for query: ${query.text()}")

            return topDocs.scoreDocs.mapNotNull { scoreDoc ->
                val doc = searcher.storedFields().document(scoreDoc.doc)
                val content = doc.get(FIELD_CONTENT) ?: return@mapNotNull null

                // Reconstruct metadata from stored fields
                val metadataMap = mutableMapOf<String, Any>()
                for (field in doc.fields) {
                    val name = field.name()
                    if (name.startsWith(FIELD_META_PREFIX)) {
                        val key = name.removePrefix(FIELD_META_PREFIX)
                        metadataMap[key] = field.stringValue()
                    }
                }

                val textSegment = TextSegment.from(content, Metadata.from(metadataMap))
                Content.from(textSegment)
            }
        }
    }

    fun close() {
        directory.close()
    }
}
