package dev.frost819.newbv.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore 偏好设置键定义。
 *
 * 集中管理所有偏好项的 [Preferences.Key]，键名沿用原版 BV 的缩写以保持兼容。
 * 键名规则：基本类型用语义缩写，历史遗留键名不变。
 *
 * 已删除（相对原版）：
 * - 代理相关：`enable_proxy`、`proxy_http_server`、`proxy_grpc_server`、`prefer_official_cdn`
 * - 播放器类型：`pt`（仅 Media3，无需选择）
 * - FPS 显示：`sf`（调试用，非用户功能）
 */
internal object PrefKeys {
    // ===== 账号 & 认证 =====
    val isLogin = booleanPreferencesKey("il")
    val uid = longPreferencesKey("uid")
    val sid = stringPreferencesKey("sid")
    val sessData = stringPreferencesKey("sd")
    val biliJct = stringPreferencesKey("bj")
    val uidCkMd5 = stringPreferencesKey("ucm")
    val tokenExpiredDate = longPreferencesKey("ted")
    val accessToken = stringPreferencesKey("access_token")
    val refreshToken = stringPreferencesKey("refresh_token")
    val buvid = stringPreferencesKey("random_buvid")
    val buvid3 = stringPreferencesKey("random_buvid3")
    val buvid3FromSpi = booleanPreferencesKey("buvid3_from_spi")
    val deviceCookies = stringPreferencesKey("device_cookies")
    val incognitoMode = booleanPreferencesKey("im")

    // ===== 网络 & API =====
    val apiType = intPreferencesKey("api_type")
    val crashReportEnabled = booleanPreferencesKey("crash_report_enabled")
    val autoSelectCdn = booleanPreferencesKey("auto_select_cdn")

    // ===== 播放器 - 视频 =====
    val defaultQuality = intPreferencesKey("dq")
    val defaultVideoCodec = intPreferencesKey("dvc")
    val enableSoftwareVideoDecoder = booleanPreferencesKey("enable_software_video_decoder")
    val actionAfterPlay = intPreferencesKey("action_after_play")
    val playerCustomShortcuts = stringPreferencesKey("player_custom_shortcuts")

    // ===== 播放器 - 音频 =====
    val defaultAudio = intPreferencesKey("da")
    val enableFfmpegAudioRenderer = booleanPreferencesKey("enable_ffmpeg_audio_renderer")

    // ===== 播放器 - 直播 =====
    val defaultLiveQuality = intPreferencesKey("dlq")

    // ===== 播放器 - 空降助手（SponsorBlock，片段数据来自 bsbsb.top 社区） =====

    /** 空降助手总开关。 */
    val sponsorBlockEnabled = booleanPreferencesKey("sponsor_block_enabled")

    /** 启用的跳过类别（逗号分隔的类别码，空 = 使用默认）。 */
    val sponsorBlockCategories = stringPreferencesKey("sponsor_block_categories")

    /** 跳过片段后显示提示。 */
    val sponsorBlockShowTip = booleanPreferencesKey("sponsor_block_show_tip")

    /** 进度条显示片段标记。 */
    val sponsorBlockShowMarkers = booleanPreferencesKey("sponsor_block_show_markers")

    // ===== 播放器 - 弹幕 =====
    val defaultDanmakuTypes = stringPreferencesKey("ddts")
    val defaultDanmakuScale = floatPreferencesKey("dds2")
    val defaultDanmakuOpacity = floatPreferencesKey("ddo")
    val defaultDanmakuSpeedFactor = floatPreferencesKey("ddsf")
    val defaultDanmakuArea = floatPreferencesKey("dda")
    val defaultDanmakuMask = booleanPreferencesKey("prefer_enable_webmark")

    // ===== 播放器 - 字幕 =====
    val defaultSubtitleFontSize = intPreferencesKey("dsfs")
    val defaultSubtitleBackgroundOpacity = floatPreferencesKey("dsbo")
    val defaultSubtitleBottomPadding = intPreferencesKey("dsbp")

    // ===== 播放器 - 界面 =====
    val defaultPlaySpeed = intPreferencesKey("dps")
    val showVideoInfo = booleanPreferencesKey("show_video_info")
    val showPersistentSeek = booleanPreferencesKey("show_persistent_seek")
    val showPlayerDebugInfo = booleanPreferencesKey("show_player_debug_info")

    // ===== 应用界面 =====
    val density = floatPreferencesKey("density")
    val homeLeftNavItem = intPreferencesKey("home_left_nav")
    val firstHomeTopNavItem = intPreferencesKey("first_home_top_nav")
    val firstPersonalTopNavItem = intPreferencesKey("first_personal_top_nav")

    /** 分区顶栏顺序（逗号分隔的项名，空 = 默认顺序）。 */
    val ugcTabOrder = stringPreferencesKey("ugc_tab_order")

    /** 分区顶栏中隐藏的项（逗号分隔的项名）。 */
    val ugcTabHidden = stringPreferencesKey("ugc_tab_hidden")
    val showHotword = booleanPreferencesKey("shw")
    val themeMode = intPreferencesKey("theme_mode")

    /** 强调色（ARGB，0 = 使用默认品牌色）。 */
    val accentColor = intPreferencesKey("accent_color")

    // ===== 存储设置 =====
    val cacheThreshold = intPreferencesKey("cache_threshold")
    val cacheAutoClean = booleanPreferencesKey("cache_auto_clean")
}
