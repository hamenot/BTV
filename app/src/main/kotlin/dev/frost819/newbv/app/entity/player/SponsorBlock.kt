package dev.frost819.newbv.app.entity.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 空降助手（SponsorBlock）片段类别。
 *
 * 与小电视空降助手（BilibiliSponsorBlock）的社区类别一一对应，
 * [colorHex] 取自该插件的进度条默认配色，便于跨端认知一致。
 *
 * @param code 服务端类别码
 * @param displayName 中文显示名
 * @param colorHex 进度条标记颜色（ARGB）
 * @param defaultEnabled 未做用户配置时的默认启用状态
 */
enum class SponsorBlockCategory(
    val code: String,
    val displayName: String,
    val colorHex: Long,
    val defaultEnabled: Boolean,
) {
    /** 赞助广告（恰饭推广段）。 */
    SPONSOR("sponsor", "赞助广告", 0xFF00D400, true),

    /** 自我推广（给自己频道/商品打广告）。 */
    SELFPROMO("selfpromo", "自我推广", 0xFFFFFF00, true),

    /** 三连/关注提醒。 */
    INTERACTION("interaction", "三连提醒", 0xFFCC00FF, true),

    /** 开场动画。 */
    INTRO("intro", "开场动画", 0xFF00FFFF, false),

    /** 结尾动画/致谢。 */
    OUTRO("outro", "结尾动画", 0xFF0202ED, false),

    /** 预告/回顾。 */
    PREVIEW("preview", "预告/回顾", 0xFF008FD6, false),

    /** 无关闲聊填充。 */
    FILLER("filler", "无关闲聊", 0xFF7300FF, false),

    /** 非音乐部分（音乐视频中的闲聊段）。 */
    MUSIC_OFFTOPIC("music_offtopic", "非音乐部分", 0xFFFF9900, false),

    /**
     * 精彩时刻（空降点）。该类别只做进度条标记，不参与自动跳过。
     */
    POI_HIGHLIGHT("poi_highlight", "精彩时刻(仅标记)", 0xFFFF1684, true),
    ;

    companion object {
        /** 按服务端类别码解析，未知类别返回 null。 */
        fun fromCode(code: String): SponsorBlockCategory? = entries.firstOrNull { it.code == code }

        /** 默认启用的类别码列表。 */
        fun defaultCodes(): List<String> = entries.filter { it.defaultEnabled }.map { it.code }
    }
}

/**
 * 已解析的空降片段（领域模型）。
 *
 * @param uuid 片段唯一 ID（用于跳过去重）
 * @param category 类别
 * @param startSec 起始秒
 * @param endSec 结束秒
 * @param skippable 是否参与自动跳过（poi_highlight 仅做标记）
 */
data class SponsorBlockSegment(
    val uuid: String,
    val category: SponsorBlockCategory,
    val startSec: Double,
    val endSec: Double,
    val skippable: Boolean,
) {
    /** 起始毫秒。 */
    val startMs: Long get() = (startSec * 1000).toLong()

    /** 结束毫秒。 */
    val endMs: Long get() = (endSec * 1000).toLong()

    /** 片段时长（秒）。 */
    val durationSec: Double get() = endSec - startSec
}

/**
 * 空降片段启用类别解析。
 *
 * @param raw Prefs 中的逗号分隔类别码字符串（空 = 默认类别）。
 * @return 启用的类别集合。
 */
fun parseSponsorBlockCategories(raw: String): Set<SponsorBlockCategory> {
    if (raw.isBlank()) {
        return SponsorBlockCategory.entries.filter { it.defaultEnabled }.toSet()
    }
    return raw
        .split(",")
        .mapNotNull { SponsorBlockCategory.fromCode(it.trim()) }
        .toSet()
}

/**
 * 过滤出当前偏好下参与自动跳过的片段（按播放位置查找用）。
 */
fun List<SponsorBlockSegment>.skippableFor(prefCategoriesRaw: String): List<SponsorBlockSegment> {
    val enabled = parseSponsorBlockCategories(prefCategoriesRaw)
    return filter { it.skippable && it.category in enabled }
}

/**
 * 过滤出当前偏好下需要绘制进度条标记的片段。
 */
fun List<SponsorBlockSegment>.markersFor(prefCategoriesRaw: String): List<SponsorBlockSegment> {
    val enabled = parseSponsorBlockCategories(prefCategoriesRaw)
    return filter { it.category in enabled }
}

/**
 * bsbsb.top `/api/skipSegments` 片段 DTO。
 *
 * 响应示例：
 * ```json
 * [{"cid":"42789702998","category":"sponsor","actionType":"skip",
 *   "segment":[336.209,416.439],"UUID":"...","videoDuration":997,
 *   "locked":0,"votes":0,"description":""}]
 * ```
 */
@Serializable
data class SponsorBlockSegmentDto(
    /** 分 P 的 CID（字符串形式）；"0" 视为整支视频通用。 */
    val cid: String = "0",
    val category: String = "",
    val actionType: String = "skip",
    /** [起始秒, 结束秒]。 */
    val segment: List<Double> = emptyList(),
    @SerialName("UUID") val uuid: String = "",
    val videoDuration: Double = 0.0,
    val votes: Int = 0,
    val description: String = "",
) {
    /**
     * 转为领域模型；无法解析（类别未知/区间不完整/非 skip|poi 动作）时返回 null。
     */
    fun toSegment(): SponsorBlockSegment? {
        if (segment.size < 2) return null
        val category = SponsorBlockCategory.fromCode(category) ?: return null
        val skippable = actionType.equals("skip", ignoreCase = true) && category != SponsorBlockCategory.POI_HIGHLIGHT
        val start = segment[0]
        val end = segment[1]
        if (end <= start) return null
        return SponsorBlockSegment(
            uuid = uuid,
            category = category,
            startSec = start,
            endSec = end,
            skippable = skippable,
        )
    }
}
