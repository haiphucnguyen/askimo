/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.askimo.core.chat.domain.ChatSession
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFilesKnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFoldersKnowledgeSourceConfig
import io.askimo.core.chat.domain.Project
import io.askimo.core.chat.domain.UrlKnowledgeSourceConfig
import io.askimo.core.chat.dto.FileAttachmentDTO
import io.askimo.core.db.DatabaseManager
import io.askimo.core.event.EventBus
import io.askimo.core.event.internal.ProjectIndexingRequestedEvent
import io.askimo.core.event.internal.ProjectReIndexEvent
import io.askimo.core.event.internal.SessionsRefreshEvent
import io.askimo.core.mcp.ProjectMcpInstance
import io.askimo.core.mcp.ProjectMcpInstanceService
import io.askimo.core.util.TimeUtil
import io.askimo.desktop.chat.chatInputField
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.ui.themedTooltip
import io.askimo.desktop.session.SessionActionMenu
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import kotlin.collections.emptyList
import kotlin.let

/**
 * Project view showing project details and chat interface.
 */
@Composable
fun projectView(
    project: Project,
    onStartChat: (projectId: String, message: String, attachments: List<FileAttachmentDTO>) -> Unit,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (sessionId: String, projectId: String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (String) -> Unit,
    onEditProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Create ViewModel
    val scope = rememberCoroutineScope()
    val viewModel = remember(project.id) {
        GlobalContext.get().get<ProjectViewModel> { parametersOf(scope, project.id) }
    }

    // Local UI state
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var attachments by remember { mutableStateOf<List<FileAttachmentDTO>>(emptyList()) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddReferenceMaterialDialog by remember { mutableStateOf(false) }

    // Use ViewModel state
    val currentProject = viewModel.currentProject ?: project
    val projectSessions = viewModel.projectSessions
    val allProjects = viewModel.allProjects

    // Get repository
    val projectRepository = remember { DatabaseManager.getInstance().getProjectRepository() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        // Scrollable content (Reference Materials + MCP + Sessions)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentProject.name,
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
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    ComponentColors.themedDropdownMenu(
                        expanded = showProjectMenu,
                        onDismissRequest = { showProjectMenu = false },
                    ) {
                        SessionActionMenu.projectActionMenu(
                            onEditProject = {
                                onEditProject(currentProject.id)
                                showProjectMenu = false
                            },
                            onDeleteProject = {
                                showDeleteDialog = true
                                showProjectMenu = false
                            },
                            onReindexProject = {
                                EventBus.post(
                                    ProjectReIndexEvent(
                                        projectId = currentProject.id,
                                        reason = "Manual re-index requested by user from project menu",
                                    ),
                                )
                                showProjectMenu = false
                            },
                            onDismiss = { showProjectMenu = false },
                        )
                    }
                }
            }

            // Project Description (if exists)
            currentProject.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            var isExpanded by remember { mutableStateOf(currentProject.knowledgeSources.isEmpty()) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = ComponentColors.bannerCardColors(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    // Collapsible header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left side - clickable expansion area (only if sources exist)
                        if (currentProject.knowledgeSources.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        onClick = { isExpanded = !isExpanded },
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    )
                                    .pointerHoverIcon(PointerIcon.Hand),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )

                                Text(
                                    text = stringResource("projects.sources.count", currentProject.knowledgeSources.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                val rotation by animateFloatAsState(
                                    targetValue = if (isExpanded) 180f else 0f,
                                    label = "rotation",
                                )

                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) {
                                        stringResource("projects.sources.collapse")
                                    } else {
                                        stringResource("projects.sources.expand")
                                    },
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.rotate(rotation),
                                )

                                themedTooltip(
                                    text = stringResource("projects.sources.info.tooltip"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .pointerHoverIcon(PointerIcon.Hand),
                                    )
                                }
                            }
                        } else {
                            // Show empty state with explanation
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource("projects.sources.empty.title"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource("projects.sources.empty.description"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    )
                                }

                                // Info icon with explanation tooltip
                                themedTooltip(
                                    text = stringResource("projects.sources.info.tooltip"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .pointerHoverIcon(PointerIcon.Hand),
                                    )
                                }
                            }
                        }

                        // Right side - Add button (always visible)
                        themedTooltip(text = stringResource("projects.sources.add.tooltip")) {
                            IconButton(
                                onClick = {
                                    showAddReferenceMaterialDialog = true
                                },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource("projects.sources.add.tooltip"),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // Expandable content (only show if sources exist)
                    if (currentProject.knowledgeSources.isNotEmpty()) {
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )

                                // Group knowledge sources by type
                                val groupedSources = currentProject.knowledgeSources.groupBy { source ->
                                    when (source) {
                                        is LocalFoldersKnowledgeSourceConfig -> stringResource("projects.sources.type.local_folders")
                                        is LocalFilesKnowledgeSourceConfig -> stringResource("projects.sources.type.local_files")
                                        is UrlKnowledgeSourceConfig -> stringResource("projects.sources.type.urls")
                                    }
                                }

                                // Display each group
                                groupedSources.forEach { (groupName, sources) ->
                                    // Group header
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    )

                                    // Items in this group
                                    sources.forEach { source ->
                                        knowledgeSourceItem(
                                            source = source,
                                            onDelete = {
                                                viewModel.deleteKnowledgeSource(source)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // MCP Integrations Panel
            mcpIntegrationsPanel(
                projectId = currentProject.id,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            // Sessions Section
            if (projectSessions.isNotEmpty()) {
                Text(
                    text = stringResource("project.recent.chats"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // Display sessions (no LazyColumn, just iterate)
            if (projectSessions.isEmpty()) {
                Text(
                    text = stringResource("project.no.chats"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    projectSessions.forEachIndexed { index, session ->
                        sessionCard(
                            session = session,
                            index = index,
                            onClick = { onResumeSession(session.id) },
                            onDeleteSession = { sessionId ->
                                onDeleteSession(sessionId, currentProject.id)
                            },
                            onRenameSession = onRenameSession,
                            onExportSession = onExportSession,
                            currentProject = currentProject,
                            allProjects = allProjects,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }

        // Fixed bottom: Chat Input
        chatInputField(
            inputText = inputText,
            onInputTextChange = { inputText = it },
            attachments = attachments,
            onAttachmentsChange = { attachments = it },
            onSendMessage = {
                if (inputText.text.isNotBlank()) {
                    onStartChat(currentProject.id, inputText.text, attachments)
                    inputText = TextFieldValue("")
                    attachments = emptyList()
                }
            },
            sessionId = currentProject.id,
            placeholder = stringResource("project.new.chat.placeholder", currentProject.name),
            modifier = Modifier.padding(top = 16.dp),
        )
    }

    // Delete project confirmation dialog
    if (showDeleteDialog) {
        deleteProjectDialog(
            projectName = currentProject.name,
            onConfirm = {
                onDeleteProject(currentProject.id)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    // Add reference material dialog
    if (showAddReferenceMaterialDialog) {
        addReferenceMaterialDialog(
            projectId = currentProject.id,
            onDismiss = { showAddReferenceMaterialDialog = false },
            onAdd = { newSources ->
                // Build knowledge source configs from the new items
                val newConfigs = buildKnowledgeSourceConfigs(newSources)

                // Merge with existing knowledge sources
                val mergedConfigs = mergeKnowledgeSourceConfigs(
                    existing = currentProject.knowledgeSources,
                    new = newConfigs,
                )

                // Update the project
                projectRepository.updateProject(
                    projectId = currentProject.id,
                    name = currentProject.name,
                    description = currentProject.description,
                    knowledgeSources = mergedConfigs,
                )

                // Trigger re-indexing for the new sources
                EventBus.post(
                    ProjectIndexingRequestedEvent(
                        projectId = currentProject.id,
                        watchForChanges = true,
                    ),
                )

                showAddReferenceMaterialDialog = false
            },
        )
    }
}

/**
 * Merge existing and new knowledge source configurations
 */
private fun mergeKnowledgeSourceConfigs(
    existing: List<KnowledgeSourceConfig>,
    new: List<KnowledgeSourceConfig>,
): List<KnowledgeSourceConfig> = (existing + new)
    .groupBy { it::class }
    .flatMap { (_, configs) ->
        configs.distinctBy { it.resourceIdentifier }
    }
    .sortedBy { it.resourceIdentifier }

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
    viewModel: ProjectViewModel,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var sessionIdToMove by remember { mutableStateOf<String?>(null) }

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
                            viewModel.moveSessionToProject(session.id, selectedProject.id)
                        },
                        onRemoveFromProject = {
                            viewModel.removeSessionFromProject(session.id)
                            // Refresh global sessions list (session now appears in "All Sessions")
                            EventBus.post(
                                SessionsRefreshEvent(
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
        val projectRepository = remember { DatabaseManager.getInstance().getProjectRepository() }
        newProjectDialog(
            onDismiss = {
                showNewProjectDialog = false
                sessionIdToMove = null
            },
            onCreateProject = { name, description ->
                // Project is already created in the dialog with all knowledge sources
                val createdProject = projectRepository.findProjectByName(name)

                if (createdProject != null) {
                    viewModel.moveSessionToProject(sessionIdToMove!!, createdProject.id)
                }

                showNewProjectDialog = false
                sessionIdToMove = null
            },
        )
    }
}

@Composable
private fun knowledgeSourceItem(
    source: KnowledgeSourceConfig,
    onDelete: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Resource identifier (path/URL) with bullet point
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = source.resourceIdentifier,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Delete button
        themedTooltip(text = stringResource("projects.sources.delete.tooltip")) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource("projects.sources.delete.tooltip"),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun mcpIntegrationsPanel(
    projectId: String,
    modifier: Modifier = Modifier,
) {
    val mcpService = remember { ProjectMcpInstanceService() }
    var mcpInstances by remember(projectId) { mutableStateOf(mcpService.getInstances(projectId)) }
    var isExpanded by remember(projectId) { mutableStateOf(mcpInstances.isNotEmpty()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = ComponentColors.bannerCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Collapsible header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left side - clickable expansion area (only if instances exist)
                if (mcpInstances.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                onClick = { isExpanded = !isExpanded },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            )
                            .pointerHoverIcon(PointerIcon.Hand),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )

                        Text(
                            text = stringResource("mcp.integrations.count", mcpInstances.size),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        val rotation by animateFloatAsState(
                            targetValue = if (isExpanded) 180f else 0f,
                            label = "rotation",
                        )

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) {
                                stringResource("mcp.integrations.collapse")
                            } else {
                                stringResource("mcp.integrations.expand")
                            },
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.rotate(rotation),
                        )

                        themedTooltip(
                            text = stringResource("mcp.integrations.info.tooltip"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                            )
                        }
                    }
                } else {
                    // Show empty state with explanation
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource("mcp.integrations.empty.title"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource("mcp.integrations.empty.description"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }

                        // Info icon with explanation tooltip
                        themedTooltip(
                            text = stringResource("mcp.integrations.info.tooltip"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                            )
                        }
                    }
                }

                // Right side - Add button (always visible)
                themedTooltip(text = stringResource("mcp.integrations.add.tooltip")) {
                    IconButton(
                        onClick = {
                            showAddDialog = true
                        },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource("mcp.integrations.add.tooltip"),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Expandable content (only show if instances exist)
            if (mcpInstances.isNotEmpty()) {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        // Display each MCP instance
                        mcpInstances.forEach { instance ->
                            mcpInstanceCard(
                                instance = instance,
                                onDelete = {
                                    mcpService.deleteInstance(projectId, instance.id)
                                    mcpInstances = mcpService.getInstances(projectId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Show Add MCP Integration Dialog
    if (showAddDialog) {
        addMcpIntegrationDialog(
            projectId = projectId,
            onDismiss = { showAddDialog = false },
            onSave = { serverId, name, parameters ->
                val result = mcpService.createInstance(
                    projectId = projectId,
                    serverId = serverId,
                    name = name,
                    parameterValues = parameters,
                )

                result.onSuccess { instance ->
                    mcpInstances = mcpService.getInstances(projectId)
                    isExpanded = true
                    showAddDialog = false
                }

                result.onFailure { error ->
                    errorMessage = error.message ?: "Failed to create MCP integration"
                }
            },
        )
    }

    // Show error dialog if there's an error
    errorMessage?.let { message ->
        ComponentColors.themedAlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource("mcp.integrations.error.title")) },
            text = { Text(message) },
            confirmButton = {
                primaryButton(onClick = { errorMessage = null }) {
                    Text(stringResource("dialog.close"))
                }
            },
        )
    }
}

@Composable
private fun mcpInstanceCard(
    instance: ProjectMcpInstance,
    onDelete: () -> Unit,
) {
    val serverDefinition = remember(instance.serverId) {
        io.askimo.core.mcp.config.McpServersConfig.get(instance.serverId)
    }
    var showToolsDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Instance name
                Text(
                    text = instance.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Server type
                serverDefinition?.let { def ->
                    Text(
                        text = def.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Show first few parameters
                if (instance.parameterValues.isNotEmpty()) {
                    val displayParams = instance.parameterValues.entries.take(2)
                    displayParams.forEach { (key, value) ->
                        Text(
                            text = "$key: ${value.take(200)}${if (value.length > 200) "..." else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Tools button
                themedTooltip(text = stringResource("mcp.integrations.view.tools.tooltip")) {
                    IconButton(
                        onClick = { showToolsDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = stringResource("mcp.integrations.view.tools.tooltip"),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Delete button
                themedTooltip(text = stringResource("mcp.integrations.delete.tooltip")) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource("mcp.integrations.delete.tooltip"),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    // Show Tools Dialog
    if (showToolsDialog) {
        mcpToolsDialog(
            instance = instance,
            onDismiss = { showToolsDialog = false },
        )
    }
}

@Composable
private fun mcpToolsDialog(
    instance: ProjectMcpInstance,
    onDismiss: () -> Unit,
) {
    val mcpService = remember { ProjectMcpInstanceService() }
    var tools by remember { mutableStateOf<List<dev.langchain4j.agent.tool.ToolSpecification>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(instance.id) {
        isLoading = true
        errorMessage = null

        val result = mcpService.listTools(instance.projectId, instance.id)

        result.onSuccess { toolsList ->
            tools = toolsList
            isLoading = false
        }

        result.onFailure { error ->
            errorMessage = error.message ?: "Unknown error occurred"
            isLoading = false
        }
    }

    ComponentColors.themedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource("mcp.tools.dialog.title", instance.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            // Scrollable container with controlled height and visible scrollbar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        isLoading -> {
                            // Loading state
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource("mcp.tools.dialog.loading"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        errorMessage != null -> {
                            // Error state
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = stringResource("mcp.tools.dialog.error"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }

                        tools.isNullOrEmpty() -> {
                            // Empty state
                            Text(
                                text = stringResource("mcp.tools.dialog.empty"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            // Success - display tools
                            Text(
                                text = stringResource("mcp.tools.dialog.count", tools!!.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            tools!!.forEach { tool ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = tool.name(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )

                                        tool.description()?.let { desc ->
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Visible scrollbar
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    style = defaultScrollbarStyle().copy(
                        unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    ),
                )
            }
        },
        confirmButton = {
            primaryButton(
                onClick = onDismiss,
            ) {
                Text(stringResource("dialog.close"))
            }
        },
    )
}
