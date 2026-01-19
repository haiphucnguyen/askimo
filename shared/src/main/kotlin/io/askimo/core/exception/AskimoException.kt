/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.exception

/**
 * Base exception for all Askimo-specific errors.
 * Provides message keys for localization and distinguishes between user and system errors.
 */
sealed class AskimoException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /**
     * Get the localization message key for this exception.
     */
    abstract fun getMessageKey(): String

    /**
     * Get the arguments to be used with the localization message template.
     */
    abstract fun getMessageArgs(): Map<String, String>

    /**
     * Whether this is a user error (can be fixed by user) or system error (needs support).
     */
    abstract fun isUserError(): Boolean
}
