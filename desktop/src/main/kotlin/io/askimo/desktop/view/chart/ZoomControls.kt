/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.chart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp

/**
 * Reusable zoom controls component for charts.
 * Provides zoom in and zoom out buttons with a flat 2D design.
 *
 * @param scale Current scale value
 * @param onScaleChange Callback when scale changes
 * @param minScale Minimum allowed scale value (default: 0.5f)
 * @param maxScale Maximum allowed scale value (default: 5f)
 * @param modifier Optional modifier for positioning the controls
 */
@Composable
fun zoomControls(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    minScale: Float = 0.5f,
    maxScale: Float = 5f,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        // Zoom In Button
        val zoomInInteractionSource = remember { MutableInteractionSource() }
        val isZoomInHovered by zoomInInteractionSource.collectIsHoveredAsState()

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isZoomInHovered) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            modifier = Modifier
                .size(40.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = zoomInInteractionSource,
                    indication = null, // Remove ripple effect
                    onClick = {
                        onScaleChange((scale * 1.2f).coerceIn(minScale, maxScale))
                    },
                ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = if (isZoomInHovered) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Zoom Out Button
        val zoomOutInteractionSource = remember { MutableInteractionSource() }
        val isZoomOutHovered by zoomOutInteractionSource.collectIsHoveredAsState()

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isZoomOutHovered) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            modifier = Modifier
                .size(40.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = zoomOutInteractionSource,
                    indication = null, // Remove ripple effect
                    onClick = {
                        onScaleChange((scale * 0.8f).coerceIn(minScale, maxScale))
                    },
                ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = if (isZoomOutHovered) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }
    }
}
