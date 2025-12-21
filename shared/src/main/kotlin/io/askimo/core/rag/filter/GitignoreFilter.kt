/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.filter

import io.askimo.core.logging.logger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * Parses and applies .gitignore patterns for file filtering.
 */
class GitignoreParser(private val rootPath: Path) {
    private val log = logger<GitignoreParser>()
    private val patterns = mutableListOf<GitignorePattern>()

    data class GitignorePattern(
        val pattern: String,
        val isNegation: Boolean,
        val isDirectoryOnly: Boolean,
        val regex: Regex,
        val gitignorePath: Path,
    )

    init {
        loadGitignoreFiles(rootPath)
    }

    private fun loadGitignoreFiles(path: Path) {
        try {
            Files.walk(path)
                .asSequence()
                .filter { it.fileName.toString() == ".gitignore" }
                .forEach { gitignoreFile ->
                    try {
                        val gitignoreDir = gitignoreFile.parent
                        Files.readAllLines(gitignoreFile)
                            .map { it.trim() }
                            .filterNot { it.isEmpty() || it.startsWith("#") }
                            .forEach { line ->
                                patterns.add(parsePattern(line, gitignoreDir))
                            }
                        log.debug("Loaded .gitignore: $gitignoreFile (${patterns.size} patterns)")
                    } catch (e: Exception) {
                        log.warn("Failed to load .gitignore: $gitignoreFile", e)
                    }
                }
        } catch (e: Exception) {
            log.warn("Failed to walk directory tree for .gitignore files", e)
        }
    }

    private fun parsePattern(line: String, gitignoreDir: Path): GitignorePattern {
        var pattern = line
        val isNegation = pattern.startsWith("!")
        if (isNegation) pattern = pattern.substring(1)

        val isDirectoryOnly = pattern.endsWith("/")
        if (isDirectoryOnly) pattern = pattern.removeSuffix("/")

        // Convert .gitignore pattern to regex
        val regexPattern = buildString {
            var i = 0
            while (i < pattern.length) {
                when (val c = pattern[i]) {
                    '*' -> {
                        if (i + 1 < pattern.length && pattern[i + 1] == '*') {
                            // ** matches any number of directories
                            append(".*")
                            i++ // Skip next *
                        } else {
                            // * matches anything except /
                            append("[^/]*")
                        }
                    }
                    '?' -> append("[^/]")
                    '.' -> append("\\.")
                    '/' -> append("/")
                    else -> append(Regex.escape(c.toString()))
                }
                i++
            }
        }

        // Anchor pattern appropriately
        val finalPattern = when {
            pattern.startsWith("/") -> "^${regexPattern.substring(1)}.*"
            pattern.contains("/") -> ".*/$regexPattern.*"
            else -> ".*(^|/)$regexPattern(/.*|\$)"
        }

        return GitignorePattern(
            pattern = line,
            isNegation = isNegation,
            isDirectoryOnly = isDirectoryOnly,
            regex = Regex(finalPattern),
            gitignorePath = gitignoreDir,
        )
    }

    /**
     * Check if a path should be ignored according to .gitignore rules.
     */
    fun shouldIgnore(path: Path, isDirectory: Boolean): Boolean {
        val relativePath = try {
            rootPath.relativize(path).toString().replace('\\', '/')
        } catch (e: Exception) {
            return false
        }

        var ignored = false

        // Apply patterns in order (later patterns override earlier ones)
        for (pattern in patterns) {
            // Check if pattern applies to this path (based on .gitignore location)
            val patternDir = try {
                rootPath.relativize(pattern.gitignorePath).toString().replace('\\', '/')
            } catch (e: Exception) {
                ""
            }

            if (patternDir.isNotEmpty() && !relativePath.startsWith(patternDir)) {
                continue // Pattern doesn't apply to this path
            }

            // Check if pattern matches
            val matches = pattern.regex.matches(relativePath) ||
                pattern.regex.matches("/$relativePath")

            if (matches) {
                // Directory-only patterns only match directories
                if (pattern.isDirectoryOnly && !isDirectory) {
                    continue
                }

                ignored = if (pattern.isNegation) {
                    false // Negation pattern un-ignores the file
                } else {
                    true // Normal pattern ignores the file
                }
            }
        }

        return ignored
    }

    fun hasPatterns(): Boolean = patterns.isNotEmpty()
}

/**
 * Filter based on .gitignore patterns.
 * Automatically discovers and applies .gitignore rules from Git repositories.
 * Detects Git repository for each path dynamically - supports multiple unrelated paths.
 */
class GitignoreFilter : IndexingFilter {
    override val name = "gitignore"
    override val priority = 10 // High priority - respect user's ignore rules first

    private val log = logger<GitignoreFilter>()

    // Cache of git root -> parser to avoid re-parsing .gitignore files
    private val parserCache = mutableMapOf<Path, GitignoreParser?>()

    /**
     * Find the Git repository root for a given path by walking up the directory tree
     */
    private fun findGitRoot(path: Path): Path? = generateSequence(path.toAbsolutePath()) { it.parent }
        .firstOrNull { candidate ->
            val gitDir = candidate.resolve(".git")
            Files.exists(gitDir) && Files.isDirectory(gitDir)
        }

    /**
     * Get or create a GitignoreParser for the given Git repository root
     */
    private fun getParserForGitRoot(gitRoot: Path): GitignoreParser? = parserCache.getOrPut(gitRoot) {
        try {
            GitignoreParser(gitRoot).takeIf { it.hasPatterns() }
        } catch (e: Exception) {
            log.warn("Failed to parse .gitignore for $gitRoot: ${e.message}")
            null
        }
    }

    override fun shouldExclude(path: Path, isDirectory: Boolean, context: FilterContext): Boolean {
        // Try to find Git repository root for this path
        val gitRoot = findGitRoot(if (isDirectory) path else path.parent ?: path)
            ?: return false // Not in a Git repository, don't exclude

        // Get parser for this Git repository
        val parser = getParserForGitRoot(gitRoot)
            ?: return false // No .gitignore patterns found

        // Check if path should be ignored
        return parser.shouldIgnore(path, isDirectory)
    }
}
