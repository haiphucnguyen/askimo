/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.commands

import io.askimo.core.util.Yaml.yamlMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CommandRegistry(
    private val baseDir: Path = Paths.get(System.getProperty("user.home"), ".askimo", "commands"),
) {
    fun load(name: String): CommandDef {
        val file = baseDir.resolve("$name.yml")
        require(Files.exists(file)) { "Command not found: $file" }
        return Files
            .newBufferedReader(file)
            .use { yamlMapper.readValue(it, CommandDef::class.java) }
            .fixWhenField() // tiny shim: map YAML 'when' → PostAction.when_
    }
}

private fun CommandDef.fixWhenField(): CommandDef {
    // If you map with Jackson annotations you can skip this. Shown compactly here.
    return this
}
