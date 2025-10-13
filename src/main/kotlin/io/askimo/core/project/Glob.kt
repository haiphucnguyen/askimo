/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

/**
 * Minimal glob matcher for repo paths (unix-style):
 *  - "**" matches across directories
 *  - "*"  matches within a single path segment (no '/')
 *  - "?"  matches a single character (no '/')
 */
object Glob {
    fun match(
        glob: String,
        pathUnix: String,
    ): Boolean {
        val regex = toRegex(glob)
        return regex.matches(pathUnix)
    }

    private fun toRegex(glob: String): Regex {
        // Escape regex meta, then reintroduce glob tokens
        var g = Regex.escape(glob)
        // Restore path separators
        g = g.replace("\\/".toRegex(), "/")
        // Double-star "**" -> any, including '/'
        g = g.replace("\\*\\*".toRegex(), "§DOUBLESTAR§")
        // Single-star "*" -> any except '/'
        g = g.replace("\\*".toRegex(), "[^/]*")
        // Question mark -> any single char except '/'
        g = g.replace("\\?".toRegex(), "[^/]")
        // Put back double-star
        g = g.replace("§DOUBLESTAR§", ".*")
        return Regex("^$g$")
    }
}
