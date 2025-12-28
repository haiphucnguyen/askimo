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
 *
 * @param citationStyle How to format source citations (COMPACT, DETAILED, MINIMAL)
 * @param promptTemplate Custom prompt template. Use {context} and {question} placeholders
 */
class MetadataAwareContentInjector(
    private val citationStyle: CitationStyle = CitationStyle.COMPACT,
    private val promptTemplate: String? = null,
) : ContentInjector {

    private val log = logger<MetadataAwareContentInjector>()

    enum class CitationStyle {
        /** Minimal citation: just filename */
        MINIMAL,

        /** Compact citation: filename with line numbers if available */
        COMPACT,

        /** Detailed citation: full path and line numbers */
        DETAILED,
    }

    companion object {
        /**
         * Create an injector with minimal citations (just filenames)
         * Best for: General Q&A where exact line numbers aren't critical
         */
        fun minimal(customTemplate: String? = null) = MetadataAwareContentInjector(CitationStyle.MINIMAL, customTemplate)

        /**
         * Create an injector with compact citations (filename + line numbers)
         * Best for: Code discussions, documentation review
         */
        fun compact(customTemplate: String? = null) = MetadataAwareContentInjector(CitationStyle.COMPACT, customTemplate)

        /**
         * Create an injector with detailed citations (full path + line numbers)
         * Best for: Technical debugging, code review, precise references
         */
        fun detailed(customTemplate: String? = null) = MetadataAwareContentInjector(CitationStyle.DETAILED, customTemplate)

        /**
         * Create an injector with a completely custom template
         * Template placeholders: {context}, {question}
         */
        fun custom(template: String) = MetadataAwareContentInjector(CitationStyle.COMPACT, template)
    }

    private fun formatSourceCitation(content: Content): String {
        val segment = content.textSegment()
        val meta = segment.metadata()
        val fileName = meta.getString("file_name") ?: "unknown"
        val filePath = meta.getString("file_path")
        val startLine = meta.getInteger("start_line")
        val endLine = meta.getInteger("end_line")

        return when (citationStyle) {
            CitationStyle.MINIMAL -> {
                // Just filename
                "Source: $fileName"
            }
            CitationStyle.COMPACT -> {
                // Filename with line numbers if available
                buildString {
                    append("Source: `$fileName`")
                    if (startLine != null && endLine != null) {
                        append(" (lines $startLine-$endLine)")
                    }
                }
            }
            CitationStyle.DETAILED -> {
                // Full path and all available metadata
                buildString {
                    append("Source: `$fileName`")
                    if (filePath != null && filePath != fileName) {
                        append("\nPath: `$filePath`")
                    }
                    if (startLine != null && endLine != null) {
                        append("\nLines: $startLine-$endLine")
                    }
                }
            }
        }
    }

    private fun getDefaultPromptTemplate(): String = when (citationStyle) {
        CitationStyle.MINIMAL -> """
                |Answer the following question using the provided context.
                |
                |Context:
                |{context}
                |
                |Question: {question}
        """.trimMargin()

        CitationStyle.COMPACT -> """
                |Answer the following question using the provided context.
                |You may reference the source files when relevant.
                |
                |Context:
                |{context}
                |
                |Question: {question}
        """.trimMargin()

        CitationStyle.DETAILED -> """
                |Answer the following question using the provided context.
                |
                |When citing information, you can reference the source file and line numbers.
                |For example: "According to `filename` (lines 10-15)..." or "As shown in `filename`..."
                |
                |Context:
                |{context}
                |
                |Question: {question}
        """.trimMargin()
    }

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

        // Format context with metadata based on citation style
        val formattedContext = validContents.joinToString("\n\n---\n\n") { content ->
            buildString {
                append(formatSourceCitation(content))
                append("\n\n")
                append(content.textSegment().text())
            }
        }

        // Use custom template or default based on citation style
        val template = promptTemplate ?: getDefaultPromptTemplate()
        val enhancedPrompt = template
            .replace("{context}", formattedContext)
            .replace("{question}", chatMessage.singleText() ?: "")

        log.debug(
            "Injected {} chunks with {} citation style into prompt",
            validContents.size,
            citationStyle,
        )

        return UserMessage.from(enhancedPrompt)
    }
}
