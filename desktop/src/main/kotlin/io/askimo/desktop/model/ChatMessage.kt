/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.model

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val attachments: List<FileAttachment> = emptyList(),
)

data class FileAttachment(
    val fileName: String,
    val content: String,
    val mimeType: String,
    val size: Long,
)
