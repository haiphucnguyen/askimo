/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.tools.chart

import dev.langchain4j.agent.tool.Tool
import io.askimo.tools.ToolResponseBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Tools for generating charts and visualizations.
 * These tools allow AI to create data visualizations in response to user requests.
 */
object ChartTools {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Generate a line chart for showing trends over time or continuous data.
     *
     * @param title Chart title
     * @param xAxisLabel Label for X-axis
     * @param yAxisLabel Label for Y-axis
     * @param seriesData JSON string containing series data: [{"name": "Series1", "points": [{"x": 1, "y": 10}], "color": 4284513675}]
     * @return JSON response with chart data
     *
     * @usage
     * ```
     * generateLineChart(
     *   title = "Revenue Trend 2024",
     *   xAxisLabel = "Quarter",
     *   yAxisLabel = "Revenue ($M)",
     *   seriesData = '[{"name":"Revenue","points":[{"x":1,"y":12.5},{"x":2,"y":15.2}],"color":4284513675}]'
     * )
     * ```
     *
     * @errors
     * - Invalid JSON in seriesData
     * - Missing required fields
     * - Invalid color format (must be ARGB long)
     */
    @Tool(
        """
        Generate a line chart to visualize trends, time series, or continuous data.
        Use this when the user asks to visualize trends, changes over time, or relationships between variables.
        Line charts are ideal for showing changes over time, trends, and continuous data patterns.

        Return the chart specification as a JSON code block in markdown format:
        ```json
        {
          "title": "Revenue Trend 2024",
          "xAxisLabel": "Quarter",
          "yAxisLabel": "Revenue (in millions)",
          "seriesData": [
            {
              "name": "Revenue",
              "points": [
                {"x": 1, "y": 12.5},
                {"x": 2, "y": 15.2},
                {"x": 3, "y": 18.0},
                {"x": 4, "y": 20.1}
              ],
              "color": 4284513675
            }
          ]
        }
        ```

        Each series object contains:
        - name: Series name displayed in legend (string)
        - points: Array of coordinate objects with x and y values (numbers)
        - color: ARGB color value (optional, defaults to blue 4284513675)

        Supports multiple series for comparison on the same chart.

        Standard colors: Blue=4284513675, Red=4294901760, Green=4278255360, Orange=4294951424
        """,
    )
    fun generateLineChart(
        title: String,
        xAxisLabel: String,
        yAxisLabel: String,
        seriesData: String,
    ): String = try {
        val seriesList = json.decodeFromString<List<LineChartData.Series>>(seriesData)

        require(seriesList.isNotEmpty()) { "At least one series is required" }
        require(title.isNotBlank()) { "Title is required" }

        val chartData = LineChartData(
            title = title,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            series = seriesList,
        )

        val chartJson = json.encodeToJsonElement(chartData).jsonObject

        ToolResponseBuilder.successWithData(
            output = "Generated line chart: '$title' with ${seriesList.size} series",
            data = mapOf(
                "chartData" to chartJson.toString(),
                "type" to "line",
                "seriesCount" to seriesList.size,
                "totalPoints" to seriesList.sumOf { it.points.size },
            ),
        )
    } catch (e: Exception) {
        ToolResponseBuilder.failure(
            error = "Failed to generate line chart: ${e.message}",
            metadata = mapOf(
                "title" to title,
                "exception" to (e::class.simpleName ?: "Exception"),
            ),
        )
    }

