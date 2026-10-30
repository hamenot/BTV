package dev.frost819.newbv.app.network

import dev.frost819.newbv.app.network.entity.GithubRelease
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * GitHub Releases API 封装。
 *
 * 用于检查更新和下载 APK。仓库地址：`hamenot/BTV`。
 *
 * 检查更新走 `api.github.com` 直连；下载 APK 走 [DOWNLOAD_MIRRORS] 线路链
 * （国内直连 GitHub 的资源域名经常被重置，纯直连会让更新功能不可用）。
 */
object GithubApi {
    private const val OWNER = "hamenot"
    private const val REPO = "BTV"
    private const val API_BASE = "https://api.github.com"

    /**
     * 安装包下载线路，按顺序尝试（前缀直接拼在 GitHub 原始地址前，空串表示直连）。
     *
     * 任一线路连接失败、返回非 2xx 或文件长度与 Release 声明不符时，自动换下一条。
     *
     * 为什么必须做成线路链而不是单点：公益加速站寿命普遍很短——此前唯一的
     * `ghfast.top` 2026 年已停止服务（连接直接超时），而代码里写死它，结果就是
     * 「能检查到新版本但永远下载失败」。线路全部失效时异常信息会带上每条线路的
     * 失败原因，便于用户反馈时定位。
     *
     * 维护提示：新增线路请放在直连（空串）之前；直连必须保留作兜底。
     */
    val DOWNLOAD_MIRRORS: List<String> =
        listOf(
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "",
        )

    /**
     * 单条加速线路的总耗时上限（毫秒）。
     *
     * 实测加速站即使「能连上」，也可能只吐一半数据就挂住（HTTP 200 + 提前关连接），
     * 或速度低到十几 KB/s。这类线路不能靠 socket 空闲超时发现——数据一直在流，
     * 只是永远流不完。给它一个总预算，超了就换下一条；直连线路不设此限，
     * 慢速网络下也能把文件慢慢下完。
     */
    private const val MIRROR_TIMEOUT_MS = 180_000L

    private val json =
        Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
            prettyPrint = true
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
            // 死掉的加速站不一定拒连，可能只是长期不回包：不设超时会让弹窗
            // 一直停在 0% 且没有任何反馈。
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
                requestTimeoutMillis = 10 * 60_000
            }
        }

    /** 获取最新 Release（非预发布）。 */
    suspend fun getLatestBuild(): GithubRelease {
        val response = client.get("$API_BASE/repos/$OWNER/$REPO/releases/latest").bodyAsText()
        checkErrorMessage(response)
        return json.decodeFromString(response)
    }

    /** 获取所有 Releases。 */
    suspend fun getReleases(
        pageSize: Int = 30,
        page: Int = 1,
    ): List<GithubRelease> {
        val response =
            client
                .get("$API_BASE/repos/$OWNER/$REPO/releases") {
                    parameter("per_page", pageSize)
                    parameter("page", page)
                }.bodyAsText()
        checkErrorMessage(response)
        return json.decodeFromString(response)
    }

    /**
     * 下载 Release 中的 APK 附件。
     *
     * 依次尝试 [mirrors] 中的线路，第一个成功的线路把文件完整写入 [file]。
     * 下载过程中先写 `*.part` 临时文件，成功后才改名为 [file]，因此
     * [file] 存在即代表它是一份完整的安装包。
     *
     * @param release Release 信息。
     * @param file 目标文件路径。
     * @param mirrors 下载线路，默认 [DOWNLOAD_MIRRORS]，测试时可注入。
     * @param onProgress 下载进度回调（已下载字节, 总字节）。
     * @throws IllegalStateException Release 中没有 APK 附件，或所有线路均失败。
     */
    suspend fun downloadUpdate(
        release: GithubRelease,
        file: File,
        mirrors: List<String> = DOWNLOAD_MIRRORS,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        val asset = release.tvAsset() ?: throw IllegalStateException("Release 中没有 APK 附件")
        val partFile = File(file.parentFile, "${file.name}.part")
        val failures = mutableListOf<String>()

        for (mirror in mirrors) {
            val url = mirror + asset.browserDownloadUrl
            // 不用 runCatching：它会把协程取消也当成普通失败吞掉，用户在下载途中
            // 关掉弹窗后还会继续挨个试后面的线路。这里显式区分三类异常。
            val failure: Throwable? =
                try {
                    if (mirror.isEmpty()) {
                        downloadOnce(url, partFile, asset.size.toLong(), onProgress)
                    } else {
                        withTimeout(MIRROR_TIMEOUT_MS) {
                            downloadOnce(url, partFile, asset.size.toLong(), onProgress)
                        }
                    }
                    null
                } catch (e: TimeoutCancellationException) {
                    // 线路级超时（withTimeout 抛出）：只算这条线路失败
                    IllegalStateException("线路超时（${MIRROR_TIMEOUT_MS / 1000} 秒）", e)
                } catch (e: CancellationException) {
                    // 外部取消（用户关闭弹窗、页面退出）：必须立刻向上抛，停止整条链
                    throw e
                } catch (e: Throwable) {
                    e
                }

            if (failure == null) {
                if (!partFile.renameTo(file)) {
                    // renameTo 在跨文件系统/目标已存在时可能失败，退化成复制
                    partFile.copyTo(file, overwrite = true)
                    partFile.delete()
                }
                return
            }
            partFile.delete()
            failures += "${mirror.ifEmpty { "GitHub 直连" }} → ${failure.message ?: failure::class.simpleName}"
        }

        throw IllegalStateException("所有下载线路均失败：" + failures.joinToString(separator = "；"))
    }

    /**
     * 从单条线路下载到 [target]，并校验结果完整性。
     *
     * @param url 完整下载地址（已拼接线路前缀）。
     * @param target 目标文件（`.part` 临时文件）。
     * @param expectedSize Release 声明的文件大小，`<= 0` 时跳过长度校验。
     * @param onProgress 进度回调。
     */
    private suspend fun downloadOnce(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        client
            .prepareRequest {
                url(url)
                // 强制不压缩：加速站若 gzip 了二进制，落盘长度会与 Release 声明不符
                header(HttpHeaders.AcceptEncoding, "identity")
                onDownload { downloaded, total ->
                    onProgress(downloaded, total ?: 0)
                }
            }.execute { response ->
                // 加速站出错时（404/403/限流）常返回一段 HTML 且状态码非 2xx，
                // 不校验状态码就会把错误页当成 APK 落盘
                if (!response.status.isSuccess()) {
                    error("HTTP ${response.status.value}")
                }
                // use{} 保证流被关闭：不关闭时缓冲区内容可能丢失，文件被截断
                target.outputStream().use { output ->
                    response.bodyAsChannel().copyTo(output)
                }
            }

        val actualSize = target.length()
        if (expectedSize > 0 && actualSize != expectedSize) {
            error("文件不完整（$actualSize/$expectedSize 字节）")
        }
    }

    private fun checkErrorMessage(data: String) {
        val responseElement = json.parseToJsonElement(data)
        if (responseElement !is JsonObject) return
        val responseObject = responseElement.jsonObject
        if (responseObject["message"] != null) {
            error(responseObject["message"]!!.jsonPrimitive.content)
        }
    }
}
