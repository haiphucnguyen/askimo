/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A card with a properly rounded ripple/hover effect.
 *
 * Using [Card] with its built-in `onClick` parameter causes a double border / focus-ring
 * artifact in Compose Desktop — M3 draws its own focus indicator on top of the card border.
 * This composable avoids that by attaching [Modifier.clip] + [Modifier.clickable] directly
 * on the card modifier so the ripple is clipped to the card shape without any extra outline.
 *
 * Usage:
 * ```
 * clickableCard(onClick = { /* ... */ }) {
 *     Text("Hello")
 * }
 * ```
 *
 * @param onClick Action invoked on click. Pass `null` to render a non-interactive card.
 * @param modifier Modifier applied to the card.
 * @param shape Card corner shape. Defaults to [MaterialTheme.shapes.large].
 * @param colors Card colors. Defaults to [CardDefaults.cardColors].
 * @param elevation Card shadow elevation. Defaults to 0.dp (flat).
 * @param content Card content lambda.
 */
@Composable
fun clickableCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = onClick,
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                } else {
                    Modifier
                },
            ),
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        shape = shape,
        content = { content() },
    )
}
