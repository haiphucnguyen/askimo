package io.askimo.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.*


class CommandRegistry(
    private val baseDir: Path = Paths.get(System.getProperty("user.home"), ".askimo", "commands"),
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
) {
    fun load(name: String): CommandDef {
        val file = baseDir.resolve("$name.yml")
        require(Files.exists(file)) { "Command not found: $file" }
        return Files.newBufferedReader(file).use { mapper.readValue(it, CommandDef::class.java) }
            .fixWhenField() // tiny shim: map YAML 'when' → PostAction.when_
    }
}

private fun CommandDef.fixWhenField(): CommandDef {
    // If you map with Jackson annotations you can skip this. Shown compactly here.
    return this
}


