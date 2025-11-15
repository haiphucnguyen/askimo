/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.util

import io.askimo.desktop.model.FileAttachment

/**
 * Constructs a message with file attachments that will be sent to the AI.
 * The file contents are included in the message context so the AI can read and process them.
 *
 * @param userMessage The user's message/question
 * @param attachments List of file attachments
 * @return Complete message with file context for the AI
 */
fun constructMessageWithAttachments(
    userMessage: String,
    attachments: List<FileAttachment>,
): String {
    if (attachments.isEmpty()) {
        return userMessage
    }

    return buildString {
        // Include file contents first
        attachments.forEach { attachment ->
            appendLine("---")
            appendLine("Attached file: ${attachment.fileName}")
            appendLine("File size: ${formatFileSize(attachment.size)}")
            appendLine()
            appendLine(attachment.content)
            appendLine("---")
            appendLine()
        }

        // Then include user's message/question
        appendLine(userMessage)
    }
}

/**
 * Formats file size in human-readable format.
 */
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
