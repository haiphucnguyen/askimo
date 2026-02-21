/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.chat

/**
 * Represents different creation modes for the chat input.
 * This sealed class allows for type-safe handling of different input modes
 * and easy extension to new modes in the future (e.g., Video, WebSearch).
 */
sealed class CreationMode {
    /**
     * Default chat mode - uses regular chat model
     */
    data object Chat : CreationMode()

    /**
     * Image creation mode - uses image generation model
     */
    data object Image : CreationMode()
}
