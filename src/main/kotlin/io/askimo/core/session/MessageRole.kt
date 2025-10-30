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
    ASSISTANT("assistant"),
    SYSTEM("system"),
    ;

    override fun toString(): String = value

    companion object {
        /**
         * Converts a string value to MessageRole enum.
         * @param value The string value ("user", "assistant", or "system")
         * @return The corresponding MessageRole enum value
         * @throws IllegalArgumentException if the value is not recognized
         */
        fun fromValue(value: String): MessageRole = values().find { it.value == value }
            ?: throw IllegalArgumentException("Unknown MessageRole value: $value")
    }
}
