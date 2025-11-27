/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.model

import java.time.LocalDateTime

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val attachments: List<FileAttachment> = emptyList(),
    val id: String? = null,
    val timestamp: LocalDateTime? = null,
    val isOutdated: Boolean = false,
    val editParentId: String? = null, // ID of the message that was edited to create this branch
)

data class FileAttachment(
    val fileName: String,
    val content: String,
    val mimeType: String,
    val size: Long,
)
