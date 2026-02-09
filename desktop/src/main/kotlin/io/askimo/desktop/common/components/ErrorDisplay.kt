/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.common.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.askimo.desktop.common.theme.Spacing

/**
 * Displays an inline error message with consistent styling.
 * Used in dialogs and forms to show validation errors or operation failures.
 *
 * @param errorMessage The error message to display. If null, nothing is rendered.
 * @param modifier The modifier to be applied to the error container
 */
@Composable
fun inlineErrorMessage(
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    if (errorMessage != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(Spacing.medium),
            )
        }
    }
}
