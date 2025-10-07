/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

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
fun ChatService.chat(
    prompt: String,
    onToken: (String) -> Unit = {},
): String {
    val sb = StringBuilder()
    val done = CountDownLatch(1)

    stream(prompt)
        .onPartialResponse { chunk ->
            sb.append(chunk)
            onToken(chunk)
        }.onCompleteResponse {
            done.countDown()
        }.onError { e ->
            e.printStackTrace()
            onToken("\n[error] ${e.message ?: "unknown error"}\n")
            done.countDown()
        }.start()

    done.await()
    return sb.toString()
}
