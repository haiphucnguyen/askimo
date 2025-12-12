/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.dto

import java.time.LocalDateTime

/**
 * Data Transfer Object for chat messages.
 * Used to transfer message data between layers (service -> UI).
 */
data class ChatMessageDTO(
    val id: String?,
    val content: String,
    val isUser: Boolean,
    val timestamp: LocalDateTime?,
    val isOutdated: Boolean = false,
    val editParentId: String? = null,
    val isEdited: Boolean = false,
    val attachments: List<FileAttachmentDTO> = emptyList(),
    val isFailed: Boolean = false,
)
