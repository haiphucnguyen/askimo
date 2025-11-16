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
}
