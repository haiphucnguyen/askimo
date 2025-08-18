package io.askimo.core.providers

import kotlinx.serialization.Serializable

@Serializable
enum class Style { PRECISE, BALANCED, CREATIVE }

@Serializable
enum class Verbosity { SHORT, NORMAL, LONG }

/**
 * Configuration class that combines style and verbosity settings for chat model responses.
 *
 * This class is used to configure how chat models generate responses by specifying:
 * - The creative style of the response (precise, balanced, or creative)
 * - The verbosity level that controls response length
 *
 * @property style The style setting that affects the creativity and determinism of responses
 * @property verbosity The verbosity setting that controls the length of generated responses
 */
@Serializable
data class Presets(
    val style: Style,
    val verbosity: Verbosity,
)

fun verbosityInstruction(v: Verbosity) =
    when (v) {
        Verbosity.SHORT -> "You are a concise assistant. Respond in 1–2 sentences."
        Verbosity.NORMAL -> "You are a helpful assistant. Respond with a moderate amount of detail."
        Verbosity.LONG -> "You are a detailed assistant. Respond with extended explanations."
    }

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

fun samplingFor(s: Style) =
    when (s) {
        Style.PRECISE -> Sampling(0.2, 1.0)
        Style.BALANCED -> Sampling(0.7, 1.0)
        Style.CREATIVE -> Sampling(1.1, 1.0)
    }
