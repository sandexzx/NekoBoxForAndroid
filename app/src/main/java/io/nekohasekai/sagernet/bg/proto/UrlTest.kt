package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import libcore.DownloadRetryListener

class UrlTest(
    private val withDownload: Boolean = false,
    private val downloadRetryListener: DownloadRetryListener? = null,
) {

    val link = DataStore.connectionTestURL
    private val timeout = 5000

    suspend fun doTest(profile: ProxyEntity): TestResult {
        return if (withDownload) {
            TestInstance(
                profile,
                link,
                timeout,
                downloadLink = DataStore.speedTestURL,
                downloadMaxBytes = DataStore.speedTestMaxBytes,
                downloadTimeout = DataStore.speedTestTimeout,
                downloadRetryListener = downloadRetryListener,
            ).doTest()
        } else {
            TestInstance(profile, link, timeout).doTest()
        }
    }

}
