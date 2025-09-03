package io.askimo.core.secrets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SecretRef {
    @Serializable
    @SerialName("keychain")
    data class Keychain(val service: String, val account: String) : SecretRef

    @Serializable
    @SerialName("env")
    data class EnvVar(val name: String) : SecretRef

    @Serializable
    @SerialName("file")
    data class FilePath(val path: String) : SecretRef

    /** Use sparingly; mainly for tests/dev. */
    @Serializable
    @SerialName("inline")
    data class Inline(val value: String) : SecretRef
}