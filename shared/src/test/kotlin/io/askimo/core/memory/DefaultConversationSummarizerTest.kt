/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.memory

import io.askimo.core.providers.ModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultConversationSummarizerTest {

    @Test
    fun `should parse valid JSON response`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            {
              "keyFacts": {
                "name": "John",
                "age": "30"
              },
              "mainTopics": ["introduction", "greetings"],
              "recentContext": "User introduced themselves"
            }
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(2, summary.keyFacts.size)
        assertEquals("John", summary.keyFacts["name"])
        assertEquals("30", summary.keyFacts["age"])
        assertEquals(2, summary.mainTopics.size)
        assertEquals("User introduced themselves", summary.recentContext)
    }

    @Test
    fun `should handle JSON wrapped in markdown code blocks`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            ```json
            {
              "keyFacts": {
                "topic": "testing"
              },
              "mainTopics": ["testing"],
              "recentContext": "Test context"
            }
            ```
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(1, summary.keyFacts.size)
        assertEquals("testing", summary.keyFacts["topic"])
    }

    @Test
    fun `should sanitize arrays in keyFacts to comma-separated strings`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            {
              "keyFacts": {
                "programming_language": "Java",
                "frameworks": ["OpenAI Java SDK", "LangChain4J 1.9", "Spring Boot"],
                "api": "DALL-E"
              },
              "mainTopics": ["programming"],
              "recentContext": "Discussion about frameworks"
            }
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(3, summary.keyFacts.size)
        assertEquals("Java", summary.keyFacts["programming_language"])
        assertEquals("OpenAI Java SDK, LangChain4J 1.9, Spring Boot", summary.keyFacts["frameworks"])
        assertEquals("DALL-E", summary.keyFacts["api"])
    }

    @Test
    fun `should handle multiple arrays in keyFacts`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            {
              "keyFacts": {
                "languages": ["Java", "Kotlin"],
                "tools": ["Gradle", "Maven"],
                "version": "1.0"
              },
              "mainTopics": ["development"],
              "recentContext": "Tech stack discussion"
            }
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(3, summary.keyFacts.size)
        assertEquals("Java, Kotlin", summary.keyFacts["languages"])
        assertEquals("Gradle, Maven", summary.keyFacts["tools"])
        assertEquals("1.0", summary.keyFacts["version"])
    }

    @Test
    fun `should return empty summary on parsing failure`() {
        // Given
        val mockChatFunction: (String) -> String = {
            "This is not valid JSON at all!"
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(0, summary.keyFacts.size)
        assertEquals(0, summary.mainTopics.size)
        // recentContext should be last 500 chars of conversation on error
        assertEquals("Test conversation", summary.recentContext)
    }

    @Test
    fun `should handle JSON with extra text before and after`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            Here is the summary:
            {
              "keyFacts": {
                "result": "success"
              },
              "mainTopics": ["summary"],
              "recentContext": "Test"
            }
            That's all!
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(1, summary.keyFacts.size)
        assertEquals("success", summary.keyFacts["result"])
    }

    @Test
    fun `should handle empty keyFacts`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            {
              "keyFacts": {},
              "mainTopics": ["general"],
              "recentContext": "No specific facts"
            }
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(0, summary.keyFacts.size)
        assertEquals(1, summary.mainTopics.size)
        assertEquals("No specific facts", summary.recentContext)
    }

    @Test
    fun `should handle empty mainTopics`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            {
              "keyFacts": {
                "fact": "value"
              },
              "mainTopics": [],
              "recentContext": "Context"
            }
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(1, summary.keyFacts.size)
        assertEquals(0, summary.mainTopics.size)
    }

    @Test
    fun `should handle chatModel throwing exception`() {
        // Given
        val mockChatFunction: (String) -> String = {
            throw RuntimeException("API Error")
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then - Should return empty summary without throwing
        assertEquals(0, summary.keyFacts.size)
        assertEquals(0, summary.mainTopics.size)
        assertEquals("Test conversation", summary.recentContext)
    }

    @Test
    fun `should handle arrays with single values`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            {
              "keyFacts": {
                "tool": ["Gradle"]
              },
              "mainTopics": ["build"],
              "recentContext": "Build tool"
            }
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(1, summary.keyFacts.size)
        assertEquals("Gradle", summary.keyFacts["tool"])
    }

    @Test
    fun `should handle nested quotes in array values`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            {
              "keyFacts": {
                "quote": ["He said hello", "She replied hi"]
              },
              "mainTopics": ["conversation"],
              "recentContext": "Dialogue"
            }
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals(1, summary.keyFacts.size)
        assertEquals("He said hello, She replied hi", summary.keyFacts["quote"])
    }

    @Test
    fun `should preserve special characters in values`() {
        // Given
        val mockChatFunction: (String) -> String = {
            """
            {
              "keyFacts": {
                "email": "user@example.com",
                "path": "/home/user/project"
              },
              "mainTopics": ["contact"],
              "recentContext": "User info"
            }
            """.trimIndent()
        }

        val summarizer = DefaultConversationSummarizer.createSummarizer(ModelProvider.OPENAI, mockChatFunction)

        // When
        val summary = summarizer("Test conversation")

        // Then
        assertEquals("user@example.com", summary.keyFacts["email"])
        assertEquals("/home/user/project", summary.keyFacts["path"])
    }
}
