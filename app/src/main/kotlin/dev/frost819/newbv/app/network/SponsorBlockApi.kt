package dev.frost819.newbv.app.network

import dev.frost819.newbv.app.entity.player.SponsorBlockSegmentDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * 小电视空降助手（BilibiliSponsorBlock）社区服务端 API 封装。
 *
 * 数据全部来自网友标注，服务端为 SponsorBlock 兼容实现：
 * `GET https://bsbsb.top/api/skipSegments?videoID={BV号}` 返回该视频
 * （全部分 P）的片段列表，按 `cid` 字段区分分 P。
 *
 * 只读查询，不上报任何用户数据；请求不携带 B 站登录态。
 */
object SponsorBlockApi {
    private const val HOST = "bsbsb.top"

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val REQUEST_TIMEOUT_SECONDS = 15L

    private val json =
        Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
        }

    private val client: HttpClient =
        HttpClient(OkHttp) {
            BrowserUserAgent()
            install(ContentNegotiation) {
                json(json)
            }
            install(ContentEncoding) {
                deflate(1.0f)
                gzip(0.9f)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SECONDS)
                requestTimeoutMillis = TimeUnit.SECONDS.toMillis(REQUEST_TIMEOUT_SECONDS)
            }
        }

    /**
     * 查询指定视频的空降片段。
     *
     * @param bvid 视频 BV 号。
     * @return 片段列表；无片段（空数组）或服务端 404 时返回空列表。
     * @throws Exception 网络异常由调用方捕获处理。
     */
    suspend fun getSegments(bvid: String): List<SponsorBlockSegmentDto> {
        val response =
            client.get("https://$HOST/api/skipSegments") {
                parameter("videoID", bvid)
            }
        if (response.status.value == 404) return emptyList()
        val text = response.bodyAsText()
        if (text.isBlank()) return emptyList()
        return json.decodeFromString<List<SponsorBlockSegmentDto>>(text)
    }
}
