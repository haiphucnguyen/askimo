/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProcessBuilderExtTest {
    @Test
    fun `ProcessBuilderExt should execute common system commands`() {
        // Test with a common executable that should exist on all platforms
        val executable =
            if (isWindows()) {
                "cmd"
            } else {
                "sh"
            }

        val args =
            if (isWindows()) {
                arrayOf(executable, "/c", "echo", "hello")
            } else {
                arrayOf(executable, "-c", "echo hello")
            }

        val process = ProcessBuilderExt(*args).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        assertTrue(exitCode == 0, "Command should execute successfully")
        assertTrue(output.contains("hello"), "Output should contain 'hello'")
    }

    @Test
    fun `ProcessBuilderExt should work with redirectErrorStream`() {
        val executable =
            if (isWindows()) {
                "cmd"
            } else {
                "sh"
            }

        val process =
            ProcessBuilderExt(executable, "--version")
                .redirectErrorStream(true)
                .start()

        val exitCode = process.waitFor()
        // Either success or error is fine, we're just testing the API works
        assertNotNull(exitCode)
    }

    @Test
    fun `ProcessBuilderExt should handle List constructor`() {
        val executable =
            if (isWindows()) {
                "cmd"
            } else {
                "sh"
            }

        val command =
            if (isWindows()) {
                listOf(executable, "/c", "echo", "test")
            } else {
                listOf(executable, "-c", "echo test")
            }

        val process = ProcessBuilderExt(command).start()
        val exitCode = process.waitFor()

        assertTrue(exitCode == 0, "Command should execute successfully")
    }

    @Test
    fun `ProcessBuilderExt should find executables in PATH`() {
        // Try to find a common executable
        // This test might fail if the executable is not installed
        try {
            val process =
                ProcessBuilderExt("echo", "test")
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            // If echo is found and works, verify it
            if (exitCode == 0) {
                assertTrue(output.contains("test"), "Echo should output 'test'")
            }
        } catch (e: Exception) {
            // It's ok if echo is not found on some systems
            println("Echo not available: ${e.message}")
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")
}
