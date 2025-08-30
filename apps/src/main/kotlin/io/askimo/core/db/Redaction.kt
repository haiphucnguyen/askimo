/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

fun String.redactJdbcCredentials(): String {
    val schemeIdx = indexOf("://")
    if (schemeIdx < 0) return this
    val afterScheme = substring(schemeIdx + 3)
    val atIdx = afterScheme.indexOf('@')
    if (atIdx < 0) return this // no userinfo section

    val userInfo = afterScheme.substring(0, atIdx)
    val rest = afterScheme.substring(atIdx) // includes '@' and host/params

    val redactedUserInfo =
        if (':' in userInfo) {
            // keep user, mask password: user:***@host
            userInfo.substringBefore(':') + ":***"
        } else {
            // no password present; either plain user or something else
            userInfo
        }
    return substring(0, schemeIdx + 3) + redactedUserInfo + rest
}

fun DbConnection.redactedUrl(): String = url.redactJdbcCredentials()
