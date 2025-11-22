/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.model

import androidx.compose.ui.graphics.Color

enum class AccentColor(val displayName: String, val lightColor: Color, val darkColor: Color) {
    // Deep/Dark variants - rich, bold colors
    DEEP_BLUE("Deep Blue", Color(0xFF003D82), Color(0xFF1E88E5)),
    DEEP_PURPLE("Deep Purple", Color(0xFF5B21B6), Color(0xFF9333EA)),
    DEEP_GREEN("Deep Green", Color(0xFF065F46), Color(0xFF10B981)),
    DEEP_RED("Deep Red", Color(0xFF991B1B), Color(0xFFEF4444)),

    // Balanced variants - moderate saturation, professional
    OCEAN_BLUE("Ocean Blue", Color(0xFF0066CC), Color(0xFF4DA6FF)),
    VIBRANT_PURPLE("Vibrant Purple", Color(0xFF7C3AED), Color(0xFFA78BFA)),
    EMERALD_GREEN("Emerald", Color(0xFF059669), Color(0xFF34D399)),
    SUNSET_ORANGE("Sunset Orange", Color(0xFFEA580C), Color(0xFFFB923C)),
    ROSE_PINK("Rose Pink", Color(0xFFE11D48), Color(0xFFFB7185)),

    // Light variants - soft, pastel-like colors
    SKY_BLUE("Sky Blue", Color(0xFF0284C7), Color(0xFF7DD3FC)),
    LAVENDER("Lavender", Color(0xFF9333EA), Color(0xFFD8B4FE)),
    MINT("Mint", Color(0xFF10B981), Color(0xFF6EE7B7)),
    PEACH("Peach", Color(0xFFF97316), Color(0xFFFDBA74)),
    CORAL("Coral", Color(0xFFDC2626), Color(0xFFF87171)),

    // Vibrant/Bright variants - eye-catching
    TURQUOISE("Turquoise", Color(0xFF0891B2), Color(0xFF22D3EE)),
    MAGENTA("Magenta", Color(0xFFDB2777), Color(0xFFF9A8D4)),
    LIME("Lime Green", Color(0xFF65A30D), Color(0xFFA3E635)),
    AMBER("Amber", Color(0xFFD97706), Color(0xFFFBBF24)),
}
