/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import dev.langchain4j.agent.tool.Tool
import io.askimo.core.logging.display
import io.askimo.core.logging.logger
import io.askimo.tools.fs.LocalFsTools
import io.askimo.tools.git.GitTools
import org.jline.reader.ParsedLine

class ListToolsCommandHandler : CommandHandler {
    private val log = logger<ListToolsCommandHandler>()

    override val keyword = ":tools"
    override val description = "List all available tools"

    override fun handle(line: ParsedLine) {
        val providers = listOf(GitTools(), LocalFsTools)

        log.display("🔧 Available Tools")
        log.display("──────────────────────────────")

        providers.forEach { provider ->
            val className = provider.javaClass.simpleName
            log.display("\n📦 $className")

            val tools = mutableListOf<Pair<String, String>>()

            provider.javaClass.declaredMethods.forEach { method ->
                val toolAnnotation = method.getAnnotation(Tool::class.java)
                if (toolAnnotation != null) {
                    val toolName = toolAnnotation.name.ifBlank { method.name }
                    val description = toolAnnotation.value.firstOrNull() ?: "No description"
                    tools.add(toolName to description.trim())
                }
            }

            tools.sortedBy { it.first }.forEach { (name, desc) ->
                log.display("  • $name")
                if (desc.isNotEmpty()) {
                    // Handle multi-line descriptions
                    desc.lines().forEachIndexed { index, line ->
                        if (index == 0) {
                            log.display("    $line")
                        } else if (line.trim().isNotEmpty()) {
                            log.display("    $line")
                        }
                    }
                }
            }
        }

        val totalCount = providers.sumOf { provider ->
            provider.javaClass.declaredMethods.count {
                it.getAnnotation(Tool::class.java) != null
            }
        }

        log.display("\n──────────────────────────────")
        log.display("Total: $totalCount tools")
    }
}
