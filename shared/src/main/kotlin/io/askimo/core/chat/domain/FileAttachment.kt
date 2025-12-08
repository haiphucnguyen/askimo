/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import java.time.LocalDateTime

/**
 * Represents a file attachment associated with a chat message.
 * File content is stored separately on the filesystem, this model contains only metadata.
 *
 * @property id Unique identifier for the attachment
 * @property messageId ID of the message this attachment belongs to
 * @property sessionId ID of the session (for easier cleanup)
 * @property fileName Original name of the file
 * @property mimeType MIME type/file extension
 * @property size File size in bytes
 * @property createdAt Timestamp when the attachment was created
 * @property content File content (lazy-loaded, null when loaded from DB)
 */
data class FileAttachment(
    val id: String,
    val messageId: String,
    val sessionId: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val content: String? = null,
)
