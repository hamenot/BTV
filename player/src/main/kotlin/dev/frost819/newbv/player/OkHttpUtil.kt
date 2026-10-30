package dev.frost819.newbv.player

import android.content.Context
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * OkHttp 工具类，用于生成带自定义 SSL 配置的 OkHttpClient。
 *
 * 主要解决 B 站视频域名（bilivideo.com / bilivideo.cn）证书互通问题：
 * - 加载系统 CA 证书库
 * - 追加自定义 CA 证书（如 GlobalSign ECC Root CA R5）
 * - 允许 bilivideo.com 和 bilivideo.cn 域名证书互通
 *
 * 注意：构建成本较高（读取系统 CA 库 + assets 证书 + 初始化 SSLContext），
 * 且 OkHttpClient 自带独立的 ConnectionPool。**必须由 `PlayerModule` 以单例持有，
 * 进程内只创建一份**，不要在播放器实例内按需创建。
 */
object OkHttpUtil {
    /**
     * 创建带自定义 SSL 配置的 OkHttpClient。
     *
     * 连接池参数保持 OkHttp 默认值（5 条空闲连接 / 5 分钟保活）；因为该 client
     * 在进程内共享，这就是播放器侧连接与线程数的全局上限。
     *
     * @param context Android Context，用于读取 assets 中的 CA 证书文件
     * @return 配置好 SSL 和域名验证的 OkHttpClient
     */
    fun generateCustomSslOkHttpClient(context: Context): OkHttpClient {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val customCaMap =
            mapOf(
                "custom:r5" to "GlobalSign ECC Root CA R5.crt",
            )

        val keyStoreType = KeyStore.getDefaultType()
        val systemKeyStore =
            KeyStore.getInstance("AndroidCAStore").apply {
                load(null, null)
            }
        val customKeyStore =
            KeyStore.getInstance(keyStoreType).apply {
                load(null, null)

                // 复制系统 CA 证书
                systemKeyStore.aliases().toList().forEach {
                    setCertificateEntry(it, systemKeyStore.getCertificate(it))
                }
                // 追加自定义 CA 证书
                customCaMap.forEach { (alias, caFilename) ->
                    val certificateInputStream = context.assets.open(caFilename)
                    val certificate = certificateFactory.generateCertificate(certificateInputStream)
                    setCertificateEntry(alias, certificate)
                }
            }

        val tmfAlgorithm: String = TrustManagerFactory.getDefaultAlgorithm()
        val trustManagerFactory =
            TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                init(customKeyStore)
            }

        val sslContext: SSLContext =
            SSLContext.getInstance("TLS").apply {
                init(null, trustManagerFactory.trustManagers, null)
            }

        return OkHttpClient
            .Builder()
            .sslSocketFactory(
                sslContext.socketFactory,
                trustManagerFactory.trustManagers[0] as X509TrustManager,
            ).hostnameVerifier { hostname, session ->
                // bilivideo.com 与 bilivideo.cn 的证书由 B 站 CDN 混用，标准校验会失败，
                // 需要放宽；但**不能**无条件返回 true——那样任何一张合法证书（例如攻击者
                // 自有的 attacker.com）都能冒充视频节点。这里的折中是：只接受证书 SAN
                // 里确实写着 B 站视频域名的证书，即「跨域名互通」成立，但他域证书仍然被拒。
                if (isBiliVideoHost(hostname)) {
                    biliVideoCertificateVerifier.verify(hostname, session)
                } else {
                    defaultHostnameVerifier.verify(hostname, session)
                }
            }.build()
    }

    /** B 站视频 CDN 使用的两个顶级域名（证书互通只在这两个域之间成立）。 */
    private val biliVideoDomains = listOf("bilivideo.com", "bilivideo.cn")

    /** 系统默认的主机名校验器，非视频域名一律交回给它。 */
    private val defaultHostnameVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    /**
     * 视频域名专用校验器：证书 SAN 中含任一 B 站视频域名即通过。
     *
     * 之所以不能直接用系统校验器：同一批 CDN 节点会用 `*.bilivideo.cn` 的证书
     * 服务 `*.bilivideo.com` 的请求，严格匹配必然失败。
     */
    private val biliVideoCertificateVerifier =
        HostnameVerifier { _, session ->
            val certificate =
                runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }
                    .getOrNull() ?: return@HostnameVerifier false
            // subjectAlternativeNames 每项形如 [类型, 值]，类型 2 为 DNS 名
            val dnsNames =
                runCatching { certificate.subjectAlternativeNames.orEmpty() }
                    .getOrDefault(emptyList())
                    .mapNotNull { entry ->
                        val type = entry.getOrNull(0) as? Int
                        val value = entry.getOrNull(1) as? String
                        if (type == SAN_TYPE_DNS) value else null
                    }
            dnsNames.any { isBiliVideoHost(it) }
        }

    /** 判断 [host] 是否属于 B 站视频 CDN 域名（同时兼容 SAN 里的通配写法 `*.xxx`）。 */
    private fun isBiliVideoHost(host: String): Boolean {
        val bare = host.removePrefix("*.").lowercase()
        return biliVideoDomains.any { domain -> bare == domain || bare.endsWith(".$domain") }
    }

    /** 证书 SAN 中 DNS 名称的类型编号。 */
    private const val SAN_TYPE_DNS = 2
}
