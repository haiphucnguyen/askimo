/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.testcontainers

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.system.measureTimeMillis

@DisabledIfEnvironmentVariable(
    named = "DISABLE_DOCKER_TESTS",
    matches = "(?i)true|1|yes",
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelCachingDemoTest {

    @Test
    @DisplayName("Demonstrate model caching behavior across multiple calls and processes")
    fun demonstrateModelCaching() {
        val modelName = "qwen2.5:0.5b"

        println("=== Model Caching Demonstration (with Named Volume Persistence) ===")

        // First call - should check container first (may find existing model from previous test process)
        val firstCallTime = measureTimeMillis {
            SharedOllama.ensureModelPulled(modelName)
        }
        println("First call took: ${firstCallTime}ms")

        // Second call - should use in-memory cache
        val secondCallTime = measureTimeMillis {
            SharedOllama.ensureModelPulled(modelName)
        }
        println("Second call took: ${secondCallTime}ms")

        // Third call - should also use in-memory cache
        val thirdCallTime = measureTimeMillis {
            SharedOllama.ensureModelPulled(modelName)
        }
        println("Third call took: ${thirdCallTime}ms")

        println("=== Performance Analysis ===")
        println("First call (container check + possible pull): ${firstCallTime}ms")
        println("Subsequent calls (memory cache): ~${(secondCallTime + thirdCallTime) / 2}ms")

        if (firstCallTime > secondCallTime) {
            val improvement = ((firstCallTime - secondCallTime).toFloat() / firstCallTime * 100).toInt()
            println("In-memory caching improved performance by ~$improvement% on subsequent calls")
        }

        // Verify model is available in container and persisted via named volume
        val listResult = SharedOllama.container.execInContainer("ollama", "list")
        val modelExists = listResult.stdout.contains(modelName)
        println("=== Persistence Verification ===")
        println("Model $modelName exists in container: $modelExists")
        println("Model is stored in named volume 'askimo-ollama-models-cache' for cross-process reuse")

        assert(modelExists) { "Model should be available in container after caching" }
    }
}
