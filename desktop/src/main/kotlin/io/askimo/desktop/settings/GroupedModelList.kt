/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.askimo.core.providers.ModelDTO
import io.askimo.core.providers.ModelProvider
import io.askimo.ui.common.theme.ComponentColors
import io.askimo.ui.common.ui.clickableCard

/**
 * Groups a list of [ModelDTO]s by their [ModelProvider].
 * Single-provider lists produce one group so no headers are shown by default.
 */
fun groupModelsByProvider(models: List<ModelDTO>): Map<ModelProvider, List<ModelDTO>> = models.groupBy { it.provider }
    .entries
    .sortedBy { it.key.name }
    .associate { it.key to it.value }

@Composable
fun groupedModelListAsCards(
    models: List<ModelDTO>,
    selectedModelId: String?,
    onModelClick: (String) -> Unit,
    showHeaders: Boolean? = null,
) {
    val groupedModels = remember(models) { groupModelsByProvider(models) }
    val shouldShowHeaders = showHeaders ?: (groupedModels.size > 1)

    groupedModels.forEach { (provider, providerModels) ->
        if (shouldShowHeaders && providerModels.isNotEmpty()) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 0.dp, vertical = 8.dp),
            )
        }

        providerModels.forEach { dto ->
            val isSelected = dto.modelId == selectedModelId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickableCard { onModelClick(dto.modelId) },
                colors = if (isSelected) {
                    ComponentColors.primaryCardColors()
                } else {
                    ComponentColors.surfaceVariantCardColors()
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dto.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selected model",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun groupedModelListAsMenuItems(
    models: List<ModelDTO>,
    selectedModelId: String?,
    onModelClick: (String) -> Unit,
    showHeaders: Boolean? = null,
) {
    val groupedModels = remember(models) { groupModelsByProvider(models) }
    val shouldShowHeaders = showHeaders ?: (groupedModels.size > 1)

    groupedModels.forEach { (provider, providerModels) ->
        if (shouldShowHeaders && providerModels.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        providerModels.forEach { dto ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = dto.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = { onModelClick(dto.modelId) },
                leadingIcon = if (dto.modelId == selectedModelId) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Current model",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
        }
    }
}
