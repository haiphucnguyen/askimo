/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.view.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.theme.ComponentColors

/**
 * Reusable session actions menu component that provides a dropdown with session-related actions.
 * Currently supports Export action.
 *
 * @param sessionId The ID of the session to perform actions on
 * @param onExportSession Callback invoked when the export action is triggered
 * @param modifier Optional modifier for the IconButton
 */
@Composable
fun sessionActionsMenu(
    sessionId: String,
    onExportSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box {
        IconButton(
            onClick = { expanded = true },
            modifier = modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource("action.more"),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        ComponentColors.themedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource("session.export"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    onExportSession(sessionId)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
        }
    }
}
