/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import java.nio.file.Path
import java.nio.file.Paths

object PathGuards {
    // Guard-list for MVP (you can expand later)
    private val blockedGlobs =
        listOf(
            ".git/**",
            "**/*.lock",
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "package.json",
            ".github/workflows/**",
        )

    /** True if abs is inside the project root directory. */
    fun isUnderRoot(
        abs: Path,
        rootAbs: String,
    ): Boolean = abs.normalize().startsWith(Paths.get(rootAbs).normalize())

    /** True if file matches any guarded pattern (do not edit). */
    fun isBlocked(abs: Path): Boolean {
        val unix = abs.toString().replace('\\', '/')
        // quick exact checks for common build files
        val name = abs.fileName?.toString() ?: ""
        if (name == "pom.xml" || name == "package.json" || name.endsWith(".lock")) return true
        return blockedGlobs.any { Glob.match(it, unix) }
    }
}
