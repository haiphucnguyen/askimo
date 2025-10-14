/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.testcontainers

import org.testcontainers.ollama.OllamaContainer
import org.testcontainers.utility.DockerImageName

/**
 * A singleton Ollama container shared across tests to avoid repeatedly
 * starting new containers. Requires Testcontainers reuse to be enabled
 * (e.g., with TESTCONTAINERS_RYUK_DISABLED and ~/.testcontainers.properties if needed).
 */
object SharedOllama {
    val container: OllamaContainer by lazy {
        val image = DockerImageName.parse("ollama/ollama:0.12.5")
        OllamaContainer(image)
            .withReuse(true)
            .apply { start() }
    }
}
