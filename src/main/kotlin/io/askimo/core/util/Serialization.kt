package io.askimo.core.util

import io.askimo.core.providers.ProviderSettings
import io.askimo.core.providers.ollama.OllamaSettings
import io.askimo.core.providers.openai.OpenAiSettings
import io.askimo.core.providers.xai.XAiSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

val coreProvidersModule =
    SerializersModule {
        polymorphic(ProviderSettings::class) {
            subclass(OpenAiSettings::class, OpenAiSettings.serializer())
            subclass(OllamaSettings::class, OllamaSettings.serializer())
            subclass(XAiSettings::class, XAiSettings.serializer())
        }
    }

fun buildJson(vararg extraModules: SerializersModule): Json =
    Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "__type" // -> "ollama"/"openai" in saved JSON
        serializersModule =
            SerializersModule {
                include(coreProvidersModule)
                extraModules.forEach { include(it) }
            }
    }

val appJson = buildJson()
