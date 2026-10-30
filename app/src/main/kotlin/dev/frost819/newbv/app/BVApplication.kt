package dev.frost819.newbv.app

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import dev.frost819.newbv.app.network.HttpServer
import dev.frost819.newbv.app.util.CacheManager
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.repositories.AuthRepository
import dev.frost819.newbv.biliapi.repositories.ChannelRepository
import dev.frost819.newbv.core.log.CrashHandler
import dev.frost819.newbv.core.log.CrashUploader
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

/**
 * new BV 应用入口。
 *
 * 初始化顺序（关键：主线程不再做磁盘读）：
 * 1. Hilt 依赖注入（由 [HiltAndroidApp] 自动处理）
 * 2. [Prefs.startInit] 在后台协程读取偏好首帧数据，立即返回
 * 3. [CrashHandler] 全局崩溃处理（不依赖偏好，尽早安装）
 * 4. 应用级协程等 [Prefs.awaitReady] 后，再做鉴权、崩溃上传、图片加载器等依赖真实偏好的初始化
 *
 * 启动页（[MainActivity]）以 `Prefs.isReady` 作为 keep-on-screen 条件，
 * 因此用户在启动阶段看到的是启动图，而不是一帧「默认密度/默认主题」的界面。
 *
 * @see Prefs
 */
@HiltAndroidApp
class BVApplication : Application() {
    private val logger = Loggers.get("BVApplication")

    /**
     * 应用级协程作用域。
     *
     * 替代原先散落在 [onCreate] 里的多个 `CoroutineScope(Dispatchers.IO + SupervisorJob())`：
     * 进程内只需一个，随进程存活，不做取消。
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var crashHandler: CrashHandler

    @Inject
    lateinit var crashUploader: CrashUploader

    @Inject
    lateinit var httpServer: HttpServer

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var channelRepository: ChannelRepository

    /**
     * 播放器与 CDN 测速共用的 OkHttpClient。
     *
     * 用 [Provider] 延迟解析：client 构建包含系统 CA 库读取与 SSLContext 初始化，
     * 放在启动时的 IO 线程预热，避免首次进播放器时在主线程做这件事。
     */
    @Inject
    lateinit var playerHttpClientProvider: Provider<OkHttpClient>

    override fun onCreate() {
        super.onCreate()

        // 偏好首帧异步读盘。读失败/超时也会放行启动页（回退默认值），不阻塞启动。
        Prefs.startInit(dataStore) { error ->
            logger.error(error) { "Failed to load preferences, fallback to defaults" }
        }

        // 崩溃处理尽早安装，它不依赖偏好，放在主线程同步完成。
        @Suppress("UNUSED_EXPRESSION")
        crashHandler

        logger.info { "Application started" }

        // 依赖偏好真实值的初始化统一在这里等首帧就绪后执行。
        applicationScope.launch {
            Prefs.awaitReady()
            applyAuthState()
            initCrashUpload()
            initImageLoader()
            // 启动时检查缓存阈值，超限自动 LRU 清理（检查时机 = App 启动 + 缓存写入后）
            CacheManager(this@BVApplication).checkCache()
            // 预热播放器 OkHttpClient（构建 SSLContext/KeyStore 较慢，放到后台）。
            runCatching { playerHttpClientProvider.get() }
                .onFailure { logger.error(it) { "Failed to warm up player OkHttpClient" } }
        }
    }

    /**
     * 把偏好里的登录凭证同步到鉴权仓库与 HTTP 层。
     *
     * 必须在 [Prefs.awaitReady] 之后调用：早于首帧读盘时这里读到的全是默认空值，
     * 会导致后续请求以游客身份发出。
     */
    private fun applyAuthState() {
        val buvid3 = Prefs.buvid3
        val deviceCookies = Prefs.deviceCookies
        val sessData = Prefs.sessData
        val biliJct = Prefs.biliJct
        val mid = Prefs.uid.takeIf { it > 0 }
        val accessToken = Prefs.accessToken

        authRepository.buvid3 = buvid3
        authRepository.deviceCookies = deviceCookies
        authRepository.sessionData = sessData
        authRepository.biliJct = biliJct
        authRepository.mid = mid
        authRepository.accessToken = accessToken
        BiliHttpApi.init(
            buvid3 = buvid3,
            deviceCookies = deviceCookies,
            sessData = sessData,
            biliJct = biliJct,
            mid = mid,
            accessToken = accessToken,
        )

        // 以下两个网络任务互不依赖，各自并行，避免串行拖慢启动。
        if (accessToken.isNotBlank() && Prefs.buvid.isNotBlank()) {
            applicationScope.launch {
                runCatching { channelRepository.initDefaultChannel(accessToken, Prefs.buvid) }
                    .onFailure { logger.error(it) { "Failed to init default gRPC channel" } }
            }
        }

        if (!Prefs.buvid3FromSpi) {
            applicationScope.launch {
                val spiResult = runCatching { BiliHttpApi.fetchBuvid3FromSpi() }.getOrNull() ?: return@launch
                Prefs.buvid3 = spiResult.buvid3
                Prefs.deviceCookies = spiResult.deviceCookies
                Prefs.buvid3FromSpi = true
                authRepository.buvid3 = spiResult.buvid3
                authRepository.deviceCookies = spiResult.deviceCookies
                BiliHttpApi.buvid3 = spiResult.buvid3
                BiliHttpApi.deviceCookies = spiResult.deviceCookies
            }
        }
    }

    /** 按用户设置启用崩溃上传，并补传上次未发送成功的崩溃日志。 */
    private fun initCrashUpload() {
        crashUploader.enabled = Prefs.crashReportEnabled
        if (crashUploader.canUpload()) {
            applicationScope.launch {
                runCatching { crashUploader.uploadPendingCrashLogs() }
                    .onFailure { logger.error(it) { "Failed to upload pending crash logs" } }
            }
        }
    }

    /**
     * 配置 Coil 图片加载器。
     *
     * 磁盘缓存上限跟随「自动清理 + 阈值」设置：开启自动清理且设了阈值时按阈值限制，
     * 否则用一个足够大但仍有上界的值（见 [CacheManager.FALLBACK_DISK_CACHE_BYTES]），
     * 避免电视盒子的 cacheDir 被无限制撑满。
     *
     * 需要读 [Prefs.cacheAutoClean] / [Prefs.cacheThreshold]，因此在首帧就绪后调用。
     */
    private fun initImageLoader() {
        val diskCacheMaxBytes =
            if (Prefs.cacheAutoClean && Prefs.cacheThreshold > 0) {
                Prefs.cacheThreshold * CacheManager.BYTES_PER_MB
            } else {
                CacheManager.FALLBACK_DISK_CACHE_BYTES
            }
        val imageDiskCache =
            DiskCache
                .Builder()
                .directory(
                    File(cacheDir, CacheManager.IMAGE_CACHE_DIR).absolutePath.toPath(),
                ).maxSizeBytes(diskCacheMaxBytes)
                .build()

        SingletonImageLoader.setSafe {
            ImageLoader
                .Builder(this)
                .crossfade(true)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCache(imageDiskCache)
                .components {
                    // 图片与播放器媒体流分属不同连接池：共用会互相挤占并发额度，
                    // 播放卡顿的代价远大于一个空闲 OkHttpClient 的内存开销。
                    add(OkHttpNetworkFetcherFactory(OkHttpClient()))
                }.build()
        }
    }
}
