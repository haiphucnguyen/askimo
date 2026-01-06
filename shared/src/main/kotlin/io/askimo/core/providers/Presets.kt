/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import kotlinx.serialization.Serializable

@Serializable
enum class Style(val displayName: String, val description: String) {
    PRECISE("Precise", "Focused, deterministic responses"),
    BALANCED("Balanced", "Natural, varied responses (recommended)"),
    CREATIVE("Creative", "Imaginative, exploratory responses"),
}

/**
 * Configuration class for chat model response style.
 *
 * This class is used to configure how chat models generate responses by specifying
 * the creative style of the response (precise, balanced, or creative).
 *
 * @property style The style setting that affects the creativity and determinism of responses
 */
@Serializable
data class Presets(
    val style: Style,
)

/**
 * Configuration class for language model generation parameters.
 *
 * This class defines sampling parameters that control the randomness and diversity of generated text:
 * - Temperature affects randomness (higher values = more random outputs)
 * - Top-p affects the diversity of word choices
 *
 * @property temperature Controls randomness in text generation (0.0-2.0 typical range)
 * @property topP Controls diversity by limiting token selection to a cumulative probability threshold
 */
data class Sampling(
    val temperature: Double,
    val topP: Double,
)

fun samplingFor(s: Style) = when (s) {
    Style.PRECISE -> Sampling(0.2, 0.9)
    Style.BALANCED -> Sampling(0.7, 0.95)
    Style.CREATIVE -> Sampling(0.9, 1.0)
}
