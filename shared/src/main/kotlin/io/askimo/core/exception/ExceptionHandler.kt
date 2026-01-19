/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.exception

import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.logging.logger

/**
 * Handles exceptions by converting them to localized user-friendly messages.
 * This centralizes exception handling logic for reusability across CLI and Desktop.
 */
object ExceptionHandler {
    private val log = logger<ExceptionHandler>()

    /**
     * Handle an exception and return a localized user message.
     * Also logs the exception appropriately based on whether it's a user or system error.
     *
     * @param throwable The exception to handle
     * @param contextId Optional context ID (session ID, command name, etc.) for logging
     * @return Localized user-friendly error message
     */
    fun handle(
        throwable: Throwable,
        contextId: String? = null,
    ): String {
        // 1. Classify exception
        val askimoException = ExceptionMapper.map(throwable)

        // 2. Get localized message (uses LocalizationManager.getCurrentLocale() internally)
        val localizedMessage = LocalizationManager.getString(
            askimoException.getMessageKey(),
        )

        // 3. Replace placeholders with actual values
        var result = localizedMessage
        askimoException.getMessageArgs().forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }

        // 4. Log appropriately
        val logPrefix = contextId?.let { "[$it] " } ?: ""
        if (askimoException.isUserError()) {
            log.warn("${logPrefix}User error: ${askimoException.message}")
        } else {
            log.error("${logPrefix}System error: ${askimoException.message}", askimoException)
        }

        return result
    }

    /**
     * Handle an exception with partial content (useful for streaming responses).
     * Combines the partial content with the localized error message.
     *
     * @param throwable The exception to handle
     * @param partialContent Partial content that was generated before the error
     * @param contextId Optional context ID for logging
     * @return Partial content combined with localized error message
     */
    fun handleWithPartialContent(
        throwable: Throwable,
        partialContent: String,
        contextId: String? = null,
    ): String {
        val errorMessage = handle(throwable, contextId)

        return if (partialContent.isNotBlank()) {
            "$partialContent\n\n$errorMessage"
        } else {
            errorMessage
        }
    }
}
