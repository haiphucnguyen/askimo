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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.askimo.desktop.view.chart.zoomControls
import io.askimo.tools.chart.HistogramData
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.TextLine

/**
 * Render a histogram chart using Skiko Canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun histogramChart(
    data: HistogramData,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
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
            val chartWidth = size.width - 2 * padding
            val chartHeight = size.height - 2 * padding

            if (data.bins.isEmpty()) return@Canvas

            val maxCount = data.bins.maxOf { it.count }
            if (maxCount == 0f) return@Canvas

            // Draw title
            drawContext.canvas.nativeCanvas.apply {
                val titlePaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 24f)
                val titleLine = TextLine.make(data.title, font)
                val titleX = (size.width - titleLine.width) / 2
                drawTextLine(titleLine, titleX, 20f, titlePaint)
            }

            // Draw axes
            drawLine(
                color = axisColor,
                start = Offset(padding, size.height - padding),
                end = Offset(size.width - padding, size.height - padding),
                strokeWidth = 2f,
            )
            drawLine(
                color = axisColor,
                start = Offset(padding, padding),
                end = Offset(padding, size.height - padding),
                strokeWidth = 2f,
            )

            // Calculate bin width
            val binWidth = chartWidth / data.bins.size

            // Draw bins
            data.bins.forEachIndexed { index, bin ->
                val binHeight = (bin.count / maxCount) * chartHeight
                val x = padding + index * binWidth
                val y = size.height - padding - binHeight

                drawRect(
                    color = Color(data.color),
                    topLeft = Offset(x, y),
                    size = Size(binWidth, binHeight),
                )

                drawRect(
                    color = Color(data.color).copy(alpha = 0.3f),
                    topLeft = Offset(x, y),
                    size = Size(binWidth, binHeight),
                    style = Stroke(width = 1f),
                )

                drawContext.canvas.nativeCanvas.apply {
                    val labelPaint = Paint().apply {
                        color = textColor.toArgb()
                        mode = PaintMode.FILL
                    }
                    val font = Font(null, 12f)
                    val label = String.format("%.0f", bin.rangeStart)
                    val textLine = TextLine.make(label, font)
                    val textX = x + (binWidth - textLine.width) / 2
                    drawTextLine(textLine, textX, size.height - padding + 20f, labelPaint)
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
                    val value = maxCount - (maxCount / 4) * i
                    val y = padding + (chartHeight / 4) * i
                    val label = String.format("%.0f", value)
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

                val xLabel = TextLine.make(data.xAxisLabel, font)
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
