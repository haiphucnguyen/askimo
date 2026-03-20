/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.shell

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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import io.askimo.core.VersionInfo
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
import io.askimo.core.logging.currentFileLogger
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.ProviderSettings
import io.askimo.core.util.TimeUtil.formatInstantDisplay
import io.askimo.desktop.settings.groupModelsByFamily
import io.askimo.ui.common.i18n.stringResource
import io.askimo.ui.common.monitoring.SystemResourceMonitor
import io.askimo.ui.common.theme.ComponentColors
import io.askimo.ui.common.ui.clickableCard
import io.askimo.ui.common.ui.themedTooltip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import java.awt.Desktop
import java.net.URI

private val log = currentFileLogger()

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
        providerButton(
            currentProvider = configInfo.provider,
            onConfigureProvider = onConfigureAiProvider,
        )

        modelDropdown(
            currentProvider = configInfo.provider,
            currentModel = configInfo.model,
            onModelSelected = { newModel ->
                val appContext = get<AppContext>(AppContext::class.java)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        appContext.params.model = newModel
                        appContext.save()
                        EventBus.emit(ModelChangedEvent(configInfo.provider, newModel))
                    } catch (e: Exception) {
                        log.error("Failed to change model to $newModel for provider ${configInfo.provider}", e)
                    }
                }
            },
        )
    }
}

@Composable
private fun providerButton(
    currentProvider: ModelProvider,
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
                    tint = MaterialTheme.colorScheme.onSurface,
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
    currentProvider: ModelProvider,
    currentModel: String,
    onModelSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
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
                        val models = (factory as ChatModelFactory<ProviderSettings>)
                            .availableModels(settings)
                        availableModels = models
                    }
                } catch (e: Exception) {
                    log.error("Can not get models", e)
                    availableModels = emptyList()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Reset models and search when provider changes
    LaunchedEffect(currentProvider) {
        availableModels = emptyList()
        searchQuery = ""
    }

    Box {
        Card(
            modifier = Modifier
                .clickableCard { expanded = true }
                .widthIn(min = 60.dp, max = 250.dp),
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
                    overflow = TextOverflow.Ellipsis,
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
            onDismissRequest = {
                expanded = false
                searchQuery = ""
            },
        ) {
            when {
                isLoading -> {
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
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
                    DropdownMenuItem(
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
                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource("settings.model.search"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = ComponentColors.outlinedTextFieldColors(),
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Filter models based on search
                    val filteredModels = availableModels.filter {
                        it.contains(searchQuery, ignoreCase = true)
                    }

                    // Scrollable list with fixed dimensions to avoid intrinsic measurement issues
                    val listState = rememberLazyListState()

                    Box(
                        modifier = Modifier
                            .width(300.dp)
                            .height(400.dp),
                    ) {
                        if (filteredModels.isEmpty()) {
                            // Show "no results" message if search yields nothing
                            Text(
                                text = stringResource("settings.model.no.results"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            // Use LazyColumn so item clicks are not swallowed by the scroll modifier
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val groupedModels = groupModelsByFamily(filteredModels)
                                val showHeaders = groupedModels.size > 1
                                groupedModels.forEach { (category, categoryModels) ->
                                    if (showHeaders && categoryModels.isNotEmpty()) {
                                        item(key = "header_$category") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                            ) {
                                                Text(
                                                    text = category.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                    }
                                    items(categoryModels, key = { it }) { model ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = model,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            },
                                            onClick = {
                                                onModelSelected(model)
                                                expanded = false
                                                searchQuery = ""
                                            },
                                            leadingIcon = if (model == currentModel) {
                                                {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Current model",
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                        )
                                    }
                                }
                            }

                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(listState),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(end = 2.dp),
                            )
                        }
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
                            tint = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurface,
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
 * Wrapper class to ensure unique keys for events in LazyColumn
 */
private data class EventWithId(
    val id: String,
    val event: Event,
)

/**
 * Notification icon in bottom bar that shows user events only.
 * Developer events are excluded and shown in the Event Log dialog instead.
 */
@Composable
private fun notificationIcon(onShowUpdateDetails: () -> Unit) {
    var showEventPopup by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf<EventWithId>() }
    var unreadCount by remember { mutableStateOf(0) }
    var eventCounter by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        EventBus.userEvents.collect { event ->
            // Generate unique ID combining counter and timestamp
            val uniqueId = "${eventCounter++}_${event.timestamp.toEpochMilli()}"
            events.add(0, EventWithId(uniqueId, event))
            unreadCount++

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
                        onRemoveEvent = { eventWithId ->
                            events.remove(eventWithId)
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
    events: List<EventWithId>,
    onShowUpdateDetails: () -> Unit,
    onDismissPopup: () -> Unit,
    onRemoveEvent: (EventWithId) -> Unit,
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
                color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        HorizontalDivider()

        if (events.isEmpty()) {
            Text(
                text = stringResource("event.notification.empty"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
                        key = { it.id },
                    ) { eventWithId ->
                        eventItem(
                            event = eventWithId.event,
                            onShowUpdateDetails = onShowUpdateDetails,
                            onDismissPopup = onDismissPopup,
                            onRemoveEvent = { onRemoveEvent(eventWithId) },
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
                        else -> MaterialTheme.colorScheme.onSurface
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = formatInstantDisplay(event.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = event.getDetails(),
                style = MaterialTheme.typography.bodySmall,
                color = if (isIndexingFailure) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
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
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
