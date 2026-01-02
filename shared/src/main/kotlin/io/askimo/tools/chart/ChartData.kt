/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.tools.chart

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base interface for all chart data types.
 */
@Serializable
sealed interface ChartData {
    val title: String
    val xAxisLabel: String
    val yAxisLabel: String
}

/**
 * Data for line and scatter charts.
 */
@Serializable
@SerialName("line")
data class LineChartData(
    override val title: String,
    override val xAxisLabel: String,
    override val yAxisLabel: String,
    val series: List<Series>,
) : ChartData {
    @Serializable
    data class Series(
        val name: String,
        val points: List<Point>,
        val color: Long = DEFAULT_BLUE,
    )

    @Serializable
    data class Point(
        val x: Float,
        val y: Float,
    )

    companion object {
        const val DEFAULT_BLUE = 0xFF2196F3L
        const val DEFAULT_RED = 0xFFF44336L
        const val DEFAULT_GREEN = 0xFF4CAF50L
        const val DEFAULT_ORANGE = 0xFFFF9800L
        const val DEFAULT_PURPLE = 0xFF9C27B0L
    }
}

/**
 * Data for bar charts.
 */
@Serializable
@SerialName("bar")
data class BarChartData(
    override val title: String,
    override val xAxisLabel: String,
    override val yAxisLabel: String,
    val bars: List<Bar>,
) : ChartData {
    @Serializable
    data class Bar(
        val label: String,
        val value: Float,
        val color: Long = LineChartData.DEFAULT_BLUE,
    )
}

/**
 * Data for pie charts.
 */
@Serializable
@SerialName("pie")
data class PieChartData(
    override val title: String,
    override val xAxisLabel: String = "",
    override val yAxisLabel: String = "",
    val slices: List<Slice>,
) : ChartData {
    @Serializable
    data class Slice(
        val label: String,
        val value: Float,
        val color: Long,
    )
}

/**
 * Data for scatter plots (uses LineChartData structure).
 */
@Serializable
@SerialName("scatter")
data class ScatterChartData(
    override val title: String,
    override val xAxisLabel: String,
    override val yAxisLabel: String,
    val series: List<LineChartData.Series>,
) : ChartData

/**
 * Data for area charts.
 */
@Serializable
@SerialName("area")
data class AreaChartData(
    override val title: String,
    override val xAxisLabel: String,
    override val yAxisLabel: String,
    val series: List<LineChartData.Series>,
) : ChartData

/**
 * Data for histogram charts.
 */
@Serializable
@SerialName("histogram")
data class HistogramData(
    override val title: String,
    override val xAxisLabel: String,
    override val yAxisLabel: String,
    val bins: List<Bin>,
    val color: Long = DEFAULT_BLUE,
) : ChartData {
    @Serializable
    data class Bin(
        val rangeStart: Float,
        val rangeEnd: Float,
        val count: Float,
    )

    companion object {
        const val DEFAULT_BLUE = 0xFF2196F3L
    }
}

/**
 * Data for box plot charts.
 */
@Serializable
@SerialName("boxplot")
data class BoxPlotData(
    override val title: String,
    override val xAxisLabel: String,
    override val yAxisLabel: String,
    val boxes: List<Box>,
) : ChartData {
    @Serializable
    data class Box(
        val label: String,
        val min: Float,
        val q1: Float,
        val median: Float,
        val q3: Float,
        val max: Float,
        val outliers: List<Float> = emptyList(),
        val color: Long = DEFAULT_BLUE,
    )

    companion object {
        const val DEFAULT_BLUE = 0xFF2196F3L
    }
}
