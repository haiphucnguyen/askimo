/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.onboarding

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.askimo.core.i18n.LocalizationManager
import io.askimo.ui.common.components.primaryButton
import io.askimo.ui.common.components.secondaryButton
import io.askimo.ui.common.i18n.stringResource
import io.askimo.ui.common.theme.AppComponents
import io.askimo.ui.common.theme.Spacing
import io.askimo.ui.common.theme.ThemePreferences
import io.askimo.ui.common.ui.clickableCard
import java.util.Locale

private const val TOTAL_STEPS = 8

/**
 * Onboarding wizard shown on first launch.
 *
 * Steps:
 *  0 — Language selection  (required — no skip)
 *  1 — Analytics consent   (required — user must choose Yes or No)
 *  2 — Welcome
 *  3 — Profile (name + occupation, skippable)
 *  4 — Directives (intro)
 *  5 — Plans (intro)
 *  6 — Skills (intro)
 *  7 — Ready
 *
 * @param initialName        Pre-filled name (from existing profile, if any).
 * @param initialOccupation  Pre-filled occupation.
 * @param onComplete         Called with (locale, analyticsAccepted, name, occupation) when the wizard finishes.
 */
@Composable
fun onboardingWizardDialog(
    initialName: String = "",
    initialOccupation: String = "",
    onComplete: (locale: Locale, analyticsAccepted: Boolean, name: String, occupation: String) -> Unit,
) {
    var currentStep by remember { mutableStateOf(0) }
    var selectedLocale by remember { mutableStateOf(Locale.ENGLISH) }
    var analyticsAccepted by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(initialName) }
    var occupation by remember { mutableStateOf(initialOccupation) }

    // Reset scroll on each step change
    val scrollState = rememberScrollState()
    LaunchedEffect(currentStep) { scrollState.animateScrollTo(0) }

    // Analytics step (step 1) uses the shared nav bar like all other steps

    Dialog(
        onDismissRequest = { /* prevent accidental dismissal */ },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(760.dp).height(820.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // ── Header ────────────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource("onboarding.title"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource("onboarding.step.indicator", currentStep + 1, TOTAL_STEPS),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.large))

                // ── Step content ─────────────────────────────────────────────
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
                            0 -> onboardingStepLanguage(
                                selectedLocale = selectedLocale,
                                onLocaleChange = { selectedLocale = it },
                            )

                            1 -> onboardingStepAnalytics(
                                analyticsAccepted = analyticsAccepted,
                                onAnalyticsAcceptedChange = { analyticsAccepted = it },
                            )

                            2 -> onboardingStepWelcome()

                            3 -> onboardingStepProfile(
                                name = name,
                                occupation = occupation,
                                onNameChange = { name = it },
                                onOccupationChange = { occupation = it },
                            )

                            4 -> onboardingStepDirectives()

                            5 -> onboardingStepPlans()

                            6 -> onboardingStepSkills()

                            7 -> onboardingStepReady()
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

                // ── Progress dots ─────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(TOTAL_STEPS) { index ->
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
                        if (index < TOTAL_STEPS - 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }

                // ── Navigation ────────────────────────────────────────────────
                Spacer(modifier = Modifier.height(Spacing.large))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Step 0 (language) has no back — it is required
                    if (currentStep == 0) {
                        Spacer(modifier = Modifier.width(1.dp))
                    } else {
                        secondaryButton(onClick = { currentStep-- }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                            Text(stringResource("onboarding.previous"))
                        }
                    }

                    if (currentStep < TOTAL_STEPS - 1) {
                        primaryButton(onClick = { currentStep++ }) {
                            Text(stringResource("onboarding.next"))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    } else {
                        primaryButton(onClick = { onComplete(selectedLocale, analyticsAccepted, name, occupation) }) {
                            Text(stringResource("onboarding.finish"))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 0: Language
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingStepLanguage(
    selectedLocale: Locale,
    onLocaleChange: (Locale) -> Unit,
) {
    val availableLanguages = remember { LocalizationManager.availableLocales }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
            text = stringResource("onboarding.step.language.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource("onboarding.step.language.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = AppComponents.bannerCardColors(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Text(
                    text = stringResource("onboarding.step.language.label"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableCard { dropdownExpanded = true },
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
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    AppComponents.dropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        availableLanguages.forEach { (locale, displayName) ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (locale == selectedLocale) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(24.dp))
                                        }
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                },
                                onClick = {
                                    onLocaleChange(locale)
                                    ThemePreferences.setLocale(locale)
                                    dropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1: Analytics
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingStepAnalytics(
    analyticsAccepted: Boolean,
    onAnalyticsAcceptedChange: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        // Header with icon
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource("onboarding.step.analytics.title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource("onboarding.step.analytics.description"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Privacy summary box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    MaterialTheme.shapes.small,
                )
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource("onboarding.step.analytics.collected.yes.features"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource("onboarding.step.analytics.collected.yes.version"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource("onboarding.step.analytics.collected.no.conversations"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource("onboarding.step.analytics.collected.no.personal"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource("onboarding.step.analytics.detail"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    MaterialTheme.shapes.small,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (analyticsAccepted) {
                        stringResource("onboarding.step.analytics.toggle.on")
                    } else {
                        stringResource("onboarding.step.analytics.toggle.off")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Switch(
                checked = analyticsAccepted,
                onCheckedChange = onAnalyticsAcceptedChange,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
        }

        TextButton(
            onClick = { uriHandler.openUri("https://askimo.chat/security/") },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(
                text = stringResource("onboarding.step.analytics.learn_more"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2: Welcome
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingStepWelcome() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
            text = stringResource("onboarding.step.welcome.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource("onboarding.step.welcome.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        onboardingFeatureCard(
            title = stringResource("onboarding.step.welcome.feature1.title"),
            description = stringResource("onboarding.step.welcome.feature1.description"),
        )
        onboardingFeatureCard(
            title = stringResource("onboarding.step.welcome.feature2.title"),
            description = stringResource("onboarding.step.welcome.feature2.description"),
        )
        onboardingFeatureCard(
            title = stringResource("onboarding.step.welcome.feature3.title"),
            description = stringResource("onboarding.step.welcome.feature3.description"),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3: Profile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingStepProfile(
    name: String,
    occupation: String,
    onNameChange: (String) -> Unit,
    onOccupationChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
            text = stringResource("onboarding.step.profile.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource("onboarding.step.profile.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(
                text = stringResource("onboarding.step.profile.name.label"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource("onboarding.step.profile.name.placeholder"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(
                text = stringResource("onboarding.step.profile.occupation.label"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = occupation,
                onValueChange = onOccupationChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource("onboarding.step.profile.occupation.placeholder"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4: Directives
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingStepDirectives() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
            text = stringResource("onboarding.step.directives.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource("onboarding.step.directives.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        onboardingFeatureCard(
            title = stringResource("onboarding.step.directives.feature1.title"),
            description = stringResource("onboarding.step.directives.feature1.description"),
        )
        onboardingFeatureCard(
            title = stringResource("onboarding.step.directives.feature2.title"),
            description = stringResource("onboarding.step.directives.feature2.description"),
        )
        onboardingFeatureCard(
            title = stringResource("onboarding.step.directives.feature3.title"),
            description = stringResource("onboarding.step.directives.feature3.description"),
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        onboardingDocLink(
            label = stringResource("onboarding.step.directives.link"),
            url = "https://askimo.chat/docs/desktop/directives/",
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 5: Plans
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingStepPlans() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
            text = stringResource("onboarding.step.plans.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource("onboarding.step.plans.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        onboardingFeatureCard(
            title = stringResource("onboarding.step.plans.feature1.title"),
            description = stringResource("onboarding.step.plans.feature1.description"),
        )
        onboardingFeatureCard(
            title = stringResource("onboarding.step.plans.feature2.title"),
            description = stringResource("onboarding.step.plans.feature2.description"),
        )
        onboardingFeatureCard(
            title = stringResource("onboarding.step.plans.feature3.title"),
            description = stringResource("onboarding.step.plans.feature3.description"),
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        onboardingDocLink(
            label = stringResource("onboarding.step.plans.link"),
            url = "https://askimo.chat/docs/desktop/plans/",
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 6: Skills
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingStepSkills() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
            text = stringResource("onboarding.step.skills.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource("onboarding.step.skills.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        onboardingFeatureCard(
            title = stringResource("onboarding.step.skills.feature1.title"),
            description = stringResource("onboarding.step.skills.feature1.description"),
        )
        onboardingFeatureCard(
            title = stringResource("onboarding.step.skills.feature2.title"),
            description = stringResource("onboarding.step.skills.feature2.description"),
        )
        onboardingFeatureCard(
            title = stringResource("onboarding.step.skills.feature3.title"),
            description = stringResource("onboarding.step.skills.feature3.description"),
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        onboardingDocLink(
            label = stringResource("onboarding.step.skills.link"),
            url = "https://askimo.chat/docs/desktop/skills/",
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 7: Ready
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingStepReady() {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
            text = stringResource("onboarding.step.ready.title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource("onboarding.step.ready.description"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = AppComponents.bannerCardColors(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                onboardingLinkItem(
                    title = stringResource("onboarding.step.ready.link.docs"),
                    onClick = { uriHandler.openUri("https://askimo.chat/docs/") },
                )
                HorizontalDivider()
                onboardingLinkItem(
                    title = stringResource("onboarding.step.ready.link.providers"),
                    onClick = { uriHandler.openUri("https://askimo.chat/docs/desktop/ai-providers/") },
                )
                HorizontalDivider()
                onboardingLinkItem(
                    title = stringResource("onboarding.step.ready.link.github"),
                    onClick = { uriHandler.openUri("https://github.com/haiphucnguyen/askimo") },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun onboardingFeatureCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = AppComponents.bannerCardColors(),
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
private fun onboardingDocLink(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri(url) },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        colors = AppComponents.primaryTextButtonColors(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun onboardingLinkItem(title: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand),
        colors = AppComponents.primaryTextButtonColors(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
