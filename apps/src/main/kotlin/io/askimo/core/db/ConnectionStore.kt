/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

data class DbConnection(
    val id: String,
    val engine: DbEngine,
    val url: String,
    val user: String,
    val secret: SecretRef,
    val readOnly: Boolean = true,
    val maxRows: Int = 100,
    val timeoutSec: Int = 10,
) {
    fun urlHostAndDb(): String = url.removePrefix("jdbc:").substringAfter("://")
}

interface ConnectionStore {
    fun get(id: String): DbConnection?

    fun list(): List<DbConnection>

    fun put(conn: DbConnection)

    fun remove(id: String): Boolean

    /** Persist to disk (user-global or project-local), no-ops in memory. */
    fun save()
}