    /**
     * Generate a bar chart for comparing discrete categories or values.
     *
     * @param title Chart title
     * @param xAxisLabel Label for X-axis (categories)
     * @param yAxisLabel Label for Y-axis (values)
     * @param barsData JSON string containing bar data: [{"label": "Q1", "value": 12.5, "color": 4284513675}]
     * @return JSON response with chart data
     *
     * @usage
     * ```
     * generateBarChart(
     *   title = "Quarterly Revenue",
     *   xAxisLabel = "Quarter",
     *   yAxisLabel = "Revenue ($M)",
     *   barsData = '[{"label":"Q1","value":12.5},{"label":"Q2","value":15.2}]'
     * )
     * ```
     *
     * @errors
     * - Invalid JSON in barsData
     * - Missing required fields
     * - Negative values in value field
     */
    @Tool(
        """
        Generate a bar chart to compare discrete categories or values.
        Use this when the user asks to compare different items, categories, or time periods.
        Bar charts are ideal for comparing quantities across different categories or showing rankings.

        Return the chart specification as a JSON code block in markdown format:
        ```json
        {
          "title": "Quarterly Revenue 2024",
          "xAxisLabel": "Quarter",
          "yAxisLabel": "Revenue (in millions)",
          "barsData": [
            {"label": "Q1", "value": 12.5, "color": 4284513675},
            {"label": "Q2", "value": 15.2, "color": 4278255360},
            {"label": "Q3", "value": 18.0, "color": 4294951424},
            {"label": "Q4", "value": 20.1, "color": 4294901760}
          ]
        }
        ```

        Each bar object contains:
        - label: Category or bar label (string)
        - value: Numeric value to display (number, must be >= 0)
        - color: ARGB color value (long integer)

        Standard colors: Blue=4284513675, Green=4278255360, Orange=4294951424, Red=4294901760
        """,
    )
    fun generateBarChart(
        title: String,
        xAxisLabel: String,
        yAxisLabel: String,
        barsData: String,
    ): String = try {
        val barsList = json.decodeFromString<List<BarChartData.Bar>>(barsData)

        require(barsList.isNotEmpty()) { "At least one bar is required" }
        require(title.isNotBlank()) { "Title is required" }

        val chartData = BarChartData(
            title = title,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            bars = barsList,
        )

        val chartJson = json.encodeToJsonElement(chartData).jsonObject

        ToolResponseBuilder.successWithData(
            output = "Generated bar chart: '$title' with ${barsList.size} bars",
            data = mapOf(
                "chartData" to chartJson.toString(),
                "type" to "bar",
                "barCount" to barsList.size,
                "maxValue" to (barsList.maxOfOrNull { it.value } ?: 0f),
                "minValue" to (barsList.minOfOrNull { it.value } ?: 0f),
            ),
        )
    } catch (e: Exception) {
        ToolResponseBuilder.failure(
            error = "Failed to generate bar chart: ${e.message}",
            metadata = mapOf(
                "title" to title,
                "exception" to (e::class.simpleName ?: "Exception"),
            ),
        )
    }

    /**
     * Generate a pie chart for showing proportions or percentages.
     *
     * @param title Chart title
     * @param slicesData JSON string containing slice data: [{"label": "Category A", "value": 35.5, "color": 4284513675}]
     * @return JSON response with chart data
     *
     * @usage
     * ```
     * generatePieChart(
     *   title = "Market Share 2024",
     *   slicesData = '[{"label":"Product A","value":35.5,"color":4284513675},{"label":"Product B","value":28.3,"color":4294901760}]'
     * )
     * ```
     *
     * @errors
     * - Invalid JSON in slicesData
     * - Missing required fields
     * - Negative values in value field
     */
    @Tool(
        """
        Generate a pie chart to show proportions, percentages, or distribution of a whole.
        Use this when the user asks to visualize parts of a whole, market share, or percentage distribution.
        Pie charts are ideal for showing relative proportions where all parts sum to 100%.

        Return the chart specification as a JSON code block in markdown format:
        ```json
        {
          "title": "Market Share 2024",
          "slicesData": [
            {"label": "Product A", "value": 35.5, "color": 4284513675},
            {"label": "Product B", "value": 28.3, "color": 4294901760},
            {"label": "Product C", "value": 22.0, "color": 4278255360},
            {"label": "Other", "value": 14.2, "color": 4294951424}
          ]
        }
        ```

        Each slice object contains:
        - label: Slice label shown in legend (string)
        - value: Numeric value or percentage (number, must be >= 0)
        - color: ARGB color value (long integer, required)

        The chart automatically calculates percentages and displays them in the legend.

        Standard colors: Blue=4284513675, Red=4294901760, Green=4278255360, Orange=4294951424
        """,
    )
    fun generatePieChart(
        title: String,
        slicesData: String,
    ): String = try {
        val slicesList = json.decodeFromString<List<PieChartData.Slice>>(slicesData)

        require(slicesList.isNotEmpty()) { "At least one slice is required" }
        require(title.isNotBlank()) { "Title is required" }

        val chartData = PieChartData(
            title = title,
            slices = slicesList,
        )

        val chartJson = json.encodeToJsonElement(chartData).jsonObject
        val total = slicesList.sumOf { it.value.toDouble() }

        ToolResponseBuilder.successWithData(
            output = "Generated pie chart: '$title' with ${slicesList.size} slices",
            data = mapOf(
                "chartData" to chartJson.toString(),
                "type" to "pie",
                "sliceCount" to slicesList.size,
                "total" to total,
            ),
        )
    } catch (e: Exception) {
        ToolResponseBuilder.failure(
            error = "Failed to generate pie chart: ${e.message}",
            metadata = mapOf(
                "title" to title,
                "exception" to (e::class.simpleName ?: "Exception"),
            ),
        )
    }

