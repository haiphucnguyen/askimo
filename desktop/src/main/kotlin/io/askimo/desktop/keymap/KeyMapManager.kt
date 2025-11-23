/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.keymap

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Centralized keyboard shortcut manager for the Askimo Desktop application.
 * Handles all keyboard shortcuts and provides platform-specific key mappings.
 */
object KeyMapManager {
    /**
     * Determines if the primary modifier key (Command on macOS, Ctrl on others) is pressed
     */
    fun KeyEvent.isPrimaryModifierPressed(): Boolean {
        val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)
        return if (isMac) isMetaPressed else isCtrlPressed
    }

    /**
     * Defines all application shortcuts
     */
    enum class AppShortcut(
        val description: String,
        val key: Key,
        val requiresPrimaryModifier: Boolean = false,
        val requiresShift: Boolean = false,
        val requiresAlt: Boolean = false,
    ) {
        // Global shortcuts
        NEW_CHAT("New Chat", Key.N, requiresPrimaryModifier = true),
        SEARCH_IN_CHAT("Search in Chat", Key.F, requiresPrimaryModifier = true),
        TOGGLE_CHAT_HISTORY("Toggle Chat History", Key.H, requiresPrimaryModifier = true),
        OPEN_SETTINGS("Open Settings", Key.Comma, requiresPrimaryModifier = true),
        STOP_AI_RESPONSE("Stop AI Response", Key.S, requiresPrimaryModifier = true),
        QUIT_APPLICATION("Quit Application", Key.Q, requiresPrimaryModifier = true),

        // Search shortcuts (platform-specific)
        CLOSE_SEARCH("Close Search", Key.Escape),
        NEXT_SEARCH_RESULT("Next Search Result", Key.G, requiresPrimaryModifier = true),
        PREVIOUS_SEARCH_RESULT("Previous Search Result", Key.G, requiresPrimaryModifier = true, requiresShift = true),

        // Input shortcuts
        ATTACH_FILE("Attach File", Key.A, requiresPrimaryModifier = true),
        SEND_MESSAGE("Send Message", Key.Enter),
        NEW_LINE("New Line", Key.Enter, requiresShift = true),
        ;

        /**
         * Checks if this shortcut matches the given key event
         */
        fun matches(keyEvent: KeyEvent): Boolean {
            if (keyEvent.key != key) return false
            if (requiresPrimaryModifier && !keyEvent.isPrimaryModifierPressed()) return false
            if (!requiresPrimaryModifier && keyEvent.isPrimaryModifierPressed()) return false
            if (requiresShift && !keyEvent.isShiftPressed) return false
            if (!requiresShift && keyEvent.isShiftPressed) return false
            if (requiresAlt && !keyEvent.isAltPressed) return false
            if (!requiresAlt && keyEvent.isAltPressed) return false
            return true
        }

        /**
         * Returns a human-readable string for this shortcut
         */
        fun getDisplayString(): String {
            val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)
            val parts = mutableListOf<String>()

            if (requiresPrimaryModifier) {
                parts.add(if (isMac) "⌘" else "Ctrl")
            }
            if (requiresShift) {
                parts.add(if (isMac) "⇧" else "Shift")
            }
            if (requiresAlt) {
                parts.add(if (isMac) "⌥" else "Alt")
            }

            // Special key names
            val keyName = when (key) {
                Key.Enter -> if (isMac) "↵" else "Enter"
                Key.Escape -> if (isMac) "⎋" else "Esc"
                Key.Comma -> ","
                else -> key.keyCode.toInt().toChar().uppercaseChar().toString()
            }
            parts.add(keyName)

            return parts.joinToString(if (isMac) "" else "+")
        }
    }

    /**
     * Handles a key event and returns the matching shortcut, if any
     */
    fun handleKeyEvent(keyEvent: KeyEvent): AppShortcut? {
        if (keyEvent.type != KeyEventType.KeyDown) return null
        return AppShortcut.entries.firstOrNull { it.matches(keyEvent) }
    }

    /**
     * Gets all shortcuts grouped by category
     * Useful for displaying shortcuts in settings or help screens
     */
    fun getAllShortcuts(): Map<String, List<AppShortcut>> = mapOf(
        "Global" to listOf(
            AppShortcut.NEW_CHAT,
            AppShortcut.SEARCH_IN_CHAT,
            AppShortcut.TOGGLE_CHAT_HISTORY,
            AppShortcut.OPEN_SETTINGS,
            AppShortcut.STOP_AI_RESPONSE,
            AppShortcut.QUIT_APPLICATION,
        ),
        "Search" to listOf(
            AppShortcut.CLOSE_SEARCH,
            AppShortcut.NEXT_SEARCH_RESULT,
            AppShortcut.PREVIOUS_SEARCH_RESULT,
        ),
        "Input" to listOf(
            AppShortcut.ATTACH_FILE,
            AppShortcut.SEND_MESSAGE,
            AppShortcut.NEW_LINE,
        ),
    )
}
