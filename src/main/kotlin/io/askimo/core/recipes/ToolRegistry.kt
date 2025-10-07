/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.recipes

import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolSpecifications
import java.lang.reflect.Method
import kotlin.reflect.jvm.kotlinFunction

class ToolRegistry(
    val instances: List<Any>,
) {
    private val annotated: Map<String, Pair<Any, Method>> =
        buildMap {
            for (inst in instances) {
                for (m in inst::class.java.methods) {
                    if (m.isAnnotationPresent(Tool::class.java)) {
                        put(m.name, inst to m) // key = method name
                    }
                }
            }
        }

    fun specifications(allowed: List<String>): List<ToolSpecification> {
        val all = instances.flatMap { ToolSpecifications.toolSpecificationsFrom(it) }
        return if (allowed.isEmpty()) all else all.filter { it.name() in allowed }
    }

    /**
     * Invoke a tool by method name, supporting:
     *  - 0 params: call()
     *  - 1 param:  call(args) where args can be String/List/Map (coerced)
     *  - N params: call(arg1,...,argN) where args must be a Map<String,Any?> keyed by param names
     */
    fun invoke(
        name: String,
        args: Any?,
    ): Any? {
        val (target, method) =
            annotated[name]
                ?: error("Tool not found or not allowed: $name")

        val javaParams = method.parameters
        val paramCount = javaParams.size

        if (paramCount == 0) {
            return method.invoke(target)
        }

        if (paramCount == 1 && args !is Map<*, *>) {
            val coerced = coerce(args, javaParams[0].type)
            return method.invoke(target, coerced)
        }

        // Multi-arg path: expect a Map of paramName -> value
        require(args is Map<*, *>) {
            "Tool '$name' requires $paramCount parameters; provide args as a map of paramName -> value"
        }

        // Get parameter names reliably via Kotlin reflection (preferred),
        // falling back to Java reflection names if kotlinFunction is null.
        val kfun = method.kotlinFunction
        val paramNames: List<String> =
            kfun
                ?.parameters
                ?.drop(1)
                ?.map { it.name ?: error("Missing parameter name for tool '$name'") }
                ?: javaParams.map { it.name }

        val argArray = Array<Any?>(paramCount) { null }
        for (i in 0 until paramCount) {
            val pName = paramNames[i]
            val pType = javaParams[i].type
            val raw = args[pName]
            argArray[i] = coerce(raw, pType)
        }

        return method.invoke(target, *argArray)
    }

    private fun coerce(
        value: Any?,
        targetType: Class<*>,
    ): Any? {
        if (value == null) return null

        // If already assignable (List/Map/String/etc.), let it through
        if (targetType.isInstance(value)) return value

        val s = value.toString()

        return when (targetType) {
            String::class.java -> s

            Boolean::class.java, java.lang.Boolean.TYPE ->
                s.equals("true", true) || s == "1"

            Integer::class.java, java.lang.Integer.TYPE ->
                s.toIntOrNull() ?: error("Cannot coerce '$value' to Int")

            Long::class.java, java.lang.Long.TYPE ->
                s.toLongOrNull() ?: error("Cannot coerce '$value' to Long")

            Double::class.java, java.lang.Double.TYPE ->
                s.toDoubleOrNull() ?: error("Cannot coerce '$value' to Double")

            Float::class.java, java.lang.Float.TYPE ->
                s.toFloatOrNull() ?: error("Cannot coerce '$value' to Float")

            else -> {
                // If target is a List or Map and value is compatible, return as-is
                if (List::class.java.isAssignableFrom(targetType) && value is List<*>) return value
                if (Map::class.java.isAssignableFrom(targetType) && value is Map<*, *>) return value
                // Last resort: return as string
                s
            }
        }
    }
}
