package dev.frost819.newbv.biliapi.http.entity.stein

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 互动视频节点信息（`/x/stein/edgeinfo_v2` 响应 data）。
 *
 * 互动视频（Stein Gate）由一棵剧情树构成：每个模块（节点）对应同一 aid 下的一个 cid 分段，
 * 模块内可包含问题（questions），问题下的选项（choices）指向下一模块（choice.id）与
 * 下一分段（choice.cid）。
 *
 * @param title 模块（分 P）标题
 * @param edgeId 当前模块 id
 * @param storyList 进度回溯信息（未登录仅有起始模块）
 * @param edges 当前模块信息（问题与选项）；结束模块无 questions
 * @param isLeaf 是否为结束模块（1 = 结束模块）
 * @param preload 预加载的分 P（当前所有选项的跳转视频）
 */
@Serializable
data class SteinEdgeInfoData(
    val title: String? = null,
    @SerialName("edge_id") val edgeId: Long = 0,
    @SerialName("story_list") val storyList: List<SteinStoryNode> = emptyList(),
    val edges: SteinEdges? = null,
    @SerialName("is_leaf") val isLeaf: Int = 0,
    val preload: SteinPreload? = null,
)

/**
 * 进度回溯节点。
 *
 * @param edgeId 模块 id
 * @param title 模块标题
 * @param cid 模块 cid
 * @param startPos 记录的播放开始位置（毫秒）
 * @param isCurrent 是否为当前模块（1 = 是）
 */
@Serializable
data class SteinStoryNode(
    @SerialName("edge_id") val edgeId: Long = 0,
    val title: String? = null,
    val cid: Long = 0,
    @SerialName("start_pos") val startPos: Long = 0,
    @SerialName("is_current") val isCurrent: Int = 0,
)

/**
 * 当前模块信息。
 *
 * @param questions 问题列表；结束模块为空
 */
@Serializable
data class SteinEdges(
    val questions: List<SteinQuestion> = emptyList(),
)

/**
 * 互动问题。
 *
 * @param type 选项显示模式（0 不显示选项 / 1 底部选项模式 / 2 坐标定点模式）
 * @param durationMs 回答限时（毫秒），-1 表示不限时
 * @param pauseVideo 弹出选项时是否暂停播放（1 = 暂停）
 * @param choices 选项列表
 */
@Serializable
data class SteinQuestion(
    val type: Int = 0,
    val duration: Long = -1,
    @SerialName("pause_video") val pauseVideo: Int = 0,
    val choices: List<SteinChoice> = emptyList(),
)

/**
 * 互动选项。
 *
 * @param choiceId 选项 id，同时是所跳转的下一模块 id（edge_id）
 * @param cid 选项所跳转分 P 的 cid
 * @param option 选项文字
 * @param x 选项出现的 x 坐标（仅坐标定点模式）
 * @param y 选项出现的 y 坐标（仅坐标定点模式）
 * @param isDefault 是否为默认选项（1 = 是）
 * @param isHidden 是否为隐藏选项（1 = 是）
 * @param condition 选项出现条件判断语句（依赖隐藏变量，本客户端不做条件求值）
 * @param platformAction 跳转信息文字（`JUMP {模块id} {cid}`）
 */
@Serializable
data class SteinChoice(
    val id: Long = 0,
    val cid: Long = 0,
    val option: String = "",
    val x: Double? = null,
    val y: Double? = null,
    @SerialName("is_default") val isDefault: Int = 0,
    @SerialName("is_hidden") val isHidden: Int = 0,
    val condition: String? = null,
    @SerialName("platform_action") val platformAction: String? = null,
)

/**
 * 预加载信息。
 *
 * @param video 预加载的分 P 列表（当前所有选项的跳转视频）
 */
@Serializable
data class SteinPreload(
    val video: List<SteinPreloadVideo> = emptyList(),
)

/**
 * 预加载分 P。
 *
 * @param aid 稿件 avid
 * @param cid 分 P cid
 */
@Serializable
data class SteinPreloadVideo(
    val aid: Long = 0,
    val cid: Long = 0,
)
