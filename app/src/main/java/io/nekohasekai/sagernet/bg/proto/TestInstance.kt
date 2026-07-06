package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.BuildConfig
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

data class TestResult(val ping: Int, val downloadSpeed: Long)

class TestInstance(
    profile: ProxyEntity,
    val link: String,
    private val timeout: Int,
    private val downloadLink: String? = null,
    private val downloadMaxBytes: Long = 0,
    private val downloadTimeout: Int = 0,
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
                            // wait for plugin start
                            delay(500)
                        }
                        val ping = Libcore.urlTest(box, link, timeout)
                        Logs.w("SpeedTest[${profile.displayName()}] ping=$ping ms")
                        var downloadSpeed = 0L
                        if (downloadLink != null && ping > 0) {
                            try {
                                downloadSpeed = Libcore.urlTestDownload(
                                    box, downloadLink, downloadMaxBytes, downloadTimeout
                                )
                                Logs.w("SpeedTest[${profile.displayName()}] downloadSpeed=$downloadSpeed B/s")
                            } catch (e: Exception) {
                                Logs.w("SpeedTest[${profile.displayName()}] download failed: ${e.readableMessage}")
                            }
                        } else if (downloadLink != null) {
                            Logs.w("SpeedTest[${profile.displayName()}] download skipped (ping=$ping)")
                        }
                        c.tryResume(TestResult(ping, downloadSpeed))
                    } catch (e: Exception) {
                        c.tryResumeWithException(e)
                    }
                }
            }
        }
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        // don't call destroyAllJsi here
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = Libcore.newSingBoxInstance(config.config, LocalResolverImpl)
    }

}
