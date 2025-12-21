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
import org.apache.lucene.index.DirectoryReader
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
 * NOTE: This retriever reads from the index only. Use LuceneIndexer for indexing operations.
 *
 * GraalVM native-image notes:
 * - Disable Lucene MemorySegments (prevents native-image link errors on some setups).
 * - Use NIOFSDirectory to avoid mmap/MemorySegment-related paths.
 */
class LuceneKeywordRetriever(
    private val projectId: String,
    private val maxResults: Int = 10,
) : ContentRetriever {

    private val log = logger<LuceneKeywordRetriever>()
    private val analyzer = StandardAnalyzer()
    private val directory: Directory

    private val indexPath: Path
        get() = RagUtils.getProjectLuceneIndexDir(projectId)

    init {
        // 1) Disable Lucene's MemorySegment-backed mmap optimizations (native-image linker friendly)
        // Must be set BEFORE any Lucene directory/index classes are initialized.
        System.setProperty("org.apache.lucene.store.MMapDirectory.enableMemorySegments", "false")

        Files.createDirectories(indexPath)

        // 2) Avoid mmap entirely: NIOFSDirectory uses NIO FileChannel read (portable & native-image friendly)
        directory = NIOFSDirectory(indexPath)
    }

    override fun retrieve(query: Query): List<Content> {
        if (!DirectoryReader.indexExists(directory)) {
            log.debug("Keyword index does not exist yet at $indexPath")
            return emptyList()
        }

        return try {
            DirectoryReader.open(directory).use { reader ->
                val searcher = IndexSearcher(reader)

                val queryParser = QueryParser(LuceneIndexer.FIELD_CONTENT, analyzer)
                val luceneQuery = try {
                    queryParser.parse(query.text())
                } catch (e: Exception) {
                    log.debug("Failed to parse query: '${query.text()}' - attempting with escaped query", e)
                    try {
                        // First try: escape special characters
                        queryParser.parse(QueryParser.escape(query.text()))
                    } catch (e2: Exception) {
                        log.debug("Escaped query also failed - attempting phrase query as final fallback", e2)
                        try {
                            // Second try: wrap in quotes for phrase query (most robust)
                            // This treats the entire query as a literal phrase
                            val escapedText = query.text().replace("\"", "\\\"")
                            queryParser.parse("\"$escapedText\"")
                        } catch (e3: Exception) {
                            // If even phrase query fails, log and skip keyword search
                            log.warn("All query parsing attempts failed for: '${query.text()}' - skipping keyword search", e3)
                            return emptyList()
                        }
                    }
                }

                val topDocs = searcher.search(luceneQuery, maxResults)
                log.debug("Keyword search found ${topDocs.scoreDocs.size} results for query: ${query.text()}")

                topDocs.scoreDocs.mapNotNull { scoreDoc ->
                    val doc = searcher.storedFields().document(scoreDoc.doc)
                    val content = doc.get(LuceneIndexer.FIELD_CONTENT) ?: return@mapNotNull null

                    // Reconstruct metadata from stored fields
                    val metadataMap = mutableMapOf<String, Any>()
                    for (field in doc.fields) {
                        val name = field.name()
                        if (name.startsWith(LuceneIndexer.FIELD_META_PREFIX)) {
                            val key = name.removePrefix(LuceneIndexer.FIELD_META_PREFIX)
                            metadataMap[key] = field.stringValue()
                        }
                    }

                    val textSegment = TextSegment.from(content, Metadata.from(metadataMap))
                    Content.from(textSegment)
                }
            }
        } catch (e: Exception) {
            log.error("Unexpected error during keyword retrieval for query: '${query.text()}'", e)
            emptyList()
        }
    }
}
