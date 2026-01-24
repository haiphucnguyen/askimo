/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.askimo.core.providers.SettingField
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun settingsConfigDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource("settings.configure.title", viewModel.provider?.name ?: ""),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                viewModel.settingsFields.forEach { field ->
                    when (field) {
                        is SettingField.TextField -> {
                            OutlinedTextField(
                                value = field.value,
                                onValueChange = { viewModel.updateSettingsField(field.name, it) },
                                label = { Text(field.label) },
                                placeholder = { Text(field.description) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (field.isPassword) {
                                    PasswordVisualTransformation()
                                } else {
                                    VisualTransformation.None
                                },
                                colors = ComponentColors.outlinedTextFieldColors(),
                            )
                        }
                        is SettingField.EnumField -> {
                            var expanded by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                            ) {
                                OutlinedTextField(
                                    value = field.options.find { it.value == field.value }?.label ?: field.value,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(field.label) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                    colors = ComponentColors.outlinedTextFieldColors(),
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    field.options.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = option.label,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                    Text(
                                                        text = option.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.updateSettingsField(field.name, option.value)
                                                expanded = false
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.closeSettingsDialog()
            }) {
                Text(stringResource("action.done"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource("action.cancel"))
            }
        },
    )
}
