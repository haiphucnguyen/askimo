/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import java.time.LocalDateTime

data class ChatFolder(
    val id: String,
    val name: String,
    val parentFolderId: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
