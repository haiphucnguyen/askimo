/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.cli

object Logger {
    var debug: Boolean = System.getenv("ASKIMO_DEBUG") == "true"

    fun log(message: () -> String) {
        if (debug) println("[debug] ${message()}")
    }
}
