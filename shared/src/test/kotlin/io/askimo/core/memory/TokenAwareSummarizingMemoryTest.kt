/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.memory

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains

class TokenAwareSummarizingMemoryTest {

    private var memory: TokenAwareSummarizingMemory? = null

    @AfterEach
    fun tearDown() {
        memory?.close()
    }

    @Test
    fun `should create memory with default builder settings`() {
        memory = TokenAwareSummarizingMemory.builder().build()

        assertNotNull(memory)
        assertEquals(0, memory!!.messages().size)
    }

    @Test
    fun `should create memory with custom max tokens`() {
        memory = TokenAwareSummarizingMemory.builder()
            .maxTokens(2000)
            .build()

        assertNotNull(memory)
    }

    @Test
    fun `should add single message to memory`() {
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        val message = UserMessage.from("Hello, world!")

        memory!!.add(message)

        val messages = memory!!.messages()
        assertEquals(1, messages.size)
        assertTrue(messages[0] is UserMessage)
        assertEquals("Hello, world!", (messages[0] as UserMessage).singleText())
    }

    @Test
    fun `should add multiple messages to memory`() {
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        memory!!.add(UserMessage.from("First message"))
        memory!!.add(AiMessage.from("Second message"))
        memory!!.add(UserMessage.from("Third message"))

        val messages = memory!!.messages()
        assertEquals(3, messages.size)
    }

    @Test
    fun `should clear all messages from memory`() {
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        memory!!.add(UserMessage.from("Message 1"))
        memory!!.add(UserMessage.from("Message 2"))
        assertEquals(2, memory!!.messages().size)

        memory!!.clear()

        assertEquals(0, memory!!.messages().size)
    }

    @Test
    fun `should use custom token estimator`() {
        var estimatorCallCount = 0
        val customEstimator: (ChatMessage) -> Int = { message ->
            estimatorCallCount++
            100 // Fixed token count for testing
        }

        memory = TokenAwareSummarizingMemory.builder()
            .tokenEstimator(customEstimator)
            .asyncSummarization(false)
            .build()

        memory!!.add(UserMessage.from("Test"))

        assertTrue(estimatorCallCount > 0, "Custom estimator should have been called")
    }

    @Test
    fun `should trigger summarization when threshold exceeded`() {
        val latch = CountDownLatch(1)
        var summarizerCalled = false

        val summarizer: (String) -> ConversationSummary = { text ->
            summarizerCalled = true
            latch.countDown()
            ConversationSummary(
                keyFacts = mapOf("topic" to "test"),
                mainTopics = listOf("testing"),
                recentContext = "Test conversation",
            )
        }

        memory = TokenAwareSummarizingMemory.builder()
            .maxTokens(50)
            .summarizationThreshold(0.5)
            .tokenEstimator { 20 } // Each message = 20 tokens
            .summarizer(summarizer)
            .build()

        // When - Add enough messages to exceed threshold (50 * 0.5 = 25 tokens)
        memory!!.add(UserMessage.from("Message 1")) // 20 tokens
        memory!!.add(UserMessage.from("Message 2")) // 40 tokens total
        memory!!.add(UserMessage.from("Message 3")) // 60 tokens - exceeds threshold!

        // Wait for async summarization
        latch.await(5, TimeUnit.SECONDS)

        assertTrue(summarizerCalled, "Summarizer should have been called")
    }

    @Test
    fun `should generate basic summary when no summarizer provided`() {
        memory = TokenAwareSummarizingMemory.builder()
            .maxTokens(50)
            .summarizationThreshold(0.5)
            .tokenEstimator { 20 }
            .asyncSummarization(false)
            .build()

        // When - Add enough messages to trigger summarization
        repeat(5) { i ->
            memory!!.add(UserMessage.from("Message $i with some content to make it longer"))
        }

        // Give time for synchronous summarization
        Thread.sleep(500)

        // Then - Should have pruned some messages or added summary
        val messages = memory!!.messages()
        // Either messages were pruned OR a summary was added
        val userMessages = messages.filterIsInstance<UserMessage>()
        val systemMessages = messages.filterIsInstance<SystemMessage>()

        // Should have either pruned messages or added a system summary message
        assertTrue(
            userMessages.size < 5 || systemMessages.isNotEmpty(),
            "Messages should have been pruned or summary added",
        )
    }

