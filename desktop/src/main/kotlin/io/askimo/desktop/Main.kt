/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.askimo.desktop.model.View
import io.askimo.desktop.ui.theme.DarkColorScheme
import io.askimo.desktop.ui.views.aboutView
import io.askimo.desktop.ui.views.chatView
import io.askimo.desktop.ui.views.sessionsView
import io.askimo.desktop.ui.views.settingsView
import io.askimo.desktop.viewmodel.ChatViewModel
import io.askimo.desktop.viewmodel.SessionsViewModel
import org.jetbrains.skia.Image

fun main() = application {
    val icon = BitmapPainter(
        Image.makeFromEncoded(
            object {}.javaClass.getResourceAsStream("/images/askimo_linux_512.png")?.readBytes()
                ?: throw IllegalStateException("Icon not found"),
        ).toComposeImageBitmap(),
    )

    Window(
        icon = icon,
        onCloseRequest = ::exitApplication,
        title = "Askimo Desktop",
        state = rememberWindowState(width = 800.dp, height = 600.dp),
    ) {
        app()
    }
}

@Composable
@Preview
fun app() {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var currentView by remember { mutableStateOf(View.CHAT) }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    var isSessionsExpanded by remember { mutableStateOf(true) }
    var attachments by remember { mutableStateOf(listOf<io.askimo.desktop.model.FileAttachment>()) }
    val scope = rememberCoroutineScope()

    // Create ViewModels
    val chatViewModel = remember { ChatViewModel(scope = scope) }
    val sessionsViewModel = remember { SessionsViewModel(scope = scope) }
    val settingsViewModel = remember { io.askimo.desktop.viewmodel.SettingsViewModel(scope = scope) }

    // Set up callback to refresh sessions list when a message is complete
    chatViewModel.setOnMessageCompleteCallback {
        sessionsViewModel.loadRecentSessions()
    }

    // Theme state
    val themeMode by io.askimo.desktop.service.ThemePreferences.themeMode.collectAsState()

    // Determine if system is in dark mode
    val isSystemInDarkMode = remember {
        try {
            val toolkit = java.awt.Toolkit.getDefaultToolkit()
            val isDark = toolkit.getDesktopProperty("awt.color.darkMode") == true
            isDark
        } catch (e: Exception) {
            false // Default to light mode if we can't detect
        }
    }

    // Calculate actual dark mode based on theme preference
    val useDarkMode = when (themeMode) {
        io.askimo.desktop.model.ThemeMode.LIGHT -> false
        io.askimo.desktop.model.ThemeMode.DARK -> true
        io.askimo.desktop.model.ThemeMode.SYSTEM -> isSystemInDarkMode
    }

    val colorScheme = if (useDarkMode) DarkColorScheme else io.askimo.desktop.ui.theme.LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
    ) {
        // Always show sidebar in expanded or collapsed mode
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar (expanded or collapsed)
            sidebar(
                isExpanded = isSidebarExpanded,
                currentView = currentView,
                isSessionsExpanded = isSessionsExpanded,
                sessionsViewModel = sessionsViewModel,
                onToggleExpand = { isSidebarExpanded = !isSidebarExpanded },
                onNewChat = {
                    chatViewModel.clearChat()
                    inputText = TextFieldValue("")
                    currentView = View.CHAT
                },
                onToggleSessions = { isSessionsExpanded = !isSessionsExpanded },
                onNavigateToSessions = { currentView = View.SESSIONS },
                onResumeSession = { sessionId ->
                    chatViewModel.resumeSession(sessionId)
                    currentView = View.CHAT
                },
                onDeleteSession = { sessionId ->
                    sessionsViewModel.deleteSession(sessionId)
                },
                onNavigateToSettings = { currentView = View.SETTINGS },
                onNavigateToAbout = { currentView = View.ABOUT },
            )

            // Main content
            mainContent(
                currentView = currentView,
                chatViewModel = chatViewModel,
                sessionsViewModel = sessionsViewModel,
                settingsViewModel = settingsViewModel,
                inputText = inputText,
                onInputTextChange = { inputText = it },
                onSendMessage = { message, fileAttachments ->
                    chatViewModel.sendMessage(message, fileAttachments)
                    inputText = TextFieldValue("")
                    attachments = emptyList()
                },
                onResumeSession = { sessionId ->
                    chatViewModel.resumeSession(sessionId)
                    currentView = View.CHAT
                },
                attachments = attachments,
                onAttachmentsChange = { attachments = it },
            )
        }
    }
}

