/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.askimo.desktop.model.View
import io.askimo.desktop.ui.views.aboutView
import io.askimo.desktop.ui.views.chatView
import io.askimo.desktop.ui.views.sessionsView
import io.askimo.desktop.ui.views.settingsView
import io.askimo.desktop.viewmodel.ChatViewModel
import io.askimo.desktop.viewmodel.SessionsViewModel
import kotlinx.coroutines.launch

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Askimo Desktop",
        state = rememberWindowState(width = 800.dp, height = 600.dp),
    ) {
        app()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun app() {
    var inputText by remember { mutableStateOf("") }
    var currentView by remember { mutableStateOf(View.CHAT) }
    var isDrawerPinned by remember { mutableStateOf(true) }
    var isSessionsExpanded by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Create ViewModels
    val chatViewModel = remember { ChatViewModel(scope = scope) }
    val sessionsViewModel = remember { SessionsViewModel(scope = scope) }

    MaterialTheme {
        if (isDrawerPinned) {
            // Pinned mode: Use Row layout with permanent sidebar
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar
                permanentSidebar(
                    currentView = currentView,
                    isSessionsExpanded = isSessionsExpanded,
                    sessionsViewModel = sessionsViewModel,
                    onTogglePin = { isDrawerPinned = false },
                    onNewChat = {
                        chatViewModel.clearChat()
                        inputText = ""
                        currentView = View.CHAT
                    },
                    onToggleSessions = { isSessionsExpanded = !isSessionsExpanded },
                    onNavigateToSessions = { currentView = View.SESSIONS },
                    onResumeSession = { sessionId ->
                        chatViewModel.resumeSession(sessionId)
                        currentView = View.CHAT
                    },
                    onNavigateToSettings = { currentView = View.SETTINGS },
                    onNavigateToAbout = { currentView = View.ABOUT },
                )

                // Main content
                mainContent(
                    currentView = currentView,
                    chatViewModel = chatViewModel,
                    sessionsViewModel = sessionsViewModel,
                    inputText = inputText,
                    onInputTextChange = { inputText = it },
                    onSendMessage = { message ->
                        chatViewModel.sendMessage(message)
                        inputText = ""
                    },
                    onToggleDrawer = { isDrawerPinned = !isDrawerPinned },
                    onResumeSession = { sessionId ->
                        chatViewModel.resumeSession(sessionId)
                        currentView = View.CHAT
                    },
                )
            }
        } else {
            // Unpinned mode: Use ModalNavigationDrawer
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    drawerContent(
                        currentView = currentView,
                        isDrawerPinned = false,
                        isSessionsExpanded = isSessionsExpanded,
                        sessionsViewModel = sessionsViewModel,
                        chatViewModel = chatViewModel,
                        onTogglePin = { isDrawerPinned = true },
                        onNewChat = {
                            chatViewModel.clearChat()
                            inputText = ""
                            currentView = View.CHAT
                            scope.launch { drawerState.close() }
                        },
                        onToggleSessions = { isSessionsExpanded = !isSessionsExpanded },
                        onNavigateToSessions = {
                            currentView = View.SESSIONS
                            scope.launch { drawerState.close() }
                        },
                        onResumeSession = { sessionId ->
                            chatViewModel.resumeSession(sessionId)
                            currentView = View.CHAT
                            scope.launch { drawerState.close() }
                        },
                        onNavigateToSettings = {
                            currentView = View.SETTINGS
                            scope.launch { drawerState.close() }
                        },
                        onNavigateToAbout = {
                            currentView = View.ABOUT
                            scope.launch { drawerState.close() }
                        },
                    )
                },
            ) {
                mainContent(
                    currentView = currentView,
                    chatViewModel = chatViewModel,
                    sessionsViewModel = sessionsViewModel,
                    inputText = inputText,
                    onInputTextChange = { inputText = it },
                    onSendMessage = { message ->
                        chatViewModel.sendMessage(message)
                        inputText = ""
                    },
                    onToggleDrawer = {
                        scope.launch {
                            if (drawerState.isClosed) {
                                drawerState.open()
                            } else {
                                drawerState.close()
                            }
                        }
                    },
                    onResumeSession = { sessionId ->
                        chatViewModel.resumeSession(sessionId)
                        currentView = View.CHAT
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun drawerContent(
    currentView: View,
    isDrawerPinned: Boolean,
    isSessionsExpanded: Boolean,
    sessionsViewModel: SessionsViewModel,
    chatViewModel: ChatViewModel,
    onTogglePin: () -> Unit,
    onNewChat: () -> Unit,
    onToggleSessions: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onResumeSession: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    ModalDrawerSheet {
        // Header with title and pin button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Askimo",
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (isDrawerPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isDrawerPinned) "Unpin sidebar" else "Pin sidebar",
                    tint = if (isDrawerPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // New Chat
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                label = { Text("New Chat") },
                selected = false,
                onClick = onNewChat,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // Sessions (Collapsible)
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                label = { Text("Sessions") },
                selected = currentView == View.SESSIONS,
                onClick = onToggleSessions,
                badge = {
                    Icon(
                        imageVector = if (isSessionsExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (isSessionsExpanded) "Collapse" else "Expand",
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // Sessions list (collapsible content)
            if (isSessionsExpanded) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    if (sessionsViewModel.recentSessions.isEmpty()) {
                        Text(
                            text = "No sessions yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        sessionsViewModel.recentSessions.forEach { session ->
                            NavigationDrawerItem(
                                icon = null,
                                label = {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                    )
                                },
                                selected = false,
                                onClick = { onResumeSession(session.id) },
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .pointerHoverIcon(
                                        PointerIcon.Hand,
                                    ),
                            )
                        }

                        // Show More button if there are more than 10 sessions
                        if (sessionsViewModel.totalSessionCount > 10) {
                            NavigationDrawerItem(
                                icon = null,
                                label = {
                                    Text(
                                        text = "More...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                selected = false,
                                onClick = onNavigateToSessions,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Settings
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = currentView == View.SETTINGS,
                onClick = onNavigateToSettings,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // About
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                label = { Text("About") },
                selected = currentView == View.ABOUT,
                onClick = onNavigateToAbout,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun permanentSidebar(
    currentView: View,
    isSessionsExpanded: Boolean,
    sessionsViewModel: SessionsViewModel,
    onTogglePin: () -> Unit,
    onNewChat: () -> Unit,
    onToggleSessions: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onResumeSession: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        // Header with title and pin button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Askimo",
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Unpin sidebar",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // New Chat
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                label = { Text("New Chat") },
                selected = false,
                onClick = onNewChat,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // Sessions (Collapsible)
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                label = { Text("Sessions") },
                selected = currentView == View.SESSIONS,
                onClick = onToggleSessions,
                badge = {
                    Icon(
                        imageVector = if (isSessionsExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (isSessionsExpanded) "Collapse" else "Expand",
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // Sessions list (collapsible content)
            if (isSessionsExpanded) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    if (sessionsViewModel.recentSessions.isEmpty()) {
                        Text(
                            text = "No sessions yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        sessionsViewModel.recentSessions.forEach { session ->
                            NavigationDrawerItem(
                                icon = null,
                                label = {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                    )
                                },
                                selected = false,
                                onClick = { onResumeSession(session.id) },
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .pointerHoverIcon(
                                        PointerIcon.Hand,
                                    ),
                            )
                        }

                        // Show More button if there are more than 10 sessions
                        if (sessionsViewModel.totalSessionCount > 10) {
                            NavigationDrawerItem(
                                icon = null,
                                label = {
                                    Text(
                                        text = "More...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                selected = false,
                                onClick = onNavigateToSessions,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Settings
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = currentView == View.SETTINGS,
                onClick = onNavigateToSettings,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // About
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                label = { Text("About") },
                selected = currentView == View.ABOUT,
                onClick = onNavigateToAbout,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun mainContent(
    currentView: View,
    chatViewModel: ChatViewModel,
    sessionsViewModel: SessionsViewModel,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onToggleDrawer: () -> Unit,
    onResumeSession: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentView) {
                            View.CHAT, View.NEW_CHAT -> "Askimo Desktop"
                            View.SESSIONS -> "Sessions"
                            View.SETTINGS -> "Settings"
                            View.ABOUT -> "About"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onToggleDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        when (currentView) {
            View.CHAT, View.NEW_CHAT -> chatView(
                messages = chatViewModel.messages,
                inputText = inputText,
                onInputTextChange = onInputTextChange,
                onSendMessage = onSendMessage,
                isLoading = chatViewModel.isLoading,
                errorMessage = chatViewModel.errorMessage,
                modifier = Modifier.padding(padding),
            )
            View.SESSIONS -> sessionsView(
                viewModel = sessionsViewModel,
                onResumeSession = onResumeSession,
                modifier = Modifier.padding(padding),
            )
            View.SETTINGS -> settingsView(
                modifier = Modifier.padding(padding),
            )
            View.ABOUT -> aboutView(
                modifier = Modifier.padding(padding),
            )
        }
    }
}
