/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

object EditIntentDetector {
    private val verbs =
        listOf(
            "add",
            "write",
            "update",
            "fix",
            "insert",
            "append",
            "document",
            "javadoc",
            "docstring",
            "comment",
            "refactor",
            "rename",
        )

    // crude path token; good enough for MVP
    private val pathToken = Regex("""[A-Za-z0-9_\-./]+(?:\.[A-Za-z0-9_\-]+)""")

    data class Intent(
        val isEdit: Boolean,
        val reason: String = "",
        val targetPaths: List<String> = emptyList(),
    )

    fun detect(
        prompt: String,
        hasActiveProject: Boolean,
    ): Intent {
        val p = prompt.lowercase()
        val mentionsVerb = verbs.any { v -> p.contains(" $v ") || p.startsWith("$v ") || p.contains("$v to ") }
        val paths =
            pathToken
                .findAll(prompt)
                .map { it.value }
                .filter { it.count { ch -> ch == '/' } >= 1 } // looks like a path
                .filter { it.contains('.') } // has an extension
                .filterNot { it.startsWith(":") } // not a command
                .distinct()
                .toList()

        val looksLikeEdit = hasActiveProject && (mentionsVerb || p.contains("javadoc") || p.contains("docstring")) && paths.isNotEmpty()
        return if (looksLikeEdit) Intent(true, "verb+path detected", paths) else Intent(false)
    }
}
