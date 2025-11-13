/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.model

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
)
