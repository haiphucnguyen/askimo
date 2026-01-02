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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.askimo.desktop.view.chart.zoomControls
import io.askimo.tools.chart.AreaChartData
import org.jetbrains.skia.Color.makeARGB
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.TextLine

/**
 * Render an area chart using Skiko Canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun areaChart(
    data: AreaChartData,
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
            val chartWidth = size.width - 2 * padding
            val chartHeight = size.height - 2 * padding

            val allPoints = data.series.flatMap { it.points }
            if (allPoints.isEmpty()) return@Canvas

            val minX = allPoints.minOf { it.x }
            val maxX = allPoints.maxOf { it.x }
            val minY = allPoints.minOf { it.y }
            val maxY = allPoints.maxOf { it.y }

            val xRange = maxX - minX
            val yRange = maxY - minY

            if (xRange == 0f || yRange == 0f) return@Canvas

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

            // Draw axes and grid
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

            for (i in 0..4) {
                val y = padding + (chartHeight / 4) * i
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(padding, y),
                    end = Offset(size.width - padding, y),
                    strokeWidth = 1f,
                )
            }

            // Draw Y-axis labels
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 16f)

                for (i in 0..4) {
                    val value = maxY - (yRange / 4) * i
                    val y = padding + (chartHeight / 4) * i
                    val label = String.format("%.1f", value)
                    val textLine = TextLine.make(label, font)
                    drawTextLine(textLine, padding - textLine.width - 8f, y + 4f, labelPaint)
                }
            }

            // Draw X-axis value labels
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 14f)

                for (i in 0..4) {
                    val value = minX + (xRange / 4) * i
                    val x = padding + (chartWidth / 4) * i

                    val label = data.xLabels?.get(value) ?: run {
                        if (value % 1 == 0f) {
                            String.format("%.0f", value)
                        } else {
                            String.format("%.1f", value)
                        }
                    }

                    val textLine = TextLine.make(label, font)
                    drawTextLine(textLine, x - textLine.width / 2, size.height - padding + 20f, labelPaint)
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

            // Draw each series as filled area
            data.series.forEach { series ->
                val path = Path()

                val firstPoint = series.points.first()
                val startX = padding + ((firstPoint.x - minX) / xRange) * chartWidth
                val startY = size.height - padding
                path.moveTo(startX, startY)

                series.points.forEach { point ->
                    val x = padding + ((point.x - minX) / xRange) * chartWidth
                    val y = size.height - padding - ((point.y - minY) / yRange) * chartHeight
                    path.lineTo(x, y)
                }

                val lastPoint = series.points.last()
                val endX = padding + ((lastPoint.x - minX) / xRange) * chartWidth
                path.lineTo(endX, size.height - padding)
                path.close()

                drawPath(
                    path = path,
                    color = Color(series.color).copy(alpha = 0.3f),
                )

                val linePath = Path()
                series.points.forEachIndexed { index, point ->
                    val x = padding + ((point.x - minX) / xRange) * chartWidth
                    val y = size.height - padding - ((point.y - minY) / yRange) * chartHeight
                    if (index == 0) {
                        linePath.moveTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = Color(series.color),
                    style = Stroke(width = 2f),
                )
            }

            // Draw legend
            var legendY = padding + 20f
            drawContext.canvas.nativeCanvas.apply {
                val font = Font(null, 16f)
                data.series.forEach { series ->
                    val legendPaint = Paint().apply {
                        color = makeARGB(
                            ((series.color shr 24) and 0xFF).toInt(),
                            ((series.color shr 16) and 0xFF).toInt(),
                            ((series.color shr 8) and 0xFF).toInt(),
                            (series.color and 0xFF).toInt(),
                        )
                        mode = PaintMode.FILL
                    }

                    drawRect(
                        Rect.makeXYWH(
                            size.width - padding - 80f,
                            legendY - 10f,
                            12f,
                            12f,
                        ),
                        legendPaint,
                    )

                    val textPaint = Paint().apply {
                        color = textColor.toArgb()
                        mode = PaintMode.FILL
                    }
                    val textLine = TextLine.make(series.name, font)
                    drawTextLine(textLine, size.width - padding - 60f, legendY, textPaint)

                    legendY += 20f
                }
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
