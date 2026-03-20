/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.common.theme
import androidx.compose.runtime.compositionLocalOf

val LocalFontScale = compositionLocalOf { 1f }

data class FontSettings(
    val fontFamily: String = "System Default",
    val fontSize: io.askimo.ui.common.theme.FontSize = _root_ide_package_.io.askimo.ui.common.theme.FontSize.MEDIUM,
) {
    companion object {
        const val SYSTEM_DEFAULT = "System Default"
    }
}
enum class FontSize(val displayName: String, val scale: Float) {
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f),
    EXTRA_LARGE("Extra Large", 1.35f),
}
