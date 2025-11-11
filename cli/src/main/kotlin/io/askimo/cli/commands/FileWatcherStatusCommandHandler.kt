/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.FileWatcherManager
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

/**
 * Command handler for showing file watcher status.
 */
class FileWatcherStatusCommandHandler : CommandHandler {
    override val keyword: String = ":watcher-status"
    override val description: String = "Show file watcher status and current watched path"

    override fun handle(line: ParsedLine) {
        if (FileWatcherManager.isWatching()) {
            val watchedPath = FileWatcherManager.getCurrentWatchedPath()
            info("🔍 File watcher is active")
            info("📁 Watching: $watchedPath")
        } else {
            info("🔍 File watcher is not active")
            info("💡 Create or use a project to start watching files")
        }
    }
}
