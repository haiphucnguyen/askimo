/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.chart.renderers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.askimo.desktop.view.chart.zoomControls
import io.askimo.tools.chart.WaterfallChartData
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.TextLine
import kotlin.math.max
import kotlin.math.min

/**
 * Render a waterfall chart showing cumulative effect of sequential values.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun waterfallChart(
    data: WaterfallChartData,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        ) {
            val padding = 80f
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 2

            if (data.items.isEmpty()) return@Canvas

            // Calculate cumulative values
            val cumulativeValues = mutableListOf<Float>()
            var cumulative = 0f
            data.items.forEach { item ->
                if (!item.isTotal) {
                    cumulative += item.value
                }
                cumulativeValues.add(cumulative)
            }

            val allValues = cumulativeValues + listOf(0f)
            val minValue = allValues.minOrNull() ?: 0f
            val maxValue = allValues.maxOrNull() ?: 100f
            val valueRange = maxValue - minValue
            val yPadding = valueRange * 0.1f

            fun valueToY(value: Float): Float = padding + chartHeight * (1 - (value - minValue + yPadding) / (valueRange + yPadding * 2))

            val zeroY = valueToY(0f)

            // Draw grid lines
            for (i in 0..4) {
                val y = padding + (chartHeight / 4) * i
                drawLine(
                    color = gridColor,
                    start = Offset(padding, y),
                    end = Offset(size.width - padding, y),
                    strokeWidth = 1f,
                )
            }

            // Draw axes
            drawLine(
                color = axisColor,
                start = Offset(padding, padding),
                end = Offset(padding, size.height - padding),
                strokeWidth = 2f,
            )
            drawLine(
                color = axisColor,
                start = Offset(padding, size.height - padding),
                end = Offset(size.width - padding, size.height - padding),
                strokeWidth = 2f,
            )

            // Draw zero line
            drawLine(
                color = axisColor.copy(alpha = 0.5f),
                start = Offset(padding, zeroY),
                end = Offset(size.width - padding, zeroY),
                strokeWidth = 1.5f,
            )

            // Draw title
            drawContext.canvas.nativeCanvas.apply {
                val titlePaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 20f)
                val title = TextLine.make(data.title, font)
                drawTextLine(title, (size.width - title.width) / 2, 20f, titlePaint)
            }

            val barWidth = (chartWidth / data.items.size) * 0.7f
            val barSpacing = chartWidth / data.items.size

            var previousCumulative = 0f

            data.items.forEachIndexed { index, item ->
                val x = padding + barSpacing * (index + 0.5f)
                val currentCumulative = cumulativeValues[index]

                val barColor = when {
                    item.isTotal -> Color(item.colorTotal)
                    item.value > 0 -> Color(item.colorPositive)
                    else -> Color(item.colorNegative)
                }

                if (item.isTotal) {
                    val barTop = valueToY(currentCumulative)
                    val barHeight = zeroY - barTop

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x - barWidth / 2, barTop),
                        size = Size(barWidth, barHeight),
                    )
                } else {
                    val startValue = previousCumulative
                    val endValue = currentCumulative

                    val barTop = valueToY(max(startValue, endValue))
                    val barBottom = valueToY(min(startValue, endValue))
                    val barHeight = barBottom - barTop

                    if (index > 0) {
                        val prevX = padding + barSpacing * (index - 0.5f)
                        drawLine(
                            color = gridColor,
                            start = Offset(prevX + barWidth / 2, valueToY(startValue)),
                            end = Offset(x - barWidth / 2, valueToY(startValue)),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
                        )
                    }

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x - barWidth / 2, barTop),
                        size = Size(barWidth, barHeight),
                    )
                }

                previousCumulative = currentCumulative
            }

            // Draw X-axis labels
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 12f)

                data.items.forEachIndexed { index, item ->
                    val x = padding + barSpacing * (index + 0.5f)
                    val textLine = TextLine.make(item.label, font)

                    save()
                    translate(x, size.height - padding + 15f)
                    rotate(-45f)
                    drawTextLine(textLine, 0f, 0f, labelPaint)
                    restore()
                }
            }

            // Draw Y-axis labels
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 16f)

                for (i in 0..4) {
                    val value = maxValue + yPadding - ((maxValue - minValue + yPadding * 2) / 4) * i
                    val y = padding + (chartHeight / 4) * i
                    val label = String.format("%.1f", value)
                    val textLine = TextLine.make(label, font)
                    drawTextLine(textLine, padding - textLine.width - 8f, y + 4f, labelPaint)
                }
            }

            // Draw axis labels
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 18f)

                val xLabel = TextLine.make(data.xAxisLabel.ifEmpty { "Categories" }, font)
                drawTextLine(xLabel, (size.width - xLabel.width) / 2, size.height - 30f, labelPaint)

                val yLabel = TextLine.make(data.yAxisLabel, font)
                drawTextLine(yLabel, 10f, 55f, labelPaint)
            }
        }

        zoomControls(
            scale = scale,
            onScaleChange = { scale = it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 16.dp),
        )
    }
}
