/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.common.ui.util

import io.askimo.core.chat.util.FileTypeSupport
import java.awt.Component
import java.awt.Container
import java.io.FilenameFilter
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JLabel

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
     * Opens a folder selection dialog and returns the selected folder path.
     * Uses JFileChooser with DIRECTORIES_ONLY mode to show only folders (no files).
     * Completely hides the file format dropdown.
     *
     * @param title The title of the folder selection dialog
     * @return The absolute path of the selected folder, or null if cancelled
     */
    fun chooseFolderPath(title: String): String? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            isMultiSelectionEnabled = false

            removeFileFilterComboBox(this)
        }

        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
    }

    /**
     * Recursively removes the file filter combo box and its label from the JFileChooser UI.
     * This completely hides both the "File Format:" label and the dropdown.
     */
    private fun removeFileFilterComboBox(container: Component) {
        if (container is JComboBox<*>) {
            container.isVisible = false
            // Also hide the parent panel that contains the label
            container.parent?.isVisible = false
            return
        }

        if (container is Container) {
            for (component in container.components) {
                if (component is JComboBox<*>) {
                    component.isVisible = false
                    // Hide the parent container which includes the label
                    component.parent?.isVisible = false
                } else if (component is JLabel) {
                    // Hide any label that might be "File Format:" or similar
                    val labelText = component.text?.lowercase() ?: ""
                    if (labelText.contains("file") && (labelText.contains("format") || labelText.contains("type"))) {
                        component.isVisible = false
                    }
                } else {
                    removeFileFilterComboBox(component)
                }
            }
        }
    }
}
