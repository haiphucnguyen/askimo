/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

object SystemPrompts {
    val markdownDefaults =
        listOf(
            "By default, always respond in GitHub-Flavored Markdown.",
            "Never return HTML unless explicitly asked.",
        )

    fun systemMessage(vararg extras: String) = (markdownDefaults + extras).joinToString("\n\n")
}
