/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.dto.ChatMessageDTO
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.chat.service.ChatSessionService
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.context.ExecutionMode
import io.askimo.core.context.getConfigInfo
import io.askimo.core.db.DatabaseManager
import io.askimo.core.event.Event
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.IndexingErrorEvent
import io.askimo.core.event.internal.IndexingErrorType
import io.askimo.core.event.internal.ProjectSessionsRefreshRequested
import io.askimo.core.i18n.LocalizationManager
import io.askimo.core.logging.LogbackConfigurator
import io.askimo.core.logging.logger
import io.askimo.core.providers.ModelProvider
import io.askimo.core.util.ProcessBuilderExt
import io.askimo.desktop.di.allDesktopModules
import io.askimo.desktop.i18n.provideLocalization
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.keymap.KeyMapManager
import io.askimo.desktop.keymap.KeyMapManager.AppShortcut
import io.askimo.desktop.preferences.StarPromptPreferences
import io.askimo.desktop.preferences.ThemePreferences
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.theme.ThemeMode
import io.askimo.desktop.theme.createCustomTypography
import io.askimo.desktop.theme.getDarkColorScheme
import io.askimo.desktop.theme.getLightColorScheme
import io.askimo.desktop.ui.dialog.errorDialog
import io.askimo.desktop.ui.dialog.updateCheckDialog
import io.askimo.desktop.view.View
import io.askimo.desktop.view.about.aboutDialog
import io.askimo.desktop.view.chat.chatView
import io.askimo.desktop.view.components.NativeMenuBar
import io.askimo.desktop.view.components.editProjectDialog
import io.askimo.desktop.view.components.eventLogPanel
import io.askimo.desktop.view.components.eventLogWindow
import io.askimo.desktop.view.components.exportSessionDialog
import io.askimo.desktop.view.components.footerBar
import io.askimo.desktop.view.components.navigationSidebar
import io.askimo.desktop.view.components.newProjectDialog
import io.askimo.desktop.view.components.renameSessionDialog
import io.askimo.desktop.view.components.sessionMemoryDialog
import io.askimo.desktop.view.components.starPromptDialog
import io.askimo.desktop.view.project.projectView
import io.askimo.desktop.view.sessions.sessionsView
import io.askimo.desktop.view.settings.providerSelectionDialog
import io.askimo.desktop.view.settings.settingsViewWithSidebar
import io.askimo.desktop.viewmodel.ChatViewModel
import io.askimo.desktop.viewmodel.ProjectsViewModel
import io.askimo.desktop.viewmodel.SessionManager
import io.askimo.desktop.viewmodel.SessionsViewModel
import io.askimo.desktop.viewmodel.SettingsViewModel
import io.askimo.desktop.viewmodel.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.koin.core.context.GlobalContext.get
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import java.awt.Cursor
import java.awt.Desktop
import java.util.UUID
import kotlin.system.exitProcess

/**
 * Detects if macOS is in dark mode by querying system defaults.
 * This is more reliable than AWT properties which often return null.
 */
