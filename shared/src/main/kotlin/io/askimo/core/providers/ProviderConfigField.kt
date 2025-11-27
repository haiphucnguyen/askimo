/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

/**
 * Represents a configurable field when setting up a provider.
 */
sealed class ProviderConfigField {
    abstract val name: String
    abstract val label: String
    abstract val description: String
    abstract val required: Boolean

    data class ApiKeyField(
        override val name: String = SettingField.API_KEY,
        override val label: String = "API Key",
        override val description: String,
        override val required: Boolean = true,
        val value: String = "",
        val hasExistingValue: Boolean = false,
    ) : ProviderConfigField()

    data class BaseUrlField(
        override val name: String = SettingField.BASE_URL,
        override val label: String = "Base URL",
        override val description: String,
        override val required: Boolean = true,
        val value: String = "",
    ) : ProviderConfigField()
}

/**
 * Result of a provider connection test.
 */
sealed class ProviderTestResult {
    data object Success : ProviderTestResult()
    data class Failure(val message: String, val helpText: String? = null) : ProviderTestResult()
}
