/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetryUtilsTest {
    @Test
    fun `retry succeeds on first attempt`() {
        var callCount = 0
        val result =
            RetryUtils.retry(
                RetryConfig(maxAttempts = 3),
            ) {
                callCount++
                "success"
            }

        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `retry succeeds on second attempt`() {
        var callCount = 0
        val result =
            RetryUtils.retry(
                RetryConfig(maxAttempts = 3, initialDelayMs = 10),
            ) {
                callCount++
                if (callCount == 1) {
                    throw RuntimeException("First attempt fails")
                }
                "success"
            }

        assertEquals("success", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `retry fails after max attempts`() {
        var callCount = 0
        val exception =
            assertThrows<RuntimeException> {
                RetryUtils.retry(
                    RetryConfig(maxAttempts = 2, initialDelayMs = 10),
                ) {
                    callCount++
                    throw RuntimeException("Always fails")
                }
            }

        assertEquals("Always fails", exception.message)
        assertEquals(2, callCount)
    }

    @Test
    fun `retry with condition only retries matching exceptions`() {
        var callCount = 0
        val exception =
            assertThrows<Exception> {
                RetryUtils.retry(
                    RetryConfig(
                        maxAttempts = 3,
                        initialDelayMs = 10,
                        retryCondition = { it is RuntimeException },
                    ),
                ) {
                    callCount++
                    throw Exception("Not retryable")
                }
            }

        assertEquals("Not retryable", exception.message)
        assertEquals(1, callCount) // Should not retry
    }

    @Test
    fun `retry calls onRetry callback`() {
        var retryCallCount = 0
        var callCount = 0

        val result =
            RetryUtils.retry(
                RetryConfig(
                    maxAttempts = 3,
                    initialDelayMs = 10,
                    onRetry = { attempt, maxAttempts, exception, delayMs ->
                        retryCallCount++
                        assertTrue(attempt > 0)
                        assertTrue(maxAttempts == 3)
                        assertTrue(delayMs > 0)
                    },
                ),
            ) {
                callCount++
                if (callCount <= 2) {
                    throw RuntimeException("Fail first two attempts")
                }
                "success"
            }

        assertEquals("success", result)
        assertEquals(3, callCount)
        assertEquals(2, retryCallCount) // Called for first two failures
    }

    @Test
    fun `OLLAMA_TRANSIENT_ERRORS preset retries correct exceptions`() {
        var callCount = 0

        // Should retry IllegalArgumentException with empty output message
        val result1 =
            RetryUtils.retry(RetryPresets.RECIPE_EXECUTOR_TRANSIENT_ERRORS) {
                callCount++
                if (callCount == 1) {
                    throw IllegalArgumentException("Model returned empty output")
                }
                "success"
            }

        assertEquals("success", result1)
        assertEquals(2, callCount)

        // Should retry NullPointerException with LangChain4j stack trace
        callCount = 0
        val npe = NullPointerException("Tool service error")
        npe.stackTrace =
            arrayOf(
                StackTraceElement("dev.langchain4j.service.tool.ToolService", "method", "file", 1),
            )

        val result2 =
            RetryUtils.retry(RetryPresets.RECIPE_EXECUTOR_TRANSIENT_ERRORS) {
                callCount++
                if (callCount == 1) {
                    throw npe
                }
                "success"
            }

        assertEquals("success", result2)
        assertEquals(2, callCount)
    }

    @Test
    fun `STREAMING_ERRORS preset retries streaming-related exceptions`() {
        var callCount = 0

        val result =
            RetryUtils.retry(RetryPresets.STREAMING_ERRORS) {
                callCount++
                if (callCount == 1) {
                    throw RuntimeException("Received empty response")
                }
                "success"
            }

        assertEquals("success", result)
        assertEquals(2, callCount)
    }
}
