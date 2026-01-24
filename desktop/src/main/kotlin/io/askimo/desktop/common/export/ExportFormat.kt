/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.common.export

import io.askimo.core.i18n.LocalizationManager

/**
 * Export format options with localization support.
 */
enum class ExportFormat(
    val extension: String,
    private val nameKey: String,
    private val descriptionKey: String,
    private val benefitKeys: List<String>,
) {
    MARKDOWN(
        extension = "md",
        nameKey = "export.format.markdown.name",
        descriptionKey = "export.format.markdown.description",
        benefitKeys = listOf(
            "export.format.markdown.benefit.1",
            "export.format.markdown.benefit.2",
            "export.format.markdown.benefit.3",
            "export.format.markdown.benefit.4",
        ),
    ),
    JSON(
        extension = "json",
        nameKey = "export.format.json.name",
        descriptionKey = "export.format.json.description",
        benefitKeys = listOf(
            "export.format.json.benefit.1",
            "export.format.json.benefit.2",
            "export.format.json.benefit.3",
            "export.format.json.benefit.4",
        ),
    ),
    HTML(
        extension = "html",
        nameKey = "export.format.html.name",
        descriptionKey = "export.format.html.description",
        benefitKeys = listOf(
            "export.format.html.benefit.1",
            "export.format.html.benefit.2",
            "export.format.html.benefit.3",
            "export.format.html.benefit.4",
        ),
    ),
    ;

    /**
     * Get the localized display name for this format.
     */
    fun getDisplayName(): String = LocalizationManager.getString(nameKey)

    /**
     * Get the localized description for this format.
     */
    fun getDescription(): String = LocalizationManager.getString(descriptionKey)

    /**
     * Get the localized benefits list for this format.
     */
    fun getBenefits(): List<String> = benefitKeys.map { LocalizationManager.getString(it) }
}
