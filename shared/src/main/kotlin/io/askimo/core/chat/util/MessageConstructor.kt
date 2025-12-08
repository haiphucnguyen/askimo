/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.util

import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.logging.logger
import io.askimo.core.util.formatFileSize
import java.io.File

private val log = logger("MessageConstructor")

/**
 * Constructs a message with file attachments that will be sent to the AI.
 * Handles lazy loading of file content from file paths.
 * The file contents are included in the message context so the AI can read and process them.
 *
 * @param userMessage The user's message/question
 * @param attachments List of file attachments (may have content or filePath)
 * @return Complete message with file context for the AI
 */
fun constructMessageWithAttachments(
    userMessage: String,
    attachments: List<FileAttachmentDTO>,
): String {
    if (attachments.isEmpty()) {
        return userMessage
    }

    return buildString {
        attachments.forEach { attachment ->
            appendLine("---")
            appendLine("Attached file: ${attachment.fileName}")
            appendLine("File size: ${formatFileSize(attachment.size)}")
            appendLine()

            // Load content lazily if needed
            val content = when {
                attachment.content != null -> attachment.content
                attachment.filePath != null -> {
                    try {
                        val file = File(attachment.filePath)
                        if (!file.exists()) {
                            log.error("File not found: ${attachment.filePath}")
                            "[Error: File not found]"
                        } else if (!FileContentExtractor.isSupported(file)) { // Pass File object for content-based detection
                            log.warn("Unsupported file type: ${attachment.fileName}")
                            "[${FileContentExtractor.getUnsupportedMessage(file)}]" // Pass File object
                        } else {
                            FileContentExtractor.extractContent(file)
                        }
                    } catch (e: Exception) {
                        log.error("Failed to extract content from ${attachment.fileName}: ${e.message}", e)
                        "[Error: Could not read file - ${e.message}]"
                    }
                }
                else -> {
                    log.error("Attachment has neither content nor filePath: ${attachment.fileName}")
                    "[Error: No content available]"
                }
            }

            appendLine(content)
            appendLine("---")
            appendLine()
        }

        // Then include user's message/question
        appendLine(userMessage)
    }
}
