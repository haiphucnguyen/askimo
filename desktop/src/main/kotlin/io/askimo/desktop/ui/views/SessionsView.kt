/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.askimo.core.session.ChatSession
import io.askimo.desktop.viewmodel.SessionsViewModel
import java.time.format.DateTimeFormatter

@Composable
fun sessionsView(
    viewModel: SessionsViewModel,
    onResumeSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        // Header with title and refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chat Sessions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh sessions",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content area
        Box(modifier = Modifier.weight(1f)) {
            when {
                viewModel.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                viewModel.errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "❌ ${viewModel.errorMessage}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = {
                            viewModel.clearError()
                            viewModel.refresh()
                        }) {
                            Text("Try Again")
                        }
                    }
                }
                viewModel.pagedSessions?.isEmpty == true -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No chat sessions found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "💡 Start a new conversation to create your first session!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sessions list
                        val pagedSessions = viewModel.pagedSessions!!
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            pagedSessions.sessions.forEach { session ->
                                sessionCard(
                                    session = session,
                                    onResumeSession = onResumeSession,
                                    onDeleteSession = { viewModel.deleteSession(it) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Pagination controls
                        if (pagedSessions.totalPages > 1) {
                            paginationControls(
                                currentPage = pagedSessions.currentPage,
                                totalPages = pagedSessions.totalPages,
                                hasPrevious = pagedSessions.hasPreviousPage,
                                hasNext = pagedSessions.hasNextPage,
                                onPrevious = { viewModel.previousPage() },
                                onNext = { viewModel.nextPage() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun sessionCard(
    session: ChatSession,
    onResumeSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // Clickable content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onResumeSession(session.id) }
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ID: ${session.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Created: ${session.createdAt.format(formatter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Updated: ${session.updatedAt.format(formatter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Menu button
            Box {
                IconButton(
                    onClick = { showMenu = true },
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
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
                    )
                }
            }
        }
    }
}

@Composable
private fun paginationControls(
    currentPage: Int,
    totalPages: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = "Previous page",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Text(
            text = "Page $currentPage of $totalPages",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        IconButton(
            onClick = onNext,
            enabled = hasNext,
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Next page",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
