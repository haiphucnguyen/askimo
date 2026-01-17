/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers.anthropic

import dev.langchain4j.data.message.UserMessage
import io.askimo.core.context.ExecutionMode
import io.askimo.core.providers.ChatClient
import io.askimo.core.providers.Presets
import io.askimo.core.providers.Style
import io.askimo.core.providers.sendStreamingMessageWithCallback
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(
    named = "ANTHROPIC_API_KEY",
    matches = ".+",
    disabledReason = "ANTHROPIC_API_KEY environment variable is required for Anthropic tests",
)
@TestInstance(Lifecycle.PER_CLASS)
class AnthropicModelFactoryTest {

    @Test
    @DisplayName("AnthropicModelFactory can stream responses from Anthropic API")
    fun canCreateChatServiceAndStream() {
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: throw IllegalStateException("ANTHROPIC_API_KEY environment variable is required")

        val settings = AnthropicSettings(apiKey = apiKey)

        val chatClient: ChatClient =
            AnthropicModelFactory().create(
                model = "claude-sonnet-4-5",
                settings = settings,
                retriever = null,
                executionMode = ExecutionMode.STATELESS_MODE,
                presets = Presets(Style.BALANCED),
            )

        val prompt = "Reply with a single short word."
        val output = chatClient.sendStreamingMessageWithCallback(UserMessage(prompt)) { _ -> }.trim()

        assertTrue(output.isNotBlank(), "Expected a non-empty response from Anthropic, but got blank: '$output'")
    }

    @Test
    @DisplayName("AnthropicModelFactory returns available models")
    fun availableModelsReturnsKnownModels() {
        val settings = AnthropicSettings(apiKey = "")
        val models = AnthropicModelFactory().availableModels(settings)

        assertTrue(models.isNotEmpty(), "Expected available models list to not be empty")
        assertTrue(models.contains("claude-sonnet-4-5"), "Expected claude-sonnet-4-5 to be in available models")
    }

    @Test
    @DisplayName("AnthropicModelFactory returns default settings")
    fun defaultSettingsTest() {
        val factory = AnthropicModelFactory()
        val defaultSettings = factory.defaultSettings()

        assertTrue(defaultSettings.baseUrl.isNotBlank(), "Expected non-blank base URL in default settings")
    }
}
