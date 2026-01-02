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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.askimo.desktop.view.chart.zoomControls
import io.askimo.tools.chart.PieChartData
import org.jetbrains.skia.Color.makeARGB
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.TextLine
import kotlin.math.min

/**
 * Render a pie chart using Skiko Canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun pieChart(
    data: PieChartData,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val backgroundColor = MaterialTheme.colorScheme.surface

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
            if (data.slices.isEmpty()) return@Canvas

            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = min(size.width, size.height) / 3

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

            val total = data.slices.sumOf { it.value.toDouble() }.toFloat()
            if (total == 0f) return@Canvas

            var currentAngle = -90f

            data.slices.forEach { slice ->
                val sweepAngle = (slice.value / total) * 360f

                drawArc(
                    color = Color(slice.color),
                    startAngle = currentAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                )

                drawArc(
                    color = backgroundColor,
                    startAngle = currentAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 2f),
                )

                currentAngle += sweepAngle
            }

            // Draw legend
            var legendY = centerY - radius + 20f
            drawContext.canvas.nativeCanvas.apply {
                val font = Font(null, 16f)
                data.slices.forEach { slice ->
                    val percentage = (slice.value / total) * 100

                    val legendPaint = Paint().apply {
                        color = makeARGB(
                            ((slice.color shr 24) and 0xFF).toInt(),
                            ((slice.color shr 16) and 0xFF).toInt(),
                            ((slice.color shr 8) and 0xFF).toInt(),
                            (slice.color and 0xFF).toInt(),
                        )
                        mode = PaintMode.FILL
                    }

                    drawRect(
                        Rect.makeXYWH(
                            centerX + radius + 30f,
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
                    val labelText = "${slice.label} (${String.format("%.1f", percentage)}%)"
                    val textLine = TextLine.make(labelText, font)
                    drawTextLine(textLine, centerX + radius + 50f, legendY, textPaint)

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
