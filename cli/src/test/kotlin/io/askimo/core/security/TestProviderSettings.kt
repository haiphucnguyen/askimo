/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.security

import io.askimo.core.providers.HasApiKey
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.SettingField
import kotlinx.serialization.Serializable

/**
 * Test-specific provider settings that implement the same interfaces as real providers
 * but use safe test names that won't conflict with actual user data.
 */
@Serializable
data class TestProviderSettings(
    override var apiKey: String = "",
    override val defaultModel: String = "test-model",
) : ProviderSettings,
    HasApiKey {
    override fun describe(): List<String> = listOf(
        "apiKey: ${apiKey.take(5)}***",
    )

    override fun toString(): String = "TestProviderSettings(apiKey=****)"

    override fun getFields(): List<SettingField> = emptyList()

    override fun updateField(fieldName: String, value: String): ProviderSettings = when (fieldName) {
        "apiKey" -> copy(apiKey = value)
        else -> this
    }

    override fun deepCopy(): ProviderSettings = copy()
}
