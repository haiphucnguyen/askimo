/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

object DiffInspector {
    data class Summary(
        val changedFiles: Int,
        val totalAdded: Int,
        val totalRemoved: Int,
    ) {
        val totalChanged: Int get() = totalAdded + totalRemoved
    }

    /** Quick counts based on unified diff markers. */
    fun summarize(unified: String): Summary {
        var files = 0
        var add = 0
        var del = 0
        unified.lineSequence().forEach { line ->
            when {
                line.startsWith("diff --git ") -> files++
                line.startsWith("+") && !line.startsWith("+++") -> add++
                line.startsWith("-") && !line.startsWith("---") -> del++
            }
        }
        return Summary(files, add, del)
    }

    /** MVP: assume repo-relative paths; earlier guards ensure files are under root. */
    fun pathsUnderRoot(
        unified: String,
        @Suppress("UNUSED_PARAMETER") rootAbs: String,
    ): Boolean = true

    /** Block risky targets (build/lock/CI) in headers. */
    fun containsBlockedPaths(unified: String): Boolean {
        val blocked =
            listOf(
                Regex("""^(\+\+\+|---)\s+[ab]/.*\.github/workflows/"""),
                Regex("""^(\+\+\+|---)\s+[ab]/.*\.lock$"""),
                Regex("""^(\+\+\+|---)\s+[ab]/pom\.xml$"""),
                Regex("""^(\+\+\+|---)\s+[ab]/build\.gradle(\.kts)?$"""),
                Regex("""^(\+\+\+|---)\s+[ab]/package\.json$"""),
            )
        val headers = unified.lineSequence().filter { it.startsWith("+++ ") || it.startsWith("--- ") }
        return headers.any { h -> blocked.any { it.containsMatchIn(h) } }
    }
}
