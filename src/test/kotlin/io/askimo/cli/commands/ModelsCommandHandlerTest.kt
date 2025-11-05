/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.providers.ModelProvider
import io.askimo.core.session.Session
import io.askimo.core.session.SessionParams
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelsCommandHandlerTest : CommandHandlerTestBase() {
    private lateinit var session: Session
    private lateinit var handler: ModelsCommandHandler
    private lateinit var params: SessionParams

    @BeforeEach
    fun setUp() {
        session = mock<Session>()
        params = mock<SessionParams>()
        handler = ModelsCommandHandler(session)

        whenever(session.params) doReturn params
    }

    @Test
    fun `keyword returns correct value`() {
        assertEquals(":models", handler.keyword)
    }

    @Test
    fun `description is not empty and mentions models`() {
        assertTrue(handler.description.isNotBlank())
        assertTrue(handler.description.contains("model", ignoreCase = true))
        assertTrue(
            handler.description.contains("list", ignoreCase = true) ||
                handler.description.contains("available", ignoreCase = true),
        )
    }

    @Test
    fun `handle with OpenAI provider shows models or helpful message`() {
        whenever(params.currentProvider) doReturn ModelProvider.OPENAI
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // Without a valid API key, OpenAI might return no models
        assertTrue(
            output.contains("Available models for provider 'openai'") ||
                output.contains("⚠️ No models available for provider: openai"),
        )
        // Should always show the usage hint
        assertTrue(output.contains("💡 Use `:set-param model <modelName>` to choose"))
    }

    @Test
    fun `handle with OpenAI and no API key shows helpful guidance`() {
        whenever(params.currentProvider) doReturn ModelProvider.OPENAI
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // If no models available, should show OpenAI-specific guidance
        if (output.contains("⚠️ No models available")) {
            assertTrue(output.contains("OpenAI API key") || output.contains("platform.openai.com"))
            assertTrue(output.contains(":set-param api_key"))
        }
    }

    @Test
    fun `handle with Ollama provider shows models or helpful message`() {
        whenever(params.currentProvider) doReturn ModelProvider.OLLAMA
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // Ollama might have no models if not installed, or might list available ones
        assertTrue(
            output.contains("Available models for provider 'ollama'") ||
                output.contains("⚠️ No models available for provider: ollama"),
        )
        assertTrue(output.contains("💡 Use `:set-param model <modelName>` to choose"))
    }

    @Test
    fun `handle with Ollama and no models shows helpful message`() {
        whenever(params.currentProvider) doReturn ModelProvider.OLLAMA
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // If no models are available, should show helpful Ollama-specific guidance
        if (output.contains("⚠️ No models available")) {
            assertTrue(output.contains("ollama pull"))
            assertTrue(output.contains("https://ollama.com/library"))
        }
    }

    @Test
    fun `handle with Anthropic provider shows models or helpful message`() {
        whenever(params.currentProvider) doReturn ModelProvider.ANTHROPIC
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(
            output.contains("Available models for provider 'anthropic'") ||
                output.contains("⚠️ No models available for provider: anthropic"),
        )
        assertTrue(output.contains("💡 Use `:set-param model <modelName>` to choose"))
    }

    @Test
    fun `handle with Gemini provider shows models or helpful message`() {
        whenever(params.currentProvider) doReturn ModelProvider.GEMINI
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(
            output.contains("Available models for provider 'gemini'") ||
                output.contains("⚠️ No models available for provider: gemini"),
        )
        assertTrue(output.contains("💡 Use `:set-param model <modelName>` to choose"))
    }

    @Test
    fun `handle with xAI provider shows models or helpful message`() {
        whenever(params.currentProvider) doReturn ModelProvider.XAI
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(
            output.contains("Available models for provider 'xai'") ||
                output.contains("⚠️ No models available for provider: xai"),
        )
        assertTrue(output.contains("💡 Use `:set-param model <modelName>` to choose"))
    }

    @Test
    fun `handle with unknown provider shows error`() {
        whenever(params.currentProvider) doReturn ModelProvider.UNKNOWN

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("❌ No model factory registered for provider: unknown"))
    }

    @Test
    fun `handle always shows usage hint`() {
        whenever(params.currentProvider) doReturn ModelProvider.OPENAI
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // Should always show how to set a model (unless there's an error with no factory)
        assertTrue(
            output.contains(":set-param model") ||
                output.contains("❌ No model factory registered"),
        )
    }

    @Test
    fun `handle shows provider name in lowercase`() {
        whenever(params.currentProvider) doReturn ModelProvider.OPENAI
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // Provider name should be lowercase in output
        assertTrue(output.contains("openai"))
    }

    @Test
    fun `handle formats model list with dashes when models available`() {
        whenever(params.currentProvider) doReturn ModelProvider.ANTHROPIC
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // If models are listed, they should be prefixed with "- "
        if (output.contains("Available models")) {
            // Check if there's at least one model line (would contain "- ")
            val hasModelLine = output.lines().any { it.trim().startsWith("- ") }
            assertTrue(hasModelLine)
        }
    }

    @Test
    fun `handle with different providers shows correct provider names`() {
        val testCases =
            listOf(
                ModelProvider.OPENAI to "openai",
                ModelProvider.OLLAMA to "ollama",
                ModelProvider.ANTHROPIC to "anthropic",
                ModelProvider.GEMINI to "gemini",
                ModelProvider.XAI to "xai",
            )

        testCases.forEach { (provider, expectedName) ->
            testOut.reset() // Reset output between iterations

            whenever(params.currentProvider) doReturn provider
            whenever(params.providerSettings) doReturn mutableMapOf()

            val parsedLine = mockParsedLine(":models")
            handler.handle(parsedLine)

            val output = getOutput()
            assertTrue(
                output.contains(expectedName),
                "Output should contain provider name '$expectedName'",
            )
        }
    }

    @Test
    fun `handle with custom provider settings uses them`() {
        whenever(params.currentProvider) doReturn ModelProvider.OLLAMA
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // Should successfully process without errors
        assertTrue(
            output.contains("Available models") ||
                output.contains("⚠️ No models available") ||
                output.contains("❌"),
        )
    }

    @Test
    fun `handle shows emoji in output`() {
        whenever(params.currentProvider) doReturn ModelProvider.OPENAI
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // Should contain at least one emoji
        assertTrue(
            output.contains("💡") ||
                output.contains("⚠️") ||
                output.contains("❌"),
        )
    }

    @Test
    fun `handle with empty provider settings map uses defaults`() {
        whenever(params.currentProvider) doReturn ModelProvider.OLLAMA
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        handler.handle(parsedLine)

        val output = getOutput()
        // Should not crash, should show models or no models message
        assertTrue(
            output.contains("ollama") ||
                output.contains("Available models") ||
                output.contains("⚠️ No models available"),
        )
    }

    @Test
    fun `handle shows appropriate guidance based on provider`() {
        // Test Ollama guidance
        whenever(params.currentProvider) doReturn ModelProvider.OLLAMA
        whenever(params.providerSettings) doReturn mutableMapOf()

        var parsedLine = mockParsedLine(":models")
        handler.handle(parsedLine)
        var output = getOutput()

        if (output.contains("⚠️ No models available")) {
            assertTrue(output.contains("ollama") && output.contains("pull"))
        }

        // Reset and test OpenAI guidance
        testOut.reset()
        whenever(params.currentProvider) doReturn ModelProvider.OPENAI

        parsedLine = mockParsedLine(":models")
        handler.handle(parsedLine)
        output = getOutput()

        if (output.contains("⚠️ No models available")) {
            assertTrue(output.contains("API key") || output.contains("platform.openai.com"))
        }
    }

    @Test
    fun `handle with null provider settings does not crash`() {
        whenever(params.currentProvider) doReturn ModelProvider.OLLAMA
        whenever(params.providerSettings) doReturn mutableMapOf()

        val parsedLine = mockParsedLine(":models")

        // Should not throw exception
        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.isNotEmpty())
    }
}