fun detectMacOSDarkMode(): Boolean {
    return try {
        val osName = System.getProperty("os.name")
        if (!osName.contains("Mac", ignoreCase = true)) {
            return false
        }

        val process =
            ProcessBuilderExt(
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

private val log = logger("Main")

fun main() {
    startKoin {
        modules(allDesktopModules)
    }

    if (AppConfig.developer.enabled &&
        AppConfig.developer.active
    ) {
        LogbackConfigurator.registerEventBusAppender()
    }

    application {
        val savedLocale = ThemePreferences.locale.value
        LocalizationManager.setLocale(savedLocale)

        val icon = BitmapPainter(
            Image.makeFromEncoded(
                object {}.javaClass.getResourceAsStream("/images/askimo_512.png")?.readBytes()
                    ?: throw IllegalStateException("Icon not found"),
            ).toComposeImageBitmap(),
        )

        // Load saved window state or use defaults
        val savedWidth = ThemePreferences.getWindowWidth()
        val savedHeight = ThemePreferences.getWindowHeight()
        val savedX = ThemePreferences.getWindowX()
        val savedY = ThemePreferences.getWindowY()
        val isMaximized = ThemePreferences.isWindowMaximized()

        val windowState = rememberWindowState(
            width = if (savedWidth > 0) savedWidth.dp else 800.dp,
            height = if (savedHeight > 0) savedHeight.dp else 600.dp,
            position = if (savedX >= 0 && savedY >= 0) {
                WindowPosition(savedX.dp, savedY.dp)
            } else {
                WindowPosition.Aligned(Alignment.Center)
            },
            placement = if (isMaximized) {
                WindowPlacement.Maximized
            } else {
                WindowPlacement.Floating
            },
        )

        // Save window state when it changes
        LaunchedEffect(windowState.size, windowState.position, windowState.placement) {
            ThemePreferences.saveWindowState(
                width = windowState.size.width.value.toInt(),
                height = windowState.size.height.value.toInt(),
                x = windowState.position.x.value.toInt(),
                y = windowState.position.y.value.toInt(),
                isMaximized = windowState.placement == WindowPlacement.Maximized,
            )
        }

        Window(
            icon = icon,
            onCloseRequest = {
                exitApplication()
            },
            title = "Askimo",
            state = windowState,
        ) {
            app(frameWindowScope = this@Window)
        }
    }
}

/**
 * Position where the Event Log panel can be docked
 */
enum class EventLogDockPosition {
    BOTTOM,
    LEFT,
    RIGHT,
}

/**
 * Data class to hold chat view state per session for restoration when switching
 */
data class ChatViewState(
    val inputText: TextFieldValue = TextFieldValue(""),
    val attachments: List<FileAttachmentDTO> = emptyList(),
    val editingMessage: ChatMessageDTO? = null,
)

@Composable
@Preview
fun app(frameWindowScope: FrameWindowScope? = null) {
    var currentView by remember { mutableStateOf(View.CHAT) }
    var previousView by remember { mutableStateOf(View.CHAT) }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    var isProjectsExpanded by remember { mutableStateOf(true) }
    var isSessionsExpanded by remember { mutableStateOf(true) }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    // Sidebar width as a fraction of screen width (0.0 to 1.0) - load from preferences
    var sidebarWidthFraction by remember { mutableStateOf(ThemePreferences.getMainSidebarWidthFraction()) }
    var showQuitDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showEventLogWindow by remember { mutableStateOf(false) }
    var showEventLogPanel by remember { mutableStateOf(false) }
    var eventLogDockPosition by remember { mutableStateOf(EventLogDockPosition.BOTTOM) }
    var eventLogPanelSize by remember { mutableStateOf(300.dp) } // Default size
    var showStarPromptDialog by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showEditProjectDialog by remember { mutableStateOf(false) }
    var editingProjectId by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogMessage by remember { mutableStateOf("") }

    // Store chat state per session for restoration when switching
    val sessionChatStates = remember { mutableStateMapOf<String, ChatViewState>() }
    val eventLogEvents = remember { mutableStateListOf<Event>() }

    LaunchedEffect(Unit) {
        EventBus.developerEvents.collect { event ->
            eventLogEvents.add(0, event)
            if (eventLogEvents.size > 1000) {
                eventLogEvents.removeAt(1000)
            }
        }
    }

    // Listen for indexing errors
    LaunchedEffect(Unit) {
        EventBus.internalEvents.collect { event ->
            if (event is IndexingErrorEvent) {
                when (event.errorType) {
                    IndexingErrorType.EMBEDDING_MODEL_NOT_FOUND -> {
                        val modelName = event.details["modelName"] ?: "unknown"
                        val provider = event.details["provider"] ?: "AI provider"
                        errorDialogTitle = "Embedding Model Not Found"
                        errorDialogMessage = "The embedding model '$modelName' was not found. Please pull or enable this embedding model from $provider before indexing."
                        showErrorDialog = true
                    }
                    IndexingErrorType.IO_ERROR -> {
                        errorDialogTitle = "IO Error"
                        errorDialogMessage = event.details["message"] ?: "An IO error occurred during indexing"
                        showErrorDialog = true
                    }
                    IndexingErrorType.UNKNOWN_ERROR -> {
                        errorDialogTitle = "Indexing Error"
                        errorDialogMessage = event.details["message"] ?: "An unknown error occurred during indexing"
                        showErrorDialog = true
                    }
                }
            }
        }
    }

    val scope = rememberCoroutineScope()

    val koin = get()

    val appContext = remember { koin.get<AppContext>() }
    val chatSessionService = remember { koin.get<ChatSessionService>() }

    val sessionManager = remember { koin.get<SessionManager>() }
    val sessionsViewModel = remember {
        koin.get<SessionsViewModel> {
            parametersOf(
                scope,
                sessionManager,
                {
                    chatSessionService.createSession(
                        ExecutionMode.STATEFUL_MODE,
                        ChatSession(
                            id = "",
                            title = "New Chat",
                            directiveId = null,
                        ),
                    ).id
                },
                {
                    // Callback when rename completes - sessions will auto-refresh via events
                },
            )
        }
    }
    val projectsViewModel = remember { koin.get<ProjectsViewModel> { parametersOf(scope) } }
    val settingsViewModel = remember { koin.get<SettingsViewModel> { parametersOf(scope) } }
    val updateViewModel = remember { koin.get<UpdateViewModel> { parametersOf(scope) } }

    LaunchedEffect(Unit) {
        val newSessionId = UUID.randomUUID().toString()
        sessionManager.createNewSession(newSessionId)
    }

    LaunchedEffect(Unit) {
        delay(5000)
        updateViewModel.checkForUpdates(silent = true)

        while (true) {
            delay(24 * 60 * 60 * 1000L) // 24 hours
            updateViewModel.checkForUpdates(silent = true)
        }
    }

    val activeSessionId = sessionManager.activeSessionId
    val chatViewModel = remember(activeSessionId) {
        activeSessionId?.let { sessionId ->
            sessionManager.getOrCreateChatViewModel(sessionId)
        }
    }

    var showProviderSetupDialog by remember { mutableStateOf(false) }
    var showSessionMemoryDialog by remember { mutableStateOf(false) }
    var sessionMemoryToShow by remember { mutableStateOf<io.askimo.core.chat.domain.SessionMemory?>(null) }

    LaunchedEffect(Unit) {
        if (appContext.getActiveProvider() == ModelProvider.UNKNOWN) {
            showProviderSetupDialog = true
        }
    }

    // Set message complete callback once per chatViewModel instance
    LaunchedEffect(chatViewModel) {
        chatViewModel?.setOnMessageCompleteCallback {
            StarPromptPreferences.incrementChatCount()
            if (StarPromptPreferences.shouldShowStarPrompt()) {
                showStarPromptDialog = true
            }
        }
    }

    // Theme state
    val themeMode by ThemePreferences.themeMode.collectAsState()
    val accentColor by ThemePreferences.accentColor.collectAsState()
    val fontSettings by ThemePreferences.fontSettings.collectAsState()
    val locale by ThemePreferences.locale.collectAsState()

    // React to locale changes - update menu bar and language settings
    LaunchedEffect(locale, frameWindowScope) {
        LocalizationManager.setLocale(locale)

        appContext.setLanguageDirective(locale)

        frameWindowScope?.let { scope ->
            NativeMenuBar.setup(
                frameWindowScope = scope,
                onShowAbout = { showAboutDialog = true },
                onShowEventLog = {
                    // Toggle between attached panel and detached window
                    if (!showEventLogPanel && !showEventLogWindow) {
                        showEventLogPanel = true // Default to attached
                    } else if (showEventLogPanel) {
                        showEventLogPanel = false // Close if already open
                    } else {
                        // If detached window is open, bring it to focus (handled by window manager)
                    }
                },
                onNewChat = {
                    sessionManager.clearActiveSession()
                    chatViewModel?.clearChat()
                    currentView = View.CHAT
                },
                onShowSettings = {
                    currentView = View.SETTINGS
                },
                onCheckForUpdates = {
                    updateViewModel.checkForUpdates(silent = false)
                },
            )
        }
    }

    var isSystemInDarkMode by remember { mutableStateOf(detectMacOSDarkMode()) }

    LaunchedEffect(themeMode) {
        if (themeMode == ThemeMode.SYSTEM) {
            isSystemInDarkMode = detectMacOSDarkMode()
        }
    }

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

    val customTypography = remember(fontSettings) {
        createCustomTypography(fontSettings)
    }

    val handleResumeSession: (String) -> Unit = { sessionId ->
        sessionManager.switchToSession(sessionId)

        currentView = View.CHAT
    }

    provideLocalization(locale = locale) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = customTypography,
        ) {
            // Main application structure: MenuBar → Body (Stack) → Footer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (currentView == View.SETTINGS) {
                        settingsViewWithSidebar(
                            onClose = {
                                currentView = previousView
                            },
                            settingsViewModel = settingsViewModel,
                        )
                    } else {
                        // Main View - With sidebar and content
                        // Main content area - supports event log docking at left/right/bottom
                        // Event Log Panel - LEFT position
                        if (showEventLogPanel && eventLogDockPosition == EventLogDockPosition.LEFT) {
                            eventLogPanel(
                                events = eventLogEvents,
                                onDetach = {
                                    showEventLogPanel = false
                                    showEventLogWindow = true
                                },
                                onClose = {
                                    showEventLogPanel = false
                                },
                                onClearEvents = {
                                    eventLogEvents.clear()
                                },
                                onDockPositionChange = { newPosition ->
                                    eventLogDockPosition = newPosition
                                },
                                currentDockPosition = eventLogDockPosition,
                                size = eventLogPanelSize,
                                onSizeChange = { newSize -> eventLogPanelSize = newSize },
                                modifier = Modifier.fillMaxHeight(),
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .onPreviewKeyEvent { keyEvent ->
                                        val shortcut = KeyMapManager.handleKeyEvent(keyEvent)

                                        when (shortcut) {
                                            AppShortcut.NEW_CHAT -> {
                                                chatViewModel?.clearChat()
                                                currentView = View.CHAT
                                                true
                                            }
                                            AppShortcut.SEARCH_IN_CHAT -> {
                                                if (currentView == View.CHAT && chatViewModel?.isSearchMode == false) {
                                                    chatViewModel.enableSearchMode()
                                                }
                                                true
                                            }
                                            AppShortcut.TOGGLE_CHAT_HISTORY -> {
                                                isSessionsExpanded = !isSessionsExpanded
                                                true
                                            }
                                            AppShortcut.OPEN_SETTINGS -> {
                                                previousView = currentView
                                                currentView = View.SETTINGS
                                                true
                                            }
                                            AppShortcut.STOP_AI_RESPONSE -> {
                                                if (chatViewModel?.isLoading == true) {
                                                    chatViewModel.cancelResponse()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            AppShortcut.QUIT_APPLICATION -> {
                                                showQuitDialog = true
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                            ) {
                                val currentSessionId = activeSessionId

                                Row(modifier = Modifier.fillMaxSize()) {
                                    BoxWithConstraints {
                                        // Calculate actual sidebar width from fraction
                                        // Min 200dp, max 35% of screen width, default ~20%
                                        val minSidebarWidth = 200.dp
                                        val maxSidebarWidthFraction = 0.35f
                                        val calculatedWidth = (maxWidth * sidebarWidthFraction).coerceIn(
                                            minSidebarWidth,
                                            maxWidth * maxSidebarWidthFraction,
                                        )

                                        navigationSidebar(
                                            isExpanded = isSidebarExpanded,
                                            width = calculatedWidth,
                                            currentView = currentView,
                                            isProjectsExpanded = isProjectsExpanded,
                                            isSessionsExpanded = isSessionsExpanded,
                                            projectsViewModel = projectsViewModel,
                                            sessionsViewModel = sessionsViewModel,
                                            currentSessionId = currentSessionId,
                                            fontScale = fontSettings.fontSize.scale,
                                            onToggleExpand = { isSidebarExpanded = !isSidebarExpanded },
                                            onNewChat = {
                                                chatViewModel?.clearChat()
                                                currentView = View.CHAT
                                            },
                                            onToggleProjects = { isProjectsExpanded = !isProjectsExpanded },
                                            onNewProject = {
                                                showNewProjectDialog = true
                                            },
                                            onSelectProject = { projectId ->
                                                selectedProjectId = projectId
                                                currentView = View.PROJECT_DETAIL
                                            },
                                            onToggleSessions = { isSessionsExpanded = !isSessionsExpanded },
                                            onNavigateToSessions = { currentView = View.SESSIONS },
                                            onResumeSession = handleResumeSession,
                                            onDeleteSession = { sessionId ->
                                                sessionsViewModel.deleteSessionWithCleanup(sessionId)
                                            },
                                            onStarSession = { sessionId, isStarred ->
                                                sessionsViewModel.updateSessionStarred(sessionId, isStarred)
                                            },
                                            onRenameSession = { sessionId, _ ->
                                                sessionsViewModel.showRenameDialog(sessionId)
                                            },
                                            onExportSession = { sessionId ->
                                                sessionsViewModel.exportSession(sessionId)
                                            },
                                            onShowSessionSummary = { sessionId ->
                                                // Query session memory from repository
                                                scope.launch {
                                                    val memory = withContext(Dispatchers.IO) {
                                                        val repository = DatabaseManager.getInstance().getSessionMemoryRepository()
                                                        repository.getBySessionId(sessionId)
                                                    }
                                                    sessionMemoryToShow = memory
                                                    showSessionMemoryDialog = true
                                                }
                                            },
                                            onNavigateToSettings = {
                                                previousView = currentView
                                                currentView = View.SETTINGS
                                            },
                                        )
                                    } // End BoxWithConstraints

                                    // Draggable divider
                                    if (isSidebarExpanded) {
                                        // Need to recalculate containerWidth for the divider
                                        BoxWithConstraints {
                                            val containerWidth = maxWidth

                                            Box(
                                                modifier = Modifier
                                                    .width(8.dp)
                                                    .fillMaxHeight()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                                                    .pointerInput(containerWidth) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            // Calculate new fraction based on drag
                                                            val dragWidthDp = (dragAmount.x / density).dp
                                                            val newFraction = sidebarWidthFraction + (dragWidthDp / containerWidth)
                                                            // Min 10%, max 35%
                                                            val coercedFraction = newFraction.coerceIn(0.10f, 0.35f)
                                                            sidebarWidthFraction = coercedFraction
                                                            // Save preference
                                                            ThemePreferences.setMainSidebarWidthFraction(coercedFraction)
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
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
                                        } // End BoxWithConstraints for divider
                                    }

                                    // Main content - only show when chatViewModel exists
                                    if (chatViewModel != null) {
                                        mainContent(
                                            currentView = currentView,
                                            chatViewModel = chatViewModel,
                                            sessionsViewModel = sessionsViewModel,
                                            projectsViewModel = projectsViewModel,
                                            appContext = appContext,
                                            sessionManager = sessionManager,
                                            onResumeSession = handleResumeSession,
                                            onNavigateToChat = {
                                                currentView = View.CHAT
                                            },
                                            onNavigateToSessions = {
                                                currentView = View.SESSIONS
                                            },
                                            onEditProject = { projectId ->
                                                editingProjectId = projectId
                                                showEditProjectDialog = true
                                            },
                                            activeSessionId = activeSessionId,
                                            sessionChatState = sessionChatStates[activeSessionId],
                                            onChatStateChange = { inputText, attachments, editingMessage ->
                                                activeSessionId?.let { sessionId ->
                                                    sessionChatStates[sessionId] = ChatViewState(
                                                        inputText = inputText,
                                                        attachments = attachments,
                                                        editingMessage = editingMessage,
                                                    )
                                                }
                                            },
                                            selectedProjectId = selectedProjectId,
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource("chat.no.active.session"),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                } // End Row (sidebar + divider + content)
                            }
                        } // End of main content column (chat/sessions)

                        // Event Log Panel - RIGHT position
                        if (showEventLogPanel && eventLogDockPosition == EventLogDockPosition.RIGHT) {
                            eventLogPanel(
                                events = eventLogEvents,
                                onDetach = {
                                    showEventLogPanel = false
                                    showEventLogWindow = true
                                },
                                onClose = {
                                    showEventLogPanel = false
                                },
                                onClearEvents = {
                                    eventLogEvents.clear()
                                },
                                onDockPositionChange = { newPosition ->
                                    eventLogDockPosition = newPosition
                                },
                                currentDockPosition = eventLogDockPosition,
                                size = eventLogPanelSize,
                                onSizeChange = { newSize -> eventLogPanelSize = newSize },
                                modifier = Modifier.fillMaxHeight(),
                            )
                        }
                    } // End of if-else (Settings OR Chat/Sessions)
                } // End of Row (Stack body)

                // Footer - Always visible at bottom
                footerBar(
                    onShowUpdateDetails = {
                        updateViewModel.showUpdateDialogForExistingRelease()
                    },
                    onConfigureAiProvider = {
                        settingsViewModel.onChangeProvider()
                    },
                )

                // Event Log Panel - BOTTOM position
                if (showEventLogPanel && eventLogDockPosition == EventLogDockPosition.BOTTOM) {
                    eventLogPanel(
                        events = eventLogEvents,
                        onDetach = {
                            showEventLogPanel = false
                            showEventLogWindow = true
                        },
                        onClose = {
                            showEventLogPanel = false
                        },
                        onClearEvents = {
                            eventLogEvents.clear()
                        },
                        onDockPositionChange = { newPosition ->
                            eventLogDockPosition = newPosition
                        },
                        currentDockPosition = eventLogDockPosition,
                        size = eventLogPanelSize,
                        onSizeChange = { newSize -> eventLogPanelSize = newSize },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } // End of main Column

            // Quit confirmation dialog
            if (showQuitDialog) {
                ComponentColors.themedAlertDialog(
                    onDismissRequest = { showQuitDialog = false },
                    title = { Text(stringResource("menu.quit") + "?") },
                    text = { Text(stringResource("session.delete.confirm")) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showQuitDialog = false
                                exitProcess(0)
                            },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text(stringResource("action.yes"))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showQuitDialog = false },
                            colors = ComponentColors.primaryTextButtonColors(),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text(stringResource("action.no"))
                        }
                    },
                )
            }

            // Provider setup required dialog
            if (showProviderSetupDialog) {
                ComponentColors.themedAlertDialog(
                    onDismissRequest = { },
                    title = {
                        Text(
                            text = stringResource("provider.setup.required.title"),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource("provider.setup.required.message"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showProviderSetupDialog = false
                                settingsViewModel.onChangeProvider(isInitialSetup = true)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text(stringResource("provider.setup.required.button"))
                        }
                    },
                )
            }

            if (settingsViewModel.showProviderDialog) {
                providerSelectionDialog(
                    viewModel = settingsViewModel,
                    onDismiss = { settingsViewModel.closeProviderDialog() },
                    onSave = { settingsViewModel.saveProvider() },
                )
            }

            // About Dialog
            if (showAboutDialog) {
                aboutDialog(onDismiss = { showAboutDialog = false })
            }

            if (updateViewModel.showUpdateDialog || updateViewModel.errorMessage != null) {
                updateCheckDialog(
                    viewModel = updateViewModel,
                    onDismiss = {
                        updateViewModel.dismissUpdateDialog()
                    },
                )
            }

            if (sessionsViewModel.showExportDialog) {
                exportSessionDialog(
                    sessionTitle = sessionsViewModel.exportSessionTitle,
                    selectedFormat = sessionsViewModel.exportFormat,
                    defaultFilename = sessionsViewModel.exportDefaultFilename,
                    onFormatChange = { format ->
                        sessionsViewModel.updateExportFormat(format)
                    },
                    onDismiss = { sessionsViewModel.dismissExportDialog() },
                    onExport = { fullPath ->
                        sessionsViewModel.executeExport(fullPath)
                    },
                )
            }

            sessionsViewModel.successMessage?.let { message ->
                Dialog(onDismissRequest = { sessionsViewModel.dismissSuccessMessage() }) {
                    Surface(
                        modifier = Modifier.width(450.dp),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 8.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp),
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = stringResource("session.export.success.title"),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Button(
                                onClick = { sessionsViewModel.dismissSuccessMessage() },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .pointerHoverIcon(PointerIcon.Hand),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(stringResource("action.ok"))
                            }
                        }
                    }
                }
            }

            // File Overwrite Confirmation Dialog
            if (sessionsViewModel.showOverwriteConfirmation) {
                Dialog(onDismissRequest = { sessionsViewModel.cancelOverwrite() }) {
                    Surface(
                        modifier = Modifier.width(450.dp),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 8.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp),
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = stringResource("file.overwrite.title"),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource("file.overwrite.message"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    sessionsViewModel.pendingExportPath?.let { path ->
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { sessionsViewModel.cancelOverwrite() },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                ) {
                                    Text(stringResource("action.cancel"))
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { sessionsViewModel.confirmOverwrite() },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                    ),
                                ) {
                                    Text(stringResource("file.overwrite.confirm"))
                                }
                            }
                        }
                    }
                }
            }

            // Rename Session Dialog
            if (sessionsViewModel.showRenameDialog) {
                renameSessionDialog(
                    currentTitle = sessionsViewModel.renameCurrentTitle,
                    onDismiss = { sessionsViewModel.dismissRenameDialog() },
                    onRename = { newTitle ->
                        sessionsViewModel.executeRename(newTitle)
                        // Refresh the session title in ChatViewModel if currently viewing this session
                        chatViewModel?.refreshSessionTitle()
                    },
                )
            }

            // Session Memory Dialog (Developer Mode)
            if (showSessionMemoryDialog) {
                sessionMemoryDialog(
                    sessionMemory = sessionMemoryToShow,
                    onDismiss = {
                        showSessionMemoryDialog = false
                        sessionMemoryToShow = null
                    },
                )
            }

            // Star Prompt Dialog (One-time after usage)
            if (showStarPromptDialog) {
                starPromptDialog(
                    onDismiss = {
                        StarPromptPreferences.markPromptDismissed()
                        showStarPromptDialog = false
                    },
                    onStar = {
                        StarPromptPreferences.markAsPrompted()
                        showStarPromptDialog = false
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().browse(
                                    java.net.URI("https://github.com/haiphucnguyen/askimo"),
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                )
            }

            // Error Dialog (for indexing errors, etc.)
            if (showErrorDialog) {
                errorDialog(
                    title = errorDialogTitle,
                    message = errorDialogMessage,
                    onDismiss = {
                        showErrorDialog = false
                    },
                )
            }

            // New Project Dialog
            if (showNewProjectDialog) {
                newProjectDialog(
                    onDismiss = { showNewProjectDialog = false },
                    onCreateProject = { _, _, _ ->
                        projectsViewModel.refresh()
                    },
                )
            }

            // Edit Project Dialog
            if (showEditProjectDialog) {
                val projectToEdit = editingProjectId?.let { id ->
                    projectsViewModel.projects.find { it.id == id }
                }
                if (projectToEdit != null) {
                    editProjectDialog(
                        project = projectToEdit,
                        onDismiss = {
                            showEditProjectDialog = false
                            editingProjectId = null
                        },
                        onSave = { projectId, name, description, knowledgeSources ->
                            projectsViewModel.updateProject(projectId, name, description, knowledgeSources)
                            showEditProjectDialog = false
                            editingProjectId = null
                        },
                    )
                }
            }

            // Event Log Window (Developer Mode - Detached)
            if (showEventLogWindow) {
                eventLogWindow(
                    events = eventLogEvents,
                    onCloseRequest = { showEventLogWindow = false },
                    onReattach = {
                        showEventLogWindow = false
                        showEventLogPanel = true
                    },
                )
            }
        } // MaterialTheme
    } // ProvideLocalization
}

@Composable
fun mainContent(
    currentView: View,
    chatViewModel: ChatViewModel,
    sessionsViewModel: SessionsViewModel,
    projectsViewModel: ProjectsViewModel,
    appContext: AppContext,
    sessionManager: SessionManager,
    onResumeSession: (String) -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onEditProject: (String) -> Unit,
    activeSessionId: String?,
    sessionChatState: ChatViewState?,
    onChatStateChange: (TextFieldValue, List<FileAttachmentDTO>, ChatMessageDTO?) -> Unit,
    selectedProjectId: String?,
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (currentView) {
            View.CHAT, View.NEW_CHAT -> {
                val configInfo = appContext.getConfigInfo()
                chatView(
                    messages = chatViewModel.messages,
                    onSendMessage = { message, fileAttachments, editingMsg ->
                        chatViewModel.sendOrEditMessage(
                            message = message,
                            attachments = fileAttachments,
                            editingMessage = editingMsg,
                        )
                    },
                    onStopResponse = { chatViewModel.cancelResponse() },
                    isLoading = chatViewModel.isLoading,
                    isThinking = chatViewModel.isThinking,
                    thinkingElapsedSeconds = chatViewModel.thinkingElapsedSeconds,
                    spinnerFrame = chatViewModel.getSpinnerFrame(),
                    errorMessage = chatViewModel.errorMessage,
                    provider = configInfo.provider.name,
                    model = configInfo.model,
                    hasMoreMessages = chatViewModel.hasMoreMessages,
                    isLoadingPrevious = chatViewModel.isLoadingPrevious,
                    onLoadPrevious = { chatViewModel.loadPreviousMessages() },
                    isSearchMode = chatViewModel.isSearchMode,
                    searchQuery = chatViewModel.searchQuery,
                    searchResults = chatViewModel.searchResults,
                    currentSearchResultIndex = chatViewModel.currentSearchResultIndex,
                    isSearching = chatViewModel.isSearching,
                    onSearch = { query -> chatViewModel.searchMessages(query) },
                    onClearSearch = { chatViewModel.clearSearch() },
                    onNextSearchResult = { chatViewModel.nextSearchResult() },
                    onPreviousSearchResult = { chatViewModel.previousSearchResult() },
                    onJumpToMessage = { messageId, timestamp ->
                        chatViewModel.jumpToMessage(messageId, timestamp)
                    },
                    selectedDirective = chatViewModel.selectedDirective,
                    onDirectiveSelected = { directiveId -> chatViewModel.setDirective(directiveId) },
                    onUpdateAIMessage = { messageId, newContent ->
                        chatViewModel.updateAIMessageContent(messageId, newContent)
                    },
                    initialInputText = sessionChatState?.inputText ?: TextFieldValue(""),
                    initialAttachments = sessionChatState?.attachments ?: emptyList(),
                    initialEditingMessage = sessionChatState?.editingMessage,
                    onStateChange = onChatStateChange,
                    sessionId = activeSessionId,
                    sessionTitle = chatViewModel?.sessionTitle,
                    project = chatViewModel?.project,
                    onRenameSession = { sessionId ->
                        sessionsViewModel.showRenameDialog(sessionId)
                    },
                    onExportSession = { sessionId ->
                        sessionsViewModel.exportSession(sessionId)
                    },
                    onDeleteSession = { sessionId ->
                        sessionsViewModel.deleteSessionWithCleanup(sessionId)
                    },
                    onRetryMessage = { failedMessageId ->
                        chatViewModel.retryMessage(failedMessageId)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            View.SESSIONS -> sessionsView(
                viewModel = sessionsViewModel,
                onResumeSession = onResumeSession,
                modifier = Modifier.fillMaxSize(),
            )
            View.PROJECTS -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource("chat.no.active.session"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            View.PROJECT_DETAIL -> {
                val projectId = selectedProjectId
                if (projectId != null) {
                    val project = projectsViewModel.projects.find { it.id == projectId }
                    if (project != null) {
                        projectView(
                            project = project,
                            appContext = appContext,
                            onStartChat = { projId, message, attachments ->
                                // Delegate to SessionManager to handle business logic
                                sessionManager.createProjectSessionAndSendMessage(
                                    projectId = projId,
                                    message = message,
                                    attachments = attachments,
                                    onComplete = { onNavigateToChat() },
                                )
                            },
                            onResumeSession = onResumeSession,
                            onDeleteSession = { sessionId, projectId ->
                                scope.launch {
                                    // Delete session from database
                                    withContext(Dispatchers.IO) {
                                        DatabaseManager.getInstance()
                                            .getChatSessionRepository()
                                            .deleteSession(sessionId)
                                    }

                                    EventBus.post(
                                        ProjectSessionsRefreshRequested(
                                            projectId = projectId,
                                            reason = "Session $sessionId deleted from project",
                                        ),
                                    )
                                }
                            },
                            onRenameSession = { sessionId, _ ->
                                sessionsViewModel.showRenameDialog(sessionId)
                            },
                            onExportSession = { sessionId ->
                                sessionsViewModel.exportSession(sessionId)
                            },
                            onEditProject = onEditProject,
                            onDeleteProject = { projectId ->
                                projectsViewModel.deleteProject(projectId)
                                onNavigateToSessions()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Project not found, show error
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource("project.not.found"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    // No project selected, show error
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource("project.not.selected"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            View.SETTINGS -> {
            }
        }
    }
}
