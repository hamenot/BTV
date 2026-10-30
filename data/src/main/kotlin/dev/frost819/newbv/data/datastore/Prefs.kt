@file:Suppress("UNCHECKED_CAST")

package dev.frost819.newbv.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 偏好设置委托。
 *
 * 提供三级读写机制，兼顾性能与持久化：
 * 1. 内存缓存：[flow]（[MutableStateFlow]），读操作直接命中内存，同步无锁。
 * 2. 写：立即更新内存（UI 瞬间响应），再异步写 DataStore（不阻塞 UI）。
 * 3. 初始化：[Prefs.init] 阻塞读取 DataStore 首帧数据填充内存，之后长连接 collect 同步。
 *
 * 支持两种委托工厂：
 * - 基本类型（String/Int/Boolean/Float/Long）：直接存储。
 * - 对象类型（Enum/Date/列表等）：通过 [save]/[restore] 映射到基本类型。
 *
 * @param T 业务层类型（如枚举、Date）。
 * @param P DataStore 持久化类型（String/Int/Boolean/Float/Long）。
 * @param key DataStore 键。
 * @param defaultValue 默认值（业务类型）。
 * @param save 业务类型 → 持久化类型 转换函数，默认直接强转。
 * @param restore 持久化类型 → 业务类型 转换函数，默认直接强转。
 */
class PrefDelegate<T, P>(
    private val key: Preferences.Key<P>,
    private val defaultValue: T,
    private val save: (T) -> P = { it as P },
    private val restore: (P) -> T = { it as T },
) : ReadWriteProperty<Any?, T> {
    /** 内存缓存流，初始值为默认值的持久化形式。 */
    internal val flow: MutableStateFlow<Any?> = MutableStateFlow(save(defaultValue))

    /**
     * 读取当前值。
     *
     * 直接读内存缓存，若无持久化值则返回 [defaultValue]。
     */
    override fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T {
        val rawValue = flow.value as? P
        return if (rawValue != null) restore(rawValue) else defaultValue
    }

    /**
     * 写入新值。
     *
     * 1. 立即更新内存缓存，UI 瞬间响应。
     * 2. 异步写入 DataStore，不阻塞调用方。
     */
    override fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T,
    ) {
        val persistValue = save(value)
        flow.value = persistValue
        val dataStore = Prefs.dataStore
        // 登记本次写入，防止随后到达的首帧快照用旧值把它覆盖回去
        Prefs.markPendingWrite(key)
        Prefs.launchPersist {
            dataStore.edit { prefs -> prefs[key] = persistValue }
        }
    }

    /** 重置内存缓存为默认值的持久化形式（用于 [Prefs.clear]）。 */
    internal fun resetToDefault() {
        flow.value = save(defaultValue)
    }
}

/**
 * 偏好设置管理器。
 *
 * 集中管理全部应用偏好项，通过 [PrefDelegate] 暴露读写属性。
 * 使用前必须在 Application.onCreate 调用 [init] 完成内存缓存初始化。
 *
 * 设计要点：
 * - [dataStore] 由 Hilt 注入（见 di 模块的 DataStoreModule）。
 * - 读操作同步无锁（命中内存），写操作异步持久化。
 * - [startInit] 在后台协程读取首帧 DataStore 数据并填满内存缓存，不阻塞调用线程；
 *   [init] 是它的阻塞封装（仅测试/强同步场景使用）。
 * - [startInit] 自动检查并生成 buvid / buvid3（若缺失）。
 *
 * 已删除（相对原版 BV）的偏好项：
 * - 代理相关：`enableProxy`、`proxyHttpServer`、`proxyGRPCServer`、`preferOfficialCdn`
 * - 播放器类型：`playerType`（仅 Media3，无需选择）
 * - FPS 显示：`showFps`（调试用，非用户功能）
 *
 * 使用示例：
 * ```
 * Prefs.startInit(dataStore)              // Application.onCreate，立即返回
 * Prefs.awaitReady()                      // 需要真实值时挂起等待
 * val isLogin = Prefs.isLogin             // 任意位置读取
 * Prefs.isLogin = true                    // 任意位置写入
 * ```
 *
 * @see PrefDelegate
 * @see PrefKeys
 * @see BuvidGenerator
 */
