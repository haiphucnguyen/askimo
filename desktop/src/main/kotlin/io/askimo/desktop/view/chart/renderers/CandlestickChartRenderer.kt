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
import io.askimo.tools.chart.CandlestickChartData
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.TextLine
import kotlin.math.max
import kotlin.math.min

/**
 * Render a candlestick chart for OHLC (Open, High, Low, Close) financial data.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun candlestickChart(
    data: CandlestickChartData,
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

            if (data.candles.isEmpty()) return@Canvas

            val allPrices = data.candles.flatMap { listOf(it.high, it.low) }
            val minPrice = allPrices.minOrNull() ?: 0f
            val maxPrice = allPrices.maxOrNull() ?: 100f
            val priceRange = maxPrice - minPrice
            val yPadding = priceRange * 0.1f

            fun priceToY(price: Float): Float = padding + chartHeight * (1 - (price - minPrice + yPadding) / (priceRange + yPadding * 2))

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

            val candleWidth = (chartWidth / data.candles.size) * 0.6f

            data.candles.forEachIndexed { index, candle ->
                val x = padding + (chartWidth / data.candles.size) * (index + 0.5f)

                val highY = priceToY(candle.high)
                val lowY = priceToY(candle.low)
                val openY = priceToY(candle.open)
                val closeY = priceToY(candle.close)

                val isBullish = candle.close >= candle.open
                val candleColor = Color(if (isBullish) candle.colorUp else candle.colorDown)

                drawLine(
                    color = candleColor,
                    start = Offset(x, highY),
                    end = Offset(x, lowY),
                    strokeWidth = 1.5f,
                )

                val bodyTop = min(openY, closeY)
                val bodyBottom = max(openY, closeY)
                val bodyHeight = bodyBottom - bodyTop

                if (isBullish) {
                    drawRect(
                        color = candleColor,
                        topLeft = Offset(x - candleWidth / 2, bodyTop),
                        size = Size(candleWidth, bodyHeight),
                        style = Stroke(width = 2f),
                    )
                } else {
                    drawRect(
                        color = candleColor,
                        topLeft = Offset(x - candleWidth / 2, bodyTop),
                        size = Size(candleWidth, bodyHeight),
                    )
                }
            }

            // Draw X-axis labels
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 14f)

                data.candles.forEachIndexed { index, candle ->
                    if (index % max(1, data.candles.size / 10) == 0 || index == data.candles.size - 1) {
                        val x = padding + (chartWidth / data.candles.size) * (index + 0.5f)
                        val label = data.xLabels?.get(candle.x) ?: candle.x.toInt().toString()
                        val textLine = TextLine.make(label, font)
                        drawTextLine(textLine, x - textLine.width / 2, size.height - padding + 20f, labelPaint)
                    }
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
                    val price = maxPrice + yPadding - ((maxPrice - minPrice + yPadding * 2) / 4) * i
                    val y = padding + (chartHeight / 4) * i
                    val label = String.format("%.2f", price)
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