    /**
     * Generate a scatter plot for showing correlations or distributions.
     *
     * @param title Chart title
     * @param xAxisLabel Label for X-axis
     * @param yAxisLabel Label for Y-axis
     * @param seriesData JSON string containing series data (same format as line chart)
     * @return JSON response with chart data
     *
     * @usage
     * ```
     * generateScatterPlot(
     *   title = "Price vs Sales Correlation",
     *   xAxisLabel = "Price ($)",
     *   yAxisLabel = "Units Sold",
     *   seriesData = '[{"name":"Products","points":[{"x":10,"y":500},{"x":15,"y":350}],"color":4284513675}]'
     * )
     * ```
     *
     * @errors
     * - Invalid JSON in seriesData
     * - Missing required fields
     */
    @Tool(
        """
        Generate a scatter plot to show correlations, distributions, or relationships between two variables.
        Use this when the user asks to visualize correlation, data distribution, or relationship between two metrics.
        Scatter plots are ideal for finding patterns, correlations, clusters, or outliers in data.

        Return the chart specification as a JSON code block in markdown format:
        ```json
        {
          "title": "Example Scatter Plot: Price vs Sales",
          "xAxisLabel": "Price (dollars)",
          "yAxisLabel": "Units Sold",
          "seriesData": [
            {
              "name": "Products",
              "points": [
                {"x": 10, "y": 500},
                {"x": 15, "y": 350},
                {"x": 20, "y": 200},
                {"x": 25, "y": 150},
                {"x": 30, "y": 100}
              ],
              "color": 4284513675
            }
          ]
        }
        ```

        Each series object contains:
        - name: Series name shown in legend (string)
        - points: Array of coordinate objects with x and y values (numbers)
        - color: ARGB color value (optional, defaults to blue 4284513675)

        Supports multiple series to show different datasets or categories on the same plot.
        Points are displayed as circles without connecting lines.

        Standard colors: Blue=4284513675, Red=4294901760, Green=4278255360, Orange=4294951424
        """,
    )
    fun generateScatterPlot(
        title: String,
        xAxisLabel: String,
        yAxisLabel: String,
        seriesData: String,
    ): String = try {
        val seriesList = json.decodeFromString<List<LineChartData.Series>>(seriesData)

        require(seriesList.isNotEmpty()) { "At least one series is required" }
        require(title.isNotBlank()) { "Title is required" }

        val chartData = ScatterChartData(
            title = title,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            series = seriesList,
        )

        val chartJson = json.encodeToJsonElement(chartData).jsonObject

        ToolResponseBuilder.successWithData(
            output = "Generated scatter plot: '$title' with ${seriesList.size} series",
            data = mapOf(
                "chartData" to chartJson.toString(),
                "type" to "scatter",
                "seriesCount" to seriesList.size,
                "totalPoints" to seriesList.sumOf { it.points.size },
            ),
        )
    } catch (e: Exception) {
        ToolResponseBuilder.failure(
            error = "Failed to generate scatter plot: ${e.message}",
            metadata = mapOf(
                "title" to title,
                "exception" to (e::class.simpleName ?: "Exception"),
            ),
        )
    }

