/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.ollama

import dev.langchain4j.memory.chat.MessageWindowChatMemory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.chat
import org.junit.jupiter.api.Assertions.assertFalse
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
class OllamaModelFactoryTest {
    companion object {
        @Container
        @JvmStatic
        val ollama: OllamaContainer =
            OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
    }

    @Test
    @DisplayName("OllamaModelFactory can stream responses from tinyllama via Testcontainers Ollama")
    fun canCreateChatServiceAndStream() {
        // Derive the base URL of the Ollama HTTP API exposed by the container
        val host = ollama.host
        val port = ollama.getMappedPort(11434)
        val baseUrl = "http://$host:$port"

        // Ensure the tinyllama model is present in the Ollama instance
        ollama.execInContainer("ollama", "pull", "tinyllama")

        val settings = OllamaSettings(baseUrl = baseUrl)

        val memory = MessageWindowChatMemory.withMaxMessages(10)

        val chatService: ChatService =
            OllamaModelFactory().create(
                model = "tinyllama",
                settings = settings,
                memory = memory,
                retrievalAugmentor = null,
            )

        val prompt = "Reply with a single short word."

        val output = chatService.chat(prompt).trim()

        // We expect some non-empty output from the model
        assertFalse(output.isBlank(), "Expected a non-empty response from tinyllama, but got blank")
    }
}
