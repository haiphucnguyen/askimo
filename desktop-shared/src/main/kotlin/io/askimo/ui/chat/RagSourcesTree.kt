/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFilesKnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFoldersKnowledgeSourceConfig
import io.askimo.core.chat.domain.UrlKnowledgeSourceConfig
import io.askimo.ui.common.i18n.stringResource
import io.askimo.ui.common.theme.AppComponents
import io.askimo.ui.common.ui.themedTooltip
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

/**
 * Tree view component for displaying RAG knowledge sources.
 * Shows files and folders with expandable/collapsible functionality.
 *
 * When [onAddToChat] is provided, each file/folder row gains a "+" quick-add button,
 * a context-menu "Add to chat" option, and a sticky bottom bar for multi-file selection.
 *
 * @param sources          Knowledge source configs to display.
 * @param modifier         Optional modifier.
 * @param selectedNode     Currently selected node (hoisted to allow viewer integration).
 * @param onNodeSelected   Called when a node is selected; passes `null` to deselect.
 * @param onRemove         Called when the user removes a knowledge source.
 * @param onAddToChat      Called with a list of file paths to attach to the current chat message.
 *                         Pass `null` to disable the feature.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ragSourcesTree(
    sources: List<KnowledgeSourceConfig>,
    modifier: Modifier = Modifier,
    selectedNode: TreeNode? = null,
    onNodeSelected: (TreeNode?) -> Unit = {},
    onRemove: (KnowledgeSourceConfig) -> Unit = {},
    onAddToChat: ((List<String>) -> Unit)? = null,
) {
    // Paths currently checked for bulk "Add to chat"
    val chatSelection = remember { mutableStateSetOf<String>() }

    // Convert sources to tree nodes
    val treeNodes = remember(sources) {
        sources.map { source ->
            when (source) {
                is LocalFoldersKnowledgeSourceConfig -> {
                    FolderTreeNode(
                        path = source.resourceIdentifier,
                        source = source,
                    )
                }

                is LocalFilesKnowledgeSourceConfig -> {
                    FileTreeNode(
                        path = source.resourceIdentifier,
                        source = source,
                    )
                }

                is UrlKnowledgeSourceConfig -> {
                    UrlTreeNode(
                        url = source.resourceIdentifier,
                        source = source,
                    )
                }
            }
        }
    }

    val listState = rememberLazyListState()

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 22.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(treeNodes) { node ->
                    treeNodeItem(
                        node = node,
                        level = 0,
                        selectedNode = selectedNode,
                        onNodeSelected = onNodeSelected,
                        onRemove = onRemove,
                        chatSelection = chatSelection,
                        onAddToChat = onAddToChat,
                    )
                }
            }

            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(listState),
                style = ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 6.dp,
                    shape = MaterialTheme.shapes.small,
                    hoverDurationMillis = 300,
                    unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
            )
        }

        // Sticky bottom action bar — only shown when files are selected for chat
        if (onAddToChat != null && chatSelection.isNotEmpty()) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource("rag.tree.chat.selected", chatSelection.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { chatSelection.clear() },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(
                            text = stringResource("rag.tree.chat.clear"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Button(
                        onClick = {
                            onAddToChat(chatSelection.toList())
                            chatSelection.clear()
                        },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource("rag.tree.chat.add"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sealed class representing different types of tree nodes
 */
sealed class TreeNode {
    abstract val displayName: String
    abstract val fullPath: String
}

data class FolderTreeNode(
    val path: String,
    val source: LocalFoldersKnowledgeSourceConfig,
    val children: List<TreeNode> = emptyList(),
) : TreeNode() {
    override val displayName: String = File(path).name.ifEmpty { path }
    override val fullPath: String = path
}

data class FileTreeNode(
    val path: String,
    val source: LocalFilesKnowledgeSourceConfig,
) : TreeNode() {
    override val displayName: String = File(path).name.ifEmpty { path }
    override val fullPath: String = path
}

data class UrlTreeNode(
    val url: String,
    val source: UrlKnowledgeSourceConfig,
) : TreeNode() {
    override val displayName: String = url
    override val fullPath: String = url
}

