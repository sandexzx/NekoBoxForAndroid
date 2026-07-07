package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.TestEndpoint

interface TestFallbackListener {
    fun onFallback(endpointLabel: String, reason: String)
}

fun shortTestError(reason: String): String {
    Regex("HTTP \\d+").find(reason)?.value?.let { return it }
    if (reason.contains("timeout", ignoreCase = true)) return "timeout"
    val cleaned = reason.substringAfter(": ").trim().ifBlank { reason.trim() }
    return if (cleaned.length > 24) cleaned.take(21) + "…" else cleaned
}

fun endpointLogRef(endpoint: TestEndpoint): String =
    "${endpoint.label} (${endpoint.url})"
