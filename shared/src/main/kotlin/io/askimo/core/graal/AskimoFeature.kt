/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.graal

import io.askimo.core.config.AppConfigData
import io.askimo.core.config.ChatConfig
import io.askimo.core.config.EmbeddingConfig
import io.askimo.core.config.IndexingConfig
import io.askimo.core.config.PgVectorConfig
import io.askimo.core.config.RetryConfig
import io.askimo.core.config.ThrottleConfig
import io.askimo.tools.fs.LocalFsTools
import io.askimo.tools.git.GitTools
import org.graalvm.nativeimage.hosted.Feature
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization
import org.graalvm.nativeimage.hosted.RuntimeReflection

/**
 * GraalVM native image feature that configures runtime reflection and class initialization
 * for Askimo components to ensure proper functionality in compiled native executables.
 */
class AskimoFeature : Feature {
    override fun beforeAnalysis(access: Feature.BeforeAnalysisAccess) {
        // Register Askimo tool classes invoked via reflection by your ToolRegistry
        registerAllDeclared(LocalFsTools::class.java, GitTools::class.java)

        // Register configuration classes for reflection
        registerAllDeclared(
            AppConfigData::class.java,
            PgVectorConfig::class.java,
            EmbeddingConfig::class.java,
            RetryConfig::class.java,
            ThrottleConfig::class.java,
            IndexingConfig::class.java,
            ChatConfig::class.java,
        )

        // Handle LangChain4j internal Jackson deserializer (package-private, cannot import directly)
        val openAiEmbeddingDeserializer =
            access.findClassByName("dev.langchain4j.model.openai.internal.embedding.OpenAiEmbeddingDeserializer")

        if (openAiEmbeddingDeserializer != null) {
            // Ensure its <clinit> runs at runtime (not during image build)
            RuntimeClassInitialization.initializeAtRunTime(openAiEmbeddingDeserializer)

            // Jackson needs constructors registered for reflection
            RuntimeReflection.register(openAiEmbeddingDeserializer)
            openAiEmbeddingDeserializer.declaredConstructors.forEach { RuntimeReflection.register(it) }
        }

        // Initialize coroutine-related classes at runtime to avoid compilation issues
        RuntimeClassInitialization.initializeAtRunTime("kotlinx.coroutines")
        RuntimeClassInitialization.initializeAtRunTime("kotlin.coroutines")

        // Register ProjectFileWatcher and related classes for reflection
        val projectFileWatcherClass = access.findClassByName("io.askimo.core.project.ProjectFileWatcher")
        if (projectFileWatcherClass != null) {
            RuntimeReflection.register(projectFileWatcherClass)
            projectFileWatcherClass.declaredMethods.forEach { RuntimeReflection.register(it) }
            projectFileWatcherClass.declaredConstructors.forEach { RuntimeReflection.register(it) }
        }
    }

    /** Register class + all declared constructors & methods for reflection. */
    private fun registerAllDeclared(vararg classes: Class<*>) {
        classes.forEach { clazz ->
            RuntimeReflection.register(clazz)
            clazz.declaredConstructors.forEach { RuntimeReflection.register(it) }
            clazz.declaredMethods.forEach { RuntimeReflection.register(it) }
        }
    }
}
