/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.shell

import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.askimo.core.event.Event
import io.askimo.core.event.EventBus
import io.askimo.core.event.system.ShellErrorEvent
import io.askimo.core.event.system.UpdateAvailableEvent
import io.askimo.core.util.TimeUtil.formatInstantDisplay
import io.askimo.ui.common.i18n.stringResource
import io.askimo.ui.common.theme.AppComponents

/**
 * Wrapper to give each notification event a stable unique key for [LazyColumn].
 */
data class NotificationEventItem(
    val id: String,
    val event: Event,
)

/**
 * Notification bell icon displayed in the footer bar.
 *
 * Subscribes to [EventBus.userEvents], accumulates up to 100 events, and shows
 * a badge with the unread count. Clicking the icon toggles a [notificationPopup].
 *
 * Shared between the community desktop app and the Pro (askimo-app) edition.
 *
 * @param onShowUpdateDetails Called when the user clicks "Details" on an [UpdateAvailableEvent].
 */
@Composable
fun notificationIcon(onShowUpdateDetails: () -> Unit) {
    var showEventPopup by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf<NotificationEventItem>() }
    var unreadCount by remember { mutableStateOf(0) }
    var eventCounter by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        EventBus.userEvents.collect { event ->
            val uniqueId = "${eventCounter++}_${event.timestamp.toEpochMilli()}"
            events.add(0, NotificationEventItem(uniqueId, event))
            unreadCount++
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
                    notificationPopup(
                        events = events,
                        onShowUpdateDetails = onShowUpdateDetails,
                        onDismissPopup = { showEventPopup = false },
                        onRemoveEvent = { item ->
                            events.remove(item)
                            if (unreadCount > 0) unreadCount--
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
 * Popup content listing all accumulated [NotificationEventItem]s.
 *
 * Height adjusts dynamically up to [maxHeight] based on the number of items.
 */
@Composable
fun notificationPopup(
    events: List<NotificationEventItem>,
    onShowUpdateDetails: () -> Unit,
    onDismissPopup: () -> Unit,
    onRemoveEvent: (NotificationEventItem) -> Unit,
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
                    ) { item ->
                        notificationEventCard(
                            event = item.event,
                            onShowUpdateDetails = onShowUpdateDetails,
                            onDismissPopup = onDismissPopup,
                            onRemoveEvent = { onRemoveEvent(item) },
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

/**
 * A single notification card inside [notificationPopup].
 *
 * Displays the event name, timestamp, details text, and an optional "Details"
 * action button for [UpdateAvailableEvent]s.
 */
@Composable
fun notificationEventCard(
    event: Event,
    onShowUpdateDetails: () -> Unit,
    onDismissPopup: () -> Unit,
    onRemoveEvent: () -> Unit,
) {
    val isUpdateEvent = event is UpdateAvailableEvent
    val isShellError = event is ShellErrorEvent

    val eventName = when (event) {
        is UpdateAvailableEvent -> stringResource("event.update.available")
        is ShellErrorEvent -> stringResource("event.shell.error")
        else -> event::class.simpleName ?: "Unknown"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = AppComponents.surfaceVariantCardColors(),
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
                        isShellError -> MaterialTheme.colorScheme.error
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
                color = if (isShellError) {
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
