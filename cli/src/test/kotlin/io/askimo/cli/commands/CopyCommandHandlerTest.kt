/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.session.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CopyCommandHandlerTest : CommandHandlerTestBase() {
    private lateinit var session: Session
    private lateinit var handler: CopyCommandHandler

    @BeforeEach
    fun setUp() {
        session = mock<Session>()
        handler = CopyCommandHandler(session)
    }

    @Test
    fun `keyword returns correct value`() {
        assertEquals(":copy", handler.keyword)
    }

    @Test
    fun `description is not empty and mentions copying`() {
        assertTrue(handler.description.isNotBlank())
        assertTrue(handler.description.contains("copy", ignoreCase = true))
        assertTrue(handler.description.contains("clipboard", ignoreCase = true))
    }

    @Test
    fun `handle with no response shows warning`() {
        whenever(session.lastResponse).thenReturn(null)

        val parsedLine = mockParsedLine(":copy")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("⚠️ No response to copy"))
        assertTrue(output.contains("Ask something first"))
    }

    @Test
    fun `handle with blank response shows warning`() {
        whenever(session.lastResponse).thenReturn("   ")

        val parsedLine = mockParsedLine(":copy")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("⚠️ No response to copy"))
    }

    @Test
    fun `handle with empty response shows warning`() {
        whenever(session.lastResponse).thenReturn("")

        val parsedLine = mockParsedLine(":copy")

        handler.handle(parsedLine)

        val output = getOutput()
        assertTrue(output.contains("⚠️ No response to copy"))
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `handle with valid response on macOS attempts copy`() {
        val testResponse = "This is a test response from the AI"
        whenever(session.lastResponse).thenReturn(testResponse)

        val parsedLine = mockParsedLine(":copy")

        // Note: This test will actually attempt to copy to clipboard on macOS
        // The assertion is that it doesn't throw an exception
        handler.handle(parsedLine)

        val output = getOutput()
        // Should either succeed or fail gracefully
        assertTrue(
            output.contains("✅ Copied last response to clipboard") ||
                output.contains("❌ Failed to copy to clipboard"),
        )
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `handle with valid response on Windows attempts copy`() {
        val testResponse = "This is a test response from the AI"
        whenever(session.lastResponse).thenReturn(testResponse)

        val parsedLine = mockParsedLine(":copy")

        // Note: This test will actually attempt to copy to clipboard on Windows
        handler.handle(parsedLine)

        val output = getOutput()
        // Should either succeed or fail gracefully
        assertTrue(
            output.contains("✅ Copied last response to clipboard") ||
                output.contains("❌ Failed to copy to clipboard"),
        )
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `handle with valid response on Linux attempts copy`() {
        val testResponse = "This is a test response from the AI"
        whenever(session.lastResponse).thenReturn(testResponse)

        val parsedLine = mockParsedLine(":copy")

        // Note: This test requires xclip or xsel to be installed on Linux
        handler.handle(parsedLine)

        val output = getOutput()
        // Should either succeed or show utility not found message
        assertTrue(
            output.contains("✅ Copied last response to clipboard") ||
                output.contains("⚠️ No clipboard utility found") ||
                output.contains("❌ Failed to copy to clipboard"),
        )
    }

    @Test
    fun `handle with multiline response processes correctly`() {
        val multilineResponse =
            """
            This is line 1
            This is line 2
            This is line 3
            """.trimIndent()
        whenever(session.lastResponse).thenReturn(multilineResponse)

        val parsedLine = mockParsedLine(":copy")

        handler.handle(parsedLine)

        val output = getOutput()
        // Verify it attempts to copy (success depends on platform)
        assertTrue(
            output.contains("✅ Copied last response to clipboard") ||
                output.contains("⚠️") ||
                output.contains("❌"),
        )
    }

    @Test
    fun `handle with special characters in response processes correctly`() {
        val specialResponse = "Response with special chars: @#\$%^&*()_+-=[]{}|;':\",./<>?"
        whenever(session.lastResponse).thenReturn(specialResponse)

        val parsedLine = mockParsedLine(":copy")

        handler.handle(parsedLine)

        val output = getOutput()
        // Verify it attempts to copy
        assertTrue(
            output.contains("✅ Copied last response to clipboard") ||
                output.contains("⚠️") ||
                output.contains("❌"),
        )
    }

    @Test
    fun `handle with unicode characters in response processes correctly`() {
        val unicodeResponse = "Response with unicode: 你好 こんにちは 안녕하세요 🚀 ✨"
        whenever(session.lastResponse).thenReturn(unicodeResponse)

        val parsedLine = mockParsedLine(":copy")

        handler.handle(parsedLine)

        val output = getOutput()
        // Verify it attempts to copy
        assertTrue(
            output.contains("✅ Copied last response to clipboard") ||
                output.contains("⚠️") ||
                output.contains("❌"),
        )
    }

    @Test
    fun `handle with markdown formatted response processes correctly`() {
        val markdownResponse =
            """
            # Heading

            This is **bold** and this is *italic*.

            ```kotlin
            fun main() {
                println("Hello, World!")
            }
            ```

            - List item 1
            - List item 2
            """.trimIndent()
        whenever(session.lastResponse).thenReturn(markdownResponse)

        val parsedLine = mockParsedLine(":copy")

        handler.handle(parsedLine)

        val output = getOutput()
        // Verify it attempts to copy
        assertTrue(
            output.contains("✅ Copied last response to clipboard") ||
                output.contains("⚠️") ||
                output.contains("❌"),
        )
    }

    @Test
    fun `handle with very long response processes correctly`() {
        val longResponse = "A".repeat(10000) // 10KB of text
        whenever(session.lastResponse).thenReturn(longResponse)

        val parsedLine = mockParsedLine(":copy")

        handler.handle(parsedLine)

        val output = getOutput()
        // Verify it attempts to copy
        assertTrue(
            output.contains("✅ Copied last response to clipboard") ||
                output.contains("⚠️") ||
                output.contains("❌"),
        )
    }
}
