/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modifier extension for creating clickable cards with rounded corners and hover effect.
 * Commonly used in settings screens for option cards.
 *
 * @param cornerRadius Corner radius matching the card's shape (default 12.dp)
 * @param onClick Callback invoked when the card is clicked
 * @return Modified Modifier with click, clip, and hover effects
 */
fun Modifier.clickableCard(
    cornerRadius: Dp = 12.dp,
    onClick: () -> Unit,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .clickable(onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)
}
