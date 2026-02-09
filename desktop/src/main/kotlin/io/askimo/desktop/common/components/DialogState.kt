/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.common.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * State holder for managing dialog errors and status.
 * Provides a consistent way to handle errors across all dialogs in the application.
 *
 * Usage:
 * ```kotlin
 * val dialogState = rememberDialogState()
 *
 * // Display error
 * InlineErrorMessage(errorMessage = dialogState.errorMessage)
 *
 * // In button onClick
 * dialogState.clearError()
 * try {
 *     // ... operation
 * } catch (e: Exception) {
 *     dialogState.setError(e, "Operation failed")
 * }
 * ```
 */
class DialogState {
    /**
     * Current error message to display. Null means no error.
     */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Clears the current error message.
     * Call this before attempting an operation to clear previous errors.
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Sets an error message from a string.
     *
     * @param message The error message to display
     */
    fun setError(message: String) {
        errorMessage = message
    }

    /**
     * Sets an error message from an exception.
     * Uses the exception's message or falls back to the provided fallback message.
     *
     * @param exception The exception that occurred
     * @param fallbackMessage Message to show if exception.message is null
     */
    fun setError(exception: Exception, fallbackMessage: String = "An error occurred") {
        errorMessage = exception.message ?: fallbackMessage
    }
}

/**
 * Remember a DialogState across recompositions.
 * Use this to create a DialogState instance in your composable.
 *
 * @return A remembered DialogState instance
 */
@Composable
fun rememberDialogState() = remember { DialogState() }
