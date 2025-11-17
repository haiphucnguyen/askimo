/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.askimo.desktop.model.FileAttachment
import io.askimo.desktop.model.ThemeMode
import io.askimo.desktop.model.View
import io.askimo.desktop.service.ThemePreferences
import io.askimo.desktop.ui.theme.ComponentColors
import io.askimo.desktop.ui.theme.createCustomTypography
import io.askimo.desktop.ui.theme.getDarkColorScheme
import io.askimo.desktop.ui.theme.getLightColorScheme
import io.askimo.desktop.ui.views.aboutView
import io.askimo.desktop.ui.views.chatView
import io.askimo.desktop.ui.views.sessionsView
import io.askimo.desktop.ui.views.settingsView
import io.askimo.desktop.viewmodel.ChatViewModel
import io.askimo.desktop.viewmodel.SessionsViewModel
import io.askimo.desktop.viewmodel.SettingsViewModel
import org.jetbrains.skia.Image
import java.awt.Cursor

/**
 * Detects if macOS is in dark mode by querying system defaults.
 * This is more reliable than AWT properties which often return null.
 */
fun detectMacOSDarkMode(): Boolean {
    return try {
        // Check if we're on macOS
        val osName = System.getProperty("os.name")
        if (!osName.contains("Mac", ignoreCase = true)) {
            return false
        }

        // Execute the defaults command to check AppleInterfaceStyle
        val process = ProcessBuilder(
            "defaults",
            "read",
            "-g",
            "AppleInterfaceStyle",
        ).start()

        val result = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        // If the command succeeds and returns "Dark", we're in dark mode
        // If the command fails (exit code != 0), the key doesn't exist, meaning light mode
        exitCode == 0 && result.equals("Dark", ignoreCase = true)
    } catch (e: Exception) {
        false
    }
}

