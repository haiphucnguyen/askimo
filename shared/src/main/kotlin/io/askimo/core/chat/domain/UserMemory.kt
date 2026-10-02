/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.core.chat.domain

import java.time.Instant

/**
 * Domain model for persistent user memory.
 *
 * Stores a compact JSON-serialized [io.askimo.core.memory.UserMemorySummary] that accumulates
 * stable facts about the user across all chat sessions. Unlike [SessionMemory], which is
 * scoped to a single session, this record is global for the local user — there is only
 * ever one row (id = "default").
 *
 * @property id Always "default" — single-row per installation.
 * @property memoryJson JSON-serialised [io.askimo.core.memory.UserMemorySummary].
 * @property lastUpdated Timestamp of the last merge.
 * @property createdAt Timestamp of initial row creation.
 */
data class UserMemory(
    val id: String = DEFAULT_ID,
    val memoryJson: String,
    val lastUpdated: Instant = Instant.now(),
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}
