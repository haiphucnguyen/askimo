/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.injector.ContentInjector
import io.askimo.core.logging.logger

/**
 * ContentInjector that includes metadata (file paths, line numbers) when formatting
 * retrieved chunks into the prompt. This allows the AI to cite exact sources.
 */
class MetadataAwareContentInjector : ContentInjector {

    private val log = logger<MetadataAwareContentInjector>()

    override fun inject(
        contents: List<Content?>?,
        chatMessage: ChatMessage?,
    ): ChatMessage? {
        if (contents.isNullOrEmpty() || chatMessage == null) {
            log.debug("No contents to inject or null chat message")
            return chatMessage
        }

        // Only process UserMessage - other message types pass through unchanged
        if (chatMessage !is UserMessage) {
            log.debug("Skipping non-user message type: {}", chatMessage.type())
            return chatMessage
        }

        val validContents = contents.filterNotNull()
        if (validContents.isEmpty()) {
            log.debug("All contents were null")
            return chatMessage
        }

        // Format context with metadata
        val formattedContext = validContents.joinToString("\n\n---\n\n") { content ->
            val segment = content.textSegment()
            val meta = segment.metadata()

            buildString {
                // Source citation header
                append("**Source:** ")
                val fileName = meta.getString("file_name") ?: "unknown"
                append("`$fileName`")

                // Add file path if available
                val filePath = meta.getString("file_path")
                if (filePath != null && filePath != fileName) {
                    append(" (`$filePath`)")
                }

                // Add line numbers if available
                val startLine = meta.getInteger("start_line")
                val endLine = meta.getInteger("end_line")
                if (startLine != null && endLine != null) {
                    append(" (lines $startLine-$endLine)")
                }

                append("\n\n")
                append(segment.text())
            }
        }

        val enhancedPrompt = """
            |Use the following context to answer the question.
            |
            |IMPORTANT: When answering, you MUST cite your sources by referencing:
            |- The file name (shown as `filename`)
            |- Line numbers (if provided, shown as "lines X-Y")
            |
            |Format citations like: "According to `filename` (lines X-Y), ..." or "In `filename`, ..."
            |
            |Context:
            |
            |$formattedContext
            |
            |---
            |
            |Question: ${chatMessage.singleText() ?: ""}
        """.trimMargin()

        log.debug("Injected {} chunks with metadata into prompt", validContents.size)

        return UserMessage.from(enhancedPrompt)
    }
}
