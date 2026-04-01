/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.util

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Perform a GET request to a local/remote URL with proxy support.
 * Returns the HTTP status code and response body, or throws on connection failure.
 */
fun httpGet(
    url: String,
    headers: Map<String, String> = emptyMap(),
    connectTimeoutMs: Long = 5_000,
    readTimeoutMs: Long = 8_000,
    httpVersion: HttpClient.Version = HttpClient.Version.HTTP_2,
): Pair<Int, String> {
    val client = ProxyUtil.configureProxy(HttpClient.newBuilder().version(httpVersion), url)
        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
        .build()
    val requestBuilder = HttpRequest.newBuilder()
        .uri(URI(url))
        .timeout(Duration.ofMillis(readTimeoutMs))
        .GET()
    headers.forEach { (k, v) -> requestBuilder.header(k, v) }
    val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    return response.statusCode() to response.body()
}

/**
 * Perform a POST request with a JSON body to a local/remote URL with proxy support.
 * Returns the HTTP status code and response body, or throws on connection failure.
 */
fun httpPost(
    url: String,
    body: String,
    headers: Map<String, String> = emptyMap(),
    connectTimeoutMs: Long = 15_000,
    readTimeoutMs: Long = 600_000,
    httpVersion: HttpClient.Version = HttpClient.Version.HTTP_2,
): Pair<Int, String> {
    val client = ProxyUtil.configureProxy(HttpClient.newBuilder().version(httpVersion), url)
        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
        .build()
    val requestBuilder = HttpRequest.newBuilder()
        .uri(URI(url))
        .timeout(Duration.ofMillis(readTimeoutMs))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
    headers.forEach { (k, v) -> requestBuilder.header(k, v) }
    val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    return response.statusCode() to response.body()
}