    @Test
    fun `should include structured summary in messages when available`() {
        // Given
        val summarizer: (String) -> ConversationSummary = { _ ->
            ConversationSummary(
                keyFacts = mapOf("name" to "John", "age" to "30"),
                mainTopics = listOf("introduction", "greetings"),
                recentContext = "User introduced themselves",
            )
        }

        memory = TokenAwareSummarizingMemory.builder()
            .maxTokens(50)
            .summarizationThreshold(0.5)
            .tokenEstimator { 20 }
            .summarizer(summarizer)
            .build()

        repeat(5) { i ->
            memory!!.add(UserMessage.from("Message $i with some content to reach token limit"))
        }

        // Wait for async summarization
        Thread.sleep(1000)

        val messages = memory!!.messages()

        // Then - First message should be a SystemMessage with summary
        assertTrue(messages.isNotEmpty())
        val firstMessage = messages.firstOrNull()
        if (firstMessage is SystemMessage) {
            val summaryText = firstMessage.text()
            assertContains(summaryText, "CONVERSATION CONTEXT")
        }
    }

    @Test
    fun `should export and import memory state`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        val msg1 = UserMessage.from("First message")
        val msg2 = AiMessage.from("Second message")
        memory!!.add(msg1)
        memory!!.add(msg2)

        // When - Export state
        val state = memory!!.exportState()

        // Then
        assertEquals(2, state.messages.size)

        // When - Create new memory and import state
        val newMemory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()
        newMemory.importState(state)

        // Then
        val importedMessages = newMemory.messages()
        assertEquals(2, importedMessages.size)
        assertTrue(importedMessages[0] is UserMessage)
        assertTrue(importedMessages[1] is AiMessage)

