package dev.frost819.newbv.biliapi.entity.pgc

data class PgcFeedData(
    var hasNext: Boolean,
    var cursor: Int,
    var items: List<PgcItem> = emptyList(),
    var ranks: List<FeedRank> = emptyList(),
) {
    companion object {
        fun fromPgcFeedData(data: dev.frost819.newbv.biliapi.http.entity.pgc.PgcFeedData): PgcFeedData =
            PgcFeedData(
                hasNext = data.hasNext,
                cursor = data.coursor,
                items = data.items.map { PgcItem.fromFeedSubItem(it) },
                ranks = emptyList(),
            )

        fun fromPgcFeedData(data: dev.frost819.newbv.biliapi.http.entity.pgc.PgcFeedV3Data): PgcFeedData {
            // 用 firstOrNull 而不是 first：分组可能没有 sub_items，取第一个会直接抛异常。
            val itemsList = data.items.firstOrNull { it.subItems.firstOrNull()?.cardStyle == "v_card" }
            val ranksList = data.items.firstOrNull { it.subItems.firstOrNull()?.cardStyle == "rank" }
            return PgcFeedData(
                hasNext = data.hasNext,
                cursor = data.coursor,
                // 单条数据异常只丢这一条，不能让整个分区变成空白。
                items =
                    itemsList?.subItems?.mapNotNull { runCatching { PgcItem.fromFeedSubItem(it) }.getOrNull() }
                        ?: emptyList(),
                // 榜单是附加内容，它解析失败绝不能影响主列表。
                ranks =
                    ranksList?.subItems?.mapNotNull { runCatching { FeedRank.fromFeedSubItem(it) }.getOrNull() }
                        ?: emptyList(),
            )
        }
    }

    data class FeedRank(
        var cover: String,
        var title: String,
        var subTitle: String,
        var items: List<PgcItem>,
    ) {
        companion object {
            fun fromFeedSubItem(
                feedSubItem: dev.frost819.newbv.biliapi.http.entity.pgc.PgcFeedV3Data.FeedItem.FeedSubItem,
            ): FeedRank =
                FeedRank(
                    cover = feedSubItem.cover,
                    title = feedSubItem.title,
                    subTitle = feedSubItem.subTitle,
                    items =
                        feedSubItem.subItems?.map { PgcItem.fromFeedSubItem(it) }
                            ?: emptyList(),
                )
        }
    }
}
