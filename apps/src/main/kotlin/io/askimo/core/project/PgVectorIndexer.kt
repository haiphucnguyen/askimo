package io.askimo.core.project

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource




class PgVectorIndexer(
    private val pgUrl: String = System.getenv("ASKIMO_PG_URL") ?: "jdbc:postgresql://localhost:5432/askimo",
    private val pgUser: String = System.getenv("ASKIMO_PG_USER") ?: "askimo",
    private val pgPass: String = System.getenv("ASKIMO_PG_PASS") ?: "askimo",
    private val table: String = System.getenv("ASKIMO_EMBED_TABLE") ?: "askimo_embeddings",
    private val embedDim: Int = 1536, // OpenAI text-embedding-3-small
    private val openAiKey: String = requireNotNull(System.getenv("OPENAI_API_KEY")) { "OPENAI_API_KEY missing" }
) {

    fun indexProject(root: Path): Int {
        require(Files.exists(root)) { "Path does not exist: $root" }

        val embeddingModel: EmbeddingModel = OpenAiEmbeddingModel.builder()
            .apiKey(openAiKey)
            .modelName("text-embedding-3-small")
            .build()

        // ✅ Use the builder with `.datasource(...)` (lowercase s)
        // and metadata config via static helpers
        val store: EmbeddingStore<TextSegment> = PgVectorEmbeddingStore.builder()
            .datasource(ds())                           // <— this is the correct method name
            .table(table)
            .dimension(embedDim)
            .createTable(true)                          // auto-create if missing
            // optional performance/index settings (IVFFlat)
            .useIndex(true)
            .indexListSize(100)
            // store text & metadata columns (JSONB variant)
            .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
            .build()

        val docs: List<Document> = FileSystemDocumentLoader.loadDocuments(root)
        if (docs.isEmpty()) return 0

        val splitter = DocumentSplitters.recursive(800, 100)

        // Some recent builds don’t expose batchSize()/maxRetries(); defaults are fine
        val ingestor = EmbeddingStoreIngestor.builder()
            .documentSplitter(splitter)
            .embeddingModel(embeddingModel)
            .embeddingStore(store)
            .build()

        ingestor.ingest(docs)
        return docs.size
    }
}