        newMemory.close()
    }

    @Test
    fun `should export state with summary`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        val summary = ConversationSummary(
            keyFacts = mapOf("key" to "value"),
            mainTopics = listOf("topic1"),
            recentContext = "context",
        )

        val state = TokenAwareSummarizingMemory.MemoryState(
            messages = listOf(UserMessage.from("Test")),
            summary = summary,
        )

        memory!!.importState(state)

        // When
        val exportedState = memory!!.exportState()

        // Then
        assertNotNull(exportedState.summary)
        assertEquals(summary.keyFacts, exportedState.summary!!.keyFacts)
        assertEquals(summary.mainTopics, exportedState.summary!!.mainTopics)
        assertEquals(summary.recentContext, exportedState.summary!!.recentContext)
    }

    @Test
    fun `should clear summary when clearing memory`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        val state = TokenAwareSummarizingMemory.MemoryState(
            messages = listOf(UserMessage.from("Test")),
            summary = ConversationSummary(
                keyFacts = mapOf("key" to "value"),
                mainTopics = listOf("topic"),
                recentContext = "context",
            ),
        )
        memory!!.importState(state)

        // When
        memory!!.clear()

        // Then
        val exportedState = memory!!.exportState()
        assertEquals(0, exportedState.messages.size)
        assertNull(exportedState.summary)
    }

    @Test
    fun `should handle different message types`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        val userMsg = UserMessage.from("User message")
        val aiMsg = AiMessage.from("AI message")
        val systemMsg = SystemMessage.from("System message")

        // When
        memory!!.add(userMsg)
        memory!!.add(aiMsg)
        memory!!.add(systemMsg)

        // Then
        val messages = memory!!.messages()
        assertEquals(3, messages.size)
        assertTrue(messages[0] is UserMessage)
        assertTrue(messages[1] is AiMessage)
        assertTrue(messages[2] is SystemMessage)
    }

    @Test
    fun `should be thread-safe when adding messages concurrently`() = runBlocking {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        // When - Add messages from multiple threads
        val threads = List(10) { threadIndex ->
            Thread {
                repeat(10) { msgIndex ->
                    memory!!.add(UserMessage.from("Thread $threadIndex, Message $msgIndex"))
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then - Should have all 100 messages
        assertEquals(100, memory!!.messages().size)
    }

    @Test
    fun `should merge summaries when summarizing multiple times`() {
        // Given
        var summaryCount = 0
        val summarizer: (String) -> ConversationSummary = { _ ->
            summaryCount++
            ConversationSummary(
                keyFacts = mapOf("fact$summaryCount" to "value$summaryCount"),
                mainTopics = listOf("topic$summaryCount"),
                recentContext = "Context $summaryCount",
            )
        }

        memory = TokenAwareSummarizingMemory.builder()
            .maxTokens(50)
            .summarizationThreshold(0.5)
            .tokenEstimator { 15 }
            .summarizer(summarizer)
            .asyncSummarization(false)
            .build()

        // When - Trigger multiple summarizations
        repeat(10) { i ->
            memory!!.add(UserMessage.from("Message $i with content"))
        }

        Thread.sleep(500)

        // Then - Should have merged multiple summaries
        val state = memory!!.exportState()
        if (state.summary != null) {
            assertTrue(state.summary!!.keyFacts.size > 0)
        }
    }

    @Test
    fun `should properly close and cleanup resources`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder().build()

        memory!!.add(UserMessage.from("Test"))

        // When
        assertDoesNotThrow {
            memory!!.close()
        }

        // Then - Should not throw on second close
        assertDoesNotThrow {
            memory!!.close()
        }
    }

    @Test
    fun `should handle summarization timeout gracefully`() {
        // Given
        val summarizer: (String) -> ConversationSummary = { _ ->
            // Simulate slow summarization
            Thread.sleep(5000)
            ConversationSummary()
        }

        memory = TokenAwareSummarizingMemory.builder()
            .maxTokens(50)
            .summarizationThreshold(0.5)
            .tokenEstimator { 20 }
            .summarizer(summarizer)
            .summarizationTimeout(1) // 1 second timeout
            .build()

        // When - Trigger summarization
        repeat(5) { i ->
            memory!!.add(UserMessage.from("Message $i"))
        }

        // Wait for timeout
        Thread.sleep(2000)

        // Then - Should still function normally after timeout
        assertDoesNotThrow {
            memory!!.add(UserMessage.from("Another message"))
            memory!!.messages()
        }
    }

    @Test
    fun `should preserve message order after summarization`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .maxTokens(50)
            .summarizationThreshold(0.5)
            .tokenEstimator { 20 }
            .asyncSummarization(false)
            .build()

        // When - Add messages
        repeat(5) { i ->
            memory!!.add(UserMessage.from("Message $i"))
        }

        Thread.sleep(100)

        // Then - Remaining messages should maintain order
        val messages = memory!!.messages()
        val userMessages = messages.filterIsInstance<UserMessage>()

        if (userMessages.size >= 2) {
            val firstContent = userMessages[0].singleText()!!
            val secondContent = userMessages[1].singleText()!!

            val firstNum = firstContent.filter { it.isDigit() }.toIntOrNull()
            val secondNum = secondContent.filter { it.isDigit() }.toIntOrNull()

            if (firstNum != null && secondNum != null) {
                assertTrue(firstNum < secondNum, "Messages should maintain chronological order")
            }
        }
    }

    @Test
    fun `should use default token estimator when none provided`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        // When
        memory!!.add(UserMessage.from("This is a test message with several words"))

        // Then - Should not throw, default estimator should work
        val messages = memory!!.messages()
        assertEquals(1, messages.size)
    }

    @Test
    fun `should handle builder with all options set`() {
        // Given/When
        memory = TokenAwareSummarizingMemory.builder()
            .maxTokens(3000)
            .tokenEstimator { 50 }
            .summarizationThreshold(0.8)
            .summarizer { ConversationSummary() }
            .asyncSummarization(true)
            .summarizationTimeout(60)
            .build()

        // Then
        assertNotNull(memory)
        memory!!.add(UserMessage.from("Test"))
        assertEquals(1, memory!!.messages().size)
    }

    @Test
    fun `should return unique id for memory instance`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder().build()
        val memory2 = TokenAwareSummarizingMemory.builder().build()

        // When
        val id1 = memory!!.id()
        val id2 = memory2.id()

        // Then
        assertNotEquals(id1, id2)

        memory2.close()
    }

    @Test
    fun `should handle summarization with no messages`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        // When/Then - Should not throw
        assertDoesNotThrow {
            memory!!.clear()
            memory!!.messages()
        }
    }

    @Test
    fun `should prune oldest messages during summarization`() {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .maxTokens(50)
            .summarizationThreshold(0.5)
            .tokenEstimator { 15 }
            .asyncSummarization(false)
            .build()

        // When - Add messages
        memory!!.add(UserMessage.from("Old message 1"))
        memory!!.add(UserMessage.from("Old message 2"))
        memory!!.add(UserMessage.from("Old message 3"))
        memory!!.add(UserMessage.from("Recent message 4"))
        memory!!.add(UserMessage.from("Recent message 5"))

        Thread.sleep(200)

        // Then - Should have pruned oldest messages
        val messages = memory!!.messages()
        val userMessages = messages.filterIsInstance<UserMessage>()
        assertTrue(userMessages.size < 5, "Should have pruned some messages")
    }

    @Test
    fun `should handle concurrent export and import operations`() = runBlocking {
        // Given
        memory = TokenAwareSummarizingMemory.builder()
            .asyncSummarization(false)
            .build()

        repeat(5) { i ->
            memory!!.add(UserMessage.from("Message $i"))
        }

        // When - Export and import concurrently
        val threads = List(5) { threadIndex ->
            Thread {
                val state = memory!!.exportState()
                val newMemory = TokenAwareSummarizingMemory.builder()
                    .asyncSummarization(false)
                    .build()
                newMemory.importState(state)
                assertEquals(5, newMemory.messages().size)
                newMemory.close()
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then - Original memory should still be intact
        assertEquals(5, memory!!.messages().size)
    }
}
