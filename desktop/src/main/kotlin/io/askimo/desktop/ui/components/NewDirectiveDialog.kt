/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.ui.theme.ComponentColors

@Composable
fun newDirectiveDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String, applyToCurrent: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var applyToCurrent by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var contentError by remember { mutableStateOf<String?>(null) }

    // Pre-load error messages in composable context
    val nameRequiredError = stringResource("directive.edit.name.required")
    val contentRequiredError = stringResource("directive.edit.content.required")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(500.dp)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title
                Text(
                    text = stringResource("directive.new.title"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text(stringResource("directive.edit.name.label")) },
                    placeholder = { Text(stringResource("directive.new.name.placeholder")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    colors = ComponentColors.outlinedTextFieldColors(),
                )

                // Content field
                OutlinedTextField(
                    value = content,
                    onValueChange = {
                        content = it
                        contentError = null
                    },
                    label = { Text(stringResource("directive.edit.content.label")) },
                    placeholder = { Text(stringResource("directive.new.content.placeholder")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 8,
                    isError = contentError != null,
                    supportingText = contentError?.let { { Text(it) } },
                    colors = ComponentColors.outlinedTextFieldColors(),
                )

                // Apply to current session checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = applyToCurrent,
                        onCheckedChange = { applyToCurrent = it },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                    Text(
                        text = stringResource("directive.new.apply.current"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("settings.cancel"))
                    }

                    TextButton(
                        onClick = {
                            // Validate inputs
                            var hasError = false

                            if (name.isBlank()) {
                                nameError = nameRequiredError
                                hasError = true
                            }

                            if (content.isBlank()) {
                                contentError = contentRequiredError
                                hasError = true
                            }

                            if (!hasError) {
                                onConfirm(name.trim(), content.trim(), applyToCurrent)
                            }
                        },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        colors = ComponentColors.primaryTextButtonColors(),
                    ) {
                        Text(stringResource("directive.new.create"))
                    }
                }
            }
        }
    }
}
