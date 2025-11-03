/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.xai

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
    named = "X_API_KEY",
    matches = ".+",
    disabledReason = "X_API_KEY environment variable is required for X-AI tests",
)
@TestInstance(Lifecycle.PER_CLASS)
class XAiModelFactoryTest {

    @Test
    @DisplayName("XAiModelFactory can stream responses from X-AI API")
    fun canCreateChatServiceAndStream() {
        val apiKey = System.getenv("X_API_KEY")
            ?: throw IllegalStateException("X_API_KEY environment variable is required")

        val settings = XAiSettings(apiKey = apiKey)
        val memory = MessageWindowChatMemory.withMaxMessages(10)

        val chatService: ChatService =
            XAiModelFactory().create(
                model = "grok-4",
                settings = settings,
                memory = memory,
                retrievalAugmentor = null,
            )

        val prompt = "Reply with a single short word."
        val output = chatService.sendStreamingMessageWithCallback(prompt) { _ -> }.trim()

        assertTrue(output.isNotBlank(), "Expected a non-empty response from X-AI, but got blank: '$output'")
    }

    @Test
    @DisplayName("XAiModelFactory returns empty list when no API key provided")
    fun availableModelsReturnsEmptyListWithoutApiKey() {
        val settings = XAiSettings(apiKey = "")
        val models = XAiModelFactory().availableModels(settings)

        assertTrue(models.isEmpty(), "Expected empty list when no API key provided")
    }

    @Test
    @DisplayName("XAiModelFactory returns default settings")
    fun defaultSettingsTest() {
        val factory = XAiModelFactory()
        val defaultSettings = factory.defaultSettings()

        assertTrue(defaultSettings is XAiSettings, "Expected XAiSettings instance")
        assertTrue(defaultSettings.apiKey.isBlank(), "Expected blank API key in default settings")
        assertTrue(defaultSettings.baseUrl.isNotBlank(), "Expected non-blank base URL in default settings")
    }
}
