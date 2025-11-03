/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.openai

import dev.langchain4j.memory.chat.MessageWindowChatMemory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.sendStreamingMessageWithCallback
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(
    named = "OPENAI_API_KEY",
    matches = ".+",
    disabledReason = "OPENAI_API_KEY environment variable is required for OpenAI tests",
)
@TestInstance(Lifecycle.PER_CLASS)
class OpenAiModelFactoryTest {

    @Test
    @DisplayName("OpenAiModelFactory can stream responses from OpenAI API")
    fun canCreateChatServiceAndStream() {
        val apiKey = System.getenv("OPENAI_API_KEY")
            ?: throw IllegalStateException("OPENAI_API_KEY environment variable is required")

        val settings = OpenAiSettings(apiKey = apiKey)
        val memory = MessageWindowChatMemory.withMaxMessages(10)

        val chatService: ChatService =
            OpenAiModelFactory().create(
                model = "gpt-3.5-turbo",
                settings = settings,
                memory = memory,
                retrievalAugmentor = null,
            )

        val prompt = "Reply with a single short word."
        val output = chatService.sendStreamingMessageWithCallback(prompt) { _ -> }.trim()

        assertTrue(output.isNotBlank(), "Expected a non-empty response from OpenAI, but got blank: '$output'")
    }

    @Test
    @DisplayName("OpenAiModelFactory returns empty list when no API key provided")
    fun availableModelsReturnsEmptyListWithoutApiKey() {
        val settings = OpenAiSettings(apiKey = "")
        val models = OpenAiModelFactory().availableModels(settings)

        assertTrue(models.isEmpty(), "Expected empty list when no API key provided")
    }

    @Test
    @DisplayName("OpenAiModelFactory returns default settings")
    fun defaultSettingsTest() {
        val factory = OpenAiModelFactory()
        val defaultSettings = factory.defaultSettings()

        assertTrue(defaultSettings is OpenAiSettings, "Expected OpenAiSettings instance")
        assertTrue(defaultSettings.apiKey.isBlank(), "Expected blank API key in default settings")
    }
}
