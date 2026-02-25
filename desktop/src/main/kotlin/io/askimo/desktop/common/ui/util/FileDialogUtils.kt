/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.common.ui.util

import io.askimo.core.chat.util.FileTypeSupport
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

/**
 * Utilities for working with file dialogs.
 */
object FileDialogUtils {
    /**
     * Creates a filename filter that accepts all supported files (text + images).
     * This allows users to attach both text files (for RAG/content extraction) and images (for vision).
     */
    fun createSupportedFileFilter(): FilenameFilter = FilenameFilter { _, name ->
        val extension = FileTypeSupport.getExtension(name)
        FileTypeSupport.isSupported(extension)
    }

    /**
     * Opens a native folder selection dialog and returns the selected folder path.
     * Uses java.awt.FileDialog with apple.awt.fileDialogForDirectories on macOS
     * for a true native folder picker experience.
     *
     * @param title The title of the folder selection dialog
     * @return The absolute path of the selected folder, or null if cancelled
     */
    fun chooseFolderPath(title: String): String? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        dialog.isVisible = true
        System.setProperty("apple.awt.fileDialogForDirectories", "false")

        return if (dialog.file != null) {
            File(dialog.directory, dialog.file).absolutePath
        } else {
            null
        }
    }
}