/**
 * Renders a single tree node (file, folder, or URL)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun treeNodeItem(
    node: TreeNode,
    level: Int,
    selectedNode: TreeNode?,
    onNodeSelected: (TreeNode) -> Unit,
    onRemove: (KnowledgeSourceConfig) -> Unit,
    chatSelection: androidx.compose.runtime.snapshots.SnapshotStateSet<String> = remember { mutableStateSetOf() },
    onAddToChat: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (node) {
        is FolderTreeNode -> folderNodeItem(node, level, selectedNode, onNodeSelected, onRemove, chatSelection, onAddToChat, modifier)
        is FileTreeNode -> fileNodeItem(node, level, selectedNode, onNodeSelected, onRemove, chatSelection, onAddToChat, modifier)
        is UrlTreeNode -> urlNodeItem(node, level, selectedNode, onNodeSelected, onRemove, modifier)
    }
}

/**
 * Renders a folder node with expand/collapse functionality
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun folderNodeItem(
    node: FolderTreeNode,
    level: Int,
    selectedNode: TreeNode?,
    onNodeSelected: (TreeNode) -> Unit,
    onRemove: (KnowledgeSourceConfig) -> Unit,
    chatSelection: androidx.compose.runtime.snapshots.SnapshotStateSet<String> = remember { mutableStateSetOf() },
    onAddToChat: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    val children = remember(node.path, isExpanded) {
        if (isExpanded) {
            loadFolderChildren(node.path)
        } else {
            emptyList()
        }
    }

    val isSelected = selectedNode == node
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Column(modifier = modifier) {
        // Folder row
        themedTooltip(text = node.fullPath) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .onClick(
                            matcher = PointerMatcher.mouse(PointerButton.Primary),
                            onClick = {
                                isExpanded = !isExpanded
                                onNodeSelected(node)
                            },
                        )
                        .onClick(
                            matcher = PointerMatcher.mouse(PointerButton.Secondary),
                            onClick = { showContextMenu = true },
                        )
                        .padding(start = (level * 16).dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                        .pointerHoverIcon(PointerIcon.Hand),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Expand/collapse arrow
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.KeyboardArrowDown
                        } else {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        },
                        contentDescription = if (isExpanded) {
                            stringResource("rag.tree.collapse")
                        } else {
                            stringResource("rag.tree.expand")
                        },
                        tint = AppComponents.secondaryIconColor(),
                        modifier = Modifier.size(16.dp),
                    )

                    // Folder icon
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                        contentDescription = null,
                        tint = AppComponents.secondaryIconColor(),
                        modifier = Modifier.size(18.dp),
                    )

                    // Folder name
                    Text(
                        text = node.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // No "Add to chat" button for folders — only individual files can be attached
                }

                // Context menu — no "Add to chat" option for folders
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = DpOffset(x = 0.dp, y = 0.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource("rag.tree.folder.open")) },
                        onClick = {
                            openInFileBrowser(node.path)
                            showContextMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource("rag.tree.folder.copy.path")) },
                        onClick = {
                            copyToClipboard(node.path)
                            showContextMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource("rag.tree.remove")) },
                        onClick = {
                            onRemove(node.source)
                            showContextMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Remove, contentDescription = null)
                        },
                    )
                }
            }
        }

        // Show children when expanded
        if (isExpanded && children.isNotEmpty()) {
            Column {
                children.forEach { child ->
                    treeNodeItem(
                        node = child,
                        level = level + 1,
                        selectedNode = selectedNode,
                        onNodeSelected = onNodeSelected,
                        onRemove = onRemove,
                        chatSelection = chatSelection,
                        onAddToChat = onAddToChat,
                    )
                }
            }
        }
    }
}

/**
 * Renders a file node
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun fileNodeItem(
    node: FileTreeNode,
    level: Int,
    selectedNode: TreeNode?,
    onNodeSelected: (TreeNode) -> Unit,
    onRemove: (KnowledgeSourceConfig) -> Unit,
    chatSelection: androidx.compose.runtime.snapshots.SnapshotStateSet<String> = remember { mutableStateSetOf() },
    onAddToChat: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val isSelected = selectedNode == node
    val isInChatSelection = node.path in chatSelection
    val backgroundColor = when {
        isInChatSelection -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    themedTooltip(text = node.fullPath) {
        Box {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(backgroundColor, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Primary),
                        onClick = { onNodeSelected(node) },
                        onDoubleClick = {
                            openInFileBrowser(node.path)
                        },
                    )
                    .onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Secondary),
                        onClick = { showContextMenu = true },
                    )
                    .padding(start = (level * 16 + 16).dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // File icon
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = AppComponents.secondaryIconColor(),
                    modifier = Modifier.size(18.dp),
                )

                // File name
                Text(
                    text = node.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // "Add to chat" quick button
                if (onAddToChat != null) {
                    if (isInChatSelection) {
                        IconButton(
                            onClick = { chatSelection.remove(node.path) },
                            modifier = Modifier.size(20.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource("rag.tree.chat.deselect"),
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { chatSelection.add(node.path) },
                            modifier = Modifier.size(20.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = stringResource("rag.tree.chat.select"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Context menu
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = DpOffset(x = 0.dp, y = 0.dp),
            ) {
                if (onAddToChat != null) {
                    if (isInChatSelection) {
                        DropdownMenuItem(
                            text = { Text(stringResource("rag.tree.chat.deselect")) },
                            onClick = {
                                chatSelection.remove(node.path)
                                showContextMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource("rag.tree.chat.select")) },
                            onClick = {
                                chatSelection.add(node.path)
                                showContextMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                            },
                        )
                    }
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(stringResource("rag.tree.file.preview")) },
                    onClick = {
                        onNodeSelected(node)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("rag.tree.file.open")) },
                    onClick = {
                        openInFileBrowser(node.path)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("rag.tree.file.open.folder")) },
                    onClick = {
                        openContainingFolder(node.path)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Folder, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("rag.tree.file.copy.path")) },
                    onClick = {
                        copyToClipboard(node.path)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("rag.tree.remove")) },
                    onClick = {
                        onRemove(node.source)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Remove, contentDescription = null)
                    },
                )
            }
        }
    }
}

/**
 * Renders a URL node
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun urlNodeItem(
    node: UrlTreeNode,
    level: Int,
    selectedNode: TreeNode?,
    onNodeSelected: (TreeNode) -> Unit,
    onRemove: (KnowledgeSourceConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val isSelected = selectedNode == node
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    themedTooltip(text = node.fullPath) {
        Box {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(backgroundColor, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Primary),
                        onClick = { onNodeSelected(node) },
                        onDoubleClick = {
                            openInBrowser(node.url)
                        },
                    )
                    .onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Secondary),
                        onClick = { showContextMenu = true },
                    )
                    .padding(start = (level * 16).dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // URL icon
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = AppComponents.secondaryIconColor(),
                    modifier = Modifier.size(18.dp),
                )

                // URL text
                Text(
                    text = node.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // Context menu
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = DpOffset(x = 0.dp, y = 0.dp),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource("rag.tree.url.open")) },
                    onClick = {
                        openInBrowser(node.url)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("rag.tree.url.copy")) },
                    onClick = {
                        copyToClipboard(node.url)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource("rag.tree.remove")) },
                    onClick = {
                        onRemove(node.source)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Remove, contentDescription = null)
                    },
                )
            }
        }
    }
}

/**
 * Loads children files and folders for a given directory path
 */
