package dev.frost819.newbv.biliapi.entity.pgc

import dev.frost819.newbv.biliapi.http.SeasonIndexType

data class PgcItem(
    var cover: String,
    var title: String,
    var subTitle: String,
    var seasonId: Int,
    var episodeId: Int,
    var seasonType: SeasonIndexType,
    var rating: String,
) {
    companion object {
        fun fromFeedSubItem(feedSubItem: dev.frost819.newbv.biliapi.http.entity.pgc.PgcFeedData.FeedSubItem): PgcItem =
            PgcItem(
                cover = feedSubItem.cover,
                title = feedSubItem.title,
                subTitle = feedSubItem.subTitle,
                // 字段可能缺失：缺 season_id 的条目仍要能显示，交给详情页兜底处理。
                seasonId = feedSubItem.seasonId ?: -1,
                episodeId = feedSubItem.episodeId,
                seasonType = SeasonIndexType.fromId(feedSubItem.seasonType ?: 0),
                rating = feedSubItem.rating ?: "0",
            )

        fun fromFeedSubItem(
            feedSubItem: dev.frost819.newbv.biliapi.http.entity.pgc.PgcFeedV3Data.FeedItem.FeedSubItem,
        ): PgcItem =
            PgcItem(
                cover = feedSubItem.cover,
                title = feedSubItem.title,
                subTitle = feedSubItem.subTitle,
                seasonId = feedSubItem.seasonId ?: -1,
                // episode_id 可能不下发，退回 inline.epId；两者都缺时给 -1 而不是抛异常。
                episodeId = feedSubItem.episodeId ?: feedSubItem.inline?.epId ?: -1,
                seasonType = SeasonIndexType.fromId(feedSubItem.seasonType ?: 0),
                rating = feedSubItem.rating ?: "0",
            )

        fun fromIndexResultItem(
            indexResultItem: dev.frost819.newbv.biliapi.http.entity.index.IndexResultData.IndexResultItem,
        ): PgcItem =
            PgcItem(
                cover = indexResultItem.cover,
                title = indexResultItem.title,
                subTitle = indexResultItem.subTitle,
                seasonId = indexResultItem.seasonId,
                episodeId = indexResultItem.firstEp.epId,
                seasonType = SeasonIndexType.fromId(indexResultItem.seasonType),
                rating = indexResultItem.score,
            )
    }
}
