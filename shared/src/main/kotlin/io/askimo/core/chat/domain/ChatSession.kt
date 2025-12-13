/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import java.time.LocalDateTime

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val projectId: String? = null, // null = no project (general chat)
    val directiveId: String? = null,
    val isStarred: Boolean = false,
    val sortOrder: Int = 0,
)
