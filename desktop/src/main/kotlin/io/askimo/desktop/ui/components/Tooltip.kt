/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A reusable tooltip component that follows the application theme.
 * This ensures consistent styling across all tooltips in the app.
 *
 * @param text The text to display in the tooltip
 * @param modifier Optional modifier for the TooltipArea
 * @param content The composable content that the tooltip wraps
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun themedTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
            Surface(
                modifier = Modifier.padding(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
        content = content,
    )
}
