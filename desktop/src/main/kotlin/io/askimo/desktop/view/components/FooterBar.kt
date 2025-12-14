/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.askimo.core.event.UpdateAvailableEvent
import io.askimo.core.util.TimeUtil.formatInstantDisplay
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.monitoring.SystemResourceMonitor
import io.askimo.desktop.theme.ComponentColors
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import java.awt.Desktop
import java.net.URI

/**
 * Displays the current AI provider and model configuration.
 * Clicking opens the provider configuration dialog.
 */
@Composable
private fun aiConfigInfo(onConfigureAiProvider: () -> Unit) {
    val appContext = remember { get<AppContext>(AppContext::class.java) }
    val configInfo = remember(appContext) { appContext.getConfigInfo() }
    val provider = configInfo.provider.name
    val model = configInfo.model

    themedTooltip(
        text = stringResource("system.ai.config.tooltip", provider, model),
    ) {
        TextButton(
            onClick = onConfigureAiProvider,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$provider: $model",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
    val scope = rememberCoroutineScope()

    val memoryUsage by resourceMonitor.memoryUsageMB.collectAsState()
    val cpuUsage by resourceMonitor.cpuUsagePercent.collectAsState()

    LaunchedEffect(Unit) {
        scope.launch {
            resourceMonitor.startMonitoring(intervalMillis = 2000)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Top border
        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComponentColors.sidebarSurfaceColor())
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

            // AI Configuration (Center)
            aiConfigInfo(onConfigureAiProvider = onConfigureAiProvider)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

    LaunchedEffect(Unit) {
        EventBus.userEvents.collect { event ->
            events.add(0, event)
            if (events.size > 100) {
                events.removeAt(100)
            }
        }
    }

    Box {
        IconButton(
            onClick = { showEventPopup = !showEventPopup },
            modifier = Modifier
                .size(32.dp)
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            BadgedBox(
                badge = {
                    if (events.isNotEmpty()) {
                        Badge(
                            modifier = Modifier
                                .widthIn(min = 20.dp)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = events.size.toString(),
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
                        onRemoveEvent = { event -> events.removeAt(events.indexOf(event)) },
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
        Text(
            text = "User Events (${events.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

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

    val eventName = when (event) {
        is UpdateAvailableEvent -> stringResource("event.update.available")
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
                    color = if (isSystemEvent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = event.source.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = formatInstantDisplay(event.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Text(
                text = event.getDetails(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
