/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.shell

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.Project
import io.askimo.core.db.DatabaseManager
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectsRefreshEvent
import io.askimo.core.event.internal.SessionsRefreshEvent
import io.askimo.core.user.domain.UserProfile
import io.askimo.desktop.View
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.LocalFontScale
import io.askimo.desktop.common.ui.themedTooltip
import io.askimo.desktop.project.ProjectsViewModel
import io.askimo.desktop.project.deleteProjectDialog
import io.askimo.desktop.project.newProjectDialog
import io.askimo.desktop.session.SessionActionMenu
import io.askimo.desktop.session.SessionsViewModel
import io.askimo.desktop.util.Platform
import org.jetbrains.skia.Image
import java.io.File
import org.jetbrains.skia.Image as SkiaImage

/**
 * Navigation sidebar component with collapsible/expandable functionality.
 * Shows app logo, navigation items, and chat sessions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun navigationSidebar(
    isExpanded: Boolean,
    width: Dp,
    currentView: View,
    isProjectsExpanded: Boolean,
    isSessionsExpanded: Boolean,
    projectsViewModel: ProjectsViewModel,
    sessionsViewModel: SessionsViewModel,
    currentSessionId: String?,
    currentProjectId: String?,
    userProfile: UserProfile?,
    onToggleExpand: () -> Unit,
    onNewChat: () -> Unit,
    onToggleProjects: () -> Unit,
    onNewProject: () -> Unit,
    onSelectProject: (String) -> Unit,
    onToggleSessions: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStarSession: (String, Boolean) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (String) -> Unit,
    onShowSessionSummary: (String) -> Unit = {},
    onEditUserProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    // Animated width for smooth transition
    val targetWidth = if (isExpanded) width else 72.dp
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 300),
    )

    if (isExpanded) {
        expandedNavigationSidebar(
            animatedWidth = animatedWidth,
            currentView = currentView,
            isProjectsExpanded = isProjectsExpanded,
            isSessionsExpanded = isSessionsExpanded,
            projectsViewModel = projectsViewModel,
            sessionsViewModel = sessionsViewModel,
            currentSessionId = currentSessionId,
            currentProjectId = currentProjectId,
            userProfile = userProfile,
            onToggleExpand = onToggleExpand,
            onNewChat = onNewChat,
            onToggleProjects = onToggleProjects,
            onNewProject = onNewProject,
            onSelectProject = onSelectProject,
            onToggleSessions = onToggleSessions,
            onNavigateToSessions = onNavigateToSessions,
            onResumeSession = onResumeSession,
            onDeleteSession = onDeleteSession,
            onStarSession = onStarSession,
            onRenameSession = onRenameSession,
            onExportSession = onExportSession,
            onShowSessionSummary = onShowSessionSummary,
            onEditUserProfile = onEditUserProfile,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAbout = onNavigateToAbout,
        )
    } else {
        collapsedNavigationSidebar(
            animatedWidth = animatedWidth,
            currentView = currentView,
            onToggleExpand = onToggleExpand,
            onNewChat = onNewChat,
            onNavigateToSessions = onNavigateToSessions,
            onEditUserProfile = onEditUserProfile,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAbout = onNavigateToAbout,
        )
    }
}

@Composable
private fun expandedNavigationSidebar(
    animatedWidth: Dp,
    currentView: View,
    isProjectsExpanded: Boolean,
    isSessionsExpanded: Boolean,
    projectsViewModel: ProjectsViewModel,
    sessionsViewModel: SessionsViewModel,
    currentSessionId: String?,
    currentProjectId: String?,
    userProfile: UserProfile?,
    onToggleExpand: () -> Unit,
    onNewChat: () -> Unit,
    onToggleProjects: () -> Unit,
    onNewProject: () -> Unit,
    onSelectProject: (String) -> Unit,
    onToggleSessions: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStarSession: (String, Boolean) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (String) -> Unit,
    onShowSessionSummary: (String) -> Unit = {},
    onEditUserProfile: () -> Unit, // Add this
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit, // Add this
) {
    val fontScale = LocalFontScale.current

    Column(
        modifier = Modifier
            .width(animatedWidth)
            .fillMaxHeight()
            .background(ComponentColors.sidebarSurfaceColor()),
    ) {
        // Header with logo and collapse button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComponentColors.sidebarHeaderColor())
                .padding((16 * fontScale).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((12 * fontScale).dp),
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
                    modifier = Modifier.size((48 * fontScale).dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Askimo AI",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            themedTooltip(
                text = stringResource("sidebar.collapse"),
            ) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuOpen,
                        contentDescription = stringResource("sidebar.collapse"),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider()

        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = (8 * fontScale).dp),
        ) {
            // New Chat
            themedTooltip(
                text = stringResource("chat.new.tooltip", Platform.modifierKey),
            ) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text(stringResource("chat.new"), style = MaterialTheme.typography.labelLarge) },
                    selected = false,
                    onClick = onNewChat,
                    modifier = Modifier
                        .padding(horizontal = (12 * fontScale).dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.navigationDrawerItemColors(),
                )
            }

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                label = { Text(stringResource("project.title"), style = MaterialTheme.typography.labelLarge) },
                selected = currentView == View.PROJECTS,
                onClick = onToggleProjects,
                badge = {
                    Icon(
                        imageVector = if (isProjectsExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (isProjectsExpanded) "Collapse" else "Expand",
                        tint = if (currentView == View.PROJECTS) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                modifier = Modifier
                    .padding(horizontal = (12 * fontScale).dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ComponentColors.navigationDrawerItemColors(),
            )
            // Projects list (collapsible content)
            if (isProjectsExpanded) {
                projectsList(
                    projectsViewModel = projectsViewModel,
                    currentProjectId = currentProjectId,
                    onNewProject = onNewProject,
                    onSelectProject = onSelectProject,
                )
            }

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                label = { Text(stringResource("chat.sessions"), style = MaterialTheme.typography.labelLarge) },
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
                        tint = if (currentView == View.SESSIONS) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                modifier = Modifier
                    .padding(horizontal = (12 * fontScale).dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ComponentColors.navigationDrawerItemColors(),
            )

            // Sessions list (collapsible content)
            if (isSessionsExpanded) {
                sessionsList(
                    sessionsViewModel = sessionsViewModel,
                    currentSessionId = currentSessionId,
                    onNavigateToSessions = onNavigateToSessions,
                    onResumeSession = onResumeSession,
                    onDeleteSession = onDeleteSession,
                    onStarSession = onStarSession,
                    onRenameSession = onRenameSession,
                    onExportSession = onExportSession,
                    onShowSessionSummary = onShowSessionSummary,
                    projectsViewModel = projectsViewModel,
                )
            }
        }

        // User Profile Section at bottom (replaces settings button)
        HorizontalDivider()
        userProfileSection(
            profile = userProfile,
            currentView = currentView,
            onEditUserProfile = onEditUserProfile,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAbout = onNavigateToAbout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (12 * fontScale).dp, vertical = (8 * fontScale).dp),
        )
    }
}

@Composable
private fun collapsedNavigationSidebar(
    animatedWidth: Dp,
    currentView: View,
    onToggleExpand: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onEditUserProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    val fontScale = LocalFontScale.current

    Column(
        modifier = Modifier
            .width(animatedWidth)
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
                .padding(vertical = (16 * fontScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            themedTooltip(
                text = stringResource("sidebar.expand"),
            ) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource("sidebar.expand"),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = (8 * fontScale).dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // New Chat
            themedTooltip(
                text = stringResource("chat.new.tooltip", Platform.modifierKey),
            ) {
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = stringResource("chat.new")) },
                    label = null,
                    selected = false,
                    onClick = onNewChat,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.navigationRailItemColors(),
                )
            }

            // Sessions
            themedTooltip(
                text = stringResource("chat.sessions.tooltip"),
            ) {
                NavigationRailItem(
                    icon = { Icon(Icons.Default.History, contentDescription = stringResource("chat.sessions")) },
                    label = null,
                    selected = currentView == View.SESSIONS,
                    onClick = onNavigateToSessions,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.navigationRailItemColors(),
                )
            }
        }

        // User Profile Icon at bottom (shows menu on click)
        HorizontalDivider()
        var showUserMenu by remember { mutableStateOf(false) }

        Box {
            themedTooltip(
                text = stringResource("user.profile.menu"),
            ) {
                NavigationRailItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource("user.profile.menu"),
                        )
                    },
                    label = null,
                    selected = false,
                    onClick = { showUserMenu = true },
                    modifier = Modifier
                        .padding(vertical = (8 * fontScale).dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.navigationRailItemColors(),
                )
            }

            // User menu dropdown
            ComponentColors.themedDropdownMenu(
                expanded = showUserMenu,
                onDismissRequest = { showUserMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource("user.menu.edit_profile")) },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    onClick = {
                        showUserMenu = false
                        onEditUserProfile()
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
                DropdownMenuItem(
                    text = { Text(stringResource("user.menu.settings")) },
                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = {
                        showUserMenu = false
                        onNavigateToSettings()
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
                DropdownMenuItem(
                    text = { Text(stringResource("user.menu.about")) },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = {
                        showUserMenu = false
                        onNavigateToAbout()
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}

@Composable
private fun projectsList(
    projectsViewModel: ProjectsViewModel,
    currentProjectId: String?,
    onNewProject: () -> Unit,
    onSelectProject: (String) -> Unit,
) {
    val fontScale = LocalFontScale.current
    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    Column(
        modifier = Modifier.padding(
            start = (32 * fontScale).dp,
            end = (12 * fontScale).dp,
            top = (4 * fontScale).dp,
            bottom = (4 * fontScale).dp,
        ),
    ) {
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = {
                Text(
                    stringResource("project.new"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            selected = false,
            onClick = onNewProject,
            modifier = Modifier
                .padding(vertical = (2 * fontScale).dp)
                .pointerHoverIcon(PointerIcon.Hand),
            colors = ComponentColors.navigationDrawerItemColors(),
        )

        if (projectsViewModel.projects.isEmpty()) {
            // No projects yet
            Text(
                text = stringResource("project.none"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = (16 * fontScale).dp,
                    vertical = (8 * fontScale).dp,
                ),
            )
        } else {
            // Display projects
            projectsViewModel.projects.forEach { project ->
                projectItemWithMenu(
                    project = project,
                    isSelected = project.id == currentProjectId,
                    onProjectClick = { onSelectProject(project.id) },
                    onDeleteProject = { projectToDelete = it },
                )
            }
        }
    }

    // Delete confirmation dialog
    projectToDelete?.let { project ->
        deleteProjectDialog(
            projectName = project.name,
            onConfirm = {
                projectsViewModel.deleteProject(project.id)
                projectToDelete = null
            },
            onDismiss = { projectToDelete = null },
        )
    }
}

/**
 * Reusable label composable for navigation items with a menu button.
 * Displays text with ellipsis and a three-dot menu button on the right.
 */
