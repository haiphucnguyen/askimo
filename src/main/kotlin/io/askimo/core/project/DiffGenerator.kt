/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import dev.langchain4j.service.TokenStream
import io.askimo.core.providers.ChatService

class DiffGenerator(
    private val chat: ChatService,
) {
    /**
     * Builds the diff prompt and returns the full model output as a string.
     * Expects the model to follow the "diff only" instruction.
     */
    fun generateDiff(request: DiffRequest): String {
        val built = PromptBuilder.build(request)

        val prompt = built.asSingleMessage()

        val sb = StringBuilder()
        val stream: TokenStream = chat.stream(prompt)
        stream.onPartialResponse { sb.append(it) }
        return sb
            .toString()
            .trim()
            .removeMarkdownFencesIfAny()
    }
}

/** Best-effort cleanup if a model still wraps the diff in triple backticks. */
private fun String.removeMarkdownFencesIfAny(): String {
    val trimmed = this.trim()
    if (trimmed.startsWith("```")) {
        val idx = trimmed.indexOf('\n')
        val afterFence = if (idx >= 0) trimmed.substring(idx + 1) else trimmed
        val end = afterFence.lastIndexOf("```")
        return if (end >= 0) afterFence.take(end).trim() else afterFence.trim()
    }
    return trimmed
}
