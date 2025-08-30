/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

/** Supported engines (extend later) */
enum class DbEngine { POSTGRES, MYSQL, SQLSERVER, SQLITE }

/** Where and how to get the DB password/secret. */
sealed interface SecretRef {
    data class Keychain(
        val service: String,
        val account: String,
    ) : SecretRef

    data class EnvVar(
        val name: String,
    ) : SecretRef

    data class FilePath(
        val path: String,
    ) : SecretRef

    /** Use sparingly; mainly for tests/dev. */
    data class Inline(
        val value: String,
    ) : SecretRef
}
