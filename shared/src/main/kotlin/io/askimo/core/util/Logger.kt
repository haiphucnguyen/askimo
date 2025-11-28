/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import org.slf4j.LoggerFactory

// For classes: logger<MyClass>()
inline fun <reified T> logger() = LoggerFactory.getLogger(T::class.java)

// For files: logger("MyFile") or logger("fully.qualified.FileName")
fun logger(name: String) = LoggerFactory.getLogger(name)
