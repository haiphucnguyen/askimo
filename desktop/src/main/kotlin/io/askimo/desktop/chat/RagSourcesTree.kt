/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.askimo.core.chat.domain.KnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFilesKnowledgeSourceConfig
import io.askimo.core.chat.domain.LocalFoldersKnowledgeSourceConfig
import io.askimo.core.chat.domain.UrlKnowledgeSourceConfig
import io.askimo.desktop.common.i18n.stringResource
import io.askimo.desktop.common.theme.ComponentColors
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Tree view component for displaying RAG knowledge sources.
 * Shows files and folders with expandable/collapsible functionality.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ragSourcesTree(
    sources: List<KnowledgeSourceConfig>,
    modifier: Modifier = Modifier,
) {
    // Selection state
    var selectedNode by remember { mutableStateOf<TreeNode?>(null) }

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

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(treeNodes) { node ->
            treeNodeItem(
                node = node,
                level = 0,
                selectedNode = selectedNode,
                onNodeSelected = { selectedNode = it },
            )
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
    modifier: Modifier = Modifier,
) {
    when (node) {
        is FolderTreeNode -> folderNodeItem(node, level, selectedNode, onNodeSelected, modifier)
        is FileTreeNode -> fileNodeItem(node, level, selectedNode, onNodeSelected, modifier)
        is UrlTreeNode -> urlNodeItem(node, level, selectedNode, onNodeSelected, modifier)
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
        MaterialTheme.colorScheme.surface
    }

    Column(modifier = modifier) {
        // Folder row
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = node.fullPath,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor, RoundedCornerShape(4.dp))
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
                        tint = ComponentColors.secondaryIconColor(),
                        modifier = Modifier.size(16.dp),
                    )

                    // Folder icon
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.FolderOpen
                        } else {
                            Icons.Default.Folder
                        },
                        contentDescription = null,
                        tint = ComponentColors.secondaryIconColor(),
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
                }

                // Context menu
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
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val isSelected = selectedNode == node
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = node.fullPath,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        Box {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(4.dp))
                    .onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Primary),
                        onClick = { onNodeSelected(node) },
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
                    tint = ComponentColors.secondaryIconColor(),
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
            }

            // Context menu
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = DpOffset(x = 0.dp, y = 0.dp),
            ) {
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
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val isSelected = selectedNode == node
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = node.fullPath,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        Box {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(4.dp))
                    .onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Primary),
                        onClick = { onNodeSelected(node) },
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
                    tint = ComponentColors.secondaryIconColor(),
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
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = java.awt.datatransfer.StringSelection(text)
        clipboard.setContents(stringSelection, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
