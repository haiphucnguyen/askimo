/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli.commands

import dev.langchain4j.agent.tool.Tool
import io.askimo.core.util.Logger.info
import io.askimo.tools.fs.LocalFsTools
import io.askimo.tools.git.GitTools
import org.jline.reader.ParsedLine

class ListToolsCommandHandler : CommandHandler {
    override val keyword = ":tools"
    override val description = "List all available tools"

    override fun handle(line: ParsedLine) {
        val providers = listOf(GitTools(), LocalFsTools)

        info("🔧 Available Tools")
        info("──────────────────────────────")

        providers.forEach { provider ->
            val className = provider.javaClass.simpleName
            info("\n📦 $className")

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
                info("  • $name")
                if (desc.isNotEmpty()) {
                    // Handle multi-line descriptions
                    desc.lines().forEachIndexed { index, line ->
                        if (index == 0) {
                            info("    $line")
                        } else if (line.trim().isNotEmpty()) {
                            info("    $line")
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

        info("\n──────────────────────────────")
        info("Total: $totalCount tools")
    }
}
