package io.askimo.commands

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolSpecifications
import java.lang.reflect.Method

class ToolRegistry(private val instances: List<Any>) {

    private val methodsByName: Map<String, Pair<Any, Method>> = buildMap {
        for (inst in instances) {
            for (m in inst::class.java.methods) {
                if (m.isAnnotationPresent(dev.langchain4j.agent.tool.Tool::class.java)) {
                    put(m.name, inst to m)
                }
            }
        }
    }

    fun specifications(allowedNames: List<String>): List<ToolSpecification> =
        instances.flatMap { ToolSpecifications.toolSpecificationsFrom(it) }
            .let { specs -> if (allowedNames.isEmpty()) specs else specs.filter { it.name() in allowedNames } }

    fun invoke(name: String, args: Any?): Any? {
        val (target, method) = methodsByName[name]
            ?: error("Tool not found or not allowed: $name")
        val params = method.parameterTypes
        return when {
            params.isEmpty() -> method.invoke(target)
            params.size == 1 -> {
                val single = when (val a = args) {
                    null -> null
                    is List<*> -> a // assume List<String> etc.
                    is Map<*, *> -> a
                    else -> a.toString()
                }
                method.invoke(target, single)
            }
            else -> error("Tool '$name' has ${params.size} parameters; simple invoker supports 0 or 1.")
        }
    }
}