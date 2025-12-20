/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.filter

import io.askimo.core.logging.logger
import java.nio.file.Path

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

    companion object {
        private val log = logger<FilterChain>()
    }
}
