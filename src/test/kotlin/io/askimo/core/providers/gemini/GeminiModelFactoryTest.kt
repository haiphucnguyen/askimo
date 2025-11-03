/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.gemini

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
    named = "GEMINI_API_KEY",
    matches = ".+",
    disabledReason = "GEMINI_API_KEY environment variable is required for Gemini tests",
)
@TestInstance(Lifecycle.PER_CLASS)
class GeminiModelFactoryTest {

    @Test
    @DisplayName("GeminiModelFactory can stream responses from Gemini API")
    fun canCreateChatServiceAndStream() {
        val apiKey = System.getenv("GEMINI_API_KEY")
            ?: throw IllegalStateException("GEMINI_API_KEY environment variable is required")

        val settings = GeminiSettings(apiKey = apiKey)
        val memory = MessageWindowChatMemory.withMaxMessages(10)

        val chatService: ChatService =
            GeminiModelFactory().create(
                model = "gemini-2.5-flash",
                settings = settings,
                memory = memory,
                retrievalAugmentor = null,
            )

        val prompt = "Reply with a single short word."
        val output = chatService.sendStreamingMessageWithCallback(prompt) { _ -> }.trim()

        assertTrue(output.isNotBlank(), "Expected a non-empty response from Gemini, but got blank: '$output'")
    }

    @Test
    @DisplayName("GeminiModelFactory returns empty list when no API key provided")
    fun availableModelsReturnsEmptyListWithoutApiKey() {
        val settings = GeminiSettings(apiKey = "")
        val models = GeminiModelFactory().availableModels(settings)

        assertTrue(models.isEmpty(), "Expected empty list when no API key provided")
    }

    @Test
    @DisplayName("GeminiModelFactory returns default settings")
    fun defaultSettingsTest() {
        val factory = GeminiModelFactory()
        val defaultSettings = factory.defaultSettings()

        assertTrue(defaultSettings is GeminiSettings, "Expected GeminiSettings instance")
        assertTrue(defaultSettings.apiKey.isBlank(), "Expected blank API key in default settings")
        assertTrue(defaultSettings.baseUrl.isNotBlank(), "Expected non-blank base URL in default settings")
    }
}
