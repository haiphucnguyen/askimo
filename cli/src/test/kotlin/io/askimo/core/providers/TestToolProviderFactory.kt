/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.agent.tool.ToolSpecifications
import dev.langchain4j.service.tool.DefaultToolExecutor
import dev.langchain4j.service.tool.ToolProvider
import dev.langchain4j.service.tool.ToolProviderResult
import io.askimo.tools.fs.LocalFsTools

/**
 * Factory for creating ToolProvider instances for testing purposes.
 */
object TestToolProviderFactory {

    /**
     * Creates a ToolProvider for the LocalFsTools.countEntries method.
     */
    fun createCountEntriesToolProvider(): ToolProvider {
        val toolMethod = LocalFsTools::class.java.getMethod(
            "countEntries",
            String::class.java,
            Boolean::class.javaObjectType,
            Boolean::class.javaObjectType,
        )

        return ToolProvider {
            ToolProviderResult.builder().add(
                ToolSpecifications.toolSpecificationFrom(toolMethod),
                DefaultToolExecutor.builder()
                    .`object`(LocalFsTools)
                    .methodToInvoke(toolMethod)
                    .originalMethod(toolMethod)
                    .wrapToolArgumentsExceptions(true)
                    .propagateToolExecutionExceptions(true)
                    .build(),
            ).build()
        }
    }

    /**
     * Generic method to create a ToolProvider for any tool method.
     *
     * @param toolObject The object containing the tool method
     * @param methodName The name of the method to expose as a tool
     * @param parameterTypes The parameter types of the method
     * @return A ToolProvider configured for the specified method
     */
    fun createToolProvider(
        toolObject: Any,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): ToolProvider {
        val toolMethod = toolObject::class.java.getMethod(methodName, *parameterTypes)

        return ToolProvider {
            ToolProviderResult.builder().add(
                ToolSpecifications.toolSpecificationFrom(toolMethod),
                DefaultToolExecutor.builder()
                    .`object`(toolObject)
                    .methodToInvoke(toolMethod)
                    .originalMethod(toolMethod)
                    .wrapToolArgumentsExceptions(true)
                    .propagateToolExecutionExceptions(true)
                    .build(),
            ).build()
        }
    }
}
