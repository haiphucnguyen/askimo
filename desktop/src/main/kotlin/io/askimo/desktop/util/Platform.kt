/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.util

/**
 * Platform detection utilities for desktop application.
 */
object Platform {
    private val osName: String by lazy {
        System.getProperty("os.name") ?: ""
    }

    /**
     * Returns true if the current operating system is macOS.
     */
    val isMac: Boolean by lazy {
        osName.contains("Mac", ignoreCase = true)
    }

    /**
     * Returns true if the current operating system is Windows.
     */
    val isWindows: Boolean by lazy {
        osName.contains("Windows", ignoreCase = true)
    }

    /**
     * Returns true if the current operating system is Linux.
     */
    val isLinux: Boolean by lazy {
        osName.contains("Linux", ignoreCase = true)
    }

    /**
     * Returns the platform-specific modifier key symbol.
     * Returns "⌘" for macOS, "Ctrl" for other platforms.
     */
    val modifierKey: String by lazy {
        if (isMac) "⌘" else "Ctrl"
    }

    /**
     * Returns the platform-specific modifier key name.
     * Returns "Command" for macOS, "Ctrl" for other platforms.
     */
    val modifierKeyName: String by lazy {
        if (isMac) "Command" else "Ctrl"
    }
}
