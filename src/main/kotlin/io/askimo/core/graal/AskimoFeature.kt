/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.graal

import io.askimo.core.providers.ChatService
import io.askimo.core.recipes.RecipeDef
import io.askimo.tools.git.GitTools
import org.graalvm.nativeimage.hosted.Feature
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation
import org.graalvm.nativeimage.hosted.RuntimeReflection
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.RecordComponent

class AskimoFeature : Feature {
    /**
     * Dynamic proxies to register.
     * Each inner array is the *ordered* list of interfaces passed to Proxy.newProxyInstance(...).
     * Order matters for multi-interface proxies.
     */
    private val proxies: Array<Array<Class<*>>> =
        arrayOf(
            arrayOf(ChatService::class.java),
            // example for multi-interface proxy:
            // arrayOf(If1::class.java, If2::class.java)
        )

    /** Classes to fully register for reflection (ctors + methods + fields). */
    private val reflects: Array<Class<*>> =
        arrayOf(
            RecipeDef::class.java,
            GitTools::class.java,
            // io.askimo.api.dto.ChatMessage::class.java,
            // io.askimo.api.dto.ChatResponse::class.java,
        )

    override fun beforeAnalysis(access: Feature.BeforeAnalysisAccess) {
        println(">>> AskimoFeature: beforeAnalysis running")
        // Proxies
        for (ifaces in proxies) {
            // Kotlin spread operator passes vararg correctly
            RuntimeProxyCreation.register(*ifaces)
        }

        // Classes
        reflects.forEach { registerDeep(it) }
    }

    private fun registerDeep(c: Class<*>) {
        RuntimeReflection.register(c)

        // Constructors, methods, fields
        c.declaredConstructors.forEach { RuntimeReflection.register(it as Constructor<*>) }
        c.declaredMethods.forEach { RuntimeReflection.register(it as Method) }
        c.declaredFields.forEach { RuntimeReflection.register(it as Field) }

        // Records: canonical ctor + component accessors (safe to try)
        if (c.isRecord) {
            try {
                val components: Array<RecordComponent> = c.recordComponents
                val sig = components.map { it.type }.toTypedArray()
                val canonical = c.getDeclaredConstructor(*sig)
                RuntimeReflection.register(canonical)
                components.forEach { comp ->
                    RuntimeReflection.register(comp.accessor)
                }
            } catch (_: Throwable) {
                // ignore; some environments may not expose full record metadata
            }
        }
    }
}
