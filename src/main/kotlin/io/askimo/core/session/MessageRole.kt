/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

/**
 * Enum for chat message roles.
 */
enum class MessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        /**
         * Converts a string value to MessageRole enum.
         * @param value The string value ("user" or "assistant")
         * @return The corresponding MessageRole enum value
         * @throws IllegalArgumentException if the value is not recognized
         */
        fun fromValue(value: String): MessageRole {
            return entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown message role: $value")
        }

        /**
         * Safely converts a string value to MessageRole enum, returning null if not found.
         * @param value The string value ("user" or "assistant")
         * @return The corresponding MessageRole enum value or null if not found
         */
        fun fromValueOrNull(value: String): MessageRole? {
            return entries.find { it.value == value }
        }
    }
}
