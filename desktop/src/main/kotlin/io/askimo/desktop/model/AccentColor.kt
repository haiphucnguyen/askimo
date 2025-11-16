/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.model

import androidx.compose.ui.graphics.Color

enum class AccentColor(val displayName: String, val lightColor: Color, val darkColor: Color) {
    GREEN("Green", Color(0xFF006C4C), Color(0xFF6CDBAC)),
    BLUE("Blue", Color(0xFF0061A4), Color(0xFF9CCAFF)),
    PURPLE("Purple", Color(0xFF6750A4), Color(0xFFD0BCFF)),
    ORANGE("Orange", Color(0xFF8C5000), Color(0xFFFFB871)),
    PINK("Pink", Color(0xFFC00058), Color(0xFFFFB0C8)),
    TEAL("Teal", Color(0xFF006874), Color(0xFF4FD8EB)),
}
