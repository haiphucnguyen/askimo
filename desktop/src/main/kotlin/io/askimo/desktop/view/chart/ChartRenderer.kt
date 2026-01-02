/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.askimo.tools.chart.AreaChartData
import io.askimo.tools.chart.BarChartData
import io.askimo.tools.chart.BoxPlotData
import io.askimo.tools.chart.CandlestickChartData
import io.askimo.tools.chart.ChartData
import io.askimo.tools.chart.HistogramData
import io.askimo.tools.chart.LineChartData
import io.askimo.tools.chart.PieChartData
import io.askimo.tools.chart.ScatterChartData
import io.askimo.tools.chart.WaterfallChartData
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.TextLine
import kotlin.math.min

/**
 * Main chart renderer that delegates to specific chart type renderers.
 */
@Composable
fun chartRenderer(
    chartData: ChartData,
    modifier: Modifier = Modifier,
) {
    when (chartData) {
        is LineChartData -> lineChart(data = chartData, modifier = modifier)
        is BarChartData -> barChart(data = chartData, modifier = modifier)
        is PieChartData -> pieChart(data = chartData, modifier = modifier)
        is ScatterChartData -> scatterChart(data = chartData, modifier = modifier)
        is AreaChartData -> areaChart(data = chartData, modifier = modifier)
        is HistogramData -> histogramChart(data = chartData, modifier = modifier)
        is BoxPlotData -> boxPlotChart(data = chartData, modifier = modifier)
        is CandlestickChartData -> candlestickChart(data = chartData, modifier = modifier)
        is WaterfallChartData -> waterfallChart(data = chartData, modifier = modifier)
    }
}

