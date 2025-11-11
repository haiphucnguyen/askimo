/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

data class DiffRequest(
    val header: EnvHeaderV0,
    val instruction: String,
    val files: List<SourceFile>,
)

data class EnvHeaderV0(
    val project: EnvProject,
    val constraints: EnvConstraints,
    val targetHints: List<String>,
    val policy: EnvPolicy,
)

data class EnvProject(
    val id: String,
    val root: String,
    val cwd: String,
    val createdAt: String,
)

data class EnvConstraints(
    val maxFiles: Int,
    val maxChangedLines: Int,
    val allowDirty: Boolean,
)

data class EnvPolicy(
    val editScope: String,
    val noBuildFileEdits: Boolean,
    val allowBinaryEdits: Boolean,
)

data class SourceFile(
    val path: String, // repo-relative path, e.g. "apps/backend/.../ABC.java"
    val eol: String, // "LF" | "CRLF" | "MIXED"
    val text: String,
)
