package dev.frost819.newbv.biliapi.entity.stein

/**
 * 互动视频当前节点（模块）的规范化信息。
 *
 * 由 `/x/stein/edgeinfo_v2` 响应归一化而来，供播放器直接消费。
 *
 * @param edgeId 当前模块 id
 * @param title 模块（分 P）标题
 * @param isLeaf 是否为结束模块（结束模块无选项）
 * @param pauseVideo 弹出选项时是否应暂停播放
 * @param durationMs 回答限时（毫秒），-1 表示不限时
 * @param choices 可选的选项列表（已过滤隐藏选项）
 */
data class SteinEdge(
    val edgeId: Long,
    val title: String,
    val isLeaf: Boolean,
    val pauseVideo: Boolean,
    val durationMs: Long,
    val choices: List<SteinEdgeChoice>,
)

/**
 * 互动视频选项。
 *
 * @param choiceId 选项 id，同时是所跳转的下一模块 id（edge_id）
 * @param cid 选项所跳转分 P 的 cid
 * @param text 选项文字
 * @param x 选项出现的 x 坐标（仅坐标定点模式有值）
 * @param y 选项出现的 y 坐标（仅坐标定点模式有值）
 * @param isDefault 是否为默认选项
 */
data class SteinEdgeChoice(
    val choiceId: Long,
    val cid: Long,
    val text: String,
    val x: Double? = null,
    val y: Double? = null,
    val isDefault: Boolean = false,
)
