/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> logger() = LoggerFactory.getLogger(T::class.java)

fun logger(name: String) = LoggerFactory.getLogger(name)

fun Logger.display(message: String) {
    println(message)
    this.info(message)
}

fun Logger.displayError(message: String, throwable: Throwable? = null) {
    println(message)
    this.error(message, throwable)
}
