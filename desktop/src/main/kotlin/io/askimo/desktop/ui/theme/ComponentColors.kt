/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemColors
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Centralized component colors that use theme colors consistently.
 * This ensures all interactive components use the custom accent color properly.
 *
 * ## Three-Level Color Hierarchy:
 *
 * 1. **Prominent (30% accent opacity)** - primaryCardColors()
 *    - Selected items, active states, important headers
 *    - Text color: onPrimaryContainer
 *
 * 2. **Subtle (15% accent opacity)** - bannerCardColors()
 *    - Section banners, informational headers, list items
 *    - Text color: onSecondaryContainer
 *
 * 3. **Neutral (0% accent)** - surfaceVariantCardColors()
 *    - Unselected items, inactive states, general content
 *    - Text color: onSurfaceVariant
 *
 * ## Usage Rules:
 *
 * 1. Always use these functions instead of CardDefaults.cardColors() directly
 * 2. Always set explicit color/tint on Text/Icon components
 * 3. Match text colors to card type (see examples below)
 *
 * ## Examples:
 *
 * ```kotlin
 * // Prominent
 * Card(colors = ComponentColors.primaryCardColors()) {
 *     Text("Selected", color = MaterialTheme.colorScheme.onPrimaryContainer)
 * }
 *
 * // Subtle
 * Card(colors = ComponentColors.bannerCardColors()) {
 *     Text("Section", color = MaterialTheme.colorScheme.onSecondaryContainer)
 * }
 *
 * // Neutral
 * Card(colors = ComponentColors.surfaceVariantCardColors()) {
 *     Text("Default", color = MaterialTheme.colorScheme.onSurfaceVariant)
 * }
 * ```
 *
 * See docs/THEME_COLOR_GUIDE.md for complete documentation.
 */
object ComponentColors {

    /**
     * Navigation drawer item colors that use custom theme colors
     * - Unselected: transparent background, normal text/icon colors
     * - Selected: primaryContainer background (accent color), onPrimaryContainer text/icon
     */
    @Composable
    fun navigationDrawerItemColors(): NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        unselectedContainerColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    /**
     * Navigation rail item colors that use custom theme colors
     * - Unselected: normal icon color
     * - Selected: primaryContainer indicator (accent color), onPrimaryContainer icon
     */
    @Composable
    fun navigationRailItemColors(): NavigationRailItemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    )

    /**
     * Primary card colors (for important info cards like Provider/Model header, selected options)
     * - Container: primaryContainer (accent color)
     * - Content: onPrimaryContainer
     */
    @Composable
    fun primaryCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    /**
     * Banner card colors (for section headers like Chat Configuration, Font Settings)
     * - Container: secondaryContainer (subtle accent-related color)
     * - Content: onSecondaryContainer
     */
    @Composable
    fun bannerCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    /**
     * Secondary card colors (for search indicators, file attachments in input)
     * - Container: secondaryContainer
     * - Content: onSecondaryContainer
     */
    @Composable
    fun secondaryCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    /**
     * Surface variant card colors (for general cards like About page)
     * - Container: surfaceVariant
     * - Content: onSurfaceVariant
     */
    @Composable
    fun surfaceVariantCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    /**
     * Primary icon button colors
     * - Content: primary color (accent)
     * - Disabled: onSurface with alpha
     */
    @Composable
    fun primaryIconButtonColors(): IconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )

    /**
     * Text button colors for primary actions
     * - Content: primary color (accent)
     */
    @Composable
    fun primaryTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
    )

    /**
     * Outlined text field colors that match the theme's divider colors
     * - Unfocused border: outlineVariant (matches dividers)
     * - Focused border: primary (accent color)
     * - Text: onSurface
     * - Placeholder: onSurfaceVariant
     */
    @Composable
    fun outlinedTextFieldColors(): androidx.compose.material3.TextFieldColors =
        androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
        )

    /**
     * Sidebar surface color with subtle accent tint
     * - Applies a noticeable tint of the accent color to the surface
     * - Makes sidebar visually distinct from main content
     * - Adapts to light/dark mode (8% tint for light, 12% for dark)
     * - Ties into custom theme colors
     */
    @Composable
    fun sidebarSurfaceColor(): androidx.compose.ui.graphics.Color {
        val surfaceColor = MaterialTheme.colorScheme.surface
        val primaryColor = MaterialTheme.colorScheme.primary

        // Determine if we're in light or dark mode
        val isLight = surfaceColor.luminance() > 0.5

        // Apply noticeable tint (8% for light mode, 12% for dark mode)
        val tintAmount = if (isLight) 0.08f else 0.12f

        return Color(
            red = surfaceColor.red + (primaryColor.red - surfaceColor.red) * tintAmount,
            green = surfaceColor.green + (primaryColor.green - surfaceColor.green) * tintAmount,
            blue = surfaceColor.blue + (primaryColor.blue - surfaceColor.blue) * tintAmount,
            alpha = surfaceColor.alpha
        )
    }

    /**
     * Sidebar header color with stronger accent tint
     * - Applies a stronger tint than sidebar for visual hierarchy
     * - Makes header distinct from sidebar body
     * - Adapts to light/dark mode (15% tint for light, 20% for dark)
     * - Ties into custom theme colors
     */
    @Composable
    fun sidebarHeaderColor(): Color {
        val surfaceColor = MaterialTheme.colorScheme.surface
        val primaryColor = MaterialTheme.colorScheme.primary

        // Determine if we're in light or dark mode
        val isLight = surfaceColor.luminance() > 0.5

        // Apply stronger tint for header (15% for light mode, 20% for dark mode)
        val tintAmount = if (isLight) 0.15f else 0.20f

        return Color(
            red = surfaceColor.red + (primaryColor.red - surfaceColor.red) * tintAmount,
            green = surfaceColor.green + (primaryColor.green - surfaceColor.green) * tintAmount,
            blue = surfaceColor.blue + (primaryColor.blue - surfaceColor.blue) * tintAmount,
            alpha = surfaceColor.alpha
        )
    }
}
