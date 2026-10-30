package dev.frost819.newbv.biliapi.http.entity.video

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 播放器 v2 信息（`/x/player/v2` 响应 data 的互动视频相关字段）。
 *
 * 仅解析互动视频（Stein Gate）需要的 `interaction` 对象，其余字段忽略。
 * 普通视频的响应中 `interaction` 为 null / 缺失，可据此判定视频是否为互动视频。
 *
 * @param interaction 互动视频信息；非互动视频为 null
 */
@Serializable
data class PlayerV2Data(
    val interaction: SteinInteraction? = null,
)

/**
 * 互动视频信息。
 *
 * @param graphVersion 剧情图 id，请求 `/x/stein/edgeinfo_v2` 时必需
 * @param steinGuideCid 互动视频引导 cid（起始分 P cid）
 */
@Serializable
data class SteinInteraction(
    @SerialName("graph_version") val graphVersion: Long = 0,
    @SerialName("stein_guide_cid") val steinGuideCid: Long = 0,
)