private fun loadFolderChildren(folderPath: String): List<TreeNode> {
    val folder = File(folderPath)
    if (!folder.exists() || !folder.isDirectory) {
        return emptyList()
    }

    val children = mutableListOf<TreeNode>()
    val files = folder.listFiles() ?: return emptyList()

    // Sort: folders first, then files, both alphabetically
    val sortedFiles = files.sortedWith(
        compareBy<File> { !it.isDirectory }
            .thenBy { it.name.lowercase() },
    )

    sortedFiles.forEach { file ->
        when {
            file.isDirectory -> {
                children.add(
                    FolderTreeNode(
                        path = file.absolutePath,
                        source = LocalFoldersKnowledgeSourceConfig(
                            resourceIdentifier = file.absolutePath,
                        ),
                    ),
                )
            }

            file.isFile -> {
                children.add(
                    FileTreeNode(
                        path = file.absolutePath,
                        source = LocalFilesKnowledgeSourceConfig(
                            resourceIdentifier = file.absolutePath,
                        ),
                    ),
                )
            }
        }
    }

    return children
}

/**
 * Opens a file or folder in the OS file browser
 */
private fun openInFileBrowser(path: String) {
    try {
        val file = File(path)
        if (file.exists()) {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Opens the containing folder of a file in the OS file browser
 */
private fun openContainingFolder(filePath: String) {
    try {
        val file = File(filePath)
        val parentFolder = file.parentFile
        if (parentFolder?.exists() == true) {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(parentFolder)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Opens a URL in the default web browser
 */
private fun openInBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Copies text to system clipboard
 */
private fun copyToClipboard(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = StringSelection(text)
        clipboard.setContents(stringSelection, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
