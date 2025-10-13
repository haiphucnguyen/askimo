/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.data.segment.TextSegment
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ModelProvider.OLLAMA
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.ollama.OllamaContainer
import org.testcontainers.utility.DockerImageName

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_OLLAMA_TESTS", matches = "true")
@TestInstance(Lifecycle.PER_CLASS)
class EmbeddingModelFactoryOllamaTest {
    companion object {
        @Container
        @JvmStatic
        val ollama: OllamaContainer =
            OllamaContainer(DockerImageName.parse("ollama/ollama:latest")).withReuse(true)
    }

    @Test
    @DisplayName("EmbeddingModelFactory auto-pulls missing Ollama embedding model and can embed text")
    fun autoPullsMissingModelAndEmbeds() {
        val host = ollama.host
        val port = ollama.getMappedPort(11434)
        val baseUrl = "http://$host:$port"

        val embedModel = "jina-embeddings-v2-small-en"

        try {
            ollama.execInContainer("ollama", "rm", embedModel)
        } catch (_: Exception) {
            // no-op
        }


        System.setProperty("OLLAMA_URL", baseUrl)
        System.setProperty("OLLAMA_EMBED_MODEL", embedModel)

        val model = getEmbeddingModel(OLLAMA)

        val segment = TextSegment.from("hello world")
        val embedding = model.embed(segment).content().vector()

        assertTrue(embedding.isNotEmpty(), "Expected a non-empty embedding vector from $embedModel")
    }
}
