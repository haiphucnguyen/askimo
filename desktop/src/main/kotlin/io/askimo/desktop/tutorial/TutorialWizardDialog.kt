/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.tutorial

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.askimo.desktop.common.components.linkButton
import io.askimo.desktop.common.components.primaryButton
import io.askimo.desktop.common.components.secondaryButton
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import io.askimo.desktop.common.theme.Spacing

/**
 * Tutorial wizard dialog that guides users through key features.
 */
@Composable
fun tutorialWizardDialog(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 4
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = { /* Prevent dismissal */ }) {
        Surface(
            modifier = Modifier.width(600.dp).height(650.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource("tutorial.title"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource("tutorial.step.indicator", currentStep + 1, totalSteps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.large))

                // Content Area with Scrollbar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                    ) {
                        when (currentStep) {
                            0 -> tutorialStepQuickStart()
                            1 -> tutorialStepProjects()
                            2 -> tutorialStepShortcuts()
                            3 -> tutorialStepNextSteps()
                        }
                    }

                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd),
                        style = defaultScrollbarStyle().copy(
                            unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.large))

                // Progress Indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(totalSteps) { index ->
                        Card(
                            modifier = Modifier
                                .width(if (index == currentStep) 32.dp else 8.dp)
                                .height(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (index == currentStep) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        ) {}
                        if (index < totalSteps - 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.large))

                // Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Skip/Previous
                    if (currentStep == 0) {
                        secondaryButton(onClick = onSkip) {
                            Text(stringResource("tutorial.skip"))
                        }
                    } else {
                        secondaryButton(
                            onClick = { currentStep-- },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = null,
                            )
                            Text(stringResource("tutorial.previous"))
                        }
                    }

                    // Next/Finish
                    if (currentStep < totalSteps - 1) {
                        primaryButton(
                            onClick = { currentStep++ },
                        ) {
                            Text(stringResource("tutorial.next"))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        }
                    } else {
                        primaryButton(
                            onClick = onComplete,
                        ) {
                            Text(stringResource("tutorial.finish"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun tutorialStepQuickStart() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(
            text = stringResource("tutorial.step1.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource("tutorial.step1.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        // Feature Cards
        tutorialFeatureCard(
            title = stringResource("tutorial.step1.chat.title"),
            description = stringResource("tutorial.step1.chat.description"),
        )

        tutorialFeatureCard(
            title = stringResource("tutorial.step1.files.title"),
            description = stringResource("tutorial.step1.files.description"),
        )

        tutorialFeatureCard(
            title = stringResource("tutorial.step1.models.title"),
            description = stringResource("tutorial.step1.models.description"),
        )
    }
}

@Composable
private fun tutorialStepProjects() {
    val uriHandler = LocalUriHandler.current

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(
            text = stringResource("tutorial.step2.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource("tutorial.step2.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        tutorialFeatureCard(
            title = stringResource("tutorial.step2.context.title"),
            description = stringResource("tutorial.step2.context.description"),
        )

        tutorialFeatureCard(
            title = stringResource("tutorial.step2.knowledge.title"),
            description = stringResource("tutorial.step2.knowledge.description"),
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        linkButton(
            onClick = {
                uriHandler.openUri("https://askimo.chat/docs/rag/")
            },
        ) {
            Text(stringResource("tutorial.learn.more.projects"))
        }
    }
}

@Composable
private fun tutorialStepShortcuts() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(
            text = stringResource("tutorial.step3.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource("tutorial.step3.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        tutorialShortcutCard("tutorial.step3.shortcut.new.chat")
        tutorialShortcutCard("tutorial.step3.shortcut.search")
        tutorialShortcutCard("tutorial.step3.shortcut.projects")
        tutorialShortcutCard("tutorial.step3.shortcut.settings")
    }
}

@Composable
private fun tutorialStepNextSteps() {
    val uriHandler = LocalUriHandler.current

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(
            text = stringResource("tutorial.step4.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource("tutorial.step4.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = ComponentColors.bannerCardColors(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                tutorialLinkItem(
                    title = stringResource("tutorial.step4.link.docs"),
                    onClick = { uriHandler.openUri("https://askimo.chat/docs/") },
                )

                HorizontalDivider()

                tutorialLinkItem(
                    title = stringResource("tutorial.step4.link.providers"),
                    onClick = { uriHandler.openUri("https://askimo.chat/docs/desktop/ai-providers/") },
                )

                HorizontalDivider()

                tutorialLinkItem(
                    title = stringResource("tutorial.step4.link.github"),
                    onClick = { uriHandler.openUri("https://github.com/haiphucnguyen/askimo") },
                )
            }
        }
    }
}

@Composable
private fun tutorialFeatureCard(
    title: String,
    description: String,
) {
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun tutorialShortcutCard(key: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = ComponentColors.bannerCardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.large),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(key),
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(
                    text = stringResource("$key.key"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun tutorialLinkItem(
    title: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ComponentColors.primaryTextButtonColors(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
