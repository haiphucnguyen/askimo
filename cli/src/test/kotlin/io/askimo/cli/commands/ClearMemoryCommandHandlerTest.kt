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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClearMemoryCommandHandlerTest : CommandHandlerTestBase() {
    private lateinit var session: Session
    private lateinit var handler: ClearMemoryCommandHandler

    @BeforeEach
    fun setUp() {
        session = mock<Session>()
        handler = ClearMemoryCommandHandler(session)
    }

    @Test
    fun `handle clears memory for active provider and model`() {
        val provider = ModelProvider.OPENAI
        val modelName = "gpt-4"
        val params = mock<SessionParams>()

        whenever(session.getActiveProvider()) doReturn provider
        whenever(session.params) doReturn params
        whenever(params.getModel(provider)) doReturn modelName

        val parsedLine = mockParsedLine(":clear")

        handler.handle(parsedLine)

        // Verify that removeMemory was called with correct parameters
        verify(session).removeMemory(provider, modelName)

        // Verify console output
        val output = getOutput()
        assertTrue(output.contains("🧹 Chat memory cleared"))
        assertTrue(output.contains("OPENAI"))
        assertTrue(output.contains("gpt-4"))
    }

    @Test
    fun `handle clears memory for Ollama provider`() {
        val provider = ModelProvider.OLLAMA
        val modelName = "llama3.2"
        val params = mock<SessionParams>()

        whenever(session.getActiveProvider()) doReturn provider
        whenever(session.params) doReturn params
        whenever(params.getModel(provider)) doReturn modelName

        val parsedLine = mockParsedLine(":clear")

        handler.handle(parsedLine)

        verify(session).removeMemory(provider, modelName)

        val output = getOutput()
        assertTrue(output.contains("🧹 Chat memory cleared"))
        assertTrue(output.contains("OLLAMA"))
        assertTrue(output.contains("llama3.2"))
    }

    @Test
    fun `handle clears memory for Anthropic provider`() {
        val provider = ModelProvider.ANTHROPIC
        val modelName = "claude-3-5-sonnet-20241022"
        val params = mock<SessionParams>()

        whenever(session.getActiveProvider()) doReturn provider
        whenever(session.params) doReturn params
        whenever(params.getModel(provider)) doReturn modelName

        val parsedLine = mockParsedLine(":clear")

        handler.handle(parsedLine)

        verify(session).removeMemory(provider, modelName)

        val output = getOutput()
        assertTrue(output.contains("🧹 Chat memory cleared"))
        assertTrue(output.contains("ANTHROPIC"))
        assertTrue(output.contains("claude-3-5-sonnet-20241022"))
    }

    @Test
    fun `handle clears memory for Gemini provider`() {
        val provider = ModelProvider.GEMINI
        val modelName = "gemini-2.0-flash-exp"
        val params = mock<SessionParams>()

        whenever(session.getActiveProvider()) doReturn provider
        whenever(session.params) doReturn params
        whenever(params.getModel(provider)) doReturn modelName

        val parsedLine = mockParsedLine(":clear")

        handler.handle(parsedLine)

        verify(session).removeMemory(provider, modelName)

        val output = getOutput()
        assertTrue(output.contains("🧹 Chat memory cleared"))
        assertTrue(output.contains("GEMINI"))
        assertTrue(output.contains("gemini-2.0-flash-exp"))
    }

    @Test
    fun `keyword returns correct value`() {
        assertEquals(":clear", handler.keyword)
    }

    @Test
    fun `description is not empty and mentions clearing memory`() {
        assertTrue(handler.description.isNotBlank())
        assertTrue(handler.description.contains("clear", ignoreCase = true))
        assertTrue(handler.description.contains("memory", ignoreCase = true))
    }

    @Test
    fun `description mentions provider and model`() {
        assertTrue(handler.description.contains("provider", ignoreCase = true))
        assertTrue(handler.description.contains("model", ignoreCase = true))
    }
}