    /**
     * Generate an area chart for showing volume/magnitude trends over time.
     *
     * @param title Chart title
     * @param xAxisLabel Label for X-axis
     * @param yAxisLabel Label for Y-axis
     * @param seriesData JSON string containing series data (same format as line chart)
     * @return JSON response with chart data
     *
     * @usage Similar to line chart but with filled areas below lines
     */
    @Tool(
        """
        Generate an area chart to visualize volume, magnitude, or cumulative trends over time.
        Use this when you want to show the magnitude of change, cumulative totals, or emphasize volume.
        Area charts are ideal for showing totals, cumulative values, or when the area under the curve is meaningful.

        Return the chart specification as a JSON code block in markdown format:
        ```json
        {
          "title": "Chart Title",
          "xAxisLabel": "X Label",
          "yAxisLabel": "Y Label",
          "seriesData": [
            {
              "name": "Series Name",
              "points": [{"x": 1, "y": 100}, {"x": 2, "y": 150}],
              "color": 4284513675
            }
          ]
        }
        ```
        """,
    )
    fun generateAreaChart(
        title: String,
        xAxisLabel: String,
        yAxisLabel: String,
        seriesData: String,
    ): String = try {
        val seriesList = json.decodeFromString<List<LineChartData.Series>>(seriesData)

        val chartData = AreaChartData(
            title = title,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            series = seriesList,
        )

        val chartJson = json.encodeToJsonElement(chartData).jsonObject

        ToolResponseBuilder.successWithData(
            output = "Generated area chart: '$title' with ${seriesList.size} series",
            data = mapOf(
                "chartData" to chartJson.toString(),
                "type" to "area",
                "seriesCount" to seriesList.size,
                "totalPoints" to seriesList.sumOf { it.points.size },
            ),
        )
    } catch (e: Exception) {
        ToolResponseBuilder.failure(
            error = "Failed to generate area chart: ${e.message}",
            metadata = mapOf(
                "title" to title,
                "exception" to (e::class.simpleName ?: "Exception"),
            ),
        )
    }

    /**
     * Generate a histogram to show distribution of numerical data.
     *
     * @param title Chart title
     * @param xAxisLabel Label for X-axis (typically the data range)
     * @param yAxisLabel Label for Y-axis (typically "Frequency" or "Count")
     * @param binsData JSON string containing bins: [{"rangeStart": 0, "rangeEnd": 10, "count": 5}]
     * @param color Optional color (ARGB long)
     * @return JSON response with chart data
     *
     * @usage
     * ```
     * generateHistogram(
     *   title = "Age Distribution",
     *   xAxisLabel = "Age",
     *   yAxisLabel = "Count",
     *   binsData = '[{"rangeStart":0,"rangeEnd":20,"count":15},{"rangeStart":20,"rangeEnd":40,"count":25}]',
     *   color = 4284513675
     * )
     * ```
     */
    @Tool(
        """
        Generate a histogram to visualize the distribution or frequency of numerical data.
        Use this when the user wants to see how data is distributed across different ranges or bins.
        Histograms are perfect for showing frequency distributions, statistical patterns, or data distributions.

        Return the chart specification as a JSON code block in markdown format:
        ```json
        {
          "title": "Distribution Title",
          "xAxisLabel": "Value Range",
          "yAxisLabel": "Frequency",
          "binsData": [
            {"rangeStart": 0, "rangeEnd": 10, "count": 15},
            {"rangeStart": 10, "rangeEnd": 20, "count": 25},
            {"rangeStart": 20, "rangeEnd": 30, "count": 18}
          ],
          "color": 4284513675
        }
        ```

        Each bin represents a range of values and how many data points fall in that range.
        """,
    )
    fun generateHistogram(
        title: String,
        xAxisLabel: String,
        yAxisLabel: String,
        binsData: String,
        color: Long = HistogramData.DEFAULT_BLUE,
    ): String = try {
        val bins = json.decodeFromString<List<HistogramData.Bin>>(binsData)

        val chartData = HistogramData(
            title = title,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            bins = bins,
            color = color,
        )

        val chartJson = json.encodeToJsonElement(chartData).jsonObject

        ToolResponseBuilder.successWithData(
            output = "Generated histogram: '$title' with ${bins.size} bins",
            data = mapOf(
                "chartData" to chartJson.toString(),
                "type" to "histogram",
                "binCount" to bins.size,
                "totalCount" to bins.sumOf { it.count.toInt() },
            ),
        )
    } catch (e: Exception) {
        ToolResponseBuilder.failure(
            error = "Failed to generate histogram: ${e.message}",
            metadata = mapOf(
                "title" to title,
                "exception" to (e::class.simpleName ?: "Exception"),
            ),
        )
    }