object Prefs {
    /**
     * 持久化写入的协程作用域，使用 IO 调度器 + SupervisorJob（单次失败不影响后续）。
     *
     * 必须带 [CoroutineExceptionHandler]：没有它时，一次 DataStore 写入失败
     * （磁盘满 / 文件损坏 / 权限变化）会成为未捕获协程异常，直接走默认
     * UncaughtExceptionHandler **把整个进程打崩** —— 而偏好写入本质是 best-effort，
     * 失败只应记日志并保留内存缓存。
     */
    private val persistExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            // data 模块不依赖 :core 的日志设施（避免反向依赖），用标准输出即可：
            // Android 运行时会把 stdout 重定向到 Logcat。绝不能用 android.util.Log ——
            // 本模块的 JVM 单元测试没有 Robolectric，Log.w 会抛 "not mocked"。
            println("[data][Prefs] Failed to persist preference: $throwable")
        }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + persistExceptionHandler)

    /** 在途持久化任务，供 [awaitPendingWrites] 等待。 */
    private val pendingPersistJobs = ConcurrentHashMap.newKeySet<Job>()

    /** 所有委托的内存缓存流映射表，由 [registerDelegate] 填充。 */
    private val delegateMap = ConcurrentHashMap<Preferences.Key<*>, PrefDelegate<*, *>>()

    /** DataStore 实例，由 [init] 注入。 */
    @Volatile
    private var dataStoreRef: DataStore<Preferences>? = null

    /** DataStore 实例（仅供 [PrefDelegate] 内部异步写入使用）。 */
    internal val dataStore: DataStore<Preferences>
        get() = dataStoreRef ?: error("Prefs 未初始化，请先调用 Prefs.init(dataStore)")

    /** 初始化状态标志，防止重复初始化。 */
    @Volatile
    private var initialized = false

    /**
     * 首帧数据是否已从 DataStore 载入内存。
     *
     * [startInit] 起协程读盘，读完置 `true`；在此之前读取偏好一律返回默认值。
     * 需要「拿到用户真实设置再渲染」的地方（如启动页密度、主题）应等待 [awaitReady]。
     */
    @Volatile
    private var loaded = false

    /** [startInit] 是否已被调用过（与 [loaded] 区分：已开始 ≠ 已完成）。 */
    @Volatile
    private var initStarted = false

    /** 首帧数据就绪信号，[awaitReady] 在此挂起。 */
    @Volatile
    private var readySignal = CompletableDeferred<Unit>()

    /** 首帧读盘的超时兜底（毫秒）：超时后回退默认值并放行启动页。 */
    private const val LOAD_TIMEOUT_MS = 3_000L

    /** 持久化写入互斥锁，避免并发 edit 冲突。 */
    private val persistMutex = Mutex()

    /**
     * 首帧快照是否已应用完毕。
     *
     * 在此之前发生过的写入会把 key 登记到 [pendingWrites]，
     * 快照应用时跳过这些 key，避免用旧快照覆盖掉用户刚写入的新值。
     */
    @Volatile
    private var initialPrefsApplied = false

    /** 首帧读盘完成前被写过的偏好键。 */
    private val pendingWrites = ConcurrentHashMap<Preferences.Key<*>, Unit>()

    /**
     * 登记「用户在首帧读盘窗口内改过这个偏好」。
     *
     * 由 [PrefDelegate.setValue] 调用。
     */
    internal fun markPendingWrite(key: Preferences.Key<*>) {
        if (!initialPrefsApplied) pendingWrites[key] = Unit
    }

    // ===== 委托工厂 =====

    /**
     * 创建基本类型委托。
     *
     * @param T 基本类型（String/Int/Boolean/Float/Long）。
     * @param key DataStore 键。
     * @param default 默认值。
     */
    private fun <T> pref(
        key: Preferences.Key<T>,
        default: T,
    ): PrefDelegate<T, T> = PrefDelegate(key, default).also { registerDelegate(key, it) }

    /**
     * 创建对象映射委托。
     *
     * @param T 业务类型。
     * @param P 持久化类型。
     * @param key DataStore 键。
     * @param default 默认值（业务类型）。
     * @param save 业务 → 持久化 转换。
     * @param restore 持久化 → 业务 转换。
     */
    private fun <T, P> pref(
        key: Preferences.Key<P>,
        default: T,
        save: (T) -> P,
        restore: (P) -> T,
    ): PrefDelegate<T, P> = PrefDelegate(key, default, save, restore).also { registerDelegate(key, it) }

    /** 注册委托到映射表，供 [init] 同步内存缓存使用。 */
    private fun <T, P> registerDelegate(
        key: Preferences.Key<P>,
        delegate: PrefDelegate<T, P>,
    ) {
        delegateMap[key] = delegate
    }

    // ===== 偏好项定义 =====

    // --- 账号 & 认证（PRD 7.8） ---

    /** 是否已登录。 */
    var isLogin by pref(PrefKeys.isLogin, false)

    /** 当前登录用户 UID。 */
    var uid by pref(PrefKeys.uid, 0L)

    /** SID（会话标识）。 */
    var sid by pref(PrefKeys.sid, "")

    /** SESSDATA Cookie 值。 */
    var sessData by pref(PrefKeys.sessData, "")

    /** bili_jct（csrf token）。 */
    var biliJct by pref(PrefKeys.biliJct, "")

    /** uid_ck_md5（登录校验值）。 */
    var uidCkMd5 by pref(PrefKeys.uidCkMd5, "")

    /** Token 过期时间。 */
    var tokenExpiredDate by pref(
        PrefKeys.tokenExpiredDate,
        Date(0),
        save = { it.time },
        restore = { Date(it) },
    )

    /** Access Token。 */
    var accessToken by pref(PrefKeys.accessToken, "")

    /** Refresh Token。 */
    var refreshToken by pref(PrefKeys.refreshToken, "")

    /** buvid（设备标识），首次启动自动生成。 */
    var buvid by pref(PrefKeys.buvid, "")

    /** buvid3（Web 端设备标识），首次启动自动生成。 */
    var buvid3 by pref(PrefKeys.buvid3, "")

    /** buvid3 是否已通过 SPI 接口注册（已注册则不再重复获取）。 */
    var buvid3FromSpi by pref(PrefKeys.buvid3FromSpi, false)

    /** 设备 cookie 字符串（buvid3 + b_nut 等），由 SPI 流程获取并持久化。 */
    var deviceCookies by pref(PrefKeys.deviceCookies, "")

    /** 无痕模式（不记录历史）。 */
    var incognitoMode by pref(PrefKeys.incognitoMode, false)

    // --- 网络 & API（PRD 7.5） ---

    /** 接口类型（Web/App）。 */
    var apiType by pref(
        PrefKeys.apiType,
        ApiType.Web,
        save = { it.ordinal },
        restore = { ApiType.fromOrdinal(it) },
    )

    /** 是否启用崩溃日志上传（默认关闭）。 */
    var crashReportEnabled by pref(PrefKeys.crashReportEnabled, false)

    /**
     * 起播前自动测速并选择最优 CDN（默认关闭）。
     *
     * 开启后会对候选播放地址做小流量测速，选择吞吐最优的节点，并缓存测速结果。
     */
    var autoSelectCdn by pref(PrefKeys.autoSelectCdn, false)

    // --- 播放器 - 视频（PRD 7.1） ---

    /** 默认画质。 */
    var defaultQuality by pref(
        PrefKeys.defaultQuality,
        Resolution.R1080P,
        save = { it.code },
        restore = { Resolution.fromCode(it) },
    )

    /** 默认视频编码。 */
    var defaultVideoCodec by pref(
        PrefKeys.defaultVideoCodec,
        VideoCodec.AVC,
        save = { it.ordinal },
        restore = { VideoCodec.fromCode(it) },
    )

    /** 启用视频软解。 */
    var enableSoftwareVideoDecoder by pref(PrefKeys.enableSoftwareVideoDecoder, false)

    /** 播放结束动作。 */
    var actionAfterPlay by pref(
        PrefKeys.actionAfterPlay,
        ActionAfterPlay.PlayNext,
        save = { it.code },
        restore = { ActionAfterPlay.fromCode(it) },
    )

    /** 自定义播放快捷键（JSON 字符串）。 */
    var playerCustomShortcuts by pref(PrefKeys.playerCustomShortcuts, "")

    // --- 播放器 - 音频（PRD 7.1） ---

    /** 默认音频编码。 */
    var defaultAudio by pref(
        PrefKeys.defaultAudio,
        Audio.A192K,
        save = { it.code },
        restore = { Audio.fromCode(it) },
    )

    /** 启用 FFmpeg 音频软解。 */
    var enableFfmpegAudioRenderer by pref(PrefKeys.enableFfmpegAudioRenderer, false)

    // --- 播放器 - 直播 ---

    /** 默认直播画质。 */
    var defaultLiveQuality by pref(
        PrefKeys.defaultLiveQuality,
        LiveQualityLevel.AUTO,
        save = { it.qn },
        restore = { LiveQualityLevel.fromQn(it) },
    )

    // --- 播放器 - 空降助手（SponsorBlock，片段数据来自 bsbsb.top 社区） ---

    /** 空降助手总开关。 */
    var sponsorBlockEnabled by pref(PrefKeys.sponsorBlockEnabled, true)

    /** 启用的跳过类别（逗号分隔的类别码，空 = 使用默认类别）。 */
    var sponsorBlockCategories by pref(PrefKeys.sponsorBlockCategories, "")

    /** 跳过片段后显示提示。 */
    var sponsorBlockShowTip by pref(PrefKeys.sponsorBlockShowTip, true)

    /** 进度条显示片段标记。 */
    var sponsorBlockShowMarkers by pref(PrefKeys.sponsorBlockShowMarkers, true)

    // --- 播放器 - 弹幕（PRD 7.3） ---

    /** 默认弹幕类型（多选，逗号分隔的序号字符串）。 */
    var defaultDanmakuTypes by pref(
        PrefKeys.defaultDanmakuTypes,
        listOf(DanmakuType.All, DanmakuType.Rolling, DanmakuType.Top, DanmakuType.Bottom),
        save = { list -> list.joinToString(",") { it.ordinal.toString() } },
        restore = { str ->
            if (str.isEmpty()) {
                emptyList()
            } else {
                str
                    .split(",")
                    .mapNotNull { runCatching { DanmakuType.entries[it.toInt()] }.getOrNull() }
            }
        },
    )

    /** 默认弹幕大小。 */
    var defaultDanmakuScale by pref(PrefKeys.defaultDanmakuScale, 1.75f)

    /** 默认弹幕透明度。 */
    var defaultDanmakuOpacity by pref(PrefKeys.defaultDanmakuOpacity, 0.7f)

    /** 默认弹幕速度因子。 */
    var defaultDanmakuSpeedFactor by pref(PrefKeys.defaultDanmakuSpeedFactor, 1f)

    /** 默认弹幕显示区域。 */
    var defaultDanmakuArea by pref(PrefKeys.defaultDanmakuArea, 0.5f)

    /** 默认防遮挡蒙版开关。 */
    var defaultDanmakuMask by pref(PrefKeys.defaultDanmakuMask, false)

    // --- 播放器 - 字幕（PRD 7.4） ---

    /** 默认字幕字号（SP）。 */
    var defaultSubtitleFontSize by pref(PrefKeys.defaultSubtitleFontSize, 24)

    /** 默认字幕背景透明度。 */
    var defaultSubtitleBackgroundOpacity by pref(PrefKeys.defaultSubtitleBackgroundOpacity, 0.4f)

    /** 默认字幕底部边距（DP）。 */
    var defaultSubtitleBottomPadding by pref(PrefKeys.defaultSubtitleBottomPadding, 12)

    // --- 播放器 - 界面（PRD 7.1/7.2） ---

    /** 默认播放速度。 */
    var defaultPlaySpeed by pref(
        PrefKeys.defaultPlaySpeed,
        PlaySpeed.X1,
        save = { it.code },
        restore = { PlaySpeed.fromCode(it) },
    )

    /** 显示视频详情页（关闭后点击直接播放）。 */
    var showVideoInfo by pref(PrefKeys.showVideoInfo, true)

    /** 显示常显进度条。 */
    var showPersistentSeek by pref(PrefKeys.showPersistentSeek, false)

    /** 显示播放器调试信息。 */
    var showPlayerDebugInfo by pref(PrefKeys.showPlayerDebugInfo, false)

    // --- 应用界面（PRD 7.2） ---

    /** 界面缩放密度（默认 1f，app 层 init 时按屏幕宽度重新计算）。 */
    var density by pref(PrefKeys.density, 2f)

    /** 启动页（左侧导航项）。 */
    var homeLeftNavItem by pref(
        PrefKeys.homeLeftNavItem,
        LeftNaviItem.Home,
        save = { it.ordinal },
        restore = { LeftNaviItem.fromOrdinal(it) },
    )

    /** 首页置顶 Tab。 */
    var firstHomeTopNavItem by pref(
        PrefKeys.firstHomeTopNavItem,
        HomeTopNavItem.Dynamics,
        save = { it.code },
        restore = { HomeTopNavItem.fromCode(it) },
    )

    /** 分区顶栏顺序（逗号分隔的项名，空 = 默认顺序）。 */
    var ugcTabOrder by pref(PrefKeys.ugcTabOrder, "")

    /** 分区顶栏中隐藏的项（逗号分隔的项名）。 */
    var ugcTabHidden by pref(PrefKeys.ugcTabHidden, "")

    /** 个人页置顶 Tab。 */
    var firstPersonalTopNavItem by pref(
        PrefKeys.firstPersonalTopNavItem,
        PersonalTopNavItem.ToView,
        save = { it.ordinal },
        restore = { PersonalTopNavItem.fromOrdinal(it) },
    )

    /** 显示搜索热词。 */
    var showHotword by pref(PrefKeys.showHotword, true)

    /** 主题模式（跟随系统/深色/浅色）。 */
    var themeMode by pref(
        PrefKeys.themeMode,
        ThemeMode.FollowSystem,
        save = { it.ordinal },
        restore = { ThemeMode.fromOrdinal(it) },
    )

    /** 强调色（ARGB，0 = 使用默认品牌色）。 */
    var accentColor by pref(PrefKeys.accentColor, 0)

    // --- 存储设置（PRD 7.6） ---

    /** 缓存阈值（MB，0 = 无限制，默认无限制）。 */
    var cacheThreshold by pref(PrefKeys.cacheThreshold, 0)

    /** 缓存自动清空开关（关闭后不自动清理）。 */
    var cacheAutoClean by pref(PrefKeys.cacheAutoClean, true)

    // ===== Flow 属性（用于 Compose collectAsState 实时观察） =====

    /**
     * 主题模式 Flow（实时响应设置变更）。
     *
     * 用 `by lazy` 只构建一次：`stateIn` 会启动一个协程常驻收集上游 `MutableStateFlow`，
     * 若写成 `get()` 每次访问都新建，Composable 里每重组一次就泄漏一个永久协程。
     */
    val themeModeFlow: StateFlow<ThemeMode> by lazy {
        (delegateMap[PrefKeys.themeMode] as? PrefDelegate<ThemeMode, Int>)
            ?.flow
            ?.map { ThemeMode.fromOrdinal(it as? Int ?: 0) }
            ?.stateIn(scope, SharingStarted.Eagerly, ThemeMode.FollowSystem)
            ?: MutableStateFlow(ThemeMode.FollowSystem)
    }

    /** Density Flow（实时响应设置变更），同时为 `by lazy` 单例，理由见 [themeModeFlow]。 */
    val densityFlow: StateFlow<Float> by lazy {
        (delegateMap[PrefKeys.density] as? PrefDelegate<Float, Float>)
            ?.flow
            ?.map { it as? Float ?: 2f }
            ?.stateIn(scope, SharingStarted.Eagerly, 2f)
            ?: MutableStateFlow(2f)
    }

    /** 强调色 Flow（实时响应设置变更，0 = 默认），同时为 `by lazy` 单例，理由见 [themeModeFlow]。 */
    val accentColorFlow: StateFlow<Int> by lazy {
        (delegateMap[PrefKeys.accentColor] as? PrefDelegate<Int, Int>)
            ?.flow
            ?.map { it as? Int ?: 0 }
            ?.stateIn(scope, SharingStarted.Eagerly, 0)
            ?: MutableStateFlow(0)
    }

    /** 常显进度条 Flow（实时响应设置变更），同时为 `by lazy` 单例，理由见 [themeModeFlow]。 */
    val showPersistentSeekFlow: StateFlow<Boolean> by lazy {
        (delegateMap[PrefKeys.showPersistentSeek] as? PrefDelegate<Boolean, Boolean>)
            ?.flow
            ?.map { it as? Boolean ?: false }
            ?.stateIn(scope, SharingStarted.Eagerly, false)
            ?: MutableStateFlow(false)
    }

    /** 空降助手总开关 Flow（实时响应设置变更），同时为 `by lazy` 单例，理由见 [themeModeFlow]。 */
    val sponsorBlockEnabledFlow: StateFlow<Boolean> by lazy {
        (delegateMap[PrefKeys.sponsorBlockEnabled] as? PrefDelegate<Boolean, Boolean>)
            ?.flow
            ?.map { it as? Boolean ?: true }
            ?.stateIn(scope, SharingStarted.Eagerly, true)
            ?: MutableStateFlow(true)
    }

    // ===== 初始化 =====

    /** 首帧数据是否已载入内存（启动页可据此决定是否继续展示）。 */
    val isReady: Boolean get() = loaded

    /**
     * 初始化偏好设置（阻塞版）。
     *
     * 读取 DataStore 首帧数据填满内存缓存后才返回，语义与改造前完全一致，供**测试**与
     * 需要强同步保证的场景使用。
     *
     * 生产代码请用 [startInit] + [awaitReady]：`Application.onCreate` 在主线程，
     * 不该在这里做磁盘读。
     *
     * @param dataStore DataStore 实例（由 Hilt 注入）。
     */
    fun init(dataStore: DataStore<Preferences>) {
        bindDataStore(dataStore)
        applyInitialPrefs(runBlocking { dataStore.data.first() })
        loaded = true
        readySignal.complete(Unit)
    }

    /**
     * 初始化偏好设置（非阻塞版）。
     *
     * **必须在 Application.onCreate 调用**，且早于任何依赖偏好的初始化。
     * 立即返回，首帧数据由内部协程从 DataStore 读入；在此之前读取任何偏好都返回
     * 默认值，因此需要真实值的地方要先 [awaitReady]。
     *
     * 之后不再监听 DataStore 变化——所有写入都经过 [PrefDelegate.setValue]，
     * 已同步更新内存缓存，无需 collect 反向同步（且避免部分写入时的竞态）。
     *
     * 读盘失败或超时也会把 [isReady] 置 `true`（回退到默认值），否则启动页会永久停留。
     *
     * @param dataStore DataStore 实例（由 Hilt 注入）。
     * @param onError 读盘失败时的回调（由调用方决定如何记录），默认忽略。
     */
    fun startInit(
        dataStore: DataStore<Preferences>,
        onError: (Throwable) -> Unit = {},
    ) {
        bindDataStore(dataStore)

        scope.launch {
            try {
                // 加超时兜底：DataStore 首帧读理论上很快，但损坏文件/存储卡异常时
                // data.first() 可能长期挂起，不能让它把启动页一直挡着。
                val initialPrefs = withTimeoutOrNull(LOAD_TIMEOUT_MS) { dataStore.data.first() }
                if (initialPrefs != null) applyInitialPrefs(initialPrefs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            } finally {
                loaded = true
                readySignal.complete(Unit)
            }
        }
    }

    /**
     * 等待首帧数据载入完成。
     *
     * 在 [startInit] 之后调用；若已就绪则立即返回。
     */
    suspend fun awaitReady() {
        readySignal.await()
    }

    /**
     * 绑定 DataStore 实例并置初始化标志。
     *
     * [init] 与 [startInit] 共用，保证两条路径的前置校验与状态设置完全一致。
     */
    private fun bindDataStore(dataStore: DataStore<Preferences>) {
        check(!initStarted) { "Prefs 已经初始化，禁止重复调用 init()/startInit()" }
        initStarted = true
        dataStoreRef = dataStore
        initialized = true
    }

    /**
     * 把 DataStore 首帧数据写入各委托的内存缓存，并补齐 buvid。
     *
     * [init] 与 [startInit] 共用，避免两条初始化路径出现行为差异。
     */
    private fun applyInitialPrefs(initialPrefs: Preferences) {
        delegateMap.forEach { (key, delegate) ->
            // 已被用户在首帧读盘窗口内修改过的 key 一律跳过。
            // 否则「先写到 DataStore，后被旧快照覆盖回内存」会让内存与磁盘永久不一致，
            // 最严重的情况是 sessData / accessToken 被回滚，表现为「登录后几秒又被踢出」。
            if (pendingWrites.containsKey(key)) return@forEach
            if (initialPrefs.contains(key)) {
                (delegate as PrefDelegate<Any?, Any?>).flow.value = initialPrefs[key]
            }
        }
        initialPrefsApplied = true
        pendingWrites.clear()
        checkAndInitBuvid(initialPrefs)
    }

    /** 检查 buvid / buvid3 是否缺失，缺失则自动生成并持久化。 */
    private fun checkAndInitBuvid(prefs: Preferences) {
        if (!prefs.contains(PrefKeys.buvid) || prefs[PrefKeys.buvid].isNullOrEmpty()) {
            buvid = BuvidGenerator.generateBuvid()
        }
        if (!prefs.contains(PrefKeys.buvid3) || prefs[PrefKeys.buvid3].isNullOrEmpty()) {
            buvid3 = BuvidGenerator.generateBuvid3()
        }
    }

    /**
     * 在持久化协程作用域中执行写操作。
     *
     * 使用互斥锁保证写入串行（DataStore 内部已串行，此处做二次保护避免并发 edit）。
     */
    internal fun launchPersist(block: suspend () -> Unit) {
        val job =
            scope.launch {
                persistMutex.withLock { block() }
            }
        pendingPersistJobs.add(job)
        job.invokeOnCompletion { pendingPersistJobs.remove(job) }
    }

    /**
     * 等待所有在途持久化写入完成。
     *
     * 供测试在删除临时 DataStore 文件**之前**调用：`resetForTesting()` 只重置内存
     * 状态，不会等待后台协程 —— 测试先删文件再让残留协程写盘，就会产生
     * IOException 并以未捕获异常的形式污染同一个 JVM 里的后续测试。
     * 生产代码不需要调用（写入本来就是 fire-and-forget）。
     */
    internal suspend fun awaitPendingWrites() {
        while (true) {
            val jobs = pendingPersistJobs.toList()
            if (jobs.isEmpty()) return
            jobs.forEach { it.join() }
            // join 之后集合里可能又有新任务进来（setValue 在主线程随时触发），
            // 再查一轮直到为空
            if (pendingPersistJobs.isEmpty()) return
        }
    }

    /**
     * 重置所有偏好为默认值（测试用）。
     *
     * 清空 DataStore 并重置所有委托的内存缓存为默认值。
     *
     * 走 [persistMutex] 串行化：不这样做，清空操作会和还在排队的委托写入并发，
     * 可能出现「清空后又被一次迟到的写入复活」的竞态。
     */
    suspend fun clear() {
        check(initialized) { "Prefs 未初始化，无法 clear" }
        persistMutex.withLock { dataStore.edit { it.clear() } }
        delegateMap.forEach { (_, delegate) -> delegate.resetToDefault() }
    }

    /** 获取指定偏好键对应的 [MutableStateFlow]（用于 Compose 观察偏好变化）。 */
    @Suppress("UNCHECKED_CAST")
    fun <T> flowOf(key: Preferences.Key<T>): MutableStateFlow<Any?>? = delegateMap[key]?.flow

    // ===== 测试辅助 =====

    /**
     * 重置初始化状态（仅测试用）。
     *
     * 允许在单元测试中重复 [init]：
     * 1. 重置初始化标志、就绪信号与 DataStore 引用。
     * 2. 将所有委托的内存缓存重置为默认值。
     * 3. 保留 [delegateMap] 不清空（委托本身在 object 加载时创建，无法重建）。
     *
     * 生产代码禁止调用。
     */
    @androidx.annotation.VisibleForTesting
    fun resetForTesting() {
        // 先取消在途持久化写入：
        // 测试的 tearDown 会删除临时 DataStore 文件，若写入协程晚于删除才落地，
        // 会抛 IOException（Windows 上还可能表现为 rename 失败）。
        // 生产路径不经过本方法，取消行为不会影响真实运行。
        pendingPersistJobs.forEach { it.cancel() }
        pendingPersistJobs.clear()
        initialized = false
        initStarted = false
        loaded = false
        initialPrefsApplied = false
        pendingWrites.clear()
        readySignal = CompletableDeferred()
        dataStoreRef = null
        delegateMap.forEach { (_, delegate) -> delegate.resetToDefault() }
    }
}
