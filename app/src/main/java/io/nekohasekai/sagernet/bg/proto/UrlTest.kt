package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.TestEndpoints
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity

class UrlTest(
    private val withDownload: Boolean = false,
    private val fallbackListener: TestFallbackListener? = null,
) {

    private val pingEndpoints = TestEndpoints.orderedConnectionEndpoints(DataStore.connectionTestProvider)
    private val timeout = 5000

    suspend fun doTest(profile: ProxyEntity): TestResult {
        return if (withDownload) {
            TestInstance(
                profile,
                pingEndpoints,
                timeout,
                downloadEndpoints = TestEndpoints.orderedSpeedEndpoints(DataStore.speedTestProvider),
                downloadMaxBytes = DataStore.speedTestMaxBytes,
                downloadTimeout = DataStore.speedTestTimeout,
                fallbackListener = fallbackListener,
            ).doTest()
        } else {
            TestInstance(
                profile,
                pingEndpoints,
                timeout,
                fallbackListener = fallbackListener,
            ).doTest()
        }
    }

}
