/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.askimo.desktop.model.ThemeMode
import io.askimo.desktop.service.ThemePreferences
import io.askimo.desktop.viewmodel.SettingsViewModel

@Composable
fun settingsView(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val currentThemeMode by ThemePreferences.themeMode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success message
    LaunchedEffect(viewModel.showSuccessMessage) {
        if (viewModel.showSuccessMessage) {
            snackbarHostState.showSnackbar(viewModel.successMessage)
            viewModel.dismissSuccessMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            HorizontalDivider()

            // Chat Configuration Section
            Text(
                text = "Chat Configuration",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Provider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Provider",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = viewModel.provider?.name ?: "Not set",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Button(
                            onClick = { viewModel.onChangeProvider() },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text("Change Provider", modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    HorizontalDivider()

                    // Model
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Model",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = viewModel.model,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Button(
                            onClick = { viewModel.onChangeModel() },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text("Change Model", modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    // Settings
                    if (viewModel.settingsDescription.isNotEmpty()) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Settings",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                viewModel.settingsDescription.forEach { setting ->
                                    Text(
                                        text = setting,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.onChangeSettings() },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Text("Change Settings", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            // Appearance Section
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )

            // Theme options
            themeOption(
                title = "Light",
                description = "Always use light theme",
                icon = { Icon(Icons.Default.LightMode, contentDescription = null) },
                selected = currentThemeMode == ThemeMode.LIGHT,
                onClick = { ThemePreferences.setThemeMode(ThemeMode.LIGHT) },
            )

            themeOption(
                title = "Dark",
                description = "Always use dark theme",
                icon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                selected = currentThemeMode == ThemeMode.DARK,
                onClick = { ThemePreferences.setThemeMode(ThemeMode.DARK) },
            )

            themeOption(
                title = "System",
                description = "Follow system theme settings",
                icon = { Icon(Icons.Default.Contrast, contentDescription = null) },
                selected = currentThemeMode == ThemeMode.SYSTEM,
                onClick = { ThemePreferences.setThemeMode(ThemeMode.SYSTEM) },
            )
        }

        // Snackbar for success messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) { data ->
            Snackbar(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Text(data.visuals.message)
                }
            }
        }
    }

    // Model selection dialog
    if (viewModel.showModelDialog) {
        modelSelectionDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModelDialog() },
            onSelectModel = { viewModel.selectModel(it) },
        )
    }

    // Settings configuration dialog
    if (viewModel.showSettingsDialog) {
        settingsConfigurationDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeSettingsDialog() },
        )
    }

    // Provider selection dialog
    if (viewModel.showProviderDialog) {
        providerSelectionDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeProviderDialog() },
            onSave = { viewModel.saveProvider() },
        )
    }
}

@Composable
private fun themeOption(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                icon()
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun modelSelectionDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onSelectModel: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Model") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    viewModel.isLoadingModels -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading models...",
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                    viewModel.modelError != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = viewModel.modelError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            viewModel.modelErrorHelp?.let { helpText ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ) {
                                    Text(
                                        text = helpText,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                    viewModel.availableModels.isEmpty() -> {
                        Text(
                            text = "No models available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            text = "Select a model for ${viewModel.provider?.name}:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        viewModel.availableModels.forEach { model ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectModel(model) }
                                    .pointerHoverIcon(PointerIcon.Hand),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (model == viewModel.model) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (model == viewModel.model) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun settingsConfigurationDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${viewModel.provider?.name} Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                viewModel.settingsFields.forEach { field ->
                    when (field) {
                        is io.askimo.core.session.SettingField.TextField -> {
                            textFieldSetting(
                                field = field,
                                onValueChange = { viewModel.updateSettingsField(field.name, it) },
                            )
                        }
                        is io.askimo.core.session.SettingField.EnumField -> {
                            enumFieldSetting(
                                field = field,
                                onValueChange = { viewModel.updateSettingsField(field.name, it) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun textFieldSetting(
    field: io.askimo.core.session.SettingField.TextField,
    onValueChange: (String) -> Unit,
) {
    var textValue by remember { mutableStateOf(field.value) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = field.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                onValueChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (field.isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (field.isPassword) {
                {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = if (passwordVisible) "Hide" else "Show",
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
        )
    }
}

@Composable
private fun enumFieldSetting(
    field: io.askimo.core.session.SettingField.EnumField,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = field.options.find { it.value == field.value }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = field.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedOption?.label ?: field.value,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        selectedOption?.description?.let { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                field.options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onValueChange(option.value)
                            expanded = false
                        },
                        leadingIcon = if (option.value == field.value) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun providerSelectionDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Change Provider",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            var providerDropdownExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Step 1: Provider selection dropdown
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Select a provider:",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = viewModel.selectedProvider?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Choose a provider...",
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { providerDropdownExpanded = true }
                                .pointerHoverIcon(PointerIcon.Hand),
                            readOnly = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = { providerDropdownExpanded = true },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Select provider")
                                }
                            },
                        )

                        DropdownMenu(
                            expanded = providerDropdownExpanded,
                            onDismissRequest = { providerDropdownExpanded = false },
                        ) {
                            viewModel.availableProviders.forEach { provider ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = provider.name.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    onClick = {
                                        viewModel.selectProviderForChange(provider)
                                        providerDropdownExpanded = false
                                    },
                                    leadingIcon = if (viewModel.selectedProvider == provider) {
                                        {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }

                // Step 2: Configuration fields (shown after provider is selected)
                if (viewModel.selectedProvider != null && viewModel.providerConfigFields.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Configure provider:",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    viewModel.providerConfigFields.forEach { field ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = field.label + if (field.required) " *" else "",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = field.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            when (field) {
                                is io.askimo.core.session.ProviderConfigField.ApiKeyField -> {
                                    OutlinedTextField(
                                        value = viewModel.providerFieldValues[field.name] ?: "",
                                        onValueChange = { viewModel.updateProviderField(field.name, it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        placeholder = {
                                            Text(
                                                if (field.hasExistingValue) {
                                                    "API key stored securely"
                                                } else {
                                                    "Enter API key"
                                                },
                                            )
                                        },
                                        trailingIcon = {
                                            Row {
                                                if (field.hasExistingValue) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Already stored",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(Icons.Default.Lock, contentDescription = "Password")
                                            }
                                        },
                                    )
                                }
                                is io.askimo.core.session.ProviderConfigField.BaseUrlField -> {
                                    OutlinedTextField(
                                        value = viewModel.providerFieldValues[field.name] ?: "",
                                        onValueChange = { viewModel.updateProviderField(field.name, it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("http://localhost:11434") },
                                    )
                                }
                            }
                        }
                    }

                    // Connection error display
                    if (viewModel.connectionError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Column {
                                        Text(
                                            text = viewModel.connectionError ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        if (viewModel.connectionErrorHelp != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = viewModel.connectionErrorHelp ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Test Connection button
                if (viewModel.selectedProvider != null && viewModel.providerConfigFields.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.testConnection() },
                        enabled = !viewModel.isTestingConnection,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        if (viewModel.isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing...")
                        } else {
                            Text("Test Connection")
                        }
                    }
                }

                // Save button
                Button(
                    onClick = onSave,
                    enabled = viewModel.selectedProvider != null && !viewModel.isTestingConnection,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text("Cancel")
            }
        },
    )
}
