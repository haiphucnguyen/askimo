/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import io.askimo.core.secrets.SecretRef


fun SecretRef.summary(): String =
    when (this) {
        is SecretRef.Keychain -> "keychain:$service/$account"
        is SecretRef.EnvVar -> "env:$name"
        is SecretRef.FilePath -> "file:$path"
        is SecretRef.Inline -> "inline:***"
    }
