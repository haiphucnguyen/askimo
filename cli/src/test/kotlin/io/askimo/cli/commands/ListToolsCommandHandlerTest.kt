/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListToolsCommandHandlerTest : CommandHandlerTestBase() {
    private lateinit var handler: ListToolsCommandHandler

    @BeforeEach
    fun setUp() {
        handler = ListToolsCommandHandler()
    }

    @Test
    fun `keyword returns correct value`() {
        assertEquals(":tools", handler.keyword)
    }

    @Test
    fun `description is not empty and mentions tools`() {
        assertTrue(handler.description.isNotBlank())
        assertTrue(handler.description.contains("tool", ignoreCase = true))
    }

    @Test
    fun `handle lists all available tools`() {
        val parsedLine = mockParsedLine(":tools")

        handler.handle(parsedLine)

        val output = getOutput()

        // Check for header
        assertTrue(output.contains("🔧 Available Tools"))

        // Check for GitTools
        assertTrue(output.contains("GitTools"))
        assertTrue(output.contains("branch"))
        assertTrue(output.contains("commit"))
        assertTrue(output.contains("stagedDiff"))
        assertTrue(output.contains("status"))

        // Check for LocalFsTools
        assertTrue(output.contains("LocalFsTools"))
        assertTrue(output.contains("readFile"))
        assertTrue(output.contains("writeFile"))
        assertTrue(output.contains("runCommand"))

        // Check for total count
        assertTrue(output.contains("Total:"))
        assertTrue(output.contains("tools"))
    }
}
