/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.session.Session
import io.askimo.core.session.SessionParams
import io.askimo.testcontainers.SharedOllama
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

@DisabledIfEnvironmentVariable(
    named = "DISABLE_DOCKER_TESTS",
    matches = "(?i)true|1|yes"
)
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class PgVectorIndexerOllamaTest {
    companion object {
        // Use a pgvector-enabled Postgres image
        class PgVectorPostgres(
            image: String,
        ) : PostgreSQLContainer<PgVectorPostgres>(image)

        @Container
        @JvmStatic
        val postgres: PgVectorPostgres =
            PgVectorPostgres("pgvector/pgvector:pg16").apply {
                withDatabaseName("askimo_test")
                withUsername("askimo")
                withPassword("askimo")
                withReuse(true)
            }
    }

    @Test
    @DisplayName("PgVectorIndexer indexes files and can run similarity search (Ollama embeddings)")
    fun indexAndSearch(
        @TempDir tmp: Path,
    ) {
        // Configure Ollama base URL and model (auto-pull if missing as per EmbeddingModelFactory)
        val ollama = SharedOllama.container
        val host = ollama.host
        val port = ollama.getMappedPort(11434)
        val baseUrl = "http://$host:$port"
        System.setProperty("OLLAMA_URL", baseUrl)
        System.setProperty("OLLAMA_EMBED_MODEL", "jina/jina-embeddings-v2-small-en:latest")

        // Configure Postgres connection for PgVectorIndexer
        val pgHost = postgres.host
        val pgPort = postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
        val jdbcUrl = "jdbc:postgresql://$pgHost:$pgPort/${postgres.databaseName}"
        System.setProperty("ASKIMO_PG_URL", jdbcUrl)
        System.setProperty("ASKIMO_PG_USER", postgres.username)
        System.setProperty("ASKIMO_PG_PASS", postgres.password)

        // Create a small temp project with a couple of indexable files
        val file1 = tmp.resolve("hello.kt")
        Files.createDirectories(file1.parent)
        file1.writeText(
            """
            package demo
            // A tiny Kotlin file for indexing
            fun greet() = "hello world"
            """.trimIndent(),
        )
        val file2 = tmp.resolve("notes.md")
        file2.writeText(
            """
            # Notes
            This repository includes a greeting function.
            """.trimIndent(),
        )

        val session = Session(SessionParams(currentProvider = OLLAMA))
        val indexer =
            PgVectorIndexer(
                pgUrl = System.getProperty("ASKIMO_PG_URL"),
                pgUser = System.getProperty("ASKIMO_PG_USER"),
                pgPass = System.getProperty("ASKIMO_PG_PASS"),
                projectId = "pgvector-indexer-test",
                preferredDim = null,
                session = session,
            )

        val count = indexer.indexProject(tmp)
        assertEquals(2, count, "Expected to index exactly 2 files")

        // Ensure embed works
        val vec = indexer.embed("hello world from test")
        assertFalse(vec.isEmpty(), "Embedding vector should not be empty")

        // Similarity search for a token present in file1
        val query = indexer.embed("greeting function in kotlin")
        val results = indexer.similaritySearch(query, 2)
        assertTrue(results.isNotEmpty(), "Expected at least one search result")

        val concatenated = results.joinToString("\n")
        assertTrue(
            concatenated.contains("hello.kt") || concatenated.contains("greet()") || concatenated.contains("hello world"),
            "Search results should reference or contain content from hello.kt",
        )
    }
}
