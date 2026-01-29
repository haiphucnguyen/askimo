/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.addons.mcp
data class ConfigField(
    val type: ConfigFieldType,
    val label: String,
    val description: String? = null,
    val required: Boolean = true,
    val defaultValue: String? = null,
    val secret: Boolean = false,
)
enum class ConfigFieldType {
    TEXT,
    NUMBER,
    BOOLEAN,
    SELECT,
    TEXTAREA,
}
