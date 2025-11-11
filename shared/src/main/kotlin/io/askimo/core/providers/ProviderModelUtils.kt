/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.util.Logger.debug
import io.askimo.core.util.Logger.info
import io.askimo.core.util.appJson
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

object ProviderModelUtils {
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
        info("⚠️ Failed to fetch models from $providerName: ${e.message}")
        debug(e)
        emptyList()
    }
}
