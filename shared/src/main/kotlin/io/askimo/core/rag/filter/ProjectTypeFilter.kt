/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.filter

import io.askimo.core.config.AppConfig
import java.nio.file.Path

/**
 * Filter based on project type-specific exclude patterns.
 * Falls back to common excludes if no specific project type detected.
 */
class ProjectTypeFilter : IndexingFilter {
    override val name = "projecttype"
    override val priority = 50 // Medium priority - project conventions

    override fun shouldExclude(path: Path, isDirectory: Boolean, context: FilterContext): Boolean {
        val relativePath = context.relativePath.replace('\\', '/')

        // Check project-specific excludes
        for (projectType in context.projectTypes) {
            if (matchesPattern(relativePath, context.fileName, projectType.excludePaths, isDirectory)) {
                return true
            }
        }

        // Check common excludes as fallback
        return matchesPattern(relativePath, context.fileName, AppConfig.indexing.commonExcludes, isDirectory)
    }

    private fun matchesPattern(
        relativePath: String,
        fileName: String,
        patterns: Set<String>,
        isDirectory: Boolean
    ): Boolean {
        for (pattern in patterns) {
            // Directory patterns (ending with /)
            if (pattern.endsWith("/")) {
                if (!isDirectory) continue

                val dirPattern = pattern.removeSuffix("/")
                if (relativePath.contains("/$dirPattern/") ||
                    relativePath.startsWith("$dirPattern/") ||
                    relativePath == dirPattern
                ) {
                    return true
                }
            } else {
                // File or wildcard patterns
                if (pattern.contains("*")) {
                    val regex = pattern.replace("*", ".*").toRegex()
                    if (regex.matches(relativePath) || regex.matches(fileName)) {
                        return true
                    }
                } else {
                    if (relativePath.contains("/$pattern/") ||
                        relativePath.startsWith("$pattern/") ||
                        relativePath == pattern ||
                        fileName == pattern
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

