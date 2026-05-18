/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Askimo
 */
package io.askimo.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.askimo.core.db.DatabaseManager
import io.askimo.core.skills.SkillRepository
import io.askimo.core.skills.domain.SkillDefinition
import io.askimo.core.skills.domain.SkillRunRecord
import io.askimo.ui.common.preferences.ApplicationPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun skillsView(
    onNavigateToSkillsSettings: () -> Unit = {},
) {
    val skillRepository = remember { SkillRepository() }
    val historyRepo = remember { DatabaseManager.getInstance().getSkillRunHistoryRepository() }
    var refreshKey by remember { mutableStateOf(0) }
    val skills by remember(refreshKey) { mutableStateOf(skillRepository.getSkillsOnly()) }

    var selectedSkill by remember { mutableStateOf<SkillDefinition?>(null) }
    var historyPanelExpanded by remember { mutableStateOf(ApplicationPreferences.getSkillsSidePanelExpanded()) }

    var allRunHistory by remember { mutableStateOf(listOf<SkillRunRecord>()) }
    var allHistoryRefreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(allHistoryRefreshKey) {
        allRunHistory = withContext(Dispatchers.IO) {
            historyRepo.findAll()
        }
    }

    var pendingHistoryRecord by remember { mutableStateOf<SkillRunRecord?>(null) }

    val coroutineScope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (selectedSkill == null) {
                skillsListContent(
                    skills = skills,
                    onSelectSkill = { selectedSkill = it },
                    onRefresh = { refreshKey++ },
                    onNavigateToSkillsSettings = onNavigateToSkillsSettings,
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
            onDeleteRecord = { record ->
                coroutineScope.launch(Dispatchers.IO) {
                    historyRepo.deleteById(record.id)
                    allRunHistory = historyRepo.findAll()
                }
            },
        )
    }
}
