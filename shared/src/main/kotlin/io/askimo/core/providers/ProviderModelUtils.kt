/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.ToolExecutionResultMessage
import io.askimo.core.logging.displayError
import io.askimo.core.logging.logger
import io.askimo.core.util.appJson
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

object ProviderModelUtils {
    private val log = logger<ProviderModelUtils>()

    fun hallucinatedToolHandler(request: ToolExecutionRequest): ToolExecutionResultMessage {
        val toolName = request.name()
        log.warn("LLM hallucinated tool: '$toolName'")

        return ToolExecutionResultMessage.from(
            request,
            """
            Error: Tool '$toolName' does not exist.

            Please use only the tools that have been explicitly provided to you.
            Do not invent or assume the existence of tools.
            """.trimIndent(),
        )
    }

    fun fetchModels(
        apiKey: String,
        url: String,
        providerName: ModelProvider,
    ): List<String> = try {
        val uri = URI(url).toURL()
        val connection = uri.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")

        connection.inputStream.bufferedReader().use { reader ->
            val jsonElement = appJson.parseToJsonElement(reader.readText())

            val data = jsonElement.jsonObject["data"]?.jsonArray.orEmpty()

            data
                .mapNotNull { element ->
                    element.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                }.distinct()
                .sorted()
        }
    } catch (e: Exception) {
        log.displayError("⚠️ Failed to fetch models from $providerName: ${e.message}", e)
        emptyList()
    }
}
