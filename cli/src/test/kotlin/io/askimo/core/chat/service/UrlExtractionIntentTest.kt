/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests for URL extraction intent detection in UrlIntentDetector.
 *
 * These tests verify that the detector correctly identifies when users explicitly
 * request URL content extraction, following the pattern used by ChatGPT, Claude,
 * and other AI services.
 */
class UrlExtractionIntentTest {

    companion object {
        private const val TEST_URL = "https://example.com/article"
    }

    @Test
    fun `should extract URL when user asks to summarize`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Summarize $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Can you summarize $TEST_URL?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Please summarize this: $TEST_URL", TEST_URL))
    }

    @Test
    fun `should extract URL when user asks to analyze`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Analyze $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Can you analyze $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Please analyze the content at $TEST_URL", TEST_URL))
    }

    @Test
    fun `should extract URL when user asks to explain`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Explain $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Can you explain what's in $TEST_URL?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Please explain $TEST_URL", TEST_URL))
    }

    @Test
    fun `should extract URL when user asks to read`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Read $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Can you read $TEST_URL for me?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Please read this article: $TEST_URL", TEST_URL))
    }

    @Test
    fun `should extract URL when user asks to review`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Review $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Can you review $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Please review the content at $TEST_URL", TEST_URL))
    }

    @Test
    fun `should extract URL when user asks to check content`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Check what's in $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Can you check this $TEST_URL?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Please check the content at $TEST_URL", TEST_URL))
    }

    @Test
    fun `should extract URL when user asks to examine`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Examine $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Can you examine $TEST_URL", TEST_URL))
    }

    // ========== Tests for Questions About URL ==========

    @Test
    fun `should extract URL when asking what URL says`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What does $TEST_URL say?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What does $TEST_URL say about AI?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What does this say $TEST_URL", TEST_URL))
    }

    @Test
    fun `should extract URL when asking what URL is about`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What is $TEST_URL about?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What does $TEST_URL talk about?", TEST_URL))
    }

    @Test
    fun `should extract URL when asking what's on the URL`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What's on $TEST_URL?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What is on $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What's in $TEST_URL?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What is in $TEST_URL", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What's at $TEST_URL?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What is at $TEST_URL", TEST_URL))
    }

    @Test
    fun `should extract URL when asking what the page says`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What does the page $TEST_URL say?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What does this page say $TEST_URL", TEST_URL))
    }

    // ========== Tests for URL with Question Mark ==========

    @Test
    fun `should extract URL when URL is followed by question mark`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL - can you help me understand this?", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Look at $TEST_URL, what do you think?", TEST_URL))
    }

    @Test
    fun `should extract URL when asking to tell me about URL`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL tell me about this", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL can you tell me what this says?", TEST_URL))
    }

    @Test
    fun `should extract URL when asking to show me URL content`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL show me what's there", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL please show me the content", TEST_URL))
    }

    // ========== Tests for Reverse Patterns (URL before action) ==========

    @Test
    fun `should extract URL when URL comes before action verb`() {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL - summarize this please", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL analyze it", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL explain what it says", TEST_URL))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL review the content", TEST_URL))
    }

    // ========== Tests for Case Insensitivity ==========

    @ParameterizedTest
    @ValueSource(
        strings = [
            "SUMMARIZE https://example.com/article",
            "Summarize https://example.com/article",
            "summarize https://example.com/article",
            "SuMmArIzE https://example.com/article",
            "What does HTTPS://EXAMPLE.COM/ARTICLE say?",
            "WHAT DOES https://example.com/article SAY?",
        ],
    )
    fun `should handle case insensitive patterns`(message: String) {
        assertTrue(UrlIntentDetector.shouldExtractUrlContent(message, TEST_URL))
    }

    // ========== Tests for Cases When URL Should NOT Be Extracted ==========

    @Test
    fun `should NOT extract URL when just mentioning as reference`() {
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("See the docs at $TEST_URL", TEST_URL))
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("More info here: $TEST_URL", TEST_URL))
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("Reference: $TEST_URL", TEST_URL))
    }

    @Test
    fun `should NOT extract URL when sharing for later`() {
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("I found this interesting article: $TEST_URL", TEST_URL))
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("You might like this: $TEST_URL", TEST_URL))
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("Here's a good resource: $TEST_URL", TEST_URL))
    }

    @Test
    fun `should NOT extract URL when just providing context`() {
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("I was reading $TEST_URL and had a question about AI", TEST_URL))
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("According to $TEST_URL, AI is advancing rapidly", TEST_URL))
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("The article $TEST_URL mentions several points", TEST_URL))
    }

    @Test
    fun `should NOT extract URL when asking unrelated question`() {
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("What is machine learning? Also, see $TEST_URL", TEST_URL))
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("How does neural network work? Ref: $TEST_URL", TEST_URL))
    }

    @Test
    fun `should NOT extract URL when just bookmarking or saving`() {
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("Save this for later: $TEST_URL", TEST_URL))
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("Bookmark: $TEST_URL", TEST_URL))
        assertFalse(UrlIntentDetector.shouldExtractUrlContent("Remember to check $TEST_URL later", TEST_URL))
    }

    // ========== Tests for Multiple URLs ==========

    @Test
    fun `should correctly identify which URLs need extraction in multi-URL message`() {
        val url1 = "https://example.com/article1"
        val url2 = "https://example.com/article2"
        val message = "Summarize $url1 and here's another reference: $url2"

        assertTrue(UrlIntentDetector.shouldExtractUrlContent(message, url1), "Should extract first URL with action verb")
        assertFalse(UrlIntentDetector.shouldExtractUrlContent(message, url2), "Should NOT extract second URL as it's just a reference")
    }

    @Test
    fun `should extract both URLs when both have explicit intent`() {
        val url1 = "https://example.com/article1"
        val url2 = "https://example.com/article2"
        val message = "Summarize $url1 and analyze $url2"

        assertTrue(UrlIntentDetector.shouldExtractUrlContent(message, url1))
        assertTrue(UrlIntentDetector.shouldExtractUrlContent(message, url2))
    }

    // ========== Tests for Edge Cases ==========

    @Test
    fun `should handle URL with query parameters`() {
        val urlWithParams = "https://example.com/article?id=123&source=test"
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Summarize $urlWithParams", urlWithParams))
    }

    @Test
    fun `should handle URL with fragments`() {
        val urlWithFragment = "https://example.com/article#section2"
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("What does $urlWithFragment say?", urlWithFragment))
    }

    @Test
    fun `should handle very long messages with URL`() {
        val longMessage = "I was wondering if you could help me with something. " +
            "I've been reading a lot about AI lately and came across this article. " +
            "Can you summarize $TEST_URL for me? " +
            "I think it would really help me understand the topic better."
        assertTrue(UrlIntentDetector.shouldExtractUrlContent(longMessage, TEST_URL))
    }

    @Test
    fun `should handle URL at different positions in message`() {
        // URL at start
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("$TEST_URL - can you summarize this?", TEST_URL))

        // URL in middle
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Please summarize $TEST_URL for me", TEST_URL))

        // URL at end
        assertTrue(UrlIntentDetector.shouldExtractUrlContent("Can you summarize this article: $TEST_URL", TEST_URL))
    }
}
