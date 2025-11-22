/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.util

import io.askimo.core.util.formatFileSize
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
