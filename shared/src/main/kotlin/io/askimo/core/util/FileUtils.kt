/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

/**
 * Formats file size in human-readable format.
 *
 * @param bytes The file size in bytes
 * @return Formatted string (e.g., "1.5 KB", "2 MB")
 */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
