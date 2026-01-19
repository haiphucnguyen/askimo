/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.rag.extraction

import java.nio.file.Path

/**
 * Resource identifier for local files.
 */
data class FileResourceIdentifier(
    val filePath: Path,
) : ResourceIdentifier {
    override fun asString(): String = filePath.toString()
}
