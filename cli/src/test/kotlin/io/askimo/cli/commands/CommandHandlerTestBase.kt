/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import org.jline.reader.ParsedLine
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Base class for command handler tests that provides common test infrastructure.
 *
 * This class handles:
 * - Console output capture for verifying printed messages
 * - Mock creation for ParsedLine
 * - Setup and teardown of test resources
 */
abstract class CommandHandlerTestBase {
    protected lateinit var originalOut: PrintStream
    protected lateinit var testOut: ByteArrayOutputStream

    @BeforeEach
    fun setUpBase() {
        // Capture console output
        originalOut = System.out
        testOut = ByteArrayOutputStream()
        System.setOut(PrintStream(testOut))
    }

    @AfterEach
    fun tearDownBase() {
        System.setOut(originalOut)
    }

    /**
     * Helper function to create mock ParsedLine with specified words.
     */
    protected fun mockParsedLine(vararg words: String): ParsedLine {
        val parsedLine = mock<ParsedLine>()
        whenever(parsedLine.words()) doReturn words.toList()
        return parsedLine
    }

    /**
     * Helper function to get the captured console output.
     */
    protected fun getOutput(): String = testOut.toString()
}
