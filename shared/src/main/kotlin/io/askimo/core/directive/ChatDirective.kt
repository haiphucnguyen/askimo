/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.directive

import java.time.LocalDateTime
import java.util.UUID

/**
 * Represents a custom instruction/directive that users can apply to chat sessions
 * to influence AI behavior (tone, format, style, etc.)
 */
data class ChatDirective(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
