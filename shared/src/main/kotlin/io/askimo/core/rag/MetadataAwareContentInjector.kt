/* SPDX-License-Identifier: AGPLv3
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
 * @param useAbsolutePaths If true, use absolute file paths in citations; if false, use relative filenames
 */
class MetadataAwareContentInjector(
    private val citationStyle: CitationStyle = CitationStyle.COMPACT,
    private val promptTemplate: String? = null,
    private val useAbsolutePaths: Boolean = true,
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
        fun minimal(customTemplate: String? = null, useAbsolutePaths: Boolean = true) = MetadataAwareContentInjector(CitationStyle.MINIMAL, customTemplate, useAbsolutePaths)

        /**
         * Create an injector with compact citations (filename + line numbers)
         * Best for: Code discussions, documentation review
         */
        fun compact(customTemplate: String? = null, useAbsolutePaths: Boolean = true) = MetadataAwareContentInjector(CitationStyle.COMPACT, customTemplate, useAbsolutePaths)

        /**
         * Create an injector with detailed citations (full path + line numbers)
         * Best for: Technical debugging, code review, precise references
         */
        fun detailed(customTemplate: String? = null, useAbsolutePaths: Boolean = true) = MetadataAwareContentInjector(CitationStyle.DETAILED, customTemplate, useAbsolutePaths)

        /**
         * Create an injector with a completely custom template
         * Template placeholders: {context}, {question}
         */
        fun custom(template: String, useAbsolutePaths: Boolean = true) = MetadataAwareContentInjector(CitationStyle.COMPACT, template, useAbsolutePaths)
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
                // Just filename or path
                if (filePath != null && useAbsolutePaths) {
                    "Source: [`$fileName`](file://$filePath)"
                } else {
                    "Source: $fileName"
                }
            }
            CitationStyle.COMPACT -> {
                // Filename with line numbers if available
                buildString {
                    if (filePath != null && useAbsolutePaths) {
                        append("Source: [`$fileName`](file://$filePath")
                        if (startLine != null && endLine != null) {
                            append("#L$startLine-L$endLine")
                        }
                        append(")")
                    } else {
                        append("Source: `$fileName`")
                    }
                    if (startLine != null && endLine != null) {
                        append(" (lines $startLine-$endLine)")
                    }
                }
            }
            CitationStyle.DETAILED -> {
                // Full path and all available metadata
                buildString {
                    if (filePath != null && useAbsolutePaths) {
                        append("Source: [`$fileName`](file://$filePath")
                        if (startLine != null && endLine != null) {
                            append("#L$startLine-L$endLine")
                        }
                        append(")")
                        append("\nPath: `$filePath`")
                    } else {
                        append("Source: `$fileName`")
                        if (filePath != null && filePath != fileName) {
                            append("\nPath: `$filePath`")
                        }
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

        CitationStyle.COMPACT -> if (useAbsolutePaths) {
            """
                |Answer the following question using the provided context.
                |When referencing sources, include the exact markdown link format provided in the context naturally in your response.
                |You can use varied phrases like:
                |- "As mentioned in [`filename`](file://path#L10-L15)..."
                |- "The [`filename`](file://path#L20-L30) shows..."
                |- "Based on [`filename`](file://path#L40-L50)..."
                |- "In [`filename`](file://path#L60-L70), we see..."
                |Vary your phrasing to make citations feel natural.
                |
                |Context:
                |{context}
                |
                |Question: {question}
            """.trimMargin()
        } else {
            """
                |Answer the following question using the provided context.
                |When referencing sources, mention the source filename and line numbers naturally in your response.
                |You can use varied phrases like:
                |- "As mentioned in `filename` (lines 10-15)..."
                |- "The `filename` shows..."
                |- "Based on `filename` (lines 20-30)..."
                |- "In `filename`, we see..."
                |Vary your phrasing to make citations feel natural.
                |
                |Context:
                |{context}
                |
                |Question: {question}
            """.trimMargin()
        }

        CitationStyle.DETAILED -> if (useAbsolutePaths) {
            """
                |Answer the following question using the provided context.
                |
                |When citing information, include the exact markdown link format provided in the context.
                |Use varied, natural phrases such as:
                |- "As mentioned in [`filename`](file://path#L10-L15)..."
                |- "The [`filename`](file://path#L20-L30) indicates..."
                |- "Based on [`filename`](file://path#L40-L50)..."
                |- "Looking at [`filename`](file://path#L60-L70)..."
                |- "The code in [`filename`](file://path#L80-L90) demonstrates..."
                |
                |This allows the user to click on the reference and jump directly to the source file.
                |Avoid repeating the same citation phrase - vary your wording to make the response more natural.
                |
                |Context:
                |{context}
                |
                |Question: {question}
            """.trimMargin()
        } else {
            """
                |Answer the following question using the provided context.
                |
                |When citing information, mention the source file path and line numbers naturally.
                |Use varied phrases such as:
                |- "As mentioned in `filename` (lines 10-15)..."
                |- "The `filename` indicates..."
                |- "Based on `path/to/file`..."
                |- "Looking at `filename` (lines 20-30)..."
                |
                |Vary your phrasing to make citations feel natural and avoid repetition.
                |
                |Context:
                |{context}
                |
                |Question: {question}
            """.trimMargin()
        }
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