fun main() = application {
    val icon = BitmapPainter(
        Image.makeFromEncoded(
            object {}.javaClass.getResourceAsStream("/images/askimo_512.png")?.readBytes()
                ?: throw IllegalStateException("Icon not found"),
        ).toComposeImageBitmap(),
    )

    Window(
        icon = icon,
        onCloseRequest = ::exitApplication,
        title = "Askimo",
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
    var attachments by remember { mutableStateOf(listOf<FileAttachment>()) }
    var sidebarWidth by remember { mutableStateOf(280.dp) }
    val scope = rememberCoroutineScope()

    // Create ViewModels
    val chatViewModel = remember { ChatViewModel(scope = scope) }
    val sessionsViewModel = remember { SessionsViewModel(scope = scope) }
    val settingsViewModel = remember { SettingsViewModel(scope = scope) }

    // Set up callback to refresh sessions list when a message is complete
    chatViewModel.setOnMessageCompleteCallback {
        sessionsViewModel.loadRecentSessions()
    }

    // Theme state
    val themeMode by ThemePreferences.themeMode.collectAsState()
    val accentColor by ThemePreferences.accentColor.collectAsState()
    val fontSettings by ThemePreferences.fontSettings.collectAsState()

    // State to track system theme - detect when needed
    var isSystemInDarkMode by remember { mutableStateOf(detectMacOSDarkMode()) }

    // Detect system theme when switching to SYSTEM mode
    LaunchedEffect(themeMode) {
        if (themeMode == ThemeMode.SYSTEM) {
            isSystemInDarkMode = detectMacOSDarkMode()
        }
    }

    // Calculate actual dark mode based on theme preference
    val useDarkMode = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkMode
    }

    val colorScheme = if (useDarkMode) {
        getDarkColorScheme(accentColor)
    } else {
        getLightColorScheme(accentColor)
    }

    // Create custom typography based on font settings
    val customTypography = remember(fontSettings) {
        createCustomTypography(fontSettings)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = customTypography,
    ) {
        // Always show sidebar in expanded or collapsed mode
        Row(modifier = Modifier.fillMaxSize()) {
            // Observe current session ID
            val currentSessionId by chatViewModel.currentSessionId.collectAsState()

            // Sidebar (expanded or collapsed)
            sidebar(
                isExpanded = isSidebarExpanded,
                width = sidebarWidth,
                currentView = currentView,
                isSessionsExpanded = isSessionsExpanded,
                sessionsViewModel = sessionsViewModel,
                currentSessionId = currentSessionId,
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

            // Draggable divider
            if (isSidebarExpanded) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newWidth = (sidebarWidth.value + dragAmount.x / density).dp
                                // Constrain width between 200dp and 500dp
                                sidebarWidth = newWidth.coerceIn(200.dp, 500.dp)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Visual grip indicator
                    Column(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight(0.1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }

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
                onNavigateToSettings = { currentView = View.SETTINGS },
            )
        }
    }
}

@Composable
fun sidebar(
    isExpanded: Boolean,
    width: androidx.compose.ui.unit.Dp,
    currentView: View,
    isSessionsExpanded: Boolean,
    sessionsViewModel: SessionsViewModel,
    currentSessionId: String?,
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
                .width(width)
                .fillMaxHeight()
                .background(ComponentColors.sidebarSurfaceColor()),
        ) {
            // Header with logo and collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ComponentColors.sidebarHeaderColor())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = remember {
                            BitmapPainter(
                                Image.makeFromEncoded(
                                    object {}.javaClass.getResourceAsStream("/images/askimo_logo_64.png")?.readBytes()
                                        ?: throw IllegalStateException("Icon not found"),
                                ).toComposeImageBitmap(),
                            )
                        },
                        contentDescription = "Askimo",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Askimo AI",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
                    colors = ComponentColors.navigationDrawerItemColors(),
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
                    colors = ComponentColors.navigationDrawerItemColors(),
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
                                    isSelected = session.id == currentSessionId,
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
                                    colors = ComponentColors.navigationDrawerItemColors(),
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
                    colors = ComponentColors.navigationDrawerItemColors(),
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
                    colors = ComponentColors.navigationDrawerItemColors(),
                )
            }
        }
    } else {
        // Collapsed sidebar with icons only
        Column(
            modifier = Modifier
                .width(72.dp)
                .fillMaxHeight()
                .background(ComponentColors.sidebarSurfaceColor())
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header with expand button only
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ComponentColors.sidebarHeaderColor())
                    .padding(vertical = 16.dp),
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
                    colors = ComponentColors.navigationRailItemColors(),
                )

                // Sessions
                NavigationRailItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "Sessions") },
                    label = null,
                    selected = currentView == View.SESSIONS,
                    onClick = onNavigateToSessions,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.navigationRailItemColors(),
                )

                // Settings
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = null,
                    selected = currentView == View.SETTINGS,
                    onClick = onNavigateToSettings,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.navigationRailItemColors(),
                )

                // About
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "About") },
                    label = null,
                    selected = currentView == View.ABOUT,
                    onClick = onNavigateToAbout,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.navigationRailItemColors(),
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
    settingsViewModel: SettingsViewModel,
    inputText: TextFieldValue,
    onInputTextChange: (TextFieldValue) -> Unit,
    onSendMessage: (String, List<FileAttachment>) -> Unit,
    onResumeSession: (String) -> Unit,
    attachments: List<FileAttachment>,
    onAttachmentsChange: (List<FileAttachment>) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (currentView) {
            View.CHAT, View.NEW_CHAT -> {
                val configInfo = chatViewModel.getSessionConfigInfo()
                chatView(
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
                    provider = configInfo.provider.name,
                    model = configInfo.model,
                    onNavigateToSettings = onNavigateToSettings,
                    hasMoreMessages = chatViewModel.hasMoreMessages,
                    isLoadingPrevious = chatViewModel.isLoadingPrevious,
                    onLoadPrevious = { chatViewModel.loadPreviousMessages() },
                    isSearchMode = chatViewModel.isSearchMode,
                    searchQuery = chatViewModel.searchQuery,
                    searchResults = chatViewModel.searchResults,
                    isSearching = chatViewModel.isSearching,
                    onSearch = { query -> chatViewModel.searchMessages(query) },
                    onClearSearch = { chatViewModel.clearSearch() },
                    onJumpToMessage = { messageId, timestamp ->
                        chatViewModel.jumpToMessage(messageId, timestamp)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun sessionItemWithMenu(
    session: io.askimo.core.session.ChatSession,
    isSelected: Boolean,
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
                TooltipArea(
                    tooltip = {
                        Surface(
                            modifier = Modifier.padding(4.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            shape = MaterialTheme.shapes.small,
                            shadowElevation = 4.dp,
                        ) {
                            Text(
                                text = session.title,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    delayMillis = 500,
                    tooltipPlacement = TooltipPlacement.CursorPoint(
                        offset = DpOffset(0.dp, 16.dp),
                    ),
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            selected = isSelected,
            onClick = { onResumeSession(session.id) },
            modifier = Modifier
                .weight(1f)
                .pointerHoverIcon(PointerIcon.Hand),
            colors = ComponentColors.navigationDrawerItemColors(),
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
