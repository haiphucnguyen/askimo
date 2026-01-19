/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.util

/**
 * Formats milliseconds to a human-readable string with two consecutive time units.
 *
 * Examples:
 * - 500ms -> "500ms"
 * - 1500ms -> "1s 500ms"
 * - 65000ms -> "1m 5s"
 * - 3665000ms -> "1h 1m"
 * - 90061000ms -> "1d 1h"
 */
fun formatDuration(ms: Long): String {
    if (ms < 0) return "0ms"

    val days = ms / 86400000
    val hours = (ms % 86400000) / 3600000
    val minutes = (ms % 3600000) / 60000
    val seconds = (ms % 60000) / 1000
    val millis = ms % 1000

    return when {
        days > 0 -> {
            if (hours > 0) {
                "${days}d ${hours}h"
            } else if (minutes > 0) {
                "${days}d ${minutes}m"
            } else if (seconds > 0) {
                "${days}d ${seconds}s"
            } else {
                "${days}d"
            }
        }
        hours > 0 -> {
            if (minutes > 0) {
                "${hours}h ${minutes}m"
            } else if (seconds > 0) {
                "${hours}h ${seconds}s"
            } else {
                "${hours}h"
            }
        }
        minutes > 0 -> {
            if (seconds > 0) {
                "${minutes}m ${seconds}s"
            } else if (millis > 0) {
                "${minutes}m ${millis}ms"
            } else {
                "${minutes}m"
            }
        }
        seconds > 0 -> {
            if (millis > 0) {
                "${seconds}s ${millis}ms"
            } else {
                "${seconds}s"
            }
        }
        else -> "${millis}ms"
    }
}

/**
 * Formats milliseconds to a detailed string with up to 4 consecutive time units for tooltips.
 *
 * Examples:
 * - 500ms -> "500 milliseconds"
 * - 1500ms -> "1 second 500 milliseconds"
 * - 65000ms -> "1 minute 5 seconds"
 * - 3665000ms -> "1 hour 1 minute 5 seconds"
 * - 90061000ms -> "1 day 1 hour 1 minute 1 second"
 */
fun formatDurationDetailed(ms: Long): String {
    if (ms < 0) return "0 milliseconds"

    val days = ms / 86400000
    val hours = (ms % 86400000) / 3600000
    val minutes = (ms % 3600000) / 60000
    val seconds = (ms % 60000) / 1000
    val millis = ms % 1000

    val parts = mutableListOf<String>()

    if (days > 0) parts.add(pluralize(days, "day"))
    if (hours > 0) parts.add(pluralize(hours, "hour"))
    if (minutes > 0) parts.add(pluralize(minutes, "minute"))
    if (seconds > 0) parts.add(pluralize(seconds, "second"))
    if (millis > 0 && parts.size < 4) parts.add(pluralize(millis, "millisecond"))

    // Take up to 4 consecutive units
    val selected = parts.take(4)

    return if (selected.isEmpty()) {
        "0 milliseconds"
    } else {
        selected.joinToString(" ")
    }
}

private fun pluralize(value: Long, unit: String): String = if (value == 1L) "$value $unit" else "$value ${unit}s"
