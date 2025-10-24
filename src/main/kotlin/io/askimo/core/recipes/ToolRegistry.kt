/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.recipes

import dev.langchain4j.agent.tool.Tool
import io.askimo.tools.fs.LocalFsTools
import io.askimo.tools.git.GitTools

class ToolRegistry private constructor(
    private val annotated: Map<String, Pair<Any, java.lang.reflect.Method>>,
) {
    fun invoke(
        name: String,
        args: Any?,
    ): Any? {
        val (target, m) =
            annotated[name]
                ?: error("Tool not found or not allowed: $name. Available: ${annotated.keys.sorted()}")
        m.isAccessible = true

        fun coerce(
            value: Any?,
            targetType: Class<*>,
        ): Any? {
            if (value == null) {
                return when (targetType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Double.TYPE -> 0.0
                    java.lang.Float.TYPE -> 0.0f
                    else -> null
                }
            }
            return when (targetType) {
                String::class.java -> value.toString()
                java.lang.Boolean.TYPE, java.lang.Boolean::class.java ->
                    when (value) {
                        is Boolean -> value
                        is String -> value.equals("true", ignoreCase = true)
                        else -> false
                    }
                java.lang.Integer.TYPE, java.lang.Integer::class.java ->
                    when (value) {
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull() ?: 0
                        else -> 0
                    }
                java.lang.Long.TYPE, java.lang.Long::class.java ->
                    when (value) {
                        is Number -> value.toLong()
                        is String -> value.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                java.lang.Double.TYPE, java.lang.Double::class.java ->
                    when (value) {
                        is Number -> value.toDouble()
                        is String -> value.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                java.lang.Float.TYPE, java.lang.Float::class.java ->
                    when (value) {
                        is Number -> value.toFloat()
                        is String -> value.toFloatOrNull() ?: 0.0f
                        else -> 0.0f
                    }
                else -> value
            }
        }

        return when (args) {
            null -> m.invoke(target)
            is Array<*> -> m.invoke(target, *args)
            is List<*> -> {
                val params = m.parameters
                if (params.size == 1 && params[0].type.isAssignableFrom(List::class.java)) {
                    // Method expects a single List parameter
                    m.invoke(target, args)
                } else {
                    val callArgs = Array<Any?>(params.size) { null }
                    for (i in params.indices) {
                        val raw = if (i < args.size) args[i] else null
                        callArgs[i] = coerce(raw, params[i].type)
                    }
                    m.invoke(target, *callArgs)
                }
            }
            is Map<*, *> -> {
                val params = m.parameters
                val callArgs = Array<Any?>(params.size) { null }
                for (i in params.indices) {
                    val p = params[i]
                    val raw = args[p.name]
                    callArgs[i] = coerce(raw, p.type)
                }
                m.invoke(target, *callArgs)
            }
            else -> m.invoke(target, args)
        }
    }

    fun keys(): Set<String> = annotated.keys

    companion object {
        fun from(
            providers: List<Any>,
            allow: Set<String>? = null,
        ): ToolRegistry {
            val map = mutableMapOf<String, Pair<Any, java.lang.reflect.Method>>()
            providers.forEach { p ->
                p.javaClass.declaredMethods.forEach { m ->
                    val ann = m.getAnnotation(Tool::class.java) ?: return@forEach
                    // Use explicit name if provided; otherwise fall back to method name
                    val toolName = ann.name.ifBlank { m.name }
                    if (allow == null || toolName in allow) map[toolName] = p to m
                }
            }
            return ToolRegistry(map)
        }

        /**
         * Create a registry with built-in providers used by Askimo (GitTools, IoTools).
         */
        fun defaults(allow: Set<String>? = null): ToolRegistry =
            from(
                providers =
                    listOf(
                        GitTools(),
                        LocalFsTools,
                    ),
                allow = allow,
            )
    }
}
