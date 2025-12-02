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
import io.askimo.core.i18n.LocalizationManager
import io.askimo.desktop.util.Platform

/**
 * Centralized keyboard shortcut manager for the Askimo Desktop application.
 * Handles all keyboard shortcuts and provides platform-specific key mappings.
 */
object KeyMapManager {
    /**
     * Determines if the primary modifier key (Command on macOS, Ctrl on others) is pressed
     */
    fun KeyEvent.isPrimaryModifierPressed(): Boolean = if (Platform.isMac) isMetaPressed else isCtrlPressed

    /**
     * Defines all application shortcuts
     */
    enum class AppShortcut(
        val descriptionKey: String,
        val key: Key,
        val requiresPrimaryModifier: Boolean = false,
        val requiresShift: Boolean = false,
        val requiresAlt: Boolean = false,
    ) {
        // Global shortcuts
        NEW_CHAT("shortcut.new.chat", Key.N, requiresPrimaryModifier = true),
        SEARCH_IN_CHAT("shortcut.search.in.chat", Key.F, requiresPrimaryModifier = true),
        TOGGLE_CHAT_HISTORY("shortcut.toggle.chat.history", Key.H, requiresPrimaryModifier = true),
        OPEN_SETTINGS("shortcut.open.settings", Key.Comma, requiresPrimaryModifier = true),
        STOP_AI_RESPONSE("shortcut.stop.ai.response", Key.S, requiresPrimaryModifier = true),
        QUIT_APPLICATION("shortcut.quit.application", Key.Q, requiresPrimaryModifier = true),

        // Search shortcuts (platform-specific)
        CLOSE_SEARCH("shortcut.close.search", Key.Escape),
        NEXT_SEARCH_RESULT("shortcut.next.search.result", Key.G, requiresPrimaryModifier = true),
        PREVIOUS_SEARCH_RESULT("shortcut.previous.search.result", Key.G, requiresPrimaryModifier = true, requiresShift = true),

        // Input shortcuts
        ATTACH_FILE("shortcut.attach.file", Key.A, requiresPrimaryModifier = true),
        SEND_MESSAGE("shortcut.send.message", Key.Enter),
        NEW_LINE("shortcut.new.line", Key.Enter, requiresShift = true),
        ;

        /**
         * Gets the localized description for this shortcut
         */
        fun getDescription(): String = LocalizationManager.getString(descriptionKey)

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
            val parts = mutableListOf<String>()

            if (requiresPrimaryModifier) {
                parts.add(Platform.modifierKey)
            }
            if (requiresShift) {
                parts.add(if (Platform.isMac) "⇧" else "Shift")
            }
            if (requiresAlt) {
                parts.add(if (Platform.isMac) "⌥" else "Alt")
            }

            // Special key names
            val keyName = when (key) {
                Key.Enter -> if (Platform.isMac) "↵" else "Enter"
                Key.Escape -> if (Platform.isMac) "⎋" else "Esc"
                Key.Comma -> ","
                else -> key.keyCode.toInt().toChar().uppercaseChar().toString()
            }
            parts.add(keyName)

            return parts.joinToString(if (Platform.isMac) "" else "+")
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
        LocalizationManager.getString("shortcut.category.global") to listOf(
            AppShortcut.NEW_CHAT,
            AppShortcut.SEARCH_IN_CHAT,
            AppShortcut.TOGGLE_CHAT_HISTORY,
            AppShortcut.OPEN_SETTINGS,
            AppShortcut.STOP_AI_RESPONSE,
            AppShortcut.QUIT_APPLICATION,
        ),
        LocalizationManager.getString("shortcut.category.search") to listOf(
            AppShortcut.CLOSE_SEARCH,
            AppShortcut.NEXT_SEARCH_RESULT,
            AppShortcut.PREVIOUS_SEARCH_RESULT,
        ),
        LocalizationManager.getString("shortcut.category.input") to listOf(
            AppShortcut.ATTACH_FILE,
            AppShortcut.SEND_MESSAGE,
            AppShortcut.NEW_LINE,
        ),
    )
}
