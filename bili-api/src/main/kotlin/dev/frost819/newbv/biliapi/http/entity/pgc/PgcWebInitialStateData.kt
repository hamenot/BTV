package dev.frost819.newbv.biliapi.http.entity.pgc

import dev.frost819.newbv.biliapi.http.entity.web.Hover
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * PGC 首页 ssr 数据
 */
@Serializable
data class PgcWebInitialStateData(
    val modules: Modules,
) {
    /**
     * @param banner 轮播图
     * @param index 索引
     * @param ext 时间表
     */
    @Suppress("KDocUnresolvedReference")
    @Serializable
    data class Modules(
        val banner: Banner,
        // val index: Index,
        // val ext:Ext,
    ) {
        @Serializable
        data class Banner(
            val title: String,
            val spmid: String,
            val size: Int,
            val style: String,
            val headers: JsonArray,
            val items: List<BannerItem>,
            val wids: JsonArray,
            @SerialName("module_id")
            val moduleId: Int,
        ) {
/**
             * PGC 横幅条目。
             *
             * B 站对不同分区下发两种 schema：
             * - `web_banner_v3`（番剧/国创）：带 `evaluate`（简介）、`rating`（评分）、
             *   `stat`（统计）与 `season_id`/`episode_id`。
             * - `web_banner_v2`（电影/电视剧/综艺/纪录片）：不下发上述字段，
             *   只下发 `sub_title`（宣传语）、`hover.text`（角标）与 `item_id`，
             *   `season_id`/`episode_id` 也可能缺失，只能从 `link` 里解析。
             */
            @Serializable
            data class BannerItem(
                val rating: String? = null,
                val title: String,
                val cover: String,
                val link: String,
                val evaluate: String? = null,
                val report: JsonElement? = null,
                val hover: Hover? = null,
                val stat: Stat? = null,
                val values: JsonArray? = null,
                @SerialName("season_id")
                val seasonId: Int? = null,
                @SerialName("season_type")
                val seasonType: Int? = null,
                @SerialName("rating_count")
                val ratingCount: Int? = null,
                @SerialName("episode_id")
                val episodeId: Int? = null,
                @SerialName("big_cover")
                val bigCover: String? = null,
                @SerialName("play_btn")
                val playBtn: Int? = null,
                @SerialName("play_title")
                val playTitle: String? = null,
                @SerialName("rank_id")
                val rankId: Int,
                @SerialName("user_status")
                val userStatus: UserStatus? = null,
                @SerialName("date_ts")
                val dateTs: Int? = null,
                @SerialName("day_of_week")
                val dayOfWeek: Int? = null,
                @SerialName("is_today")
                val isToday: Int? = null,
                @SerialName("is_latest")
                val isLatest: Int? = null,
                val id: String,
                @SerialName("showReportData")
                val showReportData: ShowReportData,
                /** 宣传语/副标题，web_banner_v2 schema 用它替代简介，作为简介缺失时的兜底文案。 */
                @SerialName("sub_title")
                val subTitle: String? = null,
                /** banner 条目 id（新 schema 用它替代 season_id/episode_id 标识条目）。 */
                @SerialName("item_id")
                val itemId: Int? = null,
                /** webp 版封面（与 [cover] 同图不同格式）。 */
                @SerialName("webpcover")
                val webpCover: String? = null,
                /** 角标文本数组，新 schema 下恒为空数组。 */
                @SerialName("text")
                val text: JsonArray? = null,
            ) {
                @Serializable
                data class Stat(
                    val view: Long,
                )

                @Serializable
                data class UserStatus(
                    val follow: Int,
                )

                @Serializable
                data class ShowReportData(
                    @SerialName("module_type")
                    val moduleType: String,
                    @SerialName("module_id")
                    val moduleId: Int,
                    @SerialName("ep_id")
                    val epId: Int? = null,
                    @SerialName("season_id")
                    val seasonId: Int? = null,
                )
            }
        }
    }
}
