package dev.frost819.newbv.app.network.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Release 数据模型。
 *
 * 对应 `GET /repos/{owner}/{repo}/releases/latest` 响应。
 *
 * @property assets 附件列表（APK 等）。
 * @property body Release Notes 正文。
 * @property name Release 标题。
 * @property tagName Git 标签名。
 * @property prerelease 是否为预发布。
 */
@Serializable
data class GithubRelease(
    val assets: List<Asset>,
    @SerialName("assets_url")
    val assetsUrl: String,
    val body: String,
    @SerialName("created_at")
    val createdAt: String,
    val draft: Boolean,
    @SerialName("html_url")
    val htmlUrl: String,
    val id: Int,
    val name: String,
    val prerelease: Boolean,
    @SerialName("published_at")
    val publishedAt: String,
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("tarball_url")
    val tarballUrl: String,
    @SerialName("target_commitish")
    val targetCommitish: String,
    @SerialName("upload_url")
    val uploadUrl: String,
    val url: String,
    @SerialName("zipball_url")
    val zipballUrl: String,
) {
    val isPreRelease: Boolean get() = prerelease

    /**
     * 用于展示的版本号：去掉 Release 标题里的发布名前缀，
     * 与 [dev.frost819.newbv.BuildConfig.VERSION_NAME] 的格式保持一致。
     *
     * 兼容两种命名：新版 `newBV 1.1.0.r145.xxxx` 与旧版 `BTV 1.1.0.r145.xxxx`。
     * 例：`newBV 0.1Beta1` → `0.1Beta1`；`BTV 0.1Beta1` → `0.1Beta1`。
     */
    val versionLabel: String
        get() =
            name
                .removePrefix("newBV")
                .removePrefix("BTV")
                .trim()

    /**
     * 挑出用于自动更新的 TV 版安装包。
     *
     * 发布包命名约定为 `BTV_<versionCode>_<versionName>_release.apk`
     * （更早的版本用 `BV_` 前缀）。只接受 `.apk`，避免把映射文件、校验文件
     * 当成安装包；同类中优先 `release`，其次 `alpha`，最后取任意 APK。
     *
     * 「检查更新」与「下载」都必须用本方法选包，否则两处判断规则不一致时
     * 会出现「检查到更新，下载却报找不到安装包」。
     *
     * @return 匹配到的附件，没有可用 APK 时返回 `null`。
     */
    fun tvAsset(): Asset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        return apks.firstOrNull { it.name.contains("release", ignoreCase = true) }
            ?: apks.firstOrNull { it.name.contains("alpha", ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    /**
     * Release 附件（APK 文件等）。
     *
     * @property browserDownloadUrl 直链下载地址。
     * @property name 文件名。
     * @property size 文件大小（字节）。
     * @property contentType MIME 类型。
     */
    @Serializable
    data class Asset(
        @SerialName("browser_download_url")
        val browserDownloadUrl: String,
        @SerialName("content_type")
        val contentType: String,
        @SerialName("download_count")
        val downloadCount: Int,
        val id: Int,
        val name: String,
        val size: Int,
        val state: String,
        val url: String,
    )
}