/**
 * Render a line chart using Skiko Canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun lineChart(
    data: LineChartData,
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
            val padding = 60f
            val chartWidth = size.width - 2 * padding
            val chartHeight = size.height - 2 * padding

            // Find min/max for scaling
            val allPoints = data.series.flatMap { it.points }
            if (allPoints.isEmpty()) return@Canvas

            val minX = allPoints.minOf { it.x }
            val maxX = allPoints.maxOf { it.x }
            val minY = allPoints.minOf { it.y }
            val maxY = allPoints.maxOf { it.y }

            val xRange = maxX - minX
            val yRange = maxY - minY

            // Avoid division by zero
            if (xRange == 0f || yRange == 0f) return@Canvas

            // Draw title (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val titlePaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 24f) // Increased from 20f
                val titleLine = TextLine.make(data.title, font)
                val titleX = (size.width - titleLine.width) / 2
                drawTextLine(titleLine, titleX, 35f, titlePaint)
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

            // Draw grid lines
            for (i in 0..4) {
                val y = padding + (chartHeight / 4) * i
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(padding, y),
                    end = Offset(size.width - padding, y),
                    strokeWidth = 1f,
                )
            }

            // Draw Y-axis labels (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 16f) // Increased from 14f

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

                // Draw labels at regular intervals
                for (i in 0..4) {
                    val value = minX + (xRange / 4) * i
                    val x = padding + (chartWidth / 4) * i

                    // Use custom label if provided, otherwise format numeric value
                    val label = data.xLabels?.get(value) ?: run {
                        if (value % 1 == 0f) {
                            // Integer values (like years)
                            String.format("%.0f", value)
                        } else {
                            // Decimal values
                            String.format("%.1f", value)
                        }
                    }

                    val textLine = TextLine.make(label, font)
                    // Center the text under the position
                    drawTextLine(textLine, x - textLine.width / 2, size.height - padding + 20f, labelPaint)
                }
            }

            // Draw axis labels (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 18f) // Increased from 16f

                // X-axis label
                val xLabel = TextLine.make(data.xAxisLabel, font)
                drawTextLine(xLabel, (size.width - xLabel.width) / 2, size.height - 10f, labelPaint)

                // Y-axis label
                val yLabel = TextLine.make(data.yAxisLabel, font)
                drawTextLine(yLabel, 10f, padding - 10f, labelPaint)
            }

            // Draw each series
            data.series.forEach { series ->
                val path = Path()
                series.points.forEachIndexed { index, point ->
                    val x = padding + ((point.x - minX) / xRange) * chartWidth
                    val y = size.height - padding - ((point.y - minY) / yRange) * chartHeight

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }

                    // Draw point markers
                    drawCircle(
                        color = Color(series.color),
                        radius = 4f,
                        center = Offset(x, y),
                    )
                }

                // Draw line
                drawPath(
                    path = path,
                    color = Color(series.color),
                    style = Stroke(width = 2f),
                )
            }

            // Draw legend (larger font)
            var legendY = padding + 20f
            drawContext.canvas.nativeCanvas.apply {
                val font = Font(null, 16f) // Increased from 14f
                data.series.forEach { series ->
                    val legendPaint = Paint().apply {
                        color = org.jetbrains.skia.Color.makeARGB(
                            ((series.color shr 24) and 0xFF).toInt(),
                            ((series.color shr 16) and 0xFF).toInt(),
                            ((series.color shr 8) and 0xFF).toInt(),
                            (series.color and 0xFF).toInt(),
                        )
                        mode = PaintMode.FILL
                    }

                    // Draw legend color box
                    drawRect(
                        org.jetbrains.skia.Rect.makeXYWH(
                            size.width - padding - 80f,
                            legendY - 10f,
                            12f,
                            12f,
                        ),
                        legendPaint,
                    )

                    // Draw legend text
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

        // Zoom controls (vertical column)
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 1.2f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 0.8f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

/**
 * Render a bar chart using Skiko Canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun barChart(
    data: BarChartData,
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
            val padding = 60f
            val chartWidth = size.width - 2 * padding
            val chartHeight = size.height - 2 * padding

            if (data.bars.isEmpty()) return@Canvas

            val maxValue = data.bars.maxOf { it.value }
            if (maxValue == 0f) return@Canvas

            // Draw title (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val titlePaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 24f) // Increased from 20f
                val titleLine = TextLine.make(data.title, font)
                val titleX = (size.width - titleLine.width) / 2
                drawTextLine(titleLine, titleX, 35f, titlePaint)
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

            // Calculate bar dimensions
            val barWidth = chartWidth / data.bars.size * 0.7f
            val barSpacing = chartWidth / data.bars.size * 0.3f

            // Draw bars
            data.bars.forEachIndexed { index, bar ->
                val barHeight = (bar.value / maxValue) * chartHeight
                val x = padding + index * (barWidth + barSpacing) + barSpacing / 2
                val y = size.height - padding - barHeight

                // Draw bar
                drawRect(
                    color = Color(bar.color),
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                )

                // Draw bar label (larger font)
                drawContext.canvas.nativeCanvas.apply {
                    val labelPaint = Paint().apply {
                        color = textColor.toArgb()
                        mode = PaintMode.FILL
                    }
                    val font = Font(null, 15f) // Increased from 13f
                    val textLine = TextLine.make(bar.label, font)
                    val textX = x + (barWidth - textLine.width) / 2
                    drawTextLine(textLine, textX, size.height - padding + 20f, labelPaint)
                }

                // Draw value on top of bar (larger font)
                drawContext.canvas.nativeCanvas.apply {
                    val valuePaint = Paint().apply {
                        color = textColor.toArgb()
                        mode = PaintMode.FILL
                    }
                    val font = Font(null, 14f) // Increased from 12f
                    val valueText = String.format("%.1f", bar.value)
                    val textLine = TextLine.make(valueText, font)
                    val textX = x + (barWidth - textLine.width) / 2
                    drawTextLine(textLine, textX, y - 5f, valuePaint)
                }
            }

            // Draw Y-axis labels (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 16f) // Increased from 14f

                for (i in 0..4) {
                    val value = maxValue - (maxValue / 4) * i
                    val y = padding + (chartHeight / 4) * i
                    val label = String.format("%.1f", value)
                    val textLine = TextLine.make(label, font)
                    drawTextLine(textLine, padding - textLine.width - 8f, y + 4f, labelPaint)
                }
            }

            // Draw axis labels (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 18f) // Increased from 16f

                // X-axis label
                val xLabel = TextLine.make(data.xAxisLabel, font)
                drawTextLine(xLabel, (size.width - xLabel.width) / 2, size.height - 10f, labelPaint)

                // Y-axis label
                val yLabel = TextLine.make(data.yAxisLabel, font)
                drawTextLine(yLabel, 10f, padding - 10f, labelPaint)
            }
        }

        // Zoom controls (vertical column)
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 1.2f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 0.8f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

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

            // Draw title (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val titlePaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 24f) // Increased from 20f
                val titleLine = TextLine.make(data.title, font)
                val titleX = (size.width - titleLine.width) / 2
                drawTextLine(titleLine, titleX, 35f, titlePaint)
            }

            val total = data.slices.sumOf { it.value.toDouble() }.toFloat()
            if (total == 0f) return@Canvas

            var currentAngle = -90f // Start from top

            data.slices.forEach { slice ->
                val sweepAngle = (slice.value / total) * 360f

                // Draw pie slice
                drawArc(
                    color = Color(slice.color),
                    startAngle = currentAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                )

                // Draw slice border
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

            // Draw legend (larger font)
            var legendY = centerY - radius + 20f
            drawContext.canvas.nativeCanvas.apply {
                val font = Font(null, 16f) // Increased from 14f
                data.slices.forEach { slice ->
                    val percentage = (slice.value / total) * 100

                    val legendPaint = Paint().apply {
                        color = org.jetbrains.skia.Color.makeARGB(
                            ((slice.color shr 24) and 0xFF).toInt(),
                            ((slice.color shr 16) and 0xFF).toInt(),
                            ((slice.color shr 8) and 0xFF).toInt(),
                            (slice.color and 0xFF).toInt(),
                        )
                        mode = PaintMode.FILL
                    }

                    // Draw legend color box
                    drawRect(
                        org.jetbrains.skia.Rect.makeXYWH(
                            centerX + radius + 30f,
                            legendY - 10f,
                            12f,
                            12f,
                        ),
                        legendPaint,
                    )

                    // Draw legend text
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

        // Zoom controls (vertical column)
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 1.2f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 0.8f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

/**
 * Render a scatter chart using Skiko Canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun scatterChart(
    data: ScatterChartData,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
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
            val padding = 60f
            val chartWidth = size.width - 2 * padding
            val chartHeight = size.height - 2 * padding

            // Find min/max for scaling
            val allPoints = data.series.flatMap { it.points }
            if (allPoints.isEmpty()) return@Canvas

            val minX = allPoints.minOf { it.x }
            val maxX = allPoints.maxOf { it.x }
            val minY = allPoints.minOf { it.y }
            val maxY = allPoints.maxOf { it.y }

            val xRange = maxX - minX
            val yRange = maxY - minY

            // Avoid division by zero
            if (xRange == 0f || yRange == 0f) return@Canvas

            // Draw title (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val titlePaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 24f) // Increased from 20f
                val titleLine = TextLine.make(data.title, font)
                val titleX = (size.width - titleLine.width) / 2
                drawTextLine(titleLine, titleX, 35f, titlePaint)
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

            // Draw grid lines
            for (i in 0..4) {
                val y = padding + (chartHeight / 4) * i
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(padding, y),
                    end = Offset(size.width - padding, y),
                    strokeWidth = 1f,
                )
            }

            // Draw Y-axis labels (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 16f) // Increased from 14f

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

                    // Use custom label if provided, otherwise format numeric value
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

            // Draw axis labels (larger font)
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 18f) // Increased from 16f

                // X-axis label
                val xLabel = TextLine.make(data.xAxisLabel, font)
                drawTextLine(xLabel, (size.width - xLabel.width) / 2, size.height - 10f, labelPaint)

                // Y-axis label
                val yLabel = TextLine.make(data.yAxisLabel, font)
                drawTextLine(yLabel, 10f, padding - 10f, labelPaint)
            }

            // Draw scatter points for each series
            data.series.forEach { series ->
                series.points.forEach { point ->
                    val x = padding + ((point.x - minX) / xRange) * chartWidth
                    val y = size.height - padding - ((point.y - minY) / yRange) * chartHeight

                    // Draw point
                    drawCircle(
                        color = Color(series.color),
                        radius = 5f,
                        center = Offset(x, y),
                    )

                    // Draw point border
                    drawCircle(
                        color = backgroundColor,
                        radius = 5f,
                        center = Offset(x, y),
                        style = Stroke(width = 1.5f),
                    )
                }
            }

            // Draw legend (larger font)
            var legendY = padding + 20f
            drawContext.canvas.nativeCanvas.apply {
                val font = Font(null, 16f) // Increased from 14f
                data.series.forEach { series ->
                    val legendPaint = Paint().apply {
                        color = org.jetbrains.skia.Color.makeARGB(
                            ((series.color shr 24) and 0xFF).toInt(),
                            ((series.color shr 16) and 0xFF).toInt(),
                            ((series.color shr 8) and 0xFF).toInt(),
                            (series.color and 0xFF).toInt(),
                        )
                        mode = PaintMode.FILL
                    }

                    // Draw legend circle
                    val circleX = size.width - padding - 75f
                    val circleY = legendY - 1f
                    drawCircle(
                        circleX,
                        circleY,
                        5f,
                        legendPaint,
                    )

                    // Draw legend text
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

        // Zoom controls (vertical column)
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 1.2f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 0.8f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

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
            val padding = 60f
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
                drawTextLine(titleLine, titleX, 35f, titlePaint)
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

                    // Use custom label if provided, otherwise format numeric value
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
                drawTextLine(xLabel, (size.width - xLabel.width) / 2, size.height - 10f, labelPaint)

                val yLabel = TextLine.make(data.yAxisLabel, font)
                drawTextLine(yLabel, 10f, padding - 10f, labelPaint)
            }

            // Draw each series as filled area
            data.series.forEach { series ->
                val path = Path()

                // Start from bottom-left
                val firstPoint = series.points.first()
                val startX = padding + ((firstPoint.x - minX) / xRange) * chartWidth
                val startY = size.height - padding
                path.moveTo(startX, startY)

                // Draw line through points
                series.points.forEach { point ->
                    val x = padding + ((point.x - minX) / xRange) * chartWidth
                    val y = size.height - padding - ((point.y - minY) / yRange) * chartHeight
                    path.lineTo(x, y)
                }

                // Close path back to bottom
                val lastPoint = series.points.last()
                val endX = padding + ((lastPoint.x - minX) / xRange) * chartWidth
                path.lineTo(endX, size.height - padding)
                path.close()

                // Fill area
                drawPath(
                    path = path,
                    color = Color(series.color).copy(alpha = 0.3f),
                )

                // Draw border line
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
                        color = org.jetbrains.skia.Color.makeARGB(
                            ((series.color shr 24) and 0xFF).toInt(),
                            ((series.color shr 16) and 0xFF).toInt(),
                            ((series.color shr 8) and 0xFF).toInt(),
                            (series.color and 0xFF).toInt(),
                        )
                        mode = PaintMode.FILL
                    }

                    drawRect(
                        org.jetbrains.skia.Rect.makeXYWH(
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

        // Zoom controls
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 1.2f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 0.8f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

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
            val padding = 60f
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
                drawTextLine(titleLine, titleX, 35f, titlePaint)
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

                // Draw bin
                drawRect(
                    color = Color(data.color),
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(binWidth, binHeight),
                )

                // Draw bin border
                drawRect(
                    color = Color(data.color).copy(alpha = 0.3f),
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(binWidth, binHeight),
                    style = Stroke(width = 1f),
                )

                // Draw bin label (range)
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
                drawTextLine(xLabel, (size.width - xLabel.width) / 2, size.height - 10f, labelPaint)

                val yLabel = TextLine.make(data.yAxisLabel, font)
                drawTextLine(yLabel, 10f, padding - 10f, labelPaint)
            }
        }

        // Zoom controls
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 1.2f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 0.8f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

/**
 * Render a box plot chart using Skiko Canvas.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun boxPlotChart(
    data: BoxPlotData,
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
            val padding = 60f
            val chartWidth = size.width - 2 * padding
            val chartHeight = size.height - 2 * padding

            if (data.boxes.isEmpty()) return@Canvas

            val allValues = data.boxes.flatMap { listOf(it.min, it.max) + it.outliers }
            val minValue = allValues.minOrNull() ?: 0f
            val maxValue = allValues.maxOrNull() ?: 100f
            val valueRange = maxValue - minValue

            if (valueRange == 0f) return@Canvas

            // Draw title
            drawContext.canvas.nativeCanvas.apply {
                val titlePaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 24f)
                val titleLine = TextLine.make(data.title, font)
                val titleX = (size.width - titleLine.width) / 2
                drawTextLine(titleLine, titleX, 35f, titlePaint)
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

            // Calculate box width and spacing
            val boxWidth = (chartWidth / data.boxes.size) * 0.6f
            val boxSpacing = (chartWidth / data.boxes.size) * 0.4f

            // Draw each box plot
            data.boxes.forEachIndexed { index, box ->
                val centerX = padding + index * (boxWidth + boxSpacing) + boxSpacing / 2 + boxWidth / 2

                // Helper function to get Y coordinate for a value
                fun valueToY(value: Float): Float = size.height - padding - ((value - minValue) / valueRange) * chartHeight

                val minY = valueToY(box.min)
                val q1Y = valueToY(box.q1)
                val medianY = valueToY(box.median)
                val q3Y = valueToY(box.q3)
                val maxY = valueToY(box.max)

                // Draw whiskers
                drawLine(
                    color = Color(box.color),
                    start = Offset(centerX, minY),
                    end = Offset(centerX, q1Y),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = Color(box.color),
                    start = Offset(centerX, q3Y),
                    end = Offset(centerX, maxY),
                    strokeWidth = 2f,
                )

                // Draw whisker caps
                val capWidth = boxWidth * 0.3f
                drawLine(
                    color = Color(box.color),
                    start = Offset(centerX - capWidth / 2, minY),
                    end = Offset(centerX + capWidth / 2, minY),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = Color(box.color),
                    start = Offset(centerX - capWidth / 2, maxY),
                    end = Offset(centerX + capWidth / 2, maxY),
                    strokeWidth = 2f,
                )

                // Draw box (Q1 to Q3)
                val boxLeft = centerX - boxWidth / 2
                drawRect(
                    color = Color(box.color).copy(alpha = 0.3f),
                    topLeft = Offset(boxLeft, q3Y),
                    size = androidx.compose.ui.geometry.Size(boxWidth, q1Y - q3Y),
                )
                drawRect(
                    color = Color(box.color),
                    topLeft = Offset(boxLeft, q3Y),
                    size = androidx.compose.ui.geometry.Size(boxWidth, q1Y - q3Y),
                    style = Stroke(width = 2f),
                )

                // Draw median line
                drawLine(
                    color = Color(box.color),
                    start = Offset(boxLeft, medianY),
                    end = Offset(boxLeft + boxWidth, medianY),
                    strokeWidth = 3f,
                )

                // Draw outliers
                box.outliers.forEach { outlier ->
                    val outlierY = valueToY(outlier)
                    drawCircle(
                        color = Color(box.color),
                        radius = 3f,
                        center = Offset(centerX, outlierY),
                    )
                }

                // Draw box label
                drawContext.canvas.nativeCanvas.apply {
                    val labelPaint = Paint().apply {
                        color = textColor.toArgb()
                        mode = PaintMode.FILL
                    }
                    val font = Font(null, 15f)
                    val textLine = TextLine.make(box.label, font)
                    val textX = centerX - textLine.width / 2
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
                    val value = maxValue - (valueRange / 4) * i
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

                val xLabel = TextLine.make(data.xAxisLabel, font)
                drawTextLine(xLabel, (size.width - xLabel.width) / 2, size.height - 10f, labelPaint)

                val yLabel = TextLine.make(data.yAxisLabel, font)
                drawTextLine(yLabel, 10f, padding - 10f, labelPaint)
            }
        }

        // Zoom controls
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 1.2f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = {
                    scale = (scale * 0.8f).coerceIn(0.5f, 5f)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

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

            if (data.candles.isEmpty()) {
                return@Canvas
            }

            // Find price range
            val allPrices = data.candles.flatMap { listOf(it.high, it.low) }
            val minPrice = allPrices.minOrNull() ?: 0f
            val maxPrice = allPrices.maxOrNull() ?: 100f
            val priceRange = maxPrice - minPrice
            val yPadding = priceRange * 0.1f

            // Helper function to convert price to Y coordinate
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
                drawTextLine(title, (size.width - title.width) / 2, 30f, titlePaint)
            }

            // Calculate candlestick width
            val candleWidth = (chartWidth / data.candles.size) * 0.6f

            // Draw candlesticks
            data.candles.forEachIndexed { index, candle ->
                val x = padding + (chartWidth / data.candles.size) * (index + 0.5f)

                val highY = priceToY(candle.high)
                val lowY = priceToY(candle.low)
                val openY = priceToY(candle.open)
                val closeY = priceToY(candle.close)

                val isBullish = candle.close >= candle.open
                val candleColor = Color(if (isBullish) candle.colorUp else candle.colorDown)

                // Draw high-low line (wick)
                drawLine(
                    color = candleColor,
                    start = Offset(x, highY),
                    end = Offset(x, lowY),
                    strokeWidth = 1.5f,
                )

                // Draw open-close rectangle (body)
                val bodyTop = kotlin.math.min(openY, closeY)
                val bodyBottom = kotlin.math.max(openY, closeY)
                val bodyHeight = bodyBottom - bodyTop

                if (isBullish) {
                    // Hollow rectangle for bullish (close > open)
                    drawRect(
                        color = candleColor,
                        topLeft = Offset(x - candleWidth / 2, bodyTop),
                        size = androidx.compose.ui.geometry.Size(candleWidth, bodyHeight),
                        style = Stroke(width = 2f),
                    )
                } else {
                    // Filled rectangle for bearish (close < open)
                    drawRect(
                        color = candleColor,
                        topLeft = Offset(x - candleWidth / 2, bodyTop),
                        size = androidx.compose.ui.geometry.Size(candleWidth, bodyHeight),
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
                    if (index % kotlin.math.max(1, data.candles.size / 10) == 0 || index == data.candles.size - 1) {
                        val x = padding + (chartWidth / data.candles.size) * (index + 0.5f)
                        val label = data.xLabels?.get(candle.x) ?: candle.x.toInt().toString()
                        val textLine = TextLine.make(label, font)
                        drawTextLine(textLine, x - textLine.width / 2, size.height - padding + 20f, labelPaint)
                    }
                }
            }

            // Draw Y-axis labels (prices)
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
                drawTextLine(xLabel, (size.width - xLabel.width) / 2, size.height - 10f, labelPaint)

                val yLabel = TextLine.make(data.yAxisLabel, font)
                drawTextLine(yLabel, 10f, padding - 10f, labelPaint)
            }
        }

        // Zoom controls
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { scale = (scale * 1.2f).coerceIn(0.5f, 5f) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = { scale = (scale * 0.8f).coerceIn(0.5f, 5f) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

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
            val padding = 100f
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 2

            if (data.items.isEmpty()) {
                return@Canvas
            }

            // Calculate cumulative values
            val cumulativeValues = mutableListOf<Float>()
            var cumulative = 0f
            data.items.forEach { item ->
                if (!item.isTotal) {
                    cumulative += item.value
                }
                cumulativeValues.add(cumulative)
            }

            // Find value range
            val allValues = cumulativeValues + listOf(0f)
            val minValue = allValues.minOrNull() ?: 0f
            val maxValue = allValues.maxOrNull() ?: 100f
            val valueRange = maxValue - minValue
            val yPadding = valueRange * 0.1f

            // Helper function to convert value to Y coordinate
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

            // Draw zero line (baseline)
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
                drawTextLine(title, (size.width - title.width) / 2, 30f, titlePaint)
            }

            // Calculate bar width
            val barWidth = (chartWidth / data.items.size) * 0.7f
            val barSpacing = chartWidth / data.items.size

            // Track previous cumulative for drawing connectors
            var previousCumulative = 0f

            // Draw waterfall bars
            data.items.forEachIndexed { index, item ->
                val x = padding + barSpacing * (index + 0.5f)
                val currentCumulative = cumulativeValues[index]

                val barColor = when {
                    item.isTotal -> Color(item.colorTotal)
                    item.value > 0 -> Color(item.colorPositive)
                    else -> Color(item.colorNegative)
                }

                if (item.isTotal) {
                    // Total bar: from zero to cumulative
                    val barTop = valueToY(currentCumulative)
                    val barHeight = zeroY - barTop

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x - barWidth / 2, barTop),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    )
                } else {
                    // Regular bar: show the change
                    val startValue = previousCumulative
                    val endValue = currentCumulative

                    val barTop = valueToY(kotlin.math.max(startValue, endValue))
                    val barBottom = valueToY(kotlin.math.min(startValue, endValue))
                    val barHeight = barBottom - barTop

                    // Draw connecting line from previous bar
                    if (index > 0) {
                        val prevX = padding + barSpacing * (index - 0.5f)
                        drawLine(
                            color = gridColor,
                            start = Offset(prevX + barWidth / 2, valueToY(startValue)),
                            end = Offset(x - barWidth / 2, valueToY(startValue)),
                            strokeWidth = 1.5f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
                        )
                    }

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x - barWidth / 2, barTop),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    )
                }

                previousCumulative = currentCumulative
            }

            // Draw X-axis labels (item names)
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = Paint().apply {
                    color = textColor.toArgb()
                    mode = PaintMode.FILL
                }
                val font = Font(null, 12f)

                data.items.forEachIndexed { index, item ->
                    val x = padding + barSpacing * (index + 0.5f)
                    val textLine = TextLine.make(item.label, font)

                    // Rotate labels for better readability
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
                drawTextLine(xLabel, (size.width - xLabel.width) / 2, size.height - 10f, labelPaint)

                val yLabel = TextLine.make(data.yAxisLabel, font)
                drawTextLine(yLabel, 10f, padding - 10f, labelPaint)
            }
        }

        // Zoom controls
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { scale = (scale * 1.2f).coerceIn(0.5f, 5f) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = { scale = (scale * 0.8f).coerceIn(0.5f, 5f) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}
