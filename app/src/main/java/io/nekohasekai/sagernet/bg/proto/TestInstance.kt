package io.nekohasekai.sagernet.bg.proto

import android.util.Log
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.TestEndpoint
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.buildConfig
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.tryResume
import io.nekohasekai.sagernet.ktx.tryResumeWithException
import kotlinx.coroutines.delay
import libcore.Libcore
import moe.matsuri.nb4a.net.LocalResolverImpl
import kotlin.coroutines.suspendCoroutine

private const val SPEED_TEST_LOG_TAG = "SpeedTest"
private const val DOWNLOAD_MAX_ATTEMPTS = 1

data class TestResult(
    val ping: Int,
    val downloadSpeed: Long = 0,
    val downloadBytes: Long = 0,
    val downloadMs: Long = 0,
    val downloadError: String? = null,
    val downloadEndpoint: TestEndpoint? = null,
)

class TestInstance(
    profile: ProxyEntity,
    private val pingEndpoints: List<TestEndpoint>,
    private val timeout: Int,
    private val downloadEndpoints: List<TestEndpoint>? = null,
    private val downloadMaxBytes: Long = 0,
    private val downloadTimeout: Int = 0,
    private val fallbackListener: TestFallbackListener? = null,
) : BoxInstance(profile) {

    suspend fun doTest(): TestResult {
        return suspendCoroutine { c ->
            processes = GuardedProcessPool {
                Logs.w(it)
                c.tryResumeWithException(it)
            }
            runOnDefaultDispatcher {
                use {
                    try {
                        init()
                        launch()
                        if (processes.processCount > 0) {
                            delay(500)
                        }
                        val ping = runPingTest()
                        var downloadSpeed = 0L
                        var downloadBytes = 0L
                        var downloadMs = 0L
                        var downloadError: String? = null
                        if (downloadEndpoints != null && ping > 0) {
                            val downloadResult = runDownloadTest()
                            downloadSpeed = downloadResult.speed
                            downloadBytes = downloadResult.bytes
                            downloadMs = downloadResult.ms
                            downloadError = downloadResult.error
                            c.tryResume(
                                TestResult(
                                    ping, downloadSpeed, downloadBytes, downloadMs,
                                    downloadError, downloadResult.endpoint,
                                )
                            )
                            return@runOnDefaultDispatcher
                        } else if (downloadEndpoints != null) {
                            Log.w(
                                SPEED_TEST_LOG_TAG,
                                "[${profile.displayName()}] download skipped (ping=$ping)"
                            )
                        }
                        c.tryResume(
                            TestResult(ping, downloadSpeed, downloadBytes, downloadMs, downloadError)
                        )
                    } catch (e: Exception) {
                        c.tryResumeWithException(e)
                    }
                }
            }
        }
    }

    private fun runPingTest(): Int {
        var lastError: String? = null
        for ((index, endpoint) in pingEndpoints.withIndex()) {
            try {
                val ping = Libcore.urlTest(box, endpoint.url, timeout)
                if (ping > 0) {
                    Log.w(
                        SPEED_TEST_LOG_TAG,
                        "[${profile.displayName()}] ping=$ping ms via ${endpoint.label}"
                    )
                    return ping
                }
                lastError = "ping=$ping"
                if (index < pingEndpoints.lastIndex) {
                    notifyPingFallback(endpoint, lastError)
                }
            } catch (e: Exception) {
                lastError = e.readableMessage
                if (index < pingEndpoints.lastIndex) {
                    notifyPingFallback(endpoint, lastError)
                } else {
                    Log.w(
                        SPEED_TEST_LOG_TAG,
                        "[${profile.displayName()}] ping failed via ${endpoint.label}: $lastError"
                    )
                }
            }
        }
        error(lastError ?: "connection test failed")
    }

    private fun notifyPingFallback(endpoint: TestEndpoint, reason: String) {
        val brief = shortTestError(reason)
        Log.w(
            SPEED_TEST_LOG_TAG,
            "[${profile.displayName()}] ping: ${endpoint.label} $brief → fallback"
        )
        fallbackListener?.onFallback(endpoint.label, brief)
    }

    private data class DownloadAttemptResult(
        val speed: Long = 0,
        val bytes: Long = 0,
        val ms: Long = 0,
        val error: String? = null,
        val endpoint: TestEndpoint? = null,
    )

    private fun runDownloadTest(): DownloadAttemptResult {
        var lastError: String? = null
        for ((index, endpoint) in downloadEndpoints!!.withIndex()) {
            try {
                val result = Libcore.urlTestDownload(
                    box,
                    endpoint.url,
                    downloadMaxBytes,
                    downloadTimeout,
                    null,
                    DOWNLOAD_MAX_ATTEMPTS,
                )
                if (result.speed > 0 && result.bytes > 0) {
                    Log.w(
                        SPEED_TEST_LOG_TAG,
                        "[${profile.displayName()}] download ok via ${endpointLogRef(endpoint)} " +
                            "speed=${result.speed} B/s"
                    )
                    return DownloadAttemptResult(
                        result.speed, result.bytes, result.bodyMs, endpoint = endpoint,
                    )
                }
                lastError = shortTestError("HTTP ${result.status}")
                if (index < downloadEndpoints.lastIndex) {
                    notifyDownloadFallback(endpoint, lastError)
                }
            } catch (e: Exception) {
                lastError = shortTestError(e.readableMessage)
                if (index < downloadEndpoints.lastIndex) {
                    notifyDownloadFallback(endpoint, lastError)
                }
            }
        }
        return DownloadAttemptResult(error = lastError ?: "failed")
    }

    private fun notifyDownloadFallback(endpoint: TestEndpoint, reason: String) {
        Log.w(
            SPEED_TEST_LOG_TAG,
            "[${profile.displayName()}] download: ${endpoint.label} $reason → fallback"
        )
        fallbackListener?.onFallback(endpoint.label, reason)
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = Libcore.newSingBoxInstance(config.config, LocalResolverImpl)
    }

}
