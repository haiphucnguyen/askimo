/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

/**
 * Creates common preset field for style.
 */
internal fun createCommonPresetFields(presets: Presets): List<SettingField> = listOf(
    SettingField.EnumField(
        name = SettingField.STYLE,
        label = "Style",
        description = "Response style preference",
        value = presets.style.name,
        options = getStyleOptions(),
    ),
)

/**
 * Updates a preset field (style) and returns the new presets, or null if the field is not a preset field.
 */
internal fun updatePresetField(presets: Presets, fieldName: String, value: String): Presets? = when (fieldName) {
    SettingField.STYLE -> presets.copy(style = Style.valueOf(value))
    else -> null
}

private fun getStyleOptions() = listOf(
    SettingField.EnumOption("PRECISE", "Precise", "Focused, deterministic responses with minimal creativity"),
    SettingField.EnumOption("BALANCED", "Balanced", "Moderate creativity with good coherence"),
    SettingField.EnumOption("CREATIVE", "Creative", "More varied and creative responses"),
)
