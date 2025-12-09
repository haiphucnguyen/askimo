/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.util

import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.chat.util.constructMessageWithAttachments

/**
 * Constructs a message with file attachments that will be sent to the AI.
 * Delegates to shared module implementation for consistency across UI layers.
 *
 * @param userMessage The user's message/question
 * @param attachments List of file attachments
 * @return Complete message with file context for the AI
 */
fun constructMessageWithAttachments(
    userMessage: String,
    attachments: List<FileAttachmentDTO>,
): String = constructMessageWithAttachments(userMessage, attachments)
