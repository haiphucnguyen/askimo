/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.desktop.project

import io.askimo.desktop.common.ui.util.FileDialogUtils
import java.awt.FileDialog
import java.awt.Frame
import java.util.UUID

/**
 * Helper class for browsing and adding knowledge sources (folders, files, URLs)
 */
class KnowledgeSourceBrowser(
    private val browseFolderTitle: String,
    private val browseFileTitle: String,
) {
    /**
     * Browse for a folder and return a KnowledgeSourceItem.Folder if selected
     */
    fun browseForFolder(): KnowledgeSourceItem.Folder? {
        val folderPath = FileDialogUtils.chooseFolderPath(browseFolderTitle)
        return if (folderPath != null) {
            KnowledgeSourceItem.Folder(
                id = UUID.randomUUID().toString(),
                path = folderPath,
                isValid = validateFolder(folderPath),
            )
        } else {
            null
        }
    }

    /**
     * Browse for files and return a list of KnowledgeSourceItem.File
     */
    fun browseForFiles(): List<KnowledgeSourceItem.File> {
        val dialog = FileDialog(null as Frame?, browseFileTitle, FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.setFilenameFilter(FileDialogUtils.createSupportedFileFilter())
        dialog.isVisible = true

        return dialog.files?.map { file ->
            KnowledgeSourceItem.File(
                id = UUID.randomUUID().toString(),
                path = file.absolutePath,
                isValid = validateFile(file.absolutePath),
            )
        } ?: emptyList()
    }

    /**
     * Handle adding sources based on type info
     * Returns a list of new sources to add (empty list for URL type, which needs separate dialog)
     */
    fun handleAddSource(
        typeInfo: KnowledgeSourceItem.TypeInfo,
        onShowUrlDialog: () -> Unit,
    ): List<KnowledgeSourceItem> = when (typeInfo) {
        KnowledgeSourceItem.TypeInfo.FOLDER -> {
            browseForFolder()?.let { listOf(it) } ?: emptyList()
        }
        KnowledgeSourceItem.TypeInfo.FILE -> {
            browseForFiles()
        }
        KnowledgeSourceItem.TypeInfo.URL -> {
            onShowUrlDialog()
            emptyList()
        }
    }
}
