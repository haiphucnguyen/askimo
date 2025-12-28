/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.Project
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.config.AppConfig
import io.askimo.core.context.AppContext
import io.askimo.core.db.DatabaseManager
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectReIndexEvent
import io.askimo.core.event.internal.ProjectSessionsRefreshRequested
import io.askimo.core.event.internal.SessionsRefreshRequested
import io.askimo.core.logging.logger
import io.askimo.core.util.TimeUtil
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors
import io.askimo.desktop.view.components.SessionActionMenu
import io.askimo.desktop.view.components.chatInputField
import io.askimo.desktop.view.components.deleteProjectDialog
import io.askimo.desktop.view.components.newProjectDialog
import io.askimo.desktop.view.components.themedTooltip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val log = logger("ProjectView")

/**
 * Project view showing project details and chat interface.
 */
@Composable
fun projectView(
    project: Project,
    appContext: AppContext,
    onStartChat: (projectId: String, message: String, attachments: List<FileAttachmentDTO>) -> Unit,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (sessionId: String, projectId: String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (String) -> Unit,
    onEditProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var attachments by remember { mutableStateOf<List<FileAttachmentDTO>>(emptyList()) }
    var projectSessions by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val projectRepository = remember { DatabaseManager.getInstance().getProjectRepository() }
    val sessionRepository = remember { DatabaseManager.getInstance().getChatSessionRepository() }

    // Load all projects for Move to Project functionality
    var allProjects by remember { mutableStateOf<List<Project>>(emptyList()) }
    LaunchedEffect(Unit) {
        allProjects = withContext(Dispatchers.IO) {
            projectRepository.getAllProjects()
        }
    }

    // Load sessions for this project initially
    LaunchedEffect(project.id) {
        projectSessions = withContext(Dispatchers.IO) {
            sessionRepository.getSessionsByProjectId(project.id)
        }
    }

    // Listen for project sessions refresh events
    LaunchedEffect(project.id) {
        EventBus.internalEvents.collect { event ->
            if (event is ProjectSessionsRefreshRequested && event.projectId == project.id) {
                projectSessions = withContext(Dispatchers.IO) {
                    sessionRepository.getSessionsByProjectId(project.id)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Box {
                themedTooltip(text = stringResource("project.menu.tooltip")) {
                    IconButton(
                        onClick = { showProjectMenu = true },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource("project.menu.tooltip"),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                ComponentColors.themedDropdownMenu(
                    expanded = showProjectMenu,
                    onDismissRequest = { showProjectMenu = false },
                ) {
                    SessionActionMenu.projectActionMenu(
                        onEditProject = {
                            onEditProject(project.id)
                            showProjectMenu = false
                        },
                        onDeleteProject = {
                            showDeleteDialog = true
                            showProjectMenu = false
                        },
                        onReindexProject = if (AppConfig.developer.enabled && AppConfig.developer.active) {
                            {
                                EventBus.post(
                                    ProjectReIndexEvent(
                                        projectId = project.id,
                                        reason = "Manual re-index requested by user from project menu",
                                    ),
                                )
                                showProjectMenu = false
                            }
                        } else {
                            null
                        },
                        onDismiss = { showProjectMenu = false },
                    )
                }
            }
        }

        // Project Description (if exists)
        project.description?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }

        chatInputField(
            inputText = inputText,
            onInputTextChange = { inputText = it },
            attachments = attachments,
            onAttachmentsChange = { attachments = it },
            onSendMessage = {
                if (inputText.text.isNotBlank()) {
                    onStartChat(project.id, inputText.text, attachments)
                    inputText = TextFieldValue("")
                    attachments = emptyList()
                }
            },
            sessionId = project.id,
            placeholder = stringResource("project.new.chat.placeholder", project.name),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (projectSessions.isNotEmpty()) {
            Text(
                text = stringResource("project.recent.chats"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (projectSessions.isEmpty()) {
                item {
                    Text(
                        text = stringResource("project.no.chats"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                itemsIndexed(projectSessions) { index, session ->
                    sessionCard(
                        session = session,
                        index = index,
                        onClick = { onResumeSession(session.id) },
                        onDeleteSession = { sessionId ->
                            // Pass both sessionId and projectId to parent
                            onDeleteSession(sessionId, project.id)
                        },
                        onRenameSession = onRenameSession,
                        onExportSession = onExportSession,
                        currentProject = project,
                        allProjects = allProjects,
                    )
                }
            }
        }

        // Delete project confirmation dialog
        if (showDeleteDialog) {
            deleteProjectDialog(
                projectName = project.name,
                onConfirm = {
                    onDeleteProject(project.id)
                    showDeleteDialog = false
                },
                onDismiss = { showDeleteDialog = false },
            )
        }
    }
}

@Composable
private fun sessionCard(
    session: ChatSession,
    index: Int,
    onClick: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (String) -> Unit,
    currentProject: Project,
    allProjects: List<Project>,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var sessionIdToMove by remember { mutableStateOf<String?>(null) }

    val sessionRepository = remember { DatabaseManager.getInstance().getChatSessionRepository() }
    val projectRepository = remember { DatabaseManager.getInstance().getProjectRepository() }

    val backgroundColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TimeUtil.formatDisplay(session.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
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

                ComponentColors.themedDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    SessionActionMenu.projectViewMenu(
                        sessionId = session.id,
                        currentProjectId = currentProject.id,
                        currentProjectName = currentProject.name,
                        availableProjects = allProjects,
                        onExport = { onExportSession(session.id) },
                        onRename = { onRenameSession(session.id, session.title) },
                        onDelete = { onDeleteSession(session.id) },
                        onMoveToNewProject = {
                            sessionIdToMove = session.id
                            showNewProjectDialog = true
                        },
                        onMoveToExistingProject = { selectedProject ->
                            // Move session to another project
                            sessionRepository.updateSessionProject(session.id, selectedProject.id)
                            // Refresh current project's session list (remove the moved session)
                            EventBus.post(
                                ProjectSessionsRefreshRequested(
                                    projectId = currentProject.id,
                                    reason = "Session ${session.id} moved to ${selectedProject.id}",
                                ),
                            )
                        },
                        onRemoveFromProject = {
                            sessionRepository.updateSessionProject(session.id, null)
                            // Refresh current project's session list (remove the session)
                            EventBus.post(
                                ProjectSessionsRefreshRequested(
                                    projectId = currentProject.id,
                                    reason = "Session ${session.id} removed from project",
                                ),
                            )
                            // Refresh global sessions list (session now appears in "All Sessions")
                            EventBus.post(
                                SessionsRefreshRequested(
                                    reason = "Session ${session.id} removed from project",
                                ),
                            )
                        },
                        onDismiss = { showMenu = false },
                    )
                }
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
            onCreateProject = { name, description, folderPath ->
                // Project is already created in the dialog, now associate session with it
                val createdProject = projectRepository.findProjectByName(name)

                if (createdProject != null) {
                    sessionRepository.updateSessionProject(sessionIdToMove!!, createdProject.id)
                    // Refresh current project's session list (remove the moved session)
                    EventBus.post(
                        ProjectSessionsRefreshRequested(
                            projectId = currentProject.id,
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
