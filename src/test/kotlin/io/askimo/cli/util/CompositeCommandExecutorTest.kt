/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.util

import io.askimo.cli.commands.CommandHandler
import org.jline.reader.ParsedLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompositeCommandExecutorTest {
    private fun createTestHandler(keyword: String) = object : CommandHandler {
        override val keyword = keyword
        override val description = "Test handler"
        override fun handle(line: ParsedLine) {}
    }

    @Test
    fun `should detect multiple commands`() {
        val handlers = listOf(
            createTestHandler(":set-provider"),
            createTestHandler(":set-param"),
            createTestHandler(":config"),
        )

        val args = arrayOf("--set-provider", "openai", "--set-param", "model", "gpt-4")
        assertTrue(CompositeCommandExecutor.hasMultipleCommands(args, handlers))
    }

    @Test
    fun `should not detect single command as multiple`() {
        val handlers = listOf(
            createTestHandler(":set-provider"),
            createTestHandler(":config"),
        )

        val args = arrayOf("--set-provider", "openai")
        assertFalse(CompositeCommandExecutor.hasMultipleCommands(args, handlers))
    }

    @Test
    fun `should execute multiple commands in order`() {
        val executionOrder = mutableListOf<String>()

        val handler1 = object : CommandHandler {
            override val keyword = ":set-provider"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                executionOrder.add("handler1:${line.words().drop(1).joinToString(" ")}")
            }
        }

        val handler2 = object : CommandHandler {
            override val keyword = ":set-param"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                executionOrder.add("handler2:${line.words().drop(1).joinToString(" ")}")
            }
        }

        val handler3 = object : CommandHandler {
            override val keyword = ":config"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                executionOrder.add("handler3")
            }
        }

        val handlerMap = mapOf(
            "--set-provider" to handler1,
            "--set-param" to handler2,
            "--config" to handler3,
        )

        val args = arrayOf(
            "--set-provider",
            "openai",
            "--set-param",
            "model",
            "gpt-4",
            "--config",
        )

        CompositeCommandExecutor.executeCommands(args, handlerMap)

        assertEquals(3, executionOrder.size)
        assertEquals("handler1:openai", executionOrder[0])
        assertEquals("handler2:model gpt-4", executionOrder[1])
        assertEquals("handler3", executionOrder[2])
    }

    @Test
    fun `should handle multiple instances of same command`() {
        val executionOrder = mutableListOf<String>()

        val handler = object : CommandHandler {
            override val keyword = ":set-param"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                executionOrder.add(line.words().drop(1).joinToString(" "))
            }
        }

        val handlerMap = mapOf("--set-param" to handler)

        val args = arrayOf(
            "--set-param", "temperature", "0.7",
            "--set-param", "max_tokens", "2000",
            "--set-param", "top_p", "0.9",
        )

        CompositeCommandExecutor.executeCommands(args, handlerMap)

        assertEquals(3, executionOrder.size)
        assertEquals("temperature 0.7", executionOrder[0])
        assertEquals("max_tokens 2000", executionOrder[1])
        assertEquals("top_p 0.9", executionOrder[2])
    }

    @Test
    fun `should handle commands without arguments`() {
        val executionOrder = mutableListOf<String>()

        val handler1 = object : CommandHandler {
            override val keyword = ":providers"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                executionOrder.add("providers")
            }
        }

        val handler2 = object : CommandHandler {
            override val keyword = ":tools"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                executionOrder.add("tools")
            }
        }

        val handlerMap = mapOf(
            "--providers" to handler1,
            "--tools" to handler2,
        )

        val args = arrayOf("--providers", "--tools")

        CompositeCommandExecutor.executeCommands(args, handlerMap)

        assertEquals(2, executionOrder.size)
        assertEquals("providers", executionOrder[0])
        assertEquals("tools", executionOrder[1])
    }

    @Test
    fun `should skip unknown flags`() {
        val executionOrder = mutableListOf<String>()

        val handler = object : CommandHandler {
            override val keyword = ":config"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                executionOrder.add("config")
            }
        }

        val handlerMap = mapOf("--config" to handler)

        val args = arrayOf("--unknown-flag", "value", "--config")

        CompositeCommandExecutor.executeCommands(args, handlerMap)

        assertEquals(1, executionOrder.size)
        assertEquals("config", executionOrder[0])
    }

    @Test
    fun `should handle empty arguments array`() {
        val handlerMap = mapOf<String, CommandHandler>()
        val args = arrayOf<String>()

        // Should not throw exception
        CompositeCommandExecutor.executeCommands(args, handlerMap)
    }

    @Test
    fun `should correctly parse complex command sequences`() {
        val executionOrder = mutableListOf<String>()

        val providerHandler = object : CommandHandler {
            override val keyword = ":set-provider"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                executionOrder.add("provider:${line.words()[1]}")
            }
        }

        val paramHandler = object : CommandHandler {
            override val keyword = ":set-param"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                val words = line.words()
                executionOrder.add("param:${words[1]}=${words[2]}")
            }
        }

        val configHandler = object : CommandHandler {
            override val keyword = ":config"
            override val description = "Test"
            override fun handle(line: ParsedLine) {
                executionOrder.add("config")
            }
        }

        val handlerMap = mapOf(
            "--set-provider" to providerHandler,
            "--set-param" to paramHandler,
            "--config" to configHandler,
        )

        val args = arrayOf(
            "--set-provider", "openai",
            "--set-param", "api_key", "sk-abc123",
            "--set-param", "model", "gpt-4o",
            "--set-param", "temperature", "0.7",
            "--config",
        )

        CompositeCommandExecutor.executeCommands(args, handlerMap)

        assertEquals(5, executionOrder.size)
        assertEquals("provider:openai", executionOrder[0])
        assertEquals("param:api_key=sk-abc123", executionOrder[1])
        assertEquals("param:model=gpt-4o", executionOrder[2])
        assertEquals("param:temperature=0.7", executionOrder[3])
        assertEquals("config", executionOrder[4])
    }
}
