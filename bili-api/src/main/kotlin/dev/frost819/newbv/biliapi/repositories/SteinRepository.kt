package dev.frost819.newbv.biliapi.repositories

import dev.frost819.newbv.biliapi.entity.stein.SteinEdge
import dev.frost819.newbv.biliapi.entity.stein.SteinEdgeChoice
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.biliapi.http.entity.stein.SteinEdgeInfoData
import dev.frost819.newbv.biliapi.http.entity.video.SteinInteraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 互动视频（Stein Gate）仓库。
 *
 * 提供互动视频的检测（/x/player/v2 的 interaction 字段）与
 * 节点信息查询（/x/stein/edgeinfo_v2）。
 *
 * 互动视频由一棵剧情树构成：每个模块对应同一 aid 下的一个 cid 分段，
 * 选项指向下一模块（choiceId）与下一分段（cid）。
 */
class SteinRepository {
    /**
     * 查询视频的互动信息，用于判定视频是否为互动视频并获取剧情图 id。
     *
     * 端点：GET /x/player/v2，无需登录。
     *
     * @param aid 视频 AV 号
     * @param cid 分 P CID
     * @return 互动信息；非互动视频、请求失败或未登录时返回 null（不抛异常，
     * 调用方应将 null 视为「非互动视频」并保持普通播放流程）
     */
    suspend fun getInteraction(
        aid: Long,
        cid: Long,
    ): SteinInteraction? =
        withContext(Dispatchers.IO) {
            runCatching {
                BiliHttpApi
                    .getPlayerV2Info(aid = aid, cid = cid)
                    .getResponseData()
                    .interaction
            }.getOrNull()
        }

    /**
     * 查询互动视频当前模块（节点）的信息。
     *
     * 端点：GET /x/stein/edgeinfo_v2，需要 Cookie（SESSDATA），
     * 未登录时抛出 [dev.frost819.newbv.biliapi.http.entity.AuthFailureException]。
     *
     * 响应中的多个问题/选项会展平为单一选项列表，并过滤隐藏选项
     * （is_hidden = 1）与非法选项（无 cid 或无文案）；依赖隐藏变量的
     * 条件语句（condition）不做求值，对应选项会照常展示。
     *
     * @param aid 视频 AV 号
     * @param graphVersion 剧情图 id（来自 [getInteraction]）
     * @param edgeId 目标模块 id；0 表示起始模块
     * @return 规范化的节点信息
     * @throws IllegalStateException 接口返回非 0 错误码（如 99003 剧情图已失效）
     */
    suspend fun getEdgeInfo(
        aid: Long,
        graphVersion: Long,
        edgeId: Long = 0,
    ): SteinEdge =
        withContext(Dispatchers.IO) {
            val data: SteinEdgeInfoData =
                BiliHttpApi
                    .getSteinEdgeInfo(aid = aid, graphVersion = graphVersion, edgeId = edgeId)
                    .getResponseData()
            val questions = data.edges?.questions.orEmpty()
            SteinEdge(
                edgeId = data.edgeId,
                title = data.title.orEmpty(),
                isLeaf = data.isLeaf == 1,
                pauseVideo = questions.any { it.pauseVideo == 1 },
                // 回答限时取第一个有效值（-1 表示不限时，0 等占位值忽略）
                durationMs = questions.firstOrNull { it.duration > 0 }?.duration ?: -1L,
                choices =
                    questions
                        .flatMap { it.choices }
                        .asSequence()
                        .filter { it.isHidden != 1 }
                        .filter { it.cid > 0 && it.option.isNotBlank() }
                        .map {
                            SteinEdgeChoice(
                                choiceId = it.id,
                                cid = it.cid,
                                text = it.option.trim(),
                                x = it.x,
                                y = it.y,
                                isDefault = it.isDefault == 1,
                            )
                        }.toList(),
            )
        }
}
