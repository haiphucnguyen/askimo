/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay

/**
 * A reusable tooltip component that follows the application theme.
 * Automatically positions itself to avoid overlapping with the component and screen edges.
 *
 * @param text The text to display in the tooltip
 * @param modifier Optional modifier for the TooltipBox
 * @param content The composable content that the tooltip wraps
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun themedTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    var shouldShowTooltip by remember { mutableStateOf(false) }

    // Delay before showing tooltip
    LaunchedEffect(shouldShowTooltip) {
        if (shouldShowTooltip) {
            delay(500)
            tooltipState.show()
        } else {
            tooltipState.dismiss()
        }
    }

    BoxWithConstraints {
        val density = LocalDensity.current
        val maxHeightPx = with(density) { maxHeight.toPx() }

        TooltipBox(
            positionProvider = remember(maxHeightPx) {
                SmartTooltipPositionProvider(
                    maxHeightPx = maxHeightPx,
                )
            },
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
            state = tooltipState,
            modifier = modifier,
        ) {
            Box(
                modifier = Modifier
                    .hoverable(
                        interactionSource = remember { MutableInteractionSource() },
                        enabled = true,
                    )
                    .onPointerEvent(PointerEventType.Enter) {
                        shouldShowTooltip = true
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        shouldShowTooltip = false
                    },
            ) {
                content()
            }
        }
    }
}

/**
 * Smart tooltip position provider that automatically positions the tooltip
 * to avoid overlapping with the component and screen edges.
 */
private class SmartTooltipPositionProvider(
    private val maxHeightPx: Float,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val spacing = 8 // 8dp spacing between component and tooltip

        // Determine if component is in bottom half of screen
        val isInBottomHalf = anchorBounds.top > maxHeightPx / 2

        return when {
            // Bottom of screen: show tooltip above
            isInBottomHalf -> {
                IntOffset(
                    x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
                    y = anchorBounds.top - popupContentSize.height - spacing,
                )
            }
            // Top of screen: show tooltip below
            !isInBottomHalf -> {
                IntOffset(
                    x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
                    y = anchorBounds.bottom + spacing,
                )
            }
            // Default: to the right
            else -> {
                IntOffset(
                    x = anchorBounds.right + spacing,
                    y = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2,
                )
            }
        }
    }
}
