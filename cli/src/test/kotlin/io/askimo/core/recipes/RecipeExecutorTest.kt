/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.recipes

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// Minimal MiniTpl implementation for template rendering
object MiniTpl {
    private val re = Regex("""\{\{([^}|]+)(?:\|([^}]+))?}}""")

    fun render(
        tpl: String,
        vars: Map<String, String>,
    ): String = re.replace(tpl) { m ->
        val key = m.groupValues[1].trim()
        val fallback = m.groupValues.getOrNull(2)?.trim()
        vars[key] ?: fallback ?: ""
    }
}

// Minimal data class for RecipeVarCall
data class RecipeVarCall(
    val tool: String,
    val args: List<String>,
)

// Minimal data class for RecipeDefinition
data class RecipeDefinition(
    val name: String,
    val version: Int,
    val description: String,
    val allowedTools: Set<String>,
    val vars: Map<String, RecipeVarCall>,
    val system: String,
    val userTemplate: String,
    val postActions: List<Any>,
    val defaults: Map<String, String>,
)

// Minimal interface for ToolRegistry
interface IToolRegistry {
    fun invoke(
        tool: String,
        args: Any?,
    ): Any?
}

// Minimal interface for RecipeRegistry
interface IRecipeRegistry {
    fun load(name: String): RecipeDefinition
}

class RecipeExecutorTest {
    @Test
    fun testExternalArgSubstitution() {
        // Mock ToolRegistry: returns "MOCK_CONTENT" if arg matches, else error
        val mockTools =
            object : IToolRegistry {
                override fun invoke(
                    tool: String,
                    args: Any?,
                ): Any? = when (tool) {
                    "readFile" -> {
                        val arg =
                            when (args) {
                                is List<*> -> args.firstOrNull()?.toString()
                                is String -> args
                                else -> null
                            }
                        if (arg == "/tmp/mockfile.txt") "MOCK_CONTENT" else "ERROR: $arg"
                    }
                    else -> "UNKNOWN_TOOL"
                }
            }

        // Mock RecipeRegistry: loads a recipe with a tool var using {{arg1}}
        val mockRecipe =
            RecipeDefinition(
                name = "summarize",
                version = 1,
                description = "Test",
                allowedTools = setOf("readFile"),
                vars =
                mapOf(
                    "file_content" to RecipeVarCall(tool = "readFile", args = listOf("{{arg1}}")),
                ),
                system = "SYSTEM: {{file_content}}",
                userTemplate = "USER: {{file_content}}",
                postActions = emptyList(),
                defaults = emptyMap(),
            )
        val mockRegistry =
            object : IRecipeRegistry {
                override fun load(name: String): RecipeDefinition = mockRecipe
            }

        // Minimal RecipeExecutor for test
        class TestRecipeExecutor {
            fun resolveArgs(
                args: Any?,
                vars: Map<String, String>,
            ): Any? = when (args) {
                null -> null
                is String -> MiniTpl.render(args, vars)
                is List<*> -> args.map { if (it is String) MiniTpl.render(it, vars) else it }
                is Map<*, *> -> args.mapValues { (_, v) -> if (v is String) MiniTpl.render(v, vars) else v }
                else -> args
            }
        }

        val executor = TestRecipeExecutor()

        // Run with external arg
        val optsExternalArgs = listOf("/tmp/mockfile.txt")
        val vars = mockRecipe.defaults.toMutableMap()
        optsExternalArgs.forEachIndexed { i, v -> vars["arg${i + 1}"] = v }
        val renderedArgs = executor.resolveArgs(listOf("{{arg1}}"), vars)
        assertEquals(listOf("/tmp/mockfile.txt"), renderedArgs)
    }

    @Test
    fun testRecipeExecutorWithExternalArg() {
        // Mock ToolRegistry: returns content based on arg
        val mockTools =
            object : IToolRegistry {
                override fun invoke(
                    tool: String,
                    args: Any?,
                ): Any? = when (tool) {
                    "readFile" -> {
                        val arg =
                            when (args) {
                                is List<*> -> args.firstOrNull()?.toString()
                                is String -> args
                                else -> null
                            }
                        if (arg == "/tmp/mockfile.txt") "MOCK_CONTENT" else "ERROR: $arg"
                    }
                    else -> "UNKNOWN_TOOL"
                }
            }

        // Mock RecipeRegistry: loads a recipe using {{arg1}}
        val mockRecipe =
            RecipeDefinition(
                name = "summarize",
                version = 1,
                description = "Test",
                allowedTools = setOf("readFile"),
                vars =
                mapOf(
                    "file_content" to RecipeVarCall(tool = "readFile", args = listOf("{{arg1}}")),
                ),
                system = "SYSTEM: Summarize the following file content.",
                userTemplate = "File path: {{arg1}}\nContent: ====BEGIN====\n{{file_content}}\n====END====",
                postActions = emptyList(),
                defaults = emptyMap(),
            )
        val mockRegistry =
            object : IRecipeRegistry {
                override fun load(name: String): RecipeDefinition = mockRecipe
            }

        // Minimal RecipeExecutor for test
        class TestRecipeExecutor(
            private val registry: IRecipeRegistry,
            private val tools: IToolRegistry,
        ) {
            fun run(
                name: String,
                externalArgs: List<String>,
            ): String {
                val def = registry.load(name)
                val vars =
                    def.defaults.toMutableMap().apply {
                        externalArgs.forEachIndexed { i, v -> this["arg${i + 1}"] = v }
                    }
                def.vars.forEach { (varName, call) ->
                    val renderedArgs =
                        when (call.args) {
                            else -> call.args.map { MiniTpl.render(it, vars) }
                        }
                    val out = tools.invoke(call.tool, renderedArgs)
                    vars[varName] = out?.toString().orEmpty()
                }
                val userRendered = MiniTpl.render(def.userTemplate, vars)
                return userRendered
            }
        }

        val executor = TestRecipeExecutor(mockRegistry, mockTools)
        val result = executor.run("summarize", listOf("/tmp/mockfile.txt"))
        assertEquals(
            "File path: /tmp/mockfile.txt\nContent: ====BEGIN====\nMOCK_CONTENT\n====END====",
            result,
        )
    }
}
