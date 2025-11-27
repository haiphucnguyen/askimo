/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.SettingField
import io.askimo.core.session.MemoryPolicy
import io.askimo.core.session.ProviderConfigField
import io.askimo.core.session.ProviderService
import io.askimo.core.session.ProviderTestResult
import io.askimo.core.session.Session
import io.askimo.core.session.SessionConfigManager
import io.askimo.core.session.SessionFactory
import io.askimo.core.session.SessionMode
import io.askimo.core.session.getConfigInfo
import io.askimo.desktop.util.ErrorHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing settings state and configuration information.
 *
 * This class handles the business logic for the settings view, including:
 * - Fetching and exposing the current session configuration
 * - Provider information (OpenAI, Ollama, etc.)
 * - Model information
 * - Settings descriptions
 * - Model selection with validation
 */
class SettingsViewModel(
    private val scope: CoroutineScope,
) {
    private val session: Session = SessionFactory.createSession(mode = SessionMode.DESKTOP)

    var provider by mutableStateOf<ModelProvider?>(null)
        private set

    var model by mutableStateOf("")
        private set

    var settingsDescription by mutableStateOf<List<String>>(emptyList())
        private set

    var showModelDialog by mutableStateOf(false)
        private set

    var availableModels by mutableStateOf<List<String>>(emptyList())
        private set

    var isLoadingModels by mutableStateOf(false)
        private set

    var modelError by mutableStateOf<String?>(null)
        private set

    var modelErrorHelp by mutableStateOf<String?>(null)
        private set

    var showSuccessMessage by mutableStateOf(false)
        private set

    var successMessage by mutableStateOf("")
        private set

    var showSettingsDialog by mutableStateOf(false)
        private set

    var settingsFields by mutableStateOf<List<SettingField>>(emptyList())
        private set

    var showProviderDialog by mutableStateOf(false)
        private set

    var availableProviders by mutableStateOf<List<ModelProvider>>(emptyList())
        private set

    var selectedProvider by mutableStateOf<ModelProvider?>(null)
        private set

    var providerConfigFields by mutableStateOf<List<ProviderConfigField>>(emptyList())
        private set

    var providerFieldValues by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var isTestingConnection by mutableStateOf(false)
        private set

    var connectionError by mutableStateOf<String?>(null)
        private set

    var connectionErrorHelp by mutableStateOf<String?>(null)
        private set

    init {
        loadConfiguration()
    }

    /**
     * Load the current configuration from the session.
     */
    fun loadConfiguration() {
        val configInfo = session.getConfigInfo()
        provider = configInfo.provider
        model = configInfo.model
        settingsDescription = configInfo.settingsDescription
    }

    /**
     * Handle the "Change Provider" action.
     * Opens the provider selection dialog.
     */
    fun onChangeProvider() {
        availableProviders = ProviderService.getAvailableProviders()
        connectionError = null
        connectionErrorHelp = null

        // Pre-select the current provider if it exists
        val currentProvider = provider
        if (currentProvider != null && currentProvider != ModelProvider.UNKNOWN) {
            selectedProvider = currentProvider

            // Load existing settings and configuration fields
            val existingSettings = session.params.providerSettings[currentProvider]
            providerConfigFields = ProviderService.getProviderConfigFields(currentProvider, existingSettings)

            // Initialize field values with existing or default values
            providerFieldValues = providerConfigFields.associate { field ->
                when (field) {
                    is ProviderConfigField.ApiKeyField -> field.name to field.value
                    is ProviderConfigField.BaseUrlField -> field.name to field.value
                }
            }
        } else {
            // No provider configured yet
            selectedProvider = null
            providerConfigFields = emptyList()
            providerFieldValues = emptyMap()
        }

        showProviderDialog = true
    }

    /**
     * Select a provider and show its configuration fields.
     */
    fun selectProviderForChange(newProvider: ModelProvider) {
        selectedProvider = newProvider
        connectionError = null
        connectionErrorHelp = null

        // Get existing settings if available
        val existingSettings = session.params.providerSettings[newProvider]

        // Get configuration fields for the provider
        providerConfigFields = ProviderService.getProviderConfigFields(newProvider, existingSettings)

        // Initialize field values with existing or default values
        providerFieldValues = providerConfigFields.associate { field ->
            when (field) {
                is ProviderConfigField.ApiKeyField -> field.name to field.value
                is ProviderConfigField.BaseUrlField -> field.name to field.value
            }
        }
    }

    /**
     * Update a provider configuration field value.
     */
    fun updateProviderField(fieldName: String, value: String) {
        providerFieldValues = providerFieldValues.toMutableMap().apply {
            put(fieldName, value)
        }
    }

    /**
     * Test connection to the provider with current configuration.
     */
    fun testConnection() {
        val provider = selectedProvider ?: return

        // Validate all required fields are filled
        if (!ProviderService.validateConfigFields(providerFieldValues, providerConfigFields)) {
            connectionError = "Please fill in all required fields"
            return
        }

        isTestingConnection = true
        connectionError = null
        connectionErrorHelp = null

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // Get existing settings if available
                    val existingSettings = session.params.providerSettings[provider]

                    // Create updated settings
                    val newSettings = ProviderService.createProviderSettings(
                        provider,
                        providerFieldValues,
                        existingSettings,
                    )

                    // Test connection
                    ProviderService.testProviderConnection(provider, newSettings)
                } catch (e: Exception) {
                    val errorMsg = ErrorHandler.getUserFriendlyError(e, "testing provider connection", "Failed to test connection. Please check your settings.")
                    ProviderTestResult.Failure(errorMsg)
                }
            }

            isTestingConnection = false

            when (result) {
                is ProviderTestResult.Success -> {
                    connectionError = null
                    connectionErrorHelp = null
                    successMessage = "Connection successful!"
                    showSuccessMessage = true
                }
                is ProviderTestResult.Failure -> {
                    connectionError = result.message
                    connectionErrorHelp = result.helpText
                }
            }
        }
    }

    /**
     * Save the selected provider and its configuration.
     */
    fun saveProvider() {
        val provider = selectedProvider ?: return

        // Validate all required fields are filled
        if (!ProviderService.validateConfigFields(providerFieldValues, providerConfigFields)) {
            connectionError = "Please fill in all required fields"
            return
        }

        isTestingConnection = true
        connectionError = null
        connectionErrorHelp = null

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // Get existing settings if available
                    val existingSettings = session.params.providerSettings[provider]

                    // Create updated settings
                    val newSettings = ProviderService.createProviderSettings(
                        provider,
                        providerFieldValues,
                        existingSettings,
                    )

                    // Test connection
                    val testResult = ProviderService.testProviderConnection(provider, newSettings)

                    when (testResult) {
                        is ProviderTestResult.Success -> {
                            // Change provider
                            val success = ProviderService.changeProvider(session, provider, newSettings)
                            if (success) {
                                ProviderTestResult.Success
                            } else {
                                ProviderTestResult.Failure("Failed to apply provider changes")
                            }
                        }
                        is ProviderTestResult.Failure -> testResult
                    }
                } catch (e: Exception) {
                    val errorMsg = ErrorHandler.getUserFriendlyError(e, "applying provider change", "Failed to apply provider settings. Please try again.")
                    ProviderTestResult.Failure(errorMsg)
                }
            }

            isTestingConnection = false

            when (result) {
                is ProviderTestResult.Success -> {
                    // Update local state
                    loadConfiguration()

                    // Close dialog and show success
                    showProviderDialog = false
                    successMessage = "Provider changed to ${provider.name.lowercase()}"
                    showSuccessMessage = true
                }
                is ProviderTestResult.Failure -> {
                    connectionError = result.message
                    connectionErrorHelp = result.helpText
                }
            }
        }
    }

    /**
     * Close the provider selection dialog.
     */
    fun closeProviderDialog() {
        showProviderDialog = false
        selectedProvider = null
        providerConfigFields = emptyList()
        providerFieldValues = emptyMap()
        connectionError = null
        connectionErrorHelp = null
    }

    /**
     * Handle the "Change Model" action.
     * Opens the model selection dialog.
     */
    fun onChangeModel() {
        modelError = null
        modelErrorHelp = null
        isLoadingModels = true
        showModelDialog = true

        scope.launch {
            val currentProvider = provider
            if (currentProvider == null) {
                isLoadingModels = false
                availableModels = emptyList()
                modelError = "Provider not set"
                modelErrorHelp = null
                return@launch
            }

            withContext(Dispatchers.IO) {
                val factory = ProviderRegistry.getFactory(currentProvider)
                if (factory == null) {
                    isLoadingModels = false
                    availableModels = emptyList()
                    modelError = "No model factory registered for provider: ${currentProvider.name.lowercase()}"
                    modelErrorHelp = null
                    return@withContext
                }

                val settings = session.params.providerSettings[currentProvider] ?: factory.defaultSettings()

                @Suppress("UNCHECKED_CAST")
                val models = (factory as io.askimo.core.providers.ChatModelFactory<io.askimo.core.providers.ProviderSettings>)
                    .availableModels(settings)

                isLoadingModels = false

                if (models.isEmpty()) {
                    availableModels = emptyList()
                    modelError = "No models available for ${currentProvider.name.lowercase()}"
                    modelErrorHelp = factory.getNoModelsHelpText()
                } else {
                    availableModels = models
                    modelError = null
                    modelErrorHelp = null
                }
            }
        }
    }

    /**
     * Handle the "Change Settings" action.
     * Opens the settings configuration dialog.
     */
    fun onChangeSettings() {
        provider?.let { currentProvider ->
            val currentSettings = session.getCurrentProviderSettings()
            settingsFields = currentSettings.getFields()
            showSettingsDialog = true
        }
    }

    /**
     * Select a new model and update the session.
     */
    fun selectModel(newModel: String) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    // Update the model in session params
                    session.params.model = newModel

                    // Persist the change to disk
                    SessionConfigManager.save(session.params)

                    // Rebuild the chat service with the new model
                    // Use KEEP_PER_PROVIDER_MODEL to preserve conversation history
                    session.rebuildActiveChatService(MemoryPolicy.KEEP_PER_PROVIDER_MODEL)

                    true
                } catch (_: Exception) {
                    false
                }
            }

            if (success) {
                model = newModel
                loadConfiguration() // Reload to get updated settings
                showModelDialog = false
                successMessage = "Model updated to: $newModel"
                showSuccessMessage = true
            } else {
                modelError = "Failed to change model to: $newModel"
            }
        }
    }

    /**
     * Close the model selection dialog.
     */
    fun closeModelDialog() {
        showModelDialog = false
        modelError = null
        modelErrorHelp = null
    }

    /**
     * Update a settings field value.
     */
    fun updateSettingsField(fieldName: String, value: String) {
        provider?.let { currentProvider ->
            scope.launch {
                val currentSettings = session.getCurrentProviderSettings()
                val updatedSettings = withContext(Dispatchers.IO) {
                    currentSettings.updateField(fieldName, value)
                }

                // Update session with new settings
                session.setProviderSetting(currentProvider, updatedSettings)

                // Rebuild chat service with new settings
                withContext(Dispatchers.IO) {
                    session.rebuildActiveChatService(MemoryPolicy.KEEP_PER_PROVIDER_MODEL)
                }

                // Reload configuration to refresh UI
                loadConfiguration()

                // Refresh settings fields in dialog
                settingsFields = updatedSettings.getFields()
            }
        }
    }

    /**
     * Close the settings dialog and show success message.
     */
    fun closeSettingsDialog() {
        showSettingsDialog = false
        successMessage = "Settings updated successfully"
        showSuccessMessage = true
    }

    /**
     * Dismiss the success message.
     */
    fun dismissSuccessMessage() {
        showSuccessMessage = false
    }
}
