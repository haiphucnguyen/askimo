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
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.ui.clickableCard

/**
 * Groups a list of model names by their family.
 * Examples:
 * - "gpt-4-turbo" -> "gpt-4"
 * - "claude-3-opus" -> "claude-3"
 * - "gemini-pro" -> "gemini-pro"
 */
fun groupModelsByFamily(models: List<String>): Map<String, List<String>> = models.groupBy { model ->
    val parts = model.split('-', '.')
    when {
        parts.size >= 2 -> "${parts[0]}-${parts[1]}"
        parts.size == 1 -> parts[0]
        else -> "Other"
    }
}.toSortedMap()

/**
 * Displays a grouped list of models with category headers.
 * Used in model selection dialogs and dropdowns.
 *
 * @param models The list of model names to display
 * @param selectedModel The currently selected model (if any)
 * @param onModelClick Callback when a model is clicked
 * @param showHeaders Whether to show category headers (default: true if multiple groups)
 */
@Composable
fun groupedModelListAsCards(
    models: List<String>,
    selectedModel: String?,
    onModelClick: (String) -> Unit,
    showHeaders: Boolean? = null,
) {
    val groupedModels = remember(models) { groupModelsByFamily(models) }
    val shouldShowHeaders = showHeaders ?: (groupedModels.size > 1)

    groupedModels.forEach { (category, categoryModels) ->
        // Category header
        if (shouldShowHeaders && categoryModels.isNotEmpty()) {
            Text(
                text = category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    horizontal = 0.dp,
                    vertical = 8.dp,
                ),
            )
        }

        // Models in this category
        categoryModels.forEach { model ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickableCard { onModelClick(model) },
                colors = if (model == selectedModel) {
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
                        text = model,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (model == selectedModel) {
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

/**
 * Displays a grouped list of models as dropdown menu items.
 * Used in the footer bar model dropdown.
 *
 * @param models The list of model names to display
 * @param selectedModel The currently selected model (if any)
 * @param onModelClick Callback when a model is clicked
 * @param showHeaders Whether to show category headers (default: true if multiple groups)
 */
@Composable
fun groupedModelListAsMenuItems(
    models: List<String>,
    selectedModel: String?,
    onModelClick: (String) -> Unit,
    showHeaders: Boolean? = null,
) {
    val groupedModels = remember(models) { groupModelsByFamily(models) }
    val shouldShowHeaders = showHeaders ?: (groupedModels.size > 1)

    groupedModels.forEach { (category, categoryModels) ->
        // Category header
        if (shouldShowHeaders && categoryModels.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
            ) {
                Text(
                    text = category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Models in this category
        categoryModels.forEach { model ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = model,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = { onModelClick(model) },
                leadingIcon = if (model == selectedModel) {
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
