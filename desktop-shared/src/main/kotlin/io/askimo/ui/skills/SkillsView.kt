/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.skills

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.askimo.core.db.DatabaseManager
import io.askimo.core.skills.SkillRepository
import io.askimo.core.skills.agent.AgentCommand
import io.askimo.core.skills.agent.ExternalAgent
import io.askimo.core.skills.agent.ExternalAgentLoader
import io.askimo.core.skills.domain.SkillDefinition
import io.askimo.core.skills.domain.SkillRunRecord
import io.askimo.core.util.AskimoHome
import io.askimo.ui.common.components.primaryButton
import io.askimo.ui.common.i18n.stringResource
import io.askimo.ui.common.preferences.ApplicationPreferences
import io.askimo.ui.common.theme.AppComponents
import io.askimo.ui.common.theme.Spacing
import io.askimo.ui.common.theme.ThemePreferences
import io.askimo.ui.common.ui.markdownText
import io.askimo.ui.common.ui.selectableText
import io.askimo.ui.common.ui.themedTooltip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser

private val RUN_TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun skillsView() {
    val skillRepository = remember { SkillRepository() }
    val historyRepo = remember { DatabaseManager.getInstance().getSkillRunHistoryRepository() }
    var refreshKey by remember { mutableStateOf(0) }
    val skills by remember(refreshKey) { mutableStateOf(skillRepository.getSkillsOnly()) }

    var selectedSkill by remember { mutableStateOf<SkillDefinition?>(null) }
    var historyPanelExpanded by remember { mutableStateOf(ApplicationPreferences.getSkillsSidePanelExpanded()) }

    // All-skills run history shown in the right panel
    var allRunHistory by remember { mutableStateOf(listOf<SkillRunRecord>()) }
    var allHistoryRefreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(allHistoryRefreshKey) {
        allRunHistory = withContext(Dispatchers.IO) {
            historyRepo.findAll()
        }
    }

    // Record selected from the history panel — preloaded into execution area
    var pendingHistoryRecord by remember { mutableStateOf<SkillRunRecord?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        // ── Left: skill list OR inline execution ──────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (selectedSkill == null) {
                skillsListContent(
                    skills = skills,
                    onSelectSkill = { selectedSkill = it },
                    onRefresh = { refreshKey++ },
                )
            } else {
                skillExecutionArea(
                    skill = selectedSkill!!,
                    onBack = { selectedSkill = null },
                    onRunCompleted = { allHistoryRefreshKey++ },
                    preloadRecord = pendingHistoryRecord,
                    onPreloadConsumed = { pendingHistoryRecord = null },
                )
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        skillsHistoryPanel(
            isExpanded = historyPanelExpanded,
            onExpandedChange = {
                historyPanelExpanded = it
                ApplicationPreferences.setSkillsSidePanelExpanded(it)
            },
            runHistory = if (selectedSkill != null) {
                allRunHistory.filter { it.skillPath == selectedSkill!!.relativePath }
            } else {
                allRunHistory
            },
            filterSkillName = selectedSkill?.name,
            onSelectRecord = { record ->
                val skill = skills.firstOrNull { it.relativePath == record.skillPath }
                if (skill != null) {
                    selectedSkill = skill
                    pendingHistoryRecord = record
                }
            },
        )
    }
}

