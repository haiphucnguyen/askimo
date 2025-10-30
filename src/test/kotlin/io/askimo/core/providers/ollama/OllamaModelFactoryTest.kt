/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.ollama

import dev.langchain4j.memory.chat.MessageWindowChatMemory
import io.askimo.core.providers.ChatService
import io.askimo.testcontainers.SharedOllama
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertTrue

@DisabledIfEnvironmentVariable(
    named = "DISABLE_DOCKER_TESTS",
    matches = "(?i)true|1|yes",
)
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class OllamaModelFactoryTest {
    @Test
    @DisplayName("OllamaModelFactory can stream responses from tinyllama via Testcontainers Ollama")
    fun canCreateChatServiceAndStream() {
        val ollama = SharedOllama.container
        val host = ollama.host
        val port = ollama.getMappedPort(11434)
        val baseUrl = "http://$host:$port"

        ollama.execInContainer("ollama", "pull", "tinyllama:latest")

        val settings = OllamaSettings(baseUrl = baseUrl)

        val memory = MessageWindowChatMemory.withMaxMessages(10)

        val chatService: ChatService =
            OllamaModelFactory().create(
                model = "tinyllama:latest",
                settings = settings,
                memory = memory,
                retrievalAugmentor = null,
            )

        val prompt = "Reply with a single short word."

        val result = StringBuilder()
        val stream = chatService.sendMessageStreaming(prompt)
        stream.onPartialResponse { token -> result.append(token) }
        stream.onError { error -> throw error }
        stream.start()
        val output = result.toString().trim()

        assertTrue(output.isBlank(), "Expected a non-empty response from tinyllama, but got blank")
    }
}
