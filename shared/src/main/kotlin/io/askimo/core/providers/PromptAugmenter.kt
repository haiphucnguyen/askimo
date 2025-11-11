/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.memory.ChatMemory

fun interface PromptAugmenter {
    fun augment(
        prompt: String,
        memory: ChatMemory,
    ): String
}
