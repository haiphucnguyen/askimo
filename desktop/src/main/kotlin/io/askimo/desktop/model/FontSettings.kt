/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.model

data class FontSettings(
    val fontFamily: String = "System Default",
    val fontSize: FontSize = FontSize.MEDIUM,
) {
    companion object {
        const val SYSTEM_DEFAULT = "System Default"
    }
}

enum class FontSize(val displayName: String, val scale: Float) {
    SMALL("Small", 0.875f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.125f),
    EXTRA_LARGE("Extra Large", 1.25f),
}
