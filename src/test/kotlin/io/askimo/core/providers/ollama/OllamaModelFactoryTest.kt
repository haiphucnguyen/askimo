/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.ollama

import dev.langchain4j.memory.chat.MessageWindowChatMemory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.sendStreamingMessageWithCallback
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
    @DisplayName("OllamaModelFactory can stream responses from qwen2.5:0.5b via Testcontainers Ollama")
    fun canCreateChatServiceAndStream() {
        val ollama = SharedOllama.container

        assertTrue(ollama.isRunning, "Ollama container should be running")

        val host = ollama.host
        val port = ollama.getMappedPort(11434)
        val baseUrl = "http://$host:$port"

        println("Ollama container running at: $baseUrl")

        SharedOllama.ensureModelPulled("qwen2.5:0.5b")

        val settings = OllamaSettings(baseUrl = baseUrl)
        val memory = MessageWindowChatMemory.withMaxMessages(10)

        val chatService: ChatService =
            OllamaModelFactory().create(
                model = "qwen2.5:0.5b",
                settings = settings,
                memory = memory,
                retrievalAugmentor = null,
            )

        val prompt = "Reply with a single short word."
        println("Sending prompt: '$prompt'")

        val output = chatService.sendStreamingMessageWithCallback(prompt) { token ->
            print(token)
        }.trim()

        println("\nFinal output: '$output'")

        assertTrue(output.isNotBlank(), "Expected a non-empty response from qwen2.5:0.5b, but got blank: '$output'")
    }
}
