/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtil {
    private val shortFmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun stamp(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun shortStamp(): String = LocalDateTime.now().format(shortFmt)

    /**
     * Formats a LocalDateTime with the standard display format for the given locale.
     * Default format: "MMM dd, yyyy HH:mm" (e.g., "Nov 15, 2025 14:30")
     *
     * @param dateTime The LocalDateTime to format
     * @param locale The locale to use for formatting (defaults to system locale)
     * @return The formatted date-time string
     */
    fun formatDisplay(dateTime: LocalDateTime, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", locale)
        return dateTime.format(formatter)
    }

    /**
     * Formats a LocalDateTime with a custom pattern for the given locale.
     *
     * @param dateTime The LocalDateTime to format
     * @param pattern The date-time pattern (e.g., "MMM dd, yyyy HH:mm")
     * @param locale The locale to use for formatting (defaults to system locale)
     * @return The formatted date-time string
     */
    fun format(dateTime: LocalDateTime, pattern: String, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter.ofPattern(pattern, locale)
        return dateTime.format(formatter)
    }
}
