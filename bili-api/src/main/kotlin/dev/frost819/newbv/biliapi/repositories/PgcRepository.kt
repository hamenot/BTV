package dev.frost819.newbv.biliapi.repositories

import dev.frost819.newbv.biliapi.entity.CarouselData
import dev.frost819.newbv.biliapi.entity.pgc.PgcFeedData
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import dev.frost819.newbv.biliapi.entity.pgc.index.Area
import dev.frost819.newbv.biliapi.entity.pgc.index.Copyright
import dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrder
import dev.frost819.newbv.biliapi.entity.pgc.index.IndexOrderType
import dev.frost819.newbv.biliapi.entity.pgc.index.IsFinish
import dev.frost819.newbv.biliapi.entity.pgc.index.PgcIndexData
import dev.frost819.newbv.biliapi.entity.pgc.index.Producer
import dev.frost819.newbv.biliapi.entity.pgc.index.ReleaseDate
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonMonth
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonStatus
import dev.frost819.newbv.biliapi.entity.pgc.index.SeasonVersion
import dev.frost819.newbv.biliapi.entity.pgc.index.SpokenLanguage
import dev.frost819.newbv.biliapi.entity.pgc.index.Style
import dev.frost819.newbv.biliapi.entity.pgc.index.Year
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class PgcRepository {
    /**
     * 横幅详情补全的并发上限。
     *
     * 轮播条目通常 5~10 个，但同一季可能被多条 banner 引用；
     * 不设上限时一次 `getCarousel` 会同时打出全部请求，
     * 瞬时并发过高是 B 站风控的直接触发条件之一。
     */
    private val enrichSemaphore = Semaphore(4)

    suspend fun getCarousel(pgcType: PgcType): CarouselData {
        val initialStateData = BiliHttpApi.getPgcWebInitialStateData(pgcType)
        val carouselData = CarouselData.fromPgcWebInitialStateData(initialStateData)
        // 电影/电视剧/综艺/纪录片下发的是 web_banner_v2 schema，条目里没有简介、
        // 评分与播放量，需要用季/剧集 id 额外请求补齐（限流并发，避免瞬时请求风暴）。
        val enriched =
            coroutineScope {
                carouselData.items
                    .map { item -> async { item.enrichWithSeasonDetail() } }
                    .awaitAll()
            }
        return carouselData.copy(items = enriched)
    }

    /**
     * 用季详情接口补齐横幅条目缺失的简介、评分、播放量与弹幕数。
     *
     * 番剧/国创的 banner（web_banner_v3 schema）本身就带这些字段，无需额外请求；
     * 电影/电视剧/综艺/纪录片（web_banner_v2 schema）则必须回填。
     * 单个条目请求失败时保留原数据，不影响其它条目。
     */
    private suspend fun CarouselData.CarouselItem.enrichWithSeasonDetail(): CarouselData.CarouselItem {
        // 已有完整信息就不再发请求。
        if (evaluate != null && rating != null && viewCount != null) return this
        val seasonId = this.seasonId
        val episodeId = this.episodeId
        if (seasonId == null && episodeId == null) return this

        return runCatching {
            enrichSemaphore.withPermit {
                BiliHttpApi
                    .getWebSeasonInfo(
                        seasonId = seasonId,
                        epId = episodeId,
                    ).getResponseData()
            }
        }.getOrNull()?.let { detail ->
            val newRating =
                rating
                    ?: detail.rating
                        ?.score
                        ?.takeIf { score -> score > 0f }
                        ?.let { score -> String.format("%.1f", score) }
            copy(
                evaluate = evaluate ?: detail.evaluate.takeIf { text -> text.isNotBlank() },
                rating = newRating,
                viewCount = viewCount ?: detail.stat.views,
                danmakuCount = danmakuCount ?: detail.stat.danmakus,
                // 电影/综艺的 banner 只有剧集 id，回填后补上季 id，
                // 让详情页能同时拿到整季分集列表。
                seasonId = this.seasonId ?: detail.seasonId.takeIf { id -> id > 0 },
            )
        } ?: this
    }

    suspend fun getFeed(
        pgcType: PgcType,
        cursor: Int,
    ): PgcFeedData {
        val data =
            when (pgcType) {
                PgcType.Anime, PgcType.GuoChuang ->
                    PgcFeedData.fromPgcFeedData(
                        BiliHttpApi
                            .getPgcFeedV3(
                                name = pgcType.name.lowercase(),
                                cursor = cursor,
                            ).getResponseData(),
                    )

                PgcType.Movie, PgcType.Tv, PgcType.Documentary, PgcType.Variety ->
                    PgcFeedData.fromPgcFeedData(
                        BiliHttpApi
                            .getPgcFeed(
                                name = pgcType.name.lowercase(),
                                cursor = cursor,
                            ).getResponseData(),
                    )
            }
        return data
    }

    suspend fun getPgcIndex(
        pgcType: PgcType,
        indexOrder: IndexOrder,
        indexOrderType: IndexOrderType,
        seasonVersion: SeasonVersion,
        spokenLanguage: SpokenLanguage,
        area: Area,
        isFinish: IsFinish,
        copyright: Copyright,
        seasonStatus: SeasonStatus,
        seasonMonth: SeasonMonth,
        producer: Producer,
        year: Year,
        releaseDate: ReleaseDate,
        style: Style,
        page: PgcIndexData.PgcIndexPage,
    ): PgcIndexData {
        val data =
            PgcIndexData.fromIndexResultData(
                when (pgcType) {
                    PgcType.Anime ->
                        BiliHttpApi.seasonIndexAnimeResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            seasonVersion = seasonVersion.id,
                            spokenLanguageType = spokenLanguage.id,
                            area = area.id,
                            isFinish = isFinish.id,
                            copyright = copyright.id,
                            seasonStatus = seasonStatus.id,
                            seasonMonth = seasonMonth.id,
                            year = year.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.GuoChuang ->
                        BiliHttpApi.seasonIndexGuochuangResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            seasonVersion = seasonVersion.id,
                            isFinish = isFinish.id,
                            copyright = copyright.id,
                            seasonStatus = seasonStatus.id,
                            year = year.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.Movie ->
                        BiliHttpApi.seasonIndexMovieResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            area = area.id,
                            seasonStatus = seasonStatus.id,
                            releaseDate = releaseDate.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.Documentary ->
                        BiliHttpApi.seasonIndexDocumentaryResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            area = area.id,
                            seasonStatus = seasonStatus.id,
                            producerId = producer.id,
                            releaseDate = releaseDate.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.Tv ->
                        BiliHttpApi.seasonIndexTvResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            area = area.id,
                            seasonStatus = seasonStatus.id,
                            releaseDate = releaseDate.str,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )

                    PgcType.Variety ->
                        BiliHttpApi.seasonIndexVarietyResult(
                            order = indexOrder.id,
                            sort = indexOrderType.id,
                            seasonStatus = seasonStatus.id,
                            styleId = style.id,
                            page = page.nextPage,
                            pagesize = page.pageSize,
                        )
                }.getResponseData(),
            )
        return data
    }
}
