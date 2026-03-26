/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.common.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemColors
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

object AppComponents {

    // ── Navigation ───────────────────────────────────────────────────────────

    @Composable
    fun navigationDrawerItemColors(): NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedBadgeColor = MaterialTheme.colorScheme.onPrimaryContainer,
        unselectedContainerColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedBadgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    @Composable
    fun navigationRailItemColors(): NavigationRailItemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    )

    // ── Cards ─────────────────────────────────────────────────────────────────

    @Composable
    fun primaryCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    @Composable
    fun bannerCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    @Composable
    fun secondaryCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    @Composable
    fun surfaceVariantCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // ── Buttons & Icons ───────────────────────────────────────────────────────

    @Composable
    fun primaryIconButtonColors(): IconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )

    @Composable
    fun primaryTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
    )

    // ── Inputs ────────────────────────────────────────────────────────────────

    @Composable
    fun outlinedTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.onSurface,
    )

    @Composable
    fun menuItemColors(): MenuItemColors = MenuDefaults.itemColors(
        textColor = MaterialTheme.colorScheme.onSurface,
        leadingIconColor = MaterialTheme.colorScheme.onSurface,
        trailingIconColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )

    // ── Surface Colors ────────────────────────────────────────────────────────

    @Composable
    fun sidebarSurfaceColor(): Color {
        val surfaceColor = MaterialTheme.colorScheme.surface
        val primaryColor = MaterialTheme.colorScheme.primary
        val isLight = surfaceColor.luminance() > 0.5
        val tintAmount = if (isLight) 0.08f else 0.12f
        return Color(
            red = surfaceColor.red + (primaryColor.red - surfaceColor.red) * tintAmount,
            green = surfaceColor.green + (primaryColor.green - surfaceColor.green) * tintAmount,
            blue = surfaceColor.blue + (primaryColor.blue - surfaceColor.blue) * tintAmount,
            alpha = surfaceColor.alpha,
        )
    }

    @Composable
    fun sidebarHeaderColor(): Color = MaterialTheme.colorScheme.secondaryContainer

    @Composable
    fun userMessageBackground(): Color {
        val surface = MaterialTheme.colorScheme.surface
        val isLight = surface.luminance() > 0.5
        return if (isLight) {
            Color(
                red = (surface.red * 0.92f).coerceIn(0f, 1f),
                green = (surface.green * 0.92f).coerceIn(0f, 1f),
                blue = (surface.blue * 0.92f).coerceIn(0f, 1f),
                alpha = surface.alpha,
            )
        } else {
            Color(
                red = (surface.red + 0.10f).coerceIn(0f, 1f),
                green = (surface.green + 0.10f).coerceIn(0f, 1f),
                blue = (surface.blue + 0.10f).coerceIn(0f, 1f),
                alpha = surface.alpha,
            )
        }
    }

    @Composable
    fun userMessageContentColor(): Color = MaterialTheme.colorScheme.onSurface

    @Composable
    fun codeBlockBackground(): Color {
        val surface = MaterialTheme.colorScheme.surface
        val isLight = surface.luminance() > 0.5
        return if (isLight) {
            Color(
                red = (surface.red * 0.85f).coerceIn(0f, 1f),
                green = (surface.green * 0.85f).coerceIn(0f, 1f),
                blue = (surface.blue * 0.85f).coerceIn(0f, 1f),
                alpha = surface.alpha,
            )
        } else {
            Color(
                red = (surface.red * 0.75f).coerceIn(0f, 1f),
                green = (surface.green * 0.75f).coerceIn(0f, 1f),
                blue = (surface.blue * 0.75f).coerceIn(0f, 1f),
                alpha = surface.alpha,
            )
        }
    }

    @Composable
    fun codeBlockContentColor(): Color = MaterialTheme.colorScheme.onSurface

    @Composable
    fun codeBlockBorderColor(): Color = MaterialTheme.colorScheme.outlineVariant

    @Composable
    fun isCodeBlockDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5

    // ── Icon & Text Tints ─────────────────────────────────────────────────────

    @Composable
    fun secondaryIconColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    @Composable
    fun disabledIconColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    @Composable
    fun tertiaryIconColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    @Composable
    fun secondaryTextColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    @Composable
    fun tertiaryTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    // ── Themed Composable Wrappers ────────────────────────────────────────────

    @Composable
    fun dropdownMenu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        modifier: Modifier = Modifier,
        offset: DpOffset = DpOffset.Zero,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surfaceContainer = MaterialTheme.colorScheme.surface,
            ),
        ) {
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismissRequest,
                offset = offset,
                modifier = modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(4.dp),
                ),
                content = content,
            )
        }
    }

    @Composable
    fun alertDialog(
        onDismissRequest: () -> Unit,
        confirmButton: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        dismissButton: @Composable (() -> Unit)? = null,
        icon: @Composable (() -> Unit)? = null,
        title: @Composable (() -> Unit)? = null,
        text: @Composable (() -> Unit)? = null,
        shape: Shape = AlertDialogDefaults.shape,
        containerColor: Color = MaterialTheme.colorScheme.surface,
        iconContentColor: Color = AlertDialogDefaults.iconContentColor,
        titleContentColor: Color = AlertDialogDefaults.titleContentColor,
        textContentColor: Color = AlertDialogDefaults.textContentColor,
        tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
        properties: DialogProperties = DialogProperties(),
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surfaceContainerHigh = MaterialTheme.colorScheme.surface,
            ),
        ) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = confirmButton,
                modifier = modifier,
                dismissButton = dismissButton,
                icon = icon,
                title = title,
                text = text,
                shape = shape,
                containerColor = containerColor,
                iconContentColor = iconContentColor,
                titleContentColor = titleContentColor,
                textContentColor = textContentColor,
                tonalElevation = tonalElevation,
                properties = properties,
            )
        }
    }
}
