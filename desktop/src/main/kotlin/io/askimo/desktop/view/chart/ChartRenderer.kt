/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.askimo.desktop.view.chart.renderers.areaChart
import io.askimo.desktop.view.chart.renderers.barChart
import io.askimo.desktop.view.chart.renderers.boxPlotChart
import io.askimo.desktop.view.chart.renderers.candlestickChart
import io.askimo.desktop.view.chart.renderers.histogramChart
import io.askimo.desktop.view.chart.renderers.lineChart
import io.askimo.desktop.view.chart.renderers.pieChart
import io.askimo.desktop.view.chart.renderers.scatterChart
import io.askimo.desktop.view.chart.renderers.waterfallChart
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