@Composable
private fun skillsListContent(
    skills: List<SkillDefinition>,
    onSelectSkill: (SkillDefinition) -> Unit,
    onRefresh: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var searchQuery by remember { mutableStateOf("") }

    // Filter by search query (name, description, category)
    val filtered = remember(skills, searchQuery) {
        if (searchQuery.isBlank()) {
            skills
        } else {
            val q = searchQuery.trim().lowercase()
            skills.filter {
                it.name.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.category.lowercase().contains(q)
            }
        }
    }

    // Group by top-level category ("" → "General")
    val grouped: Map<String, List<SkillDefinition>> = remember(filtered) {
        filtered
            .groupBy { it.categoryPath.firstOrNull() ?: "" }
            .entries
            .sortedWith(compareBy { if (it.key.isEmpty()) "\uFFFF" else it.key })
            .associate { it.key to it.value }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ThemePreferences.CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource("skills.view.title"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource("skills.view.description"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource("action.refresh"), tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.large))

                // ── Search bar ──
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource("skills.view.search.placeholder"), style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(18.dp).pointerHoverIcon(PointerIcon.Hand)) {
                                Icon(Icons.Default.Close, contentDescription = stringResource("skills.view.search.clear"), modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = AppComponents.outlinedTextFieldColors(),
                )

                Spacer(modifier = Modifier.height(Spacing.large))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(Spacing.large))

                if (filtered.isEmpty()) {
                    skillsGridEmptyState(hasQuery = searchQuery.isNotBlank())
                } else {
                    // Track expanded state per category — all expanded by default
                    val expandedCategories = remember(grouped.keys) {
                        mutableStateOf(grouped.keys.toSet())
                    }

                    grouped.forEach { (topCategory, groupSkills) ->
                        val isCategoryExpanded = topCategory in expandedCategories.value
                        val categoryLabel = topCategory.ifEmpty { stringResource("skills.view.category.general") }

                        // ── Category header (always shown, clickable to collapse) ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    expandedCategories.value = if (isCategoryExpanded) {
                                        expandedCategories.value - topCategory
                                    } else {
                                        expandedCategories.value + topCategory
                                    }
                                }
                                .padding(vertical = Spacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            )
                            Text(
                                text = categoryLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "(${groupSkills.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                            Icon(
                                if (isCategoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // ── Skill cards (collapsible) ──
                        if (isCategoryExpanded) {
                            Spacer(modifier = Modifier.height(Spacing.small))
                            val columns = 2
                            val gapDp = Spacing.medium
                            groupSkills.chunked(columns).forEach { rowSkills ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gapDp),
                                ) {
                                    rowSkills.forEach { skill ->
                                        skillCard(
                                            skill = skill,
                                            onClick = { onSelectSkill(skill) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(columns - rowSkills.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(gapDp))
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.medium))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(Spacing.medium))
                    }
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp),
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 8.dp,
                shape = MaterialTheme.shapes.small,
                hoverDurationMillis = 300,
                unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
            ),
        )
    }
}

