/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.db

/**
 * Generic container for paginated results.
 *
 * @param T The type of items in the page
 * @property items The items in the current page
 * @property currentPage The current page number (1-based)
 * @property totalPages The total number of pages
 * @property totalItems The total number of items across all pages
 * @property pageSize The number of items per page
 */
data class Pageable<T>(
    val items: List<T>,
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val pageSize: Int,
) {
    val hasNextPage: Boolean get() = currentPage < totalPages
    val hasPreviousPage: Boolean get() = currentPage > 1
    val isEmpty: Boolean get() = items.isEmpty()
}
