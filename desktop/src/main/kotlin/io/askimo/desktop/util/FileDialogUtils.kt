/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.util

import io.askimo.core.chat.util.FileTypeSupport
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
     * Creates a filename filter that only accepts text-extractable files (no images).
     */
    fun createTextFileFilter(): FilenameFilter = FilenameFilter { _, name ->
        val extension = FileTypeSupport.getExtension(name)
        FileTypeSupport.isTextExtractable(extension)
    }

    /**
     * Creates a filename filter that only accepts image files.
     */
    fun createImageFileFilter(): FilenameFilter = FilenameFilter { _, name ->
        val extension = FileTypeSupport.getExtension(name)
        FileTypeSupport.isImageExtension(extension)
    }
}
