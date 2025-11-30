/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.VersionInfo
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import org.jline.reader.ParsedLine

class VersionDisplayCommandHandler : CommandHandler {
    private val log = logger<VersionDisplayCommandHandler>()
    override val keyword: String = ":version"
    override val description: String = "Show detailed version and build information."

    override fun handle(line: ParsedLine) {
        val a = VersionInfo
        log.display(
            """
            ${a.name} ${a.version}
            Author: ${a.author}
            Built: ${a.buildDate}
            License: ${a.license}
            Homepage: ${a.homepage}
            Build JDK: ${a.buildJdk}
            Runtime: ${a.runtimeVm} (${a.runtimeVersion})
            """.trimIndent(),
        )
    }
}
