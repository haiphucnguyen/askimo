/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.desktop.util

import io.askimo.core.chat.util.FileContentExtractor
import java.io.FilenameFilter

/**
 * Utilities for working with file dialogs.
 */
object FileDialogUtils {
    /**
     * Creates a filename filter that only accepts files with supported extensions.
     * This filter is based on the extensions supported by FileContentExtractor.
     */
    fun createSupportedFileFilter(): FilenameFilter = FilenameFilter { _, name ->
        val extension = name.substringAfterLast('.', "").lowercase()
        extension in FileContentExtractor.ALL_SUPPORTED_EXTENSIONS
    }
}