@Composable
private fun skillsGridEmptyState(hasQuery: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Icon(
                if (hasQuery) Icons.Default.Search else Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            )
            Text(
                if (hasQuery) stringResource("skills.view.empty.search") else stringResource("skills.view.empty"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            if (!hasQuery) {
                Text(stringResource("skills.view.empty.hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun skillCard(
    skill: SkillDefinition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Card(
        modifier = modifier
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isHovered) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            // Icon + name row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text("🤖", style = MaterialTheme.typography.titleLarge)
                themedTooltip(text = skill.name, modifier = Modifier.weight(1f)) {
                    Text(
                        skill.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Description
            if (skill.description.isNotBlank()) {
                themedTooltip(text = skill.description) {
                    Text(
                        skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(Spacing.small))
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Single category tag (immediate parent) + Run arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Immediate parent as a single tag; tooltip shows full path
                val immediateParent = skill.categoryPath.lastOrNull()
                if (immediateParent != null) {
                    themedTooltip(
                        text = skill.categoryPath.joinToString(" / "),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                )
                                .border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                immediateParent,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Run arrow
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource("skills.view.run"),
                    modifier = Modifier.size(18.dp),
                    tint = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        }
    }
}

// ── Inline execution area (replaces the list when a skill is selected) ─────────

@Composable
private fun skillExecutionArea(
    skill: SkillDefinition,
    onBack: () -> Unit,
    onRunCompleted: () -> Unit = {},
    preloadRecord: SkillRunRecord? = null,
    onPreloadConsumed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val historyRepo = remember { DatabaseManager.getInstance().getSkillRunHistoryRepository() }

    var contextInput by remember(skill.relativePath) { mutableStateOf("") }
    var followUpInput by remember(skill.relativePath) { mutableStateOf("") }
    var systemPromptExpanded by remember(skill.relativePath) { mutableStateOf(false) }

    // Execution state
    var isRunning by remember(skill.relativePath) { mutableStateOf(false) }
    var responseText by remember(skill.relativePath) { mutableStateOf("") }
    var runError by remember(skill.relativePath) { mutableStateOf<String?>(null) }
    var agentStatus by remember(skill.relativePath) { mutableStateOf<String?>(null) }
    var activityLog by remember(skill.relativePath) { mutableStateOf(listOf<String>()) }
    var activityLogExpanded by remember(skill.relativePath) { mutableStateOf(false) }
    val hasResponse = responseText.isNotBlank()

    // Elapsed time counter — ticks every second while running
    var elapsedSeconds by remember(skill.relativePath) { mutableStateOf(0) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            elapsedSeconds = 0
            while (isRunning) {
                delay(1_000)
                elapsedSeconds++
            }
        }
    }

    // Working directory — default to skills-workspace, user can change it
    var workDir by remember(skill.relativePath) {
        mutableStateOf(AskimoHome.skillsWorkspaceDir().toFile())
    }

    // Load available agents once
    val availableAgents by remember { mutableStateOf(ExternalAgentLoader.available()) }
    var selectedAgent by remember(availableAgents) { mutableStateOf(availableAgents.firstOrNull()) }
    var agentDropdownExpanded by remember { mutableStateOf(false) }

    // Slash-command picker state
    var commandPickerExpanded by remember { mutableStateOf(false) }
    val commandSuggestions: List<AgentCommand> = remember(contextInput, selectedAgent) {
        val agent = selectedAgent ?: return@remember emptyList()
        if (!contextInput.startsWith("/")) return@remember emptyList()
        val query = contextInput.trimStart('/')
        agent.commands.filter { cmd ->
            query.isBlank() || cmd.name.contains(query, ignoreCase = true) || cmd.description.contains(query, ignoreCase = true)
        }
    }

    // Command mode: when input starts with "/" the system prompt is skipped
    val isCommandMode = contextInput.trimStart().startsWith("/")

    fun execute(agent: ExternalAgent, systemPrompt: String, userInput: String) {
        scope.launch {
            isRunning = true
            responseText = ""
            runError = null
            agentStatus = null
            activityLog = listOf()
            withContext(Dispatchers.IO) {
                agent.run(
                    systemPrompt = systemPrompt,
                    userInput = userInput,
                    workDir = workDir,
                    onToken = { line ->
                        scope.launch { responseText += line }
                    },
                    onStatus = { status ->
                        scope.launch {
                            agentStatus = status
                            activityLog = activityLog + status
                        }
                    },
                ).onFailure { e ->
                    scope.launch { runError = e.message ?: "Execution failed" }
                }
            }
            agentStatus = null
            isRunning = false

            // Save this run to persistent history
            val record = SkillRunRecord(
                skillPath = skill.relativePath,
                userInput = userInput,
                response = responseText,
                error = runError,
                activityLog = activityLog,
            )
            withContext(Dispatchers.IO) { historyRepo.save(record) }
            onRunCompleted()
        }
    }

    // Preload context from a history record (if any)
    LaunchedEffect(preloadRecord) {
        if (preloadRecord != null) {
            contextInput = preloadRecord.userInput
            responseText = preloadRecord.response
            runError = preloadRecord.error
            activityLog = preloadRecord.activityLog
            isRunning = false

            // Mark this record as "consumed" (cleared from pending)
            onPreloadConsumed()
        }
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ThemePreferences.CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 36.dp, top = 16.dp, bottom = 32.dp),
            ) {
                // Back to list
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.small)) {
                    IconButton(onClick = onBack, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource("action.back"), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = onBack, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                        Text(stringResource("skills.view.title"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                // Skill header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    modifier = Modifier.padding(bottom = Spacing.small),
                ) {
                    Text("🤖", style = MaterialTheme.typography.headlineLarge)
                    Column(modifier = Modifier.weight(1f)) {
                        selectableText(skill.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        if (skill.description.isNotBlank()) {
                            selectableText(skill.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Collapsible system prompt
                Spacer(modifier = Modifier.height(Spacing.small))
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerHoverIcon(PointerIcon.Hand)
                                .toggleable(value = systemPromptExpanded, role = Role.Button, onValueChange = { systemPromptExpanded = it })
                                .padding(horizontal = Spacing.large, vertical = Spacing.medium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                            ) {
                                Text(
                                    stringResource("skills.view.system.prompt.preview"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (skill.supplementalFileNames.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                shape = MaterialTheme.shapes.extraSmall,
                                            )
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            "+${skill.supplementalFileNames.size} ${if (skill.supplementalFileNames.size == 1) stringResource("skills.view.system.prompt.file") else stringResource("skills.view.system.prompt.files")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            Icon(if (systemPromptExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        if (systemPromptExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            // Show only the human-authored skill.md body
                            markdownText(
                                markdown = skill.systemPrompt.trim(),
                                modifier = Modifier.fillMaxWidth().padding(Spacing.large),
                            )
                            // Supplemental files — names only, not content
                            if (skill.supplementalFileNames.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                SelectionContainer {
                                    Column(
                                        modifier = Modifier.padding(Spacing.large),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            stringResource("skills.view.system.prompt.also.included"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                        skill.supplementalFileNames.forEach { fileName ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.FolderOpen,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                )
                                                Text(
                                                    fileName,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.extraLarge))

                // Working directory picker
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.small,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource("skills.view.workdir.label"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = workDir.absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val workdirDialogTitle = stringResource("skills.view.workdir.dialog.title")
                        TextButton(
                            onClick = {
                                val chooser = JFileChooser().apply {
                                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                    currentDirectory = workDir
                                    dialogTitle = workdirDialogTitle
                                }
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    workDir = chooser.selectedFile
                                }
                            },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text(
                                stringResource("skills.view.workdir.change"),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Context input with slash-command picker
                Box {
                    OutlinedTextField(
                        value = contextInput,
                        onValueChange = { v ->
                            contextInput = v
                            commandPickerExpanded = v.startsWith("/") && (selectedAgent?.commands?.isNotEmpty() == true)
                        },
                        placeholder = { Text(stringResource("skills.view.context.placeholder")) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        colors = AppComponents.outlinedTextFieldColors(),
                    )
                    if (commandPickerExpanded && commandSuggestions.isNotEmpty()) {
                        AppComponents.dropdownMenu(
                            expanded = true,
                            onDismissRequest = { commandPickerExpanded = false },
                        ) {
                            commandSuggestions.forEach { cmd ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    cmd.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                if (cmd.usage.isNotBlank()) {
                                                    Text(
                                                        cmd.usage,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            Text(
                                                cmd.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        contextInput = cmd.name + " "
                                        commandPickerExpanded = false
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                )
                            }
                        }
                    }
                }

                // Command mode hint — shown when input starts with "/"
                if (isCommandMode) {
                    Text(
                        text = stringResource("skills.view.command.mode.hint"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Agent picker + Execute button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Agent selector dropdown
                    Box {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier
                                .clickable(
                                    enabled = availableAgents.isNotEmpty(),
                                    onClick = { agentDropdownExpanded = true },
                                )
                                .pointerHoverIcon(if (availableAgents.isNotEmpty()) PointerIcon.Hand else PointerIcon.Default),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.medium, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                            ) {
                                Icon(
                                    Icons.Default.Extension,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedAgent != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    },
                                )
                                Text(
                                    text = selectedAgent?.name ?: stringResource("skills.view.no.agent"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selectedAgent != null) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    },
                                )
                                if (availableAgents.size > 1) {
                                    Icon(
                                        Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        AppComponents.dropdownMenu(
                            expanded = agentDropdownExpanded,
                            onDismissRequest = { agentDropdownExpanded = false },
                        ) {
                            availableAgents.forEach { agent ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Default.Extension,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (agent == selectedAgent) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                            Text(
                                                agent.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (agent == selectedAgent) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedAgent = agent
                                        agentDropdownExpanded = false
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                )
                            }
                        }
                    }

                    primaryButton(
                        onClick = {
                            val agent = selectedAgent ?: return@primaryButton
                            if (isCommandMode) {
                                execute(agent, systemPrompt = "", userInput = contextInput.trim())
                            } else {
                                execute(agent, skill.content, contextInput)
                            }
                        },
                        enabled = selectedAgent != null && !isRunning,
                    ) {
                        Icon(
                            if (isRunning) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(Spacing.small))
                        Text(
                            when {
                                isRunning -> stringResource("skills.view.running")
                                isCommandMode -> stringResource("skills.view.run.command")
                                else -> stringResource("skills.view.run")
                            },
                        )
                    }
                }

                // No agent warning
                if (availableAgents.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = stringResource("skills.view.no.agent.hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.extraLarge))

                // Response panel — shown once execution has started
                if (isRunning || hasResponse || runError != null || activityLog.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 1.dp,
                        tonalElevation = 1.dp,
                    ) {
                        Column(modifier = Modifier.padding(Spacing.large)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.medium),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (isRunning) {
                                            Icons.Default.Refresh
                                        } else if (runError != null) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.CheckCircle
                                        },
                                        null,
                                        tint = when {
                                            runError != null -> MaterialTheme.colorScheme.error
                                            isRunning -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = when {
                                            isRunning -> stringResource("skills.view.running")
                                            runError != null -> stringResource("skills.view.response.error")
                                            else -> stringResource("skills.view.response.title")
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (isRunning) {
                                        Text(
                                            text = "${elapsedSeconds}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                                if (hasResponse) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(responseText))
                                        },
                                        modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = stringResource("skills.view.copy"),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            if (runError != null) {
                                Text(
                                    text = runError!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = Spacing.medium),
                                )
                            } else if (responseText.isNotBlank()) {
                                markdownText(
                                    markdown = responseText,
                                    isStreaming = isRunning,
                                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium),
                                )
                                // Tool status shown below streaming content while still running
                                if (isRunning && agentStatus != null) {
                                    Text(
                                        text = agentStatus!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(top = Spacing.small),
                                    )
                                }
                            } else {
                                // Running — show animated cursor + current tool status
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(
                                            text = "▌",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    if (agentStatus != null) {
                                        Text(
                                            text = agentStatus!!,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.medium))

                    // Activity Log — collapsible, shown when there are events
                    if (activityLog.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.medium,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            ),
                        ) {
                            Column {
                                // Header row — always visible, toggles expansion
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .toggleable(
                                            value = activityLogExpanded,
                                            role = Role.Button,
                                            onValueChange = { activityLogExpanded = it },
                                        )
                                        .padding(horizontal = Spacing.large, vertical = Spacing.medium),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                    ) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            stringResource("skills.view.activity.log"),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        // Event count badge
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                    shape = MaterialTheme.shapes.extraSmall,
                                                )
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                "${activityLog.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Icon(
                                        if (activityLogExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                // Log entries
                                if (activityLogExpanded) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                    SelectionContainer {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Spacing.large, vertical = Spacing.medium),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            activityLog.forEachIndexed { index, entry ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                                    verticalAlignment = Alignment.Top,
                                                ) {
                                                    Text(
                                                        "${index + 1}.",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                        modifier = Modifier.width(24.dp),
                                                    )
                                                    Text(
                                                        entry,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.medium))

                    // Follow-up — enabled only after a response exists
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(Spacing.large)) {
                            Text(
                                stringResource("skills.view.followup.label"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = Spacing.small),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                OutlinedTextField(
                                    value = followUpInput,
                                    onValueChange = { followUpInput = it },
                                    placeholder = { Text(stringResource("skills.view.followup.placeholder")) },
                                    modifier = Modifier.weight(1f),
                                    minLines = 1,
                                    maxLines = 5,
                                    enabled = hasResponse && !isRunning,
                                    colors = AppComponents.outlinedTextFieldColors(),
                                )
                                IconButton(
                                    onClick = {
                                        val agent = selectedAgent ?: return@IconButton
                                        if (followUpInput.isNotBlank()) {
                                            // Follow-up: send prior response as context + new input
                                            val followUpContext = "$responseText\n\n---\n\n${followUpInput.trim()}"
                                            followUpInput = ""
                                            execute(agent, skill.content, followUpContext)
                                        }
                                    },
                                    enabled = hasResponse && !isRunning && followUpInput.isNotBlank(),
                                    colors = AppComponents.primaryIconButtonColors(),
                                    modifier = Modifier.size(48.dp).pointerHoverIcon(PointerIcon.Hand),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = stringResource("skills.view.followup.send"),
                                        tint = if (hasResponse && !isRunning && followUpInput.isNotBlank()) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp),
            style = ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 8.dp,
                shape = MaterialTheme.shapes.small,
                hoverDurationMillis = 300,
                unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
            ),
        )
    }
}

// ── History panel row — compact, clickable, navigates to execution area ───────

@Composable
private fun skillRunHistoryPanelRow(
    record: SkillRunRecord,
    onClick: () -> Unit,
    showSkillName: Boolean = true,
) {
    val isError = record.error != null
    val timeLabel = RUN_TIME_FMT.format(record.createdAt)
    val skillDisplayName = remember(record.skillPath) {
        record.skillPath.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .background(
                if (isHovered) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Icon(
            if (isError) Icons.Default.Close else Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            if (showSkillName) {
                Text(
                    skillDisplayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
            if (record.userInput.isNotBlank()) {
                Text(
                    record.userInput.take(50) + if (record.userInput.length > 50) "…" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── History panel (right side, all skills, always visible) ────────────────────

@Composable
private fun skillsHistoryPanel(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    runHistory: List<SkillRunRecord> = emptyList(),
    filterSkillName: String? = null,
    onSelectRecord: (SkillRunRecord) -> Unit = {},
) {
    var panelWidth by remember {
        mutableStateOf(ApplicationPreferences.getSkillsSidePanelWidth().dp)
    }

    val animatedWidth by animateDpAsState(
        targetValue = if (isExpanded) panelWidth else 56.dp,
        animationSpec = tween(durationMillis = 300),
    )

    Card(
        modifier = Modifier.width(animatedWidth).fillMaxHeight(),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = AppComponents.sidebarSurfaceColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Drag-to-resize handle (left edge, only when expanded) ──────────
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newWidth = panelWidth - dragAmount.x.toDp()
                                    panelWidth = newWidth.coerceIn(220.dp, 560.dp)
                                },
                                onDragEnd = {
                                    ApplicationPreferences.setSkillsSidePanelWidth(panelWidth.value.toInt())
                                },
                            )
                        },
                )
            }

            // ── Expanded content ──────────────────────────────────────────────
            if (isExpanded) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            Icon(
                                Icons.Default.History,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                filterSkillName ?: stringResource("skills.view.history.title"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (runHistory.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MaterialTheme.shapes.extraSmall)
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        "${runHistory.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { onExpandedChange(false) },
                            modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = stringResource("skills.view.history.collapse"),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    HorizontalDivider()

                    // History list or empty state
                    if (runHistory.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.small),
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                )
                                Text(
                                    stringResource("skills.view.history.empty"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                            }
                        }
                    } else {
                        val panelScrollState = rememberScrollState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(panelScrollState)
                                    .padding(vertical = 4.dp),
                            ) {
                                runHistory.forEach { record ->
                                    skillRunHistoryPanelRow(
                                        record = record,
                                        showSkillName = filterSkillName == null,
                                        onClick = { onSelectRecord(record) },
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                }
                            }
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(panelScrollState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp),
                                style = ScrollbarStyle(
                                    minimalHeight = 16.dp,
                                    thickness = 6.dp,
                                    shape = MaterialTheme.shapes.small,
                                    hoverDurationMillis = 300,
                                    unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                                ),
                            )
                        }
                    }
                }
            }

            // ── Icon bar (always visible, right side) ─────────────────────────
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                themedTooltip(text = stringResource("skills.view.history.title")) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isExpanded) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onExpandedChange(!isExpanded) },
                            )
                            .pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource("skills.view.history.title"),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