@Composable
fun sidebar(
    isExpanded: Boolean,
    currentView: View,
    isSessionsExpanded: Boolean,
    sessionsViewModel: SessionsViewModel,
    onToggleExpand: () -> Unit,
    onNewChat: () -> Unit,
    onToggleSessions: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    if (isExpanded) {
        // Expanded sidebar with full text
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
            // Header with logo and collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource("/images/askimo_64.png"),
                    contentDescription = "Askimo",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardDoubleArrowLeft,
                        contentDescription = "Collapse sidebar",
                        tint = MaterialTheme.colorScheme.onSurface,
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
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
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
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
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
                                sessionItemWithMenu(
                                    session = session,
                                    onResumeSession = onResumeSession,
                                    onDeleteSession = onDeleteSession,
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
                                    modifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .pointerHoverIcon(PointerIcon.Hand),
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
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                )

                // About
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("About") },
                    selected = currentView == View.ABOUT,
                    onClick = onNavigateToAbout,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    } else {
        // Collapsed sidebar with icons only
        Column(
            modifier = Modifier
                .width(72.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header with expand button only
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardDoubleArrowRight,
                        contentDescription = "Expand sidebar",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // New Chat
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = "New Chat") },
                    label = null,
                    selected = false,
                    onClick = onNewChat,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )

                // Sessions
                NavigationRailItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "Sessions") },
                    label = null,
                    selected = currentView == View.SESSIONS,
                    onClick = onNavigateToSessions,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )

                // Settings
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = null,
                    selected = currentView == View.SETTINGS,
                    onClick = onNavigateToSettings,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )

                // About
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "About") },
                    label = null,
                    selected = currentView == View.ABOUT,
                    onClick = onNavigateToAbout,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}

@Composable
fun mainContent(
    currentView: View,
    chatViewModel: ChatViewModel,
    sessionsViewModel: SessionsViewModel,
    settingsViewModel: io.askimo.desktop.viewmodel.SettingsViewModel,
    inputText: TextFieldValue,
    onInputTextChange: (TextFieldValue) -> Unit,
    onSendMessage: (String, List<io.askimo.desktop.model.FileAttachment>) -> Unit,
    onResumeSession: (String) -> Unit,
    attachments: List<io.askimo.desktop.model.FileAttachment>,
    onAttachmentsChange: (List<io.askimo.desktop.model.FileAttachment>) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (currentView) {
            View.CHAT, View.NEW_CHAT -> chatView(
                messages = chatViewModel.messages,
                inputText = inputText,
                onInputTextChange = onInputTextChange,
                onSendMessage = onSendMessage,
                onStopResponse = { chatViewModel.cancelResponse() },
                isLoading = chatViewModel.isLoading,
                isThinking = chatViewModel.isThinking,
                thinkingElapsedSeconds = chatViewModel.thinkingElapsedSeconds,
                spinnerFrame = chatViewModel.getSpinnerFrame(),
                errorMessage = chatViewModel.errorMessage,
                attachments = attachments,
                onAttachmentsChange = onAttachmentsChange,
                modifier = Modifier.fillMaxSize(),
            )
            View.SESSIONS -> sessionsView(
                viewModel = sessionsViewModel,
                onResumeSession = onResumeSession,
                modifier = Modifier.fillMaxSize(),
            )
            View.SETTINGS -> settingsView(
                viewModel = settingsViewModel,
                modifier = Modifier.fillMaxSize(),
            )
            View.ABOUT -> aboutView(
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun sessionItemWithMenu(
    session: io.askimo.core.session.ChatSession,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                .weight(1f)
                .pointerHoverIcon(PointerIcon.Hand),
        )

        // Three-dot menu button with dropdown
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .padding(0.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(0.dp),
                )
            }

            androidx.compose.material3.DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDeleteSession(session.id)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                )
                // Future menu items can be added here
            }
        }
    }
}
