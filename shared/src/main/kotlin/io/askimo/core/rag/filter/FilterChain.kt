/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.filter

import io.askimo.core.logging.logger
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Manages and applies multiple indexing filters in priority order.
 */
class FilterChain(filters: List<IndexingFilter>) {

    // Sort filters by priority (lower number = higher priority)
    private val sortedFilters = filters.sortedBy { it.priority }

    /**
     * Check if a path should be excluded by any filter.
     * Returns immediately on first exclusion (short-circuit evaluation).
     */
    fun shouldExclude(path: Path, isDirectory: Boolean, context: FilterContext): Boolean {
        for (filter in sortedFilters) {
            if (filter.shouldExclude(path, isDirectory, context)) {
                log.debug("Path excluded by ${filter.name} filter: ${context.relativePath}")
                return true
            }
        }
        return false
    }

    /**
     * Get details about why a path was excluded (for debugging).
     */
    fun getExclusionReason(path: Path, isDirectory: Boolean, context: FilterContext): String? {
        for (filter in sortedFilters) {
            if (filter.shouldExclude(path, isDirectory, context)) {
                return "${filter.name}: ${context.relativePath}"
            }
        }
        return null
    }

    /**
     * Convenience method to check if a path should be excluded.
     * Automatically creates the FilterContext from the path.
     */
    fun shouldExcludePath(path: Path): Boolean {
        val isDirectory = path.isDirectory()
        val absolutePath = path.toAbsolutePath()
        val rootPath = if (isDirectory) absolutePath else absolutePath.parent ?: absolutePath

        val context = FilterContext(
            rootPath = rootPath,
            relativePath = path.fileName.toString(),
            fileName = path.name,
            extension = if (!isDirectory) path.extension.lowercase() else "",
        )

        return shouldExclude(path, isDirectory, context)
    }

    companion object {
        private val log = logger<FilterChain>()

        /**
         * Default filter chain with all standard filters.
         * No project root needed - filters detect their context per-path.
         */
        val DEFAULT: FilterChain by lazy {
            val filters = listOf(
                GitignoreFilter(),
                ProjectTypeFilter(),
                BinaryFileFilter(),
                FileSizeFilter(),
                CustomPatternFilter(emptyList()),
            )
            FilterChain(filters)
        }
    }
}
