/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.data.segment.TextSegment
import io.askimo.core.context.AppContext
import io.askimo.core.context.AppContextParams
import io.askimo.core.context.ExecutionMode
import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.ollama.OllamaSettings
import io.askimo.core.rag.getEmbeddingModel
import io.askimo.testcontainers.SharedOllama
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.testcontainers.junit.jupiter.Testcontainers

@DisabledIfEnvironmentVariable(
    named = "DISABLE_DOCKER_TESTS",
    matches = "(?i)true|1|yes",
)
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class EmbeddingModelFactoryOllamaTest {

    private lateinit var baseUrl: String
    private lateinit var embedModel: String
    private lateinit var appContext: AppContext

    @BeforeEach
    fun setUp() {
        AppContext.reset()

        val ollama = SharedOllama.container
        val host = ollama.host
        val port = ollama.getMappedPort(11434)
        baseUrl = "http://$host:$port/v1"

        embedModel = "jina/jina-embeddings-v2-small-en:latest"

        try {
            ollama.execInContainer("ollama", "rm", embedModel)
        } catch (_: Exception) {
            // no-op
        }

        System.setProperty("OLLAMA_URL", baseUrl)
        System.setProperty("OLLAMA_EMBED_MODEL", embedModel)

        // Create a session with OLLAMA provider configured
        val ollamaSettings = OllamaSettings(baseUrl = baseUrl)
        val params = AppContextParams(
            currentProvider = OLLAMA,
            providerSettings = mutableMapOf(OLLAMA to ollamaSettings as ProviderSettings),
        )
        appContext = AppContext.initialize(
            mode = ExecutionMode.STATELESS_MODE,
            params = params,
        )
    }

    @Test
    @DisplayName("EmbeddingModelFactory auto-pulls missing Ollama embedding model and can embed text")
    fun autoPullsMissingModelAndEmbeds() {
        val model = getEmbeddingModel(appContext)

        val segment = TextSegment.from("hello world")
        val embedding = model.embed(segment).content().vector()

        assertTrue(embedding.isNotEmpty(), "Expected a non-empty embedding vector from $embedModel")
    }
}
