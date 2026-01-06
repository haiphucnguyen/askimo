/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

/**
 * Represents a configuration field in provider settings.
 */
sealed class SettingField {
    abstract val name: String
    abstract val label: String
    abstract val description: String

    companion object {
        const val API_KEY = "apiKey"
        const val BASE_URL = "baseUrl"
        const val STYLE = "style"
    }

    data class TextField(
        override val name: String,
        override val label: String,
        override val description: String,
        val value: String,
        val isPassword: Boolean = false,
    ) : SettingField()

    data class EnumField(
        override val name: String,
        override val label: String,
        override val description: String,
        val value: String,
        val options: List<EnumOption>,
    ) : SettingField()

    data class EnumOption(
        val value: String,
        val label: String,
        val description: String,
    )
}
