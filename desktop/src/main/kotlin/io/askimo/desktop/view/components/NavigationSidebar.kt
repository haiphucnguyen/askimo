/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.Project
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.util.Platform
import io.askimo.desktop.view.View
import io.askimo.desktop.viewmodel.ProjectsViewModel
import io.askimo.desktop.viewmodel.SessionsViewModel
import org.jetbrains.skia.Image

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
    fontScale: Float,
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
    onNavigateToSettings: () -> Unit,
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
            fontScale = fontScale,
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
            onNavigateToSettings = onNavigateToSettings,
        )
    } else {
        collapsedNavigationSidebar(
            animatedWidth = animatedWidth,
            currentView = currentView,
            fontScale = fontScale,
            onToggleExpand = onToggleExpand,
            onNewChat = onNewChat,
            onNavigateToSessions = onNavigateToSessions,
            onNavigateToSettings = onNavigateToSettings,
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
    fontScale: Float,
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
    onNavigateToSettings: () -> Unit,
) {
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
                    fontScale = fontScale,
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
                    fontScale = fontScale,
                    onNavigateToSessions = onNavigateToSessions,
                    onResumeSession = onResumeSession,
                    onDeleteSession = onDeleteSession,
                    onStarSession = onStarSession,
                    onRenameSession = onRenameSession,
                    onExportSession = onExportSession,
                )
            }
        }

        // Settings at bottom
        HorizontalDivider()
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource("settings.title"), style = MaterialTheme.typography.labelLarge) },
            selected = currentView == View.SETTINGS,
            onClick = onNavigateToSettings,
            modifier = Modifier
                .padding(horizontal = (12 * fontScale).dp, vertical = (8 * fontScale).dp)
                .pointerHoverIcon(PointerIcon.Hand),
            colors = ComponentColors.navigationDrawerItemColors(),
        )
    }
}

@Composable
private fun collapsedNavigationSidebar(
    animatedWidth: Dp,
    currentView: View,
    fontScale: Float,
    onToggleExpand: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
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

        // Settings at bottom
        HorizontalDivider()
        themedTooltip(
            text = stringResource("settings.title"),
        ) {
            NavigationRailItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = stringResource("settings.title")) },
                label = null,
                selected = currentView == View.SETTINGS,
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .padding(vertical = (8 * fontScale).dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ComponentColors.navigationRailItemColors(),
            )
        }
    }
}

@Composable
private fun projectsList(
    projectsViewModel: ProjectsViewModel,
    fontScale: Float,
    onNewProject: () -> Unit,
    onSelectProject: (String) -> Unit,
) {
    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    Column(
        modifier = Modifier.padding(
            start = (32 * fontScale).dp,
            end = (12 * fontScale).dp,
            top = (4 * fontScale).dp,
            bottom = (4 * fontScale).dp,
        ),
    ) {
        if (projectsViewModel.projects.isEmpty()) {
            // No projects yet
            Text(
                text = "No projects yet",
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
                    fontScale = fontScale,
                    onProjectClick = { onSelectProject(project.id) },
                    onDeleteProject = { projectToDelete = it },
                )
            }
        }

        // New Project button
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
    }

    // Delete confirmation dialog
    projectToDelete?.let { project ->
        deleteProjectConfirmationDialog(
            projectName = project.name,
            onConfirm = {
                projectsViewModel.deleteProject(project.id)
                projectToDelete = null
            },
            onDismiss = { projectToDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun projectItemWithMenu(
    project: Project,
    fontScale: Float,
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        Box(
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            IconButton(
                                onClick = { showMenu = true },
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
                },
                selected = false,
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
private fun deleteProjectConfirmationDialog(
    projectName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(450.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title with warning icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = stringResource("project.delete.confirm.title"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Confirmation message
                Text(
                    text = stringResource("project.delete.confirm.message", projectName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ComponentColors.primaryTextButtonColors(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("project.delete.confirm.cancel"))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(stringResource("project.delete.confirm.button"))
                    }
                }
            }
        }
    }
}

@Composable
private fun sessionsList(
    sessionsViewModel: SessionsViewModel,
    currentSessionId: String?,
    fontScale: Float,
    onNavigateToSessions: () -> Unit,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStarSession: (String, Boolean) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (String) -> Unit,
) {
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
                            tint = MaterialTheme.colorScheme.primary,
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
                            color = MaterialTheme.colorScheme.primary,
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
) {
    var showMenu by remember { mutableStateOf(false) }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        Box(
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            IconButton(
                                onClick = { showMenu = true },
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
                DropdownMenuItem(
                    text = { Text(stringResource("session.export")) },
                    onClick = {
                        showMenu = false
                        onExportSession(session.id)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
                DropdownMenuItem(
                    text = { Text(stringResource("session.rename.title")) },
                    onClick = {
                        showMenu = false
                        onRenameSession(session.id, session.title ?: "")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
                DropdownMenuItem(
                    text = { Text(if (session.isStarred) stringResource("session.unstar") else stringResource("session.star")) },
                    onClick = {
                        showMenu = false
                        onStarSession(session.id, !session.isStarred)
                    },
                    leadingIcon = {
                        Icon(
                            if (session.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (session.isStarred) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
                DropdownMenuItem(
                    text = { Text(stringResource("action.delete")) },
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
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}
