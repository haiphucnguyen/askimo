/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.tools.git

import dev.langchain4j.agent.tool.Tool
import java.nio.file.Files
import java.nio.file.Paths

object IoTools {
    @Tool("Write text file to path")
    fun writeFile(
        path: String,
        content: String,
    ): String {
        val p = Paths.get(path)
        Files.createDirectories(p.toAbsolutePath().parent)
        Files.writeString(p, content)
        return "wrote:\n${p.toAbsolutePath()}"
    }
}
