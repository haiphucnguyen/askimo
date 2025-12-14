/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.preferences.DeveloperModePreferences

/**
 * Reusable session action menu items.
 */
object SessionActionMenu {

    @Composable
    fun exportMenuItem(
        onExport: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource("session.export")) },
            onClick = {
                onDismiss()
                onExport()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                )
            },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
    }

    @Composable
    fun renameMenuItem(
        onRename: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource("session.rename.title")) },
            onClick = {
                onDismiss()
                onRename()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                )
            },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
    }

    @Composable
    fun starMenuItem(
        isStarred: Boolean,
        onStar: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    if (isStarred) {
                        stringResource("session.unstar")
                    } else {
                        stringResource("session.star")
                    },
                )
            },
            onClick = {
                onDismiss()
                onStar()
            },
            leadingIcon = {
                Icon(
                    if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (isStarred) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
    }

    @Composable
    fun deleteMenuItem(
        onDelete: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource("action.delete")) },
            onClick = {
                onDismiss()
                onDelete()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
    }

    @Composable
    fun showSessionSummaryMenuItem(
        onShowSessionSummary: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource("developer.menu.show.session.summary")) },
            onClick = {
                onDismiss()
                onShowSessionSummary()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.DeveloperMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
    }

    /**
     * Menu for sidebar sessions (includes star/unstar and developer mode options).
     */
    @Composable
    fun sidebarMenu(
        isStarred: Boolean,
        onExport: () -> Unit,
        onRename: () -> Unit,
        onStar: () -> Unit,
        onDelete: () -> Unit,
        onShowSessionSummary: () -> Unit = {},
        onDismiss: () -> Unit,
    ) {
        exportMenuItem(onExport = onExport, onDismiss = onDismiss)
        renameMenuItem(onRename = onRename, onDismiss = onDismiss)
        starMenuItem(isStarred = isStarred, onStar = onStar, onDismiss = onDismiss)

        // Show Session Summary - only in developer mode
        if (DeveloperModePreferences.isEnabled() &&
            DeveloperModePreferences.isActive.value
        ) {
            showSessionSummaryMenuItem(onShowSessionSummary = onShowSessionSummary, onDismiss = onDismiss)
        }

        deleteMenuItem(onDelete = onDelete, onDismiss = onDismiss)
    }

    /**
     * Menu for project view sessions (no star/unstar).
     */
    @Composable
    fun projectViewMenu(
        onExport: () -> Unit,
        onRename: () -> Unit,
        onDelete: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        exportMenuItem(onExport = onExport, onDismiss = onDismiss)
        renameMenuItem(onRename = onRename, onDismiss = onDismiss)
        deleteMenuItem(onDelete = onDelete, onDismiss = onDismiss)
    }
}
