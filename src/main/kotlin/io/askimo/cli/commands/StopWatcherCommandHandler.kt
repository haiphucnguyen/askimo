/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import io.askimo.core.project.FileWatcherManager
import io.askimo.core.util.Logger.info
import org.jline.reader.ParsedLine

/**
 * Command handler for stopping the file watcher.
 */
class StopWatcherCommandHandler : CommandHandler {
    override val keyword: String = ":stop-watcher"
    override val description: String = "Stop the current file watcher"

    override fun handle(line: ParsedLine) {
        if (FileWatcherManager.isWatching()) {
            FileWatcherManager.stopCurrentWatcher()
            info("🔍 File watcher stopped")
        } else {
            info("🔍 No file watcher is currently active")
        }
    }
}
