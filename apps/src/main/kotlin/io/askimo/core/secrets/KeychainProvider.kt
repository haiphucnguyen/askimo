package io.askimo.core.secrets

interface KeychainProvider {
    fun getPassword(service: String, account: String): CharArray
}