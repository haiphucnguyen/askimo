/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.util.RetryPresets
import io.askimo.core.util.RetryUtils
import java.util.concurrent.CountDownLatch

/**
 * Provides a synchronous interface to chat with a language model.
 *
 * This extension function wraps the asynchronous streaming API of [ChatService]
 * into a blocking call that returns the complete response as a string.
 *
 * @param prompt The input text to send to the language model
 * @param onToken Optional callback function that is invoked for each token received from the model
 * @return The complete response from the language model as a string
 */
fun ChatService.sendStreamingMessageWithCallback(
    prompt: String,
    onToken: (String) -> Unit = {},
): String = RetryUtils.retry(RetryPresets.STREAMING_ERRORS) {
    val sb = StringBuilder()
    val done = CountDownLatch(1)
    var errorOccurred = false

    sendMessageStreaming(prompt)
        .onPartialResponse { chunk ->
            sb.append(chunk)
            onToken(chunk)
        }.onCompleteResponse {
            done.countDown()
        }.onError { e ->
            errorOccurred = true
            e.printStackTrace()
            onToken("\n[error] ${e.message ?: "unknown error"}\n")
            done.countDown()
        }.start()

    done.await()

    val result = sb.toString()

    // If we got an error during streaming or empty result, throw to trigger retry
    if (errorOccurred) {
        throw RuntimeException("Streaming error occurred")
    }

    if (result.trim().isEmpty()) {
        throw IllegalStateException("Model returned empty streaming response")
    }

    result
}
