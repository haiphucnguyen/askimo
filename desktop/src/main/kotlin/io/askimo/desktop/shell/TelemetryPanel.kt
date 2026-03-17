/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.askimo.core.context.AppContext
import io.askimo.core.telemetry.TelemetryMetrics
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.ui.themedTooltip
import io.askimo.desktop.util.formatDuration
import io.askimo.desktop.util.formatDurationDetailed
import org.koin.java.KoinJavaComponent.get
import java.util.Locale.getDefault

/**
 * Telemetry panel showing RAG and LLM metrics.
 * Max height is limited to 1/3 of parent height with scrolling support.
 */
@Composable
internal fun telemetryPanel(metrics: TelemetryMetrics, maxHeight: Dp) {
    val appContext = remember { get<AppContext>(AppContext::class.java) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header with title and reset button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource("telemetry.title"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )

                    // Only show reset button if there's data
                    if (metrics.ragClassificationTotal > 0 || metrics.llmCallsByProvider.isNotEmpty()) {
                        themedTooltip(
                            text = stringResource("telemetry.reset"),
                        ) {
                            IconButton(
                                onClick = { appContext.telemetry.reset() },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource("telemetry.reset"),
                                    modifier = Modifier.size(16.dp).pointerHoverIcon(PointerIcon.Hand),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                if (metrics.ragClassificationTotal == 0 && metrics.llmCallsByProvider.isEmpty()) {
                    Text(
                        text = stringResource("telemetry.no.data"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    return@Column
                }

                if (metrics.ragClassificationTotal > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        telemetryMetricCard(
                            label = stringResource("telemetry.rag.efficiency"),
                            value = "${String.format("%.0f", metrics.ragTriggeredPercent)}%",
                            subtitle = stringResource("telemetry.rag.triggered", metrics.ragTriggered, metrics.ragClassificationTotal),
                            modifier = Modifier.weight(1f),
                        )
                        telemetryMetricCard(
                            label = stringResource("telemetry.classification"),
                            value = formatDuration(metrics.ragAvgClassificationTimeMs),
                            valueTooltip = formatDurationDetailed(metrics.ragAvgClassificationTimeMs),
                            subtitle = stringResource("telemetry.classification.time"),
                            modifier = Modifier.weight(1f),
                        )
                        if (metrics.ragRetrievalTotal > 0) {
                            telemetryMetricCard(
                                label = stringResource("telemetry.retrieval"),
                                value = formatDuration(metrics.ragAvgRetrievalTimeMs),
                                valueTooltip = formatDurationDetailed(metrics.ragAvgRetrievalTimeMs),
                                subtitle = stringResource("telemetry.retrieval.chunks", String.format("%.1f", metrics.ragAvgChunksRetrieved)),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (metrics.llmCallsByProvider.isNotEmpty()) {
                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource("telemetry.llm.calls"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        metrics.llmCallsByProvider.forEach { (providerModel, calls) ->
                            val tokens = metrics.llmTokensByProvider[providerModel] ?: 0L
                            val avgDuration = metrics.llmAvgDurationMsByProvider[providerModel] ?: 0L
                            val errors = metrics.llmErrorsByProvider[providerModel] ?: 0

                            telemetryProviderRow(
                                providerModel = providerModel,
                                calls = calls,
                                tokens = tokens,
                                avgDurationMs = avgDuration,
                                errors = errors,
                            )
                        }
                    }

                    // Total Summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource("telemetry.total.tokens", metrics.totalTokensUsed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Vertical scrollbar
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 4.dp),
                adapter = rememberScrollbarAdapter(scrollState),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun telemetryMetricCard(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueTooltip: String? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (valueTooltip != null) {
                TooltipArea(
                    tooltip = {
                        Surface(
                            modifier = Modifier.padding(4.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = valueTooltip,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                            )
                        }
                    },
                    delayMillis = 300,
                    tooltipPlacement = TooltipPlacement.CursorPoint(
                        offset = DpOffset(0.dp, 16.dp),
                    ),
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun telemetryProviderRow(
    providerModel: String,
    calls: Int,
    tokens: Long,
    avgDurationMs: Long,
    errors: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = providerModel.split(":").joinToString(" • ") {
                it.replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase(getDefault()) else c.toString()
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource("telemetry.llm.calls.count", calls),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource("telemetry.llm.tokens", tokens),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TooltipArea(
                tooltip = {
                    Surface(
                        modifier = Modifier.padding(4.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = formatDurationDetailed(avgDurationMs),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                },
                delayMillis = 300,
                tooltipPlacement = TooltipPlacement.CursorPoint(
                    offset = DpOffset(0.dp, 16.dp),
                ),
            ) {
                Text(
                    text = formatDuration(avgDurationMs),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (errors > 0) {
                Text(
                    text = stringResource("telemetry.llm.errors", errors),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