@Composable
private fun navigationItemLabelWithMenu(
    text: String,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        Box(
            modifier = Modifier.padding(start = 4.dp),
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(24.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun projectItemWithMenu(
    project: Project,
    isSelected: Boolean,
    onProjectClick: () -> Unit,
    onDeleteProject: (Project) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val tooltipText = if (project.description.isNullOrBlank()) {
        project.name
    } else {
        "${project.name}\n\n${project.description}"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        themedTooltip(
            text = tooltipText,
        ) {
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                label = {
                    navigationItemLabelWithMenu(
                        text = project.name,
                        onMenuClick = { showMenu = true },
                    )
                },
                selected = isSelected,
                onClick = onProjectClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ComponentColors.navigationDrawerItemColors(),
            )
        }

        Box(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        ) {
            ComponentColors.themedDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource("project.delete")) },
                    onClick = {
                        showMenu = false
                        onDeleteProject(project)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}

@Composable
private fun sessionsList(
    sessionsViewModel: SessionsViewModel,
    currentSessionId: String?,
    onNavigateToSessions: () -> Unit,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStarSession: (String, Boolean) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (String) -> Unit,
    onShowSessionSummary: (String) -> Unit = {},
    projectsViewModel: ProjectsViewModel,
) {
    val fontScale = LocalFontScale.current
    var isStarredExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.padding(
            start = (32 * fontScale).dp,
            end = (12 * fontScale).dp,
            top = (4 * fontScale).dp,
            bottom = (4 * fontScale).dp,
        ),
    ) {
        if (sessionsViewModel.recentSessions.isEmpty()) {
            Text(
                text = "No sessions yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = (16 * fontScale).dp,
                    vertical = (8 * fontScale).dp,
                ),
            )
        } else {
            val starredSessions = sessionsViewModel.recentSessions.filter { it.isStarred }
            val unstarredSessions = sessionsViewModel.recentSessions.filter { !it.isStarred }

            // Starred section (collapsible)
            if (starredSessions.isNotEmpty()) {
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    label = {
                        Text(
                            "Starred (${starredSessions.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    selected = false,
                    onClick = { isStarredExpanded = !isStarredExpanded },
                    badge = {
                        Icon(
                            imageVector = if (isStarredExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = if (isStarredExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .padding(vertical = (2 * fontScale).dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.navigationDrawerItemColors(),
                )

                // Starred sessions list
                if (isStarredExpanded) {
                    Column(
                        modifier = Modifier.padding(
                            start = (16 * fontScale).dp,
                        ),
                    ) {
                        starredSessions.forEach { session ->
                            sessionItemWithMenu(
                                session = session,
                                isSelected = session.id == currentSessionId,
                                onResumeSession = onResumeSession,
                                onDeleteSession = onDeleteSession,
                                onStarSession = onStarSession,
                                onRenameSession = onRenameSession,
                                onExportSession = onExportSession,
                                onShowSessionSummary = onShowSessionSummary,
                                availableProjects = projectsViewModel.projects,
                            )
                        }
                    }
                }

                // Divider between starred and unstarred
                if (unstarredSessions.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            vertical = (8 * fontScale).dp,
                            horizontal = (8 * fontScale).dp,
                        ),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }

            // Unstarred sessions (always visible when sessions expanded)
            unstarredSessions.forEach { session ->
                sessionItemWithMenu(
                    session = session,
                    isSelected = session.id == currentSessionId,
                    onResumeSession = onResumeSession,
                    onDeleteSession = onDeleteSession,
                    onStarSession = onStarSession,
                    onRenameSession = onRenameSession,
                    onExportSession = onExportSession,
                    onShowSessionSummary = onShowSessionSummary,
                    availableProjects = projectsViewModel.projects,
                )
            }

            // Show More button if there are more sessions than the max displayed
            if (sessionsViewModel.totalSessionCount > SessionsViewModel.MAX_SIDEBAR_SESSIONS) {
                NavigationDrawerItem(
                    icon = null,
                    label = {
                        Text(
                            text = "More...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    selected = false,
                    onClick = onNavigateToSessions,
                    modifier = Modifier
                        .padding(vertical = (2 * fontScale).dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    colors = ComponentColors.navigationDrawerItemColors(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun sessionItemWithMenu(
    session: ChatSession,
    isSelected: Boolean,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStarSession: (String, Boolean) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (String) -> Unit,
    onShowSessionSummary: (String) -> Unit = {},
    availableProjects: List<Project>,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var sessionIdToMove by remember { mutableStateOf<String?>(null) }

    val sessionRepository = remember { DatabaseManager.getInstance().getChatSessionRepository() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        themedTooltip(
            text = session.title,
        ) {
            NavigationDrawerItem(
                icon = null,
                label = {
                    navigationItemLabelWithMenu(
                        text = session.title,
                        onMenuClick = { showMenu = true },
                    )
                },
                selected = isSelected,
                onClick = { onResumeSession(session.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ComponentColors.navigationDrawerItemColors(),
            )
        }

        Box(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        ) {
            ComponentColors.themedDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                SessionActionMenu.sidebarMenu(
                    isStarred = session.isStarred,
                    projects = availableProjects,
                    onExport = { onExportSession(session.id) },
                    onRename = { onRenameSession(session.id, session.title ?: "") },
                    onStar = { onStarSession(session.id, !session.isStarred) },
                    onDelete = { onDeleteSession(session.id) },
                    onMoveToNewProject = {
                        sessionIdToMove = session.id
                        showNewProjectDialog = true
                    },
                    onMoveToExistingProject = { selectedProject ->
                        // Associate session with existing project
                        sessionRepository.updateSessionProject(session.id, selectedProject.id)
                        // Publish events to refresh both projects and sessions
                        EventBus.post(
                            ProjectsRefreshEvent(
                                reason = "Session ${session.id} moved to project ${selectedProject.id}",
                            ),
                        )
                        EventBus.post(
                            SessionsRefreshEvent(
                                reason = "Session ${session.id} moved to project ${selectedProject.id}",
                            ),
                        )
                    },
                    onShowSessionSummary = { onShowSessionSummary(session.id) },
                    onDismiss = { showMenu = false },
                )
            }
        }
    }

    // New Project Dialog
    if (showNewProjectDialog && sessionIdToMove != null) {
        newProjectDialog(
            onDismiss = {
                showNewProjectDialog = false
                sessionIdToMove = null
            },
            onCreateProject = { name, description ->
                // Project is already created in the dialog with all knowledge sources
                val projectRepository = DatabaseManager.getInstance().getProjectRepository()
                val createdProject = projectRepository.findProjectByName(name)

                if (createdProject != null) {
                    sessionRepository.updateSessionProject(sessionIdToMove!!, createdProject.id)
                    // Publish events to refresh both projects and sessions
                    EventBus.post(
                        ProjectsRefreshEvent(
                            reason = "Session $sessionIdToMove moved to new project ${createdProject.id}",
                        ),
                    )
                    EventBus.post(
                        SessionsRefreshEvent(
                            reason = "Session $sessionIdToMove moved to new project ${createdProject.id}",
                        ),
                    )
                }

                showNewProjectDialog = false
                sessionIdToMove = null
            },
        )
    }
}

/**
 * User profile section at bottom of navigation sidebar.
 * Shows user avatar, name, occupation, and a menu for Edit Profile, Settings, and About.
 */
@Composable
private fun userProfileSection(
    profile: UserProfile?,
    currentView: View,
    onEditUserProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val fontScale = LocalFontScale.current

    // Load avatar image if available
    val avatarImage = remember(profile?.preferences?.get("avatarPath")) {
        profile?.preferences?.get("avatarPath")?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { showMenu = true })
                .pointerHoverIcon(PointerIcon.Hand)
                .padding((8 * fontScale).dp),
            horizontalArrangement = Arrangement.spacedBy((12 * fontScale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // User Avatar
            Box(
                modifier = Modifier
                    .size((40 * fontScale).dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarImage != null) {
                    Image(
                        bitmap = avatarImage,
                        contentDescription = stringResource("user.profile.avatar"),
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    val initials = profile?.name?.split(" ")
                        ?.mapNotNull { it.firstOrNull()?.uppercase() }
                        ?.take(2)
                        ?.joinToString("") ?: "?"

                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // User Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy((2 * fontScale).dp),
            ) {
                Text(
                    text = profile?.name ?: stringResource("user.profile.default_name"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                profile?.occupation?.let { occupation ->
                    Text(
                        text = occupation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Dropdown Menu
        ComponentColors.themedDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource("user.menu.edit_profile")) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                    )
                },
                onClick = {
                    showMenu = false
                    onEditUserProfile()
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )

            DropdownMenuItem(
                text = { Text(stringResource("user.menu.settings")) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                    )
                },
                onClick = {
                    showMenu = false
                    onNavigateToSettings()
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )

            DropdownMenuItem(
                text = { Text(stringResource("user.menu.about")) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                    )
                },
                onClick = {
                    showMenu = false
                    onNavigateToAbout()
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
        }
    }
}
