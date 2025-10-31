/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.graal

import io.askimo.tools.fs.LocalFsTools
import io.askimo.tools.git.GitTools
import org.graalvm.nativeimage.hosted.Feature
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization
import org.graalvm.nativeimage.hosted.RuntimeReflection

class AskimoFeature : Feature {
    override fun beforeAnalysis(access: Feature.BeforeAnalysisAccess) {
        // Register Askimo tool classes invoked via reflection by your ToolRegistry
        registerAllDeclared(LocalFsTools::class.java, GitTools::class.java)

        // Handle LangChain4j internal Jackson deserializer (cannot import; use analysis access)
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
