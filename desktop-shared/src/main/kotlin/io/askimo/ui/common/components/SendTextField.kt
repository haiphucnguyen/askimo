/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.askimo.ui.common.theme.AppComponents
import io.askimo.ui.common.theme.Spacing

/**
 * A reusable text input field with an integrated send button.
 *
 * Keyboard behaviour (consistent with ChatInputField):
 * - **Enter** → sends the message (calls [onSend]).
 * - **Cmd+Enter / Ctrl+Enter** → inserts a new line.
 *
 * @param value           Current text field value.
 * @param onValueChange   Called whenever the text changes.
 * @param onSend          Called when the user triggers send (Enter key or button click).
 * @param modifier        Modifier applied to the wrapping [Row].
 * @param placeholder     Placeholder text shown when the field is empty.
 * @param enabled         Whether the field and button are interactive.
 * @param isLoading       When true the send button shows a spinner instead of the send icon.
 * @param minLines        Minimum visible line count for the text field (default 1).
 * @param maxLines        Maximum visible line count before the field scrolls (default 5).
 * @param error           Optional error message shown as supporting text.
 * @param sendContentDescription  Accessibility description for the send icon button.
 */
@Composable
fun sendTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    isLoading: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 5,
    error: String? = null,
    sendContentDescription: String = "Send",
) {
    // Wrap value in TextFieldValue so we can move the cursor after newline insertion.
    val tfv = TextFieldValue(text = value, selection = TextRange(value.length))
    sendTextField(
        value = tfv,
        onValueChange = { onValueChange(it.text) },
        onSend = onSend,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        isLoading = isLoading,
        minLines = minLines,
        maxLines = maxLines,
        error = error,
        sendContentDescription = sendContentDescription,
    )
}

/**
 * Overload that works with [TextFieldValue] directly, giving the caller full cursor control.
 */
@Composable
fun sendTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    isLoading: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 5,
    error: String? = null,
    sendContentDescription: String = "Send",
) {
    val canSend = enabled && !isLoading && value.text.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { keyEvent ->
                // Cmd/Ctrl + Enter → insert a newline at the cursor position
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.Enter &&
                    (keyEvent.isMetaPressed || keyEvent.isCtrlPressed)
                ) {
                    val cursor = value.selection.start
                    val newText = value.text.substring(0, cursor) + "\n" + value.text.substring(cursor)
                    onValueChange(TextFieldValue(text = newText, selection = TextRange(cursor + 1)))
                    return@onPreviewKeyEvent true
                }
                // Plain Enter → send (consume even if blank so we don't add a newline)
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.Enter &&
                    !keyEvent.isMetaPressed &&
                    !keyEvent.isCtrlPressed
                ) {
                    if (canSend) onSend()
                    return@onPreviewKeyEvent true
                }
                false
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder) }
            } else {
                null
            },
            modifier = Modifier.weight(1f),
            minLines = minLines,
            maxLines = maxLines,
            enabled = enabled,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            colors = AppComponents.outlinedTextFieldColors(),
        )

        IconButton(
            onClick = { if (canSend) onSend() },
            enabled = canSend,
            colors = AppComponents.primaryIconButtonColors(),
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = if (error != null) 24.dp else 0.dp)
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = sendContentDescription,
                    tint = if (canSend) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}
