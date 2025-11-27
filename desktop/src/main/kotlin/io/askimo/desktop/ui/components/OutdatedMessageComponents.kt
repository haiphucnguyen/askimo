/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.askimo.desktop.i18n.stringResource
import io.askimo.desktop.model.ChatMessage

/**
 * Sealed class to represent different types of message groups
 */
sealed class MessageGroup {
    data class ActiveMessage(val message: ChatMessage) : MessageGroup()
    data class OutdatedBranch(val messages: List<ChatMessage>) : MessageGroup()
}

/**
 * Group messages into active and outdated branches for display
 */
fun groupMessagesWithOutdatedBranches(messages: List<ChatMessage>): List<MessageGroup> {
    val groups = mutableListOf<MessageGroup>()
    val outdatedBuffer = mutableListOf<ChatMessage>()

    messages.forEach { message ->
        if (message.isOutdated) {
            outdatedBuffer.add(message)
        } else {
            // If we have outdated messages buffered, create an outdated group
            if (outdatedBuffer.isNotEmpty()) {
                groups.add(MessageGroup.OutdatedBranch(outdatedBuffer.toList()))
                outdatedBuffer.clear()
            }
            // Add active message
            groups.add(MessageGroup.ActiveMessage(message))
        }
    }

    // Handle remaining outdated messages at the end
    if (outdatedBuffer.isNotEmpty()) {
        groups.add(MessageGroup.OutdatedBranch(outdatedBuffer.toList()))
    }

    return groups
}

/**
 * Component to display a collapsible branch of outdated messages
 */
@Composable
fun outdatedBranchComponent(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
    ) {
        // Collapsible header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .pointerHoverIcon(PointerIcon.Hand),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) {
                        stringResource("outdated.collapse")
                    } else {
                        stringResource("outdated.expand")
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource("outdated.branch.header", messages.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic,
                )
            }
        }

        // Expandable content
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
            ) {
                messages.forEach { message ->
                    outdatedMessageItem(message)
                }
            }
        }
    }
}

/**
 * Component to display a single outdated message
 */
@Composable
fun outdatedMessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                },
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            ),
            modifier = Modifier
                .padding(horizontal = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                // Show "outdated" label
                Text(
                    text = stringResource("outdated.label"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
