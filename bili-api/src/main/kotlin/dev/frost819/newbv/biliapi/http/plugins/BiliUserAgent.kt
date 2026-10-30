package dev.frost819.newbv.biliapi.http.plugins

import dev.frost819.newbv.biliapi.http.util.BiliAppConf
import dev.frost819.newbv.biliapi.http.util.BiliWebConf
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.http.HttpHeaders
import io.ktor.http.encodedPath
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.KtorDsl

private val LOGGER = KtorSimpleLogger("dev.frost819.newbv.biliapi.http.plugins.BiliUserAgent")

@KtorDsl
class BiliUserAgentConfig(
    var version: String = BiliAppConf.APP_VERSION_NAME,
    var buildCode: Int = BiliAppConf.APP_BUILD_CODE,
    var channel: String = BiliAppConf.CHANNEL,
    var platform: String = BiliAppConf.PLATFORM,
    var mobiApp: String = BiliAppConf.MOBI_APP,
    var model: String = BiliAppConf.model,
    var osVersion: String = BiliAppConf.osVersion,
    var network: Int = BiliAppConf.NETWORK,
    var webViewVersion: Int = BiliWebConf.webViewVersion,
) {
    var appUserAgent = ""
        private set
    var webUserAgent = ""
        private set

    fun buildUserAgents() {
        appUserAgent =
            "Mozilla/5.0 BiliDroid/$version (bbcallen@gmail.com) os/$platform model/$model mobi_app/$mobiApp " +
            "build/$buildCode channel/$channel innerVer/$buildCode osVer/$osVersion network/$network"
        webUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    }
}

val BiliUserAgent: ClientPlugin<BiliUserAgentConfig> =
    createClientPlugin("BiliUserAgent", ::BiliUserAgentConfig) {
        pluginConfig.buildUserAgents()
        val appUserAgent = pluginConfig.appUserAgent
        val webUserAgent = pluginConfig.webUserAgent
        onRequest { request, _ ->
            val userAgent =
                if (request.host == "app.bilibili.com" || request.host == "passport.bilibili.com") {
                    appUserAgent
                } else {
                    webUserAgent
                }
            // 只记录 host + path：完整 URL 含 access_key / 搜索词 / uid 等查询参数，
            // 一旦 TRACE 开启就会把这些写进 Logcat。UA 本身含设备型号，同样不打印。
            LOGGER.trace("Adding User-Agent header for ${request.url.host}${request.url.encodedPath}")
            request.header(HttpHeaders.UserAgent, userAgent)
        }
    }

@Suppress("FunctionName")
fun HttpClientConfig<*>.BiliUserAgent() {
    install(BiliUserAgent) {
    }
}
