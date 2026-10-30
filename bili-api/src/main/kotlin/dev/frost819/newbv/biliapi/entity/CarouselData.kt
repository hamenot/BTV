package dev.frost819.newbv.biliapi.entity

import dev.frost819.newbv.biliapi.util.UrlUtil
import dev.frost819.newbv.biliapi.util.toBv
import io.ktor.http.Url

data class CarouselData(
    val items: List<CarouselItem>,
) {
    companion object {
        /**
         * PGC 首页横幅（banner）转换。
         *
         * B 站对不同分区下发了两种 banner schema：
         * - `web_banner_v3`（番剧/国创）：条目内直接带 `evaluate`（简介）、
         *   `rating`（评分）、`stat.view`（播放量）以及 `season_id`/`episode_id`。
         * - `web_banner_v2`（电影/电视剧/综艺/纪录片）：条目内**没有**上述字段，
         *   只有 `sub_title`（宣传语）、`hover.text`（角标）与 `item_id`，
         *   `season_id`/`episode_id` 也可能缺失，只能从 `link` 里解析。
         *
         * 因此这里的 `evaluate` 兜底顺序为：`evaluate` → `sub_title` → 角标拼接。
         * 真实的剧情简介/评分/播放量由 [dev.frost819.newbv.biliapi.repositories.PgcRepository]
         * 额外请求 `/pgc/view/web/season` 回填。
         *
         * `link` 支持三种形态：
         * - `/bangumi/play/ep{epId}`：按剧集 id 跳转
         * - `/bangumi/play/ss{seasonId}`：按季 id 跳转
         * - `/bangumi/list/sl{seriesId}`、`/blackboard/era/...`：合集/活动页，
         *   无法映射到季或剧集，跳转后必然是空页面，故直接过滤掉。
         */
        fun fromPgcWebInitialStateData(
            data: dev.frost819.newbv.biliapi.http.entity.pgc.PgcWebInitialStateData,
        ): CarouselData {
            val result = mutableListOf<CarouselItem>()
            data.modules.banner.items.forEach {
                var cover = it.bigCover ?: it.cover
                if (cover.startsWith("//")) cover = "https:$cover"

                // 链接是协议相对的（//www.bilibili.com/bangumi/play/ss1588），
                // 先补全协议再取末段，避免 Ktor 的 Url 解析因缺 host 抛异常。
                val link = if (it.link.startsWith("//")) "https:${it.link}" else it.link
                val idStr = link.substringBefore('?').substringAfterLast('/')

                // 番剧/国创的轮播图不再下发 episode_id，只能从链接里取；
                // 电影/电视剧/综艺的链接里是 ss{seasonId} 或 ep{epId}。
                val epidFromUrl = idStr.removePrefix("ep").toIntOrNull()?.takeIf { idStr.startsWith("ep") }
                val ssidFromUrl = idStr.removePrefix("ss").toIntOrNull()?.takeIf { idStr.startsWith("ss") }

                val episodeId = it.episodeId ?: epidFromUrl
                val seasonId = it.seasonId ?: ssidFromUrl
                // 既没有 id 也解析不出 id 的条目（合集页 / 活动页）没法跳转，
                // 展示出来点进去必然是空页面，直接跳过。
                if (episodeId == null && seasonId == null) return@forEach

                val hover = it.hover
                val hoverTags = hover?.text
                val tags = hoverTags.orEmpty().filter { tag -> tag.isNotBlank() }

                result.add(
                    CarouselItem(
                        cover = cover,
                        title = it.title,
                        seasonId = seasonId,
                        episodeId = episodeId,
                        rating = it.rating?.takeIf { score -> score.isNotBlank() },
                        // v3 schema 直接带真实简介；v2 schema 没有，需要后续用季详情接口回填。
                        evaluate = it.evaluate?.takeIf { text -> text.isNotBlank() },
                        // v2 schema 的宣传语，作为简介的兜底文案。
                        subtitle = it.subTitle?.takeIf { text -> text.isNotBlank() },
                        viewCount = it.stat?.view,
                        tags = tags,
                    ),
                )
            }
            return CarouselData(result)
        }

        fun fromUgcRegionDynamicBanner(
            data: dev.frost819.newbv.biliapi.http.entity.region.RegionDynamic.Banner,
        ): CarouselData {
            val result = mutableListOf<CarouselItem>()
            data.top.forEach { top ->
                if (!UrlUtil.isVideoUrl(top.uri)) return@forEach
                val avid = UrlUtil.parseAidFromUrl(top.uri)
                val bvid = avid.toBv()
                result.add(
                    CarouselItem(
                        cover = top.image,
                        title = top.title,
                        avid = avid,
                        bvid = bvid,
                    ),
                )
            }
            return CarouselData(result)
        }

        fun fromUgcRegionLocs(data: dev.frost819.newbv.biliapi.http.entity.region.RegionLocs): CarouselData {
            val result = mutableListOf<CarouselItem>()
            data.data.forEach { (_, value) ->
                value?.filter { it.url.contains("/video/") }?.forEach { item ->
                    result.add(
                        CarouselItem(
                            cover = item.pic,
                            title = item.title,
                            bvid = Url(item.url).rawSegments.last(),
                        ),
                    )
                }
            }
            return CarouselData(result)
        }
    }

    data class CarouselItem(
        val cover: String,
        val title: String,
        /** 季 id。为 null 表示只有剧集 id（v2 schema 常见）。 */
        val seasonId: Int? = null,
        /** 剧集 id。为 null 表示只有季 id。 */
        val episodeId: Int? = null,
        val avid: Long? = null,
        val bvid: String? = null,
        /** 评分（番剧页 banner 下发）。 */
        val rating: String? = null,
        /** 剧情简介。 */
        val evaluate: String? = null,
        /** 宣传语（v2 schema 下发的副标题，简介缺失时作为兜底文案）。 */
        val subtitle: String? = null,
        /** 播放量。 */
        val viewCount: Long? = null,
        /** 弹幕数。 */
        val danmakuCount: Int? = null,
        /** 角标标签（如「真人秀」「全12集」）。 */
        val tags: List<String> = emptyList(),
    )
}
