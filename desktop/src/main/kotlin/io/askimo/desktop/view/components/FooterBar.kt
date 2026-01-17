/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import io.askimo.core.VersionInfo
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.getConfigInfo
import io.askimo.core.event.Event
import io.askimo.core.event.EventBus
import io.askimo.core.event.EventSource
import io.askimo.core.event.internal.ModelChangedEvent
import io.askimo.core.event.system.UpdateAvailableEvent
import io.askimo.core.event.user.IndexingCompletedEvent
import io.askimo.core.event.user.IndexingFailedEvent
import io.askimo.core.event.user.IndexingInProgressEvent
import io.askimo.core.event.user.IndexingStartedEvent
import io.askimo.core.telemetry.TelemetryMetrics
import io.askimo.core.util.TimeUtil.formatInstantDisplay
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.monitoring.SystemResourceMonitor
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.util.formatDuration
import io.askimo.desktop.util.formatDurationDetailed
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import java.awt.Desktop
import java.net.URI

@Composable
private fun aiConfigInfo(onConfigureAiProvider: () -> Unit) {
    val appContext = remember { get<AppContext>(AppContext::class.java) }
    var configInfo by remember { mutableStateOf(appContext.getConfigInfo()) }

    LaunchedEffect(Unit) {
        EventBus.internalEvents.collect { event ->
            if (event is ModelChangedEvent) {
                configInfo = appContext.getConfigInfo()
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Provider button - click to configure
        providerButton(
            currentProvider = configInfo.provider,
            onConfigureProvider = onConfigureAiProvider,
        )

        // Model dropdown - quick switch models
        modelDropdown(
            currentProvider = configInfo.provider,
            currentModel = configInfo.model,
            onModelSelected = { newModel ->
                val appContext = get<AppContext>(AppContext::class.java)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        appContext.params.model = newModel
                        io.askimo.core.context.AppContextConfigManager.save(appContext.params)
                        EventBus.emit(ModelChangedEvent(configInfo.provider, newModel))
                    } catch (_: Exception) {
                        // Silently handle error
                    }
                }
            },
        )
    }
}

