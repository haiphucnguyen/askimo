/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import io.askimo.core.context.MessageRole
import java.time.LocalDateTime

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val isOutdated: Boolean = false,
    val editParentId: String? = null,
    val isEdited: Boolean = false,
    val attachments: List<FileAttachment> = emptyList(),
)
