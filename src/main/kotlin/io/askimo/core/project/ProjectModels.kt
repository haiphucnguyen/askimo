/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import kotlinx.serialization.Serializable

@Serializable
data class ProjectMeta(
    // stable ID (UUIDv7/UUID4 is fine)
    val id: String,
    val name: String,
    // absolute path to repo root
    val root: String,
    // ISO-8601
    val createdAt: String,
    // ISO-8601
    val updatedAt: String,
    // ISO-8601
    val lastUsedAt: String,
)

@Serializable
data class ProjectFileV1(
    val schemaVersion: Int = 1,
    val project: ProjectMeta,
    // room for future defaults/policy/rag blocks; keep MVP lean
)

@Serializable
data class ActivePointer(
    val projectId: String,
    // absolute path
    val root: String,
    // ISO-8601
    val selectedAt: String,
)
