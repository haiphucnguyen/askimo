/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.chat.domain

import java.time.LocalDateTime

/**
 * Represents a project that groups chat sessions and provides RAG context
 * through indexed files/folders.
 *
 * Projects enable knowledge base organization:
 * - Each project has its own Lucene index for RAG
 * - Sessions belong to projects to share project-level context
 * - Indexed paths are used for semantic search across conversations
 */
data class Project(
    val id: String,
    val name: String,
    val description: String? = null,
    val indexedPaths: String, // JSON array of paths, e.g., "['/path/to/folder1', '/path/to/folder2']"
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
