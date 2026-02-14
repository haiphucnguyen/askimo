/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.core.i18n.LocalizationManager
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.Spacing
import io.askimo.desktop.common.ui.clickableCard
import java.util.Locale

/**
 * Language selection dialog shown on first launch.
 */
@Composable
fun languageSelectionDialog(
    onLanguageSelected: (Locale) -> Unit,
) {
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var selectedLocale by remember { mutableStateOf(Locale.ENGLISH) }

    val availableLanguages = remember { LocalizationManager.availableLocales }

    Dialog(onDismissRequest = { /* Prevent dismissal - user must select */ }) {
        Surface(
            modifier = Modifier.width(450.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.large),
            ) {
                // Welcome Header
                Text(
                    text = "👋 Welcome to Askimo",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Please select your preferred language",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                // Language Selection Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ComponentColors.bannerCardColors(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.large),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        Text(
                            text = "Language / 语言 / 言語 / Sprache",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickableCard { languageDropdownExpanded = true },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = availableLanguages[selectedLocale] ?: selectedLocale.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Change language",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            ComponentColors.themedDropdownMenu(
                                expanded = languageDropdownExpanded,
                                onDismissRequest = { languageDropdownExpanded = false },
                            ) {
                                availableLanguages.forEach { (locale, name) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                if (locale == selectedLocale) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedLocale = locale
                                            languageDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Continue Button
                primaryButton(
                    onClick = {
                        onLanguageSelected(selectedLocale)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource("tutorial.language.continue"))
                }
            }
        }
    }
}
