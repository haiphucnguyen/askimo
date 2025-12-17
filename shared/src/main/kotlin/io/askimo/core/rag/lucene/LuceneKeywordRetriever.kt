/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.lucene

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
import org.apache.lucene.store.FSDirectory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Lucene-based keyword search retriever using BM25 ranking.
 * Complements vector similarity search by catching exact keyword matches.
 *
 * This is a lightweight keyword index for BM25 search only.
 * Vector embeddings are stored in JVector.
 */
class LuceneKeywordRetriever(
    private val indexPath: Path,
    private val maxResults: Int = 10,
) : ContentRetriever {

    private val log = logger<LuceneKeywordRetriever>()
    private val analyzer = StandardAnalyzer()
    private val directory: FSDirectory

    init {
        Files.createDirectories(indexPath)
        directory = FSDirectory.open(indexPath)
    }

    /**
     * Index multiple text segments in batch.
     */
    fun indexSegments(textSegments: List<TextSegment>) {
        val config = IndexWriterConfig(analyzer)
        IndexWriter(directory, config).use { writer ->
            textSegments.forEach { textSegment ->
                val doc = Document()
                doc.add(TextField("content", textSegment.text(), Field.Store.YES))

                textSegment.metadata().toMap().forEach { (key, value) ->
                    doc.add(StoredField(key, value.toString()))
                }

                writer.addDocument(doc)
            }
            writer.commit()
        }
        log.debug("Indexed ${textSegments.size} text segments for keyword search")
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
        log.debug("Cleared keyword index")
    }

    override fun retrieve(query: Query): List<Content> {
        if (!DirectoryReader.indexExists(directory)) {
            log.debug("Keyword index does not exist yet")
            return emptyList()
        }

        return DirectoryReader.open(directory).use { reader ->
            val searcher = IndexSearcher(reader)

            // Use QueryParser for flexible query syntax
            val queryParser = QueryParser("content", analyzer)
            val luceneQuery = try {
                queryParser.parse(query.text())
            } catch (e: Exception) {
                log.warn("Failed to parse query: ${query.text()}, using simple term query", e)
                // Fallback to escaped query
                queryParser.parse(QueryParser.escape(query.text()))
            }

            // Search using BM25 (Lucene's default similarity)
            val topDocs = searcher.search(luceneQuery, maxResults)

            log.debug("Keyword search found ${topDocs.scoreDocs.size} results for query: ${query.text()}")

            topDocs.scoreDocs.map { scoreDoc ->
                val doc = searcher.storedFields().document(scoreDoc.doc)
                val content = doc.get("content")

                // Reconstruct metadata
                val metadataMap = mutableMapOf<String, Any>()
                doc.fields.forEach { field ->
                    if (field.name() != "content") {
                        metadataMap[field.name()] = field.stringValue()
                    }
                }

                val textSegment = TextSegment.from(content, Metadata.from(metadataMap))
                Content.from(textSegment)
            }
        }
    }

    /**
     * Close the directory.
     */
    fun close() {
        directory.close()
    }
}
