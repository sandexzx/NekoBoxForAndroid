package io.nekohasekai.sagernet

data class TestEndpoint(
    val id: String,
    val label: String,
    val url: String,
)

object TestEndpoints {

    const val DEFAULT_CONNECTION_PROVIDER = "gstatic_https"
    const val DEFAULT_SPEED_PROVIDER = "tele2"

    private val connectionProviders = listOf(
        TestEndpoint("gstatic_https", "Google (HTTPS)", "https://www.gstatic.com/generate_204"),
        TestEndpoint("gstatic_http", "Google (HTTP)", "http://www.gstatic.com/generate_204"),
        TestEndpoint("cloudflare", "Cloudflare", "http://cp.cloudflare.com/generate_204"),
        TestEndpoint("apple", "Apple", "http://www.apple.com/library/test/success.html"),
        TestEndpoint("google_clients", "Google Clients", "http://clients3.google.com/generate_204"),
    )

    private val speedProviders = listOf(
        TestEndpoint("tele2", "Tele2", "http://speedtest.tele2.net/100MB.zip"),
        TestEndpoint("hetzner", "Hetzner", "https://speed.hetzner.de/100MB.bin"),
        TestEndpoint("ovh_rbx", "OVH Roubaix", "https://rbx.proof.ovh.net/files/100Mb.dat"),
        TestEndpoint("ovh_gra", "OVH Gravelines", "https://gra.proof.ovh.net/files/100Mb.dat"),
    )

    fun resolveConnectionProvider(id: String): TestEndpoint =
        connectionProviders.find { it.id == id } ?: connectionProviders.first()

    fun resolveSpeedProvider(id: String): TestEndpoint =
        speedProviders.find { it.id == id } ?: speedProviders.first()

    fun orderedConnectionEndpoints(preferredId: String, maxAttempts: Int = 2): List<TestEndpoint> =
        orderedEndpoints(connectionProviders, preferredId, maxAttempts)

    fun orderedSpeedEndpoints(preferredId: String): List<TestEndpoint> =
        orderedEndpoints(speedProviders, preferredId, speedProviders.size)

    private fun orderedEndpoints(
        providers: List<TestEndpoint>,
        preferredId: String,
        maxAttempts: Int,
    ): List<TestEndpoint> {
        val preferred = providers.find { it.id == preferredId } ?: providers.first()
        val rest = providers.filter { it.id != preferred.id }
        return (listOf(preferred) + rest).take(maxAttempts.coerceAtLeast(1))
    }

    fun urlTestWithFallback(
        box: libcore.BoxInstance?,
        timeout: Int,
        preferredId: String,
    ): Int {
        val endpoints = orderedConnectionEndpoints(preferredId)
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                val ping = libcore.Libcore.urlTest(box, endpoint.url, timeout)
                if (ping > 0) return ping
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("connection test failed")
    }
}