@Composable
private fun providerButton(
    currentProvider: io.askimo.core.providers.ModelProvider,
    onConfigureProvider: () -> Unit,
) {
    themedTooltip(
        text = stringResource("system.ai.provider.tooltip", currentProvider.name),
    ) {
        Card(
            modifier = Modifier
                .clickableCard { onConfigureProvider() }
                .widthIn(min = 100.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = currentProvider.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun modelDropdown(
    currentProvider: io.askimo.core.providers.ModelProvider,
    currentModel: String,
    onModelSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val appContext = remember { get<AppContext>(AppContext::class.java) }
    val scope = rememberCoroutineScope()

    // Load models when provider changes or when dropdown is opened
    LaunchedEffect(currentProvider, expanded) {
        if (expanded && availableModels.isEmpty()) {
            isLoading = true
            scope.launch {
                try {
                    val settings = appContext.getOrCreateProviderSettings(currentProvider)
                    val factory = appContext.getModelFactory(currentProvider)
                    if (factory != null) {
                        @Suppress("UNCHECKED_CAST")
                        val models = (factory as io.askimo.core.providers.ChatModelFactory<io.askimo.core.providers.ProviderSettings>)
                            .availableModels(settings)
                        availableModels = models
                    }
                } catch (_: Exception) {
                    availableModels = emptyList()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Reset models when provider changes
    LaunchedEffect(currentProvider) {
        availableModels = emptyList()
    }

    Box {
        Card(
            modifier = Modifier
                .clickableCard { expanded = true }
                .widthIn(min = 120.dp, max = 250.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentModel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ComponentColors.themedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            when {
                isLoading -> {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = stringResource("settings.model.loading"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        onClick = {},
                        enabled = false,
                    )
                }
                availableModels.isEmpty() -> {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource("settings.model.none"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                }
                else -> {
                    availableModels.forEach { model ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    text = model,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                onModelSelected(model)
                                expanded = false
                            },
                            leadingIcon = if (model == currentModel) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Current model",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Footer bar component showing system resources, notifications, and version info.
 */
@Composable
fun footerBar(
    onShowUpdateDetails: () -> Unit = {},
    onConfigureAiProvider: () -> Unit = {},
) {
    val resourceMonitor = remember { get<SystemResourceMonitor>(SystemResourceMonitor::class.java) }
    val appContext = remember { get<AppContext>(AppContext::class.java) }
    val scope = rememberCoroutineScope()

    val memoryUsage by resourceMonitor.memoryUsageMB.collectAsState()
    val cpuUsage by resourceMonitor.cpuUsagePercent.collectAsState()
    val metrics by appContext.telemetry.metricsFlow.collectAsState()
    var telemetryExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            resourceMonitor.startMonitoring(intervalMillis = 2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComponentColors.sidebarSurfaceColor()),
    ) {
        // Top border
        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            themedTooltip(
                text = stringResource("system.resources.tooltip", memoryUsage.toString(), "%.1f".format(cpuUsage)),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource("system.memory") + ":",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "$memoryUsage MB",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource("system.cpu") + ":",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "%.1f%%".format(cpuUsage),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            aiConfigInfo(onConfigureAiProvider = onConfigureAiProvider)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                themedTooltip(
                    text = if (telemetryExpanded) {
                        stringResource("telemetry.hide")
                    } else {
                        stringResource("telemetry.show")
                    },
                ) {
                    IconButton(
                        onClick = { telemetryExpanded = !telemetryExpanded },
                        modifier = Modifier
                            .size(28.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = if (telemetryExpanded) {
                                stringResource("telemetry.hide")
                            } else {
                                stringResource("telemetry.show")
                            },
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                TextButton(
                    onClick = {
                        try {
                            Desktop.getDesktop().browse(URI("https://github.com/haiphucnguyen/askimo/issues"))
                        } catch (e: Exception) {
                            // Silently fail if unable to open browser
                        }
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(
                        text = stringResource("system.share.feedback"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                notificationIcon(onShowUpdateDetails = onShowUpdateDetails)

                Text(
                    text = "v${VersionInfo.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (telemetryExpanded) {
            HorizontalDivider()
            // Use a fixed max height of 250dp (approximately 1/3 of typical 800px window)
            telemetryPanel(metrics, 250.dp)
        }
    }
}

/**
 * Notification icon in bottom bar that shows user events only.
 * Developer events are excluded and shown in the Event Log dialog instead.
 */
@Composable
private fun notificationIcon(onShowUpdateDetails: () -> Unit) {
    var showEventPopup by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf<Event>() }
    var unreadCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        EventBus.userEvents.collect { event ->
            // Skip IndexingInProgressEvent unless developer mode is enabled and active
            if (event is IndexingInProgressEvent) {
                if (!(AppConfig.developer.enabled && AppConfig.developer.active)) {
                    return@collect // Skip this event
                }
            }

            // Special handling for IndexingInProgressEvent: replace old progress event for same project
            if (event is IndexingInProgressEvent) {
                // Find and remove any existing progress event for the same project
                val existingIndex = events.indexOfFirst {
                    it is IndexingInProgressEvent && it.projectId == event.projectId
                }
                if (existingIndex >= 0) {
                    events[existingIndex] = event
                } else {
                    events.add(0, event)
                    unreadCount++
                }
            } else {
                // New event - add and increment unread
                events.add(0, event)
                unreadCount++
            }

            // Keep list size manageable
            if (events.size > 100) {
                events.removeAt(100)
            }
        }
    }

    Box {
        IconButton(
            onClick = {
                showEventPopup = !showEventPopup
            },
            modifier = Modifier
                .size(32.dp)
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge(
                            modifier = Modifier
                                .widthIn(min = 20.dp)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                            )
                        }
                    }
                },
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Events",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Event popup
        if (showEventPopup) {
            Popup(
                alignment = Alignment.BottomEnd,
                offset = IntOffset(0, -40),
                onDismissRequest = { showEventPopup = false },
            ) {
                Card(
                    modifier = Modifier.padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    eventPopupContent(
                        events = events,
                        onShowUpdateDetails = onShowUpdateDetails,
                        onDismissPopup = { showEventPopup = false },
                        onRemoveEvent = { event ->
                            events.remove(event)
                            if (unreadCount > 0) {
                                unreadCount--
                            }
                        },
                        onClearAll = {
                            events.clear()
                            unreadCount = 0
                        },
                    )
                }
            }
        }
    }
}

/**
 * Displays user events in a popup.
 * Only shows events where isDeveloperEvent = false.
 * Developer events are shown separately in the Event Log dialog.
 */
@Composable
private fun eventPopupContent(
    events: List<Event>,
    onShowUpdateDetails: () -> Unit,
    onDismissPopup: () -> Unit,
    onRemoveEvent: (Event) -> Unit,
    onClearAll: () -> Unit,
) {
    val estimatedItemHeight = 128.dp
    val maxHeight = 500.dp
    val minHeight = 100.dp

    val dynamicHeight = remember(events.size) {
        val contentHeight = 60.dp + (estimatedItemHeight * events.size.toFloat())
        when {
            contentHeight < minHeight -> minHeight
            contentHeight > maxHeight -> maxHeight
            else -> contentHeight
        }
    }

    Column(
        modifier = Modifier
            .width(400.dp)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Notifications (${events.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (events.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource("event.notification.clear.all"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        HorizontalDivider()

        if (events.isEmpty()) {
            Text(
                text = stringResource("event.notification.empty"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            val listState = rememberLazyListState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dynamicHeight)
                    .padding(top = 8.dp),
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = events,
                        key = { it.timestamp.toEpochMilli() },
                    ) { event ->
                        eventItem(
                            event = event,
                            onShowUpdateDetails = onShowUpdateDetails,
                            onDismissPopup = onDismissPopup,
                            onRemoveEvent = { onRemoveEvent(event) },
                        )
                    }
                }

                if ((estimatedItemHeight * events.size.toFloat()) > maxHeight) {
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun eventItem(
    event: Event,
    onShowUpdateDetails: () -> Unit,
    onDismissPopup: () -> Unit,
    onRemoveEvent: () -> Unit,
) {
    val isSystemEvent = event.source == EventSource.SYSTEM
    val isUpdateEvent = event is UpdateAvailableEvent
    val isIndexingStarted = event is IndexingStartedEvent
    val isIndexingInProgress = event is IndexingInProgressEvent
    val isIndexingSuccess = event is IndexingCompletedEvent
    val isIndexingFailure = event is IndexingFailedEvent

    val eventName = when (event) {
        is UpdateAvailableEvent -> stringResource("event.update.available")
        is IndexingStartedEvent -> stringResource("event.indexing.started")
        is IndexingInProgressEvent -> stringResource("event.indexing.inprogress")
        is IndexingCompletedEvent -> stringResource("event.indexing.completed")
        is IndexingFailedEvent -> stringResource("event.indexing.failed")
        else -> event::class.simpleName ?: "Unknown"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = ComponentColors.surfaceVariantCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = eventName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isIndexingFailure -> MaterialTheme.colorScheme.error
                        isIndexingSuccess -> MaterialTheme.colorScheme.primary
                        isIndexingInProgress -> MaterialTheme.colorScheme.tertiary
                        isIndexingStarted -> MaterialTheme.colorScheme.onSurfaceVariant
                        isSystemEvent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                TextButton(
                    onClick = onRemoveEvent,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource("event.notification.clear"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                text = formatInstantDisplay(event.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Text(
                text = event.getDetails(),
                style = MaterialTheme.typography.bodySmall,
                color = if (isIndexingFailure) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (isUpdateEvent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            onRemoveEvent()
                            onShowUpdateDetails()
                            onDismissPopup()
                        },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(
                            text = stringResource("event.details.action"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Telemetry panel showing RAG and LLM metrics.
 * Max height is limited to 1/3 of parent height with scrolling support.
 */
@Composable
private fun telemetryPanel(metrics: TelemetryMetrics, maxHeight: Dp) {
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
                .height(maxHeight), // Force exact height to enable scrolling
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
                        color = MaterialTheme.colorScheme.primary,
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
                                    modifier = Modifier.size(16.dp),
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
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
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
            text = providerModel.split(":").joinToString(" • ") { it.capitalize() },
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
