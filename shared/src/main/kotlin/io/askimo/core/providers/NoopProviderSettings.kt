/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

object NoopProviderSettings : ProviderSettings {
    override val defaultModel: String = ""

    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL)

    override fun describe(): List<String> = listOf()

    override fun getFields(): List<SettingField> = emptyList()

    override fun updateField(fieldName: String, value: String): ProviderSettings = this
}
