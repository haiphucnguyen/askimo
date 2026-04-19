/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.ui.common.ui.util

import io.askimo.core.chat.util.FileTypeSupport
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Utilities for working with file/folder pickers via FileKit.
 *
 * FileKit uses the native OS dialog on every platform:
 *  - macOS  → NSOpenPanel / NSSavePanel
 *  - Windows → IFileOpenDialog / IFileSaveDialog
 *  - Linux   → GTK file chooser (xdg-portal / AWT fallback)
 *
 * All helpers are `suspend` functions — call them from a coroutine scope
 * (e.g. `LaunchedEffect`, a `CoroutineScope` button click handler, or `rememberCoroutineScope`).
 */
object FileDialogUtils {

    /**
     * Builds a hint accessory panel suitable for attaching to a [JFileChooser].
     *
     * Uses a non-editable, word-wrapped [JTextArea] so the text reflows naturally when the
     * dialog is resized and arbitrarily long / multi-line hints are rendered correctly.
     * The panel is visually separated from the chooser body by a 1 px top border.
     */
    private fun buildHintAccessory(hint: String): JPanel {
        val textArea = JTextArea(hint).apply {
            isEditable = false
            isFocusable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(font.name, Font.PLAIN, 11)
            foreground = Color(0x60, 0x60, 0x60)
            border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
        }
        return JPanel(BorderLayout()).apply {
            add(textArea, BorderLayout.CENTER)
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color(0xCC, 0xCC, 0xCC))
        }
    }

    /**
     * Opens a native folder picker and returns the selected directory path, or null if cancelled.
     */
    suspend fun pickFolderPath(title: String): String? = FileKit.openDirectoryPicker()?.path

    /**
     * Opens a [JFileChooser]-based folder picker with a customisable approve-button label
     * (defaults to "Select"). This avoids the platform default "Open" label that appears
     * in the native NSOpenPanel / IFileOpenDialog and is therefore less confusing when the
     * intent is to *select* a directory rather than open a file inside it.
     *
     * An optional [navigationHint] text is shown as a small accessory panel inside the
     * dialog (e.g. "Double-click a folder to navigate into it") to guide novice users.
     *
     * Runs on the AWT Event Dispatch Thread via [SwingUtilities.invokeAndWait] to comply
     * with Swing's threading model, then returns the result on the caller's coroutine context.
     *
     * @param title            Dialog window title.
     * @param approveButtonText Label shown on the confirm button (default: "Select").
     * @param navigationHint   Optional hint shown at the bottom of the dialog to guide navigation.
     * @return The absolute path of the chosen directory, or `null` if cancelled.
     */
    suspend fun pickFolderPathWithChooser(
        title: String,
        approveButtonText: String = "Select",
        navigationHint: String? = "Tip: Double-click a folder to open it, then click \u201c$approveButtonText\u201d to choose it",
    ): String? = withContext(Dispatchers.IO) {
        // JFileChooser must be shown on the AWT EDT; invokeAndWait blocks the IO thread
        // until the dialog is dismissed so we can safely return the result.
        var selectedPath: String? = null
        SwingUtilities.invokeAndWait {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (_: Exception) { /* best-effort */ }

            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false

                if (!navigationHint.isNullOrBlank()) {
                    accessory = buildHintAccessory(navigationHint)
                }
            }

            val result = chooser.showDialog(null, approveButtonText)
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedPath = chooser.selectedFile?.absolutePath
            }
        }
        selectedPath
    }

    /**
     * Opens a [JFileChooser]-based multi-file picker with a supported-file-type filter and
     * a customisable approve-button label (defaults to "Select").
     *
     * This is intentionally non-native — the same UX exception applied to the folder chooser —
     * so that the "Add Reference" dialog feels consistent and gives users a richer filter UI.
     *
     * Runs on the AWT Event Dispatch Thread via [SwingUtilities.invokeAndWait] to comply
     * with Swing's threading model, then returns the result on the caller's coroutine context.
     *
     * @param title            Dialog window title.
     * @param approveButtonText Label shown on the confirm button (default: "Select").
     * @param extensions       File extensions to filter (without dot). Null = show all supported types.
     * @return The list of absolute paths of chosen files, or an empty list if cancelled.
     */
    suspend fun pickFilePathsWithChooser(
        title: String,
        approveButtonText: String = "Select",
        extensions: List<String>? = FileTypeSupport.supportedExtensions(),
    ): List<String> = withContext(Dispatchers.IO) {
        var selectedPaths: List<String> = emptyList()
        SwingUtilities.invokeAndWait {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (_: Exception) { /* best-effort */ }

            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = true

                if (!extensions.isNullOrEmpty()) {
                    val extsArray = extensions.toTypedArray()
                    val description = extsArray.joinToString(", ") { ".$it" }
                    addChoosableFileFilter(FileNameExtensionFilter(description, *extsArray))
                    isAcceptAllFileFilterUsed = false
                }
            }

            val result = chooser.showDialog(null, approveButtonText)
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedPaths = chooser.selectedFiles.map { it.absolutePath }
            }
        }
        selectedPaths
    }

    /**
     * Opens a native single-file picker filtered to [extensions] and returns the
     * selected file path, or null if cancelled.
     * Pass null to [extensions] to allow all supported file types.
     */
    suspend fun pickFilePath(
        title: String,
        extensions: List<String>? = FileTypeSupport.supportedExtensions(),
    ): String? = FileKit.openFilePicker(
        type = FileKitType.File(extensions ?: emptyList()),
    )?.path

    /**
     * Opens a native multi-file picker filtered to [extensions] and returns a list
     * of selected file paths (empty if cancelled).
     * Pass null to [extensions] to allow all supported file types.
     */
    suspend fun pickFilePaths(
        title: String,
        extensions: List<String>? = FileTypeSupport.supportedExtensions(),
    ): List<String> = FileKit.openFilePicker(
        type = FileKitType.File(extensions ?: emptyList()),
        mode = FileKitMode.Multiple(),
    )?.map { it.path } ?: emptyList()

    /**
     * Opens a native image-file picker and returns the selected file path, or null if cancelled.
     */
    suspend fun pickImagePath(title: String): String? = FileKit.openFilePicker(
        type = FileKitType.Image,
    )?.path

    /**
     * Opens a native save dialog and returns the target [File], or null if cancelled.
     *
     * @param suggestedName Default file name shown in the dialog (without extension).
     * @param extension     File extension without dot, e.g. `"pdf"`.
     */
    suspend fun pickSavePath(
        suggestedName: String,
        extension: String,
        title: String = "",
    ): File? = FileKit.openFileSaver(
        suggestedName = suggestedName,
        extension = extension,
        dialogSettings = FileKitDialogSettings.createDefault(),
    )?.let { File(it.path) }
}