    /**
     * Generate a box plot to show statistical distribution with quartiles.
     *
     * @param title Chart title
     * @param xAxisLabel Label for X-axis (category names)
     * @param yAxisLabel Label for Y-axis (value range)
     * @param boxesData JSON string containing boxes: [{"label":"A","min":10,"q1":20,"median":25,"q3":30,"max":40,"outliers":[5,45],"color":4284513675}]
     * @return JSON response with chart data
     *
     * @usage
     * ```
     * generateBoxPlot(
     *   title = "Test Score Distribution by Class",
     *   xAxisLabel = "Class",
     *   yAxisLabel = "Score",
     *   boxesData = '[{"label":"Class A","min":60,"q1":70,"median":80,"q3":85,"max":95,"outliers":[55],"color":4284513675}]'
     * )
     * ```
     */
    @Tool(
        """
        Generate a box plot (box-and-whisker chart) to show statistical distribution of data.
        Use this when the user wants to compare distributions, show quartiles, medians, or identify outliers.
        Box plots are ideal for statistical analysis, comparing multiple distributions, or showing data spread.

        Return the chart specification as a JSON code block in markdown format:
        ```json
        {
          "title": "Distribution Comparison",
          "xAxisLabel": "Category",
          "yAxisLabel": "Value",
          "boxesData": [
            {
              "label": "Group A",
              "min": 10,
              "q1": 20,
              "median": 25,
              "q3": 30,
              "max": 40,
              "outliers": [5, 45],
              "color": 4284513675
            }
          ]
        }
        ```

        Where:
        - min: Minimum value (lower whisker)
        - q1: First quartile (25th percentile, bottom of box)
        - median: Median value (50th percentile, line in box)
        - q3: Third quartile (75th percentile, top of box)
        - max: Maximum value (upper whisker)
        - outliers: Array of outlier values (optional)
        """,
    )
    fun generateBoxPlot(
        title: String,
        xAxisLabel: String,
        yAxisLabel: String,
        boxesData: String,
    ): String = try {
        val boxes = json.decodeFromString<List<BoxPlotData.Box>>(boxesData)

        val chartData = BoxPlotData(
            title = title,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            boxes = boxes,
        )

        val chartJson = json.encodeToJsonElement(chartData).jsonObject

        ToolResponseBuilder.successWithData(
            output = "Generated box plot: '$title' with ${boxes.size} boxes",
            data = mapOf(
                "chartData" to chartJson.toString(),
                "type" to "boxplot",
                "boxCount" to boxes.size,
                "totalOutliers" to boxes.sumOf { it.outliers.size },
            ),
        )
    } catch (e: Exception) {
        ToolResponseBuilder.failure(
            error = "Failed to generate box plot: ${e.message}",
            metadata = mapOf(
                "title" to title,
                "exception" to (e::class.simpleName ?: "Exception"),
            ),
        )
    }
}
