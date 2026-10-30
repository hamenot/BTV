package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.PgcCarousel
import dev.frost819.newbv.app.ui.component.TopNav
import dev.frost819.newbv.app.ui.component.TopNavItem
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.videocard.SeasonCard
import dev.frost819.newbv.app.ui.component.videocard.SeasonCardData
import dev.frost819.newbv.app.ui.navigation.PgcFeatureRoute
import dev.frost819.newbv.app.viewmodel.pgc.PgcViewModel
import dev.frost819.newbv.biliapi.entity.CarouselData
import dev.frost819.newbv.biliapi.entity.pgc.PgcItem
import dev.frost819.newbv.biliapi.entity.pgc.PgcType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import androidx.compose.material3.Scaffold as Material3Scaffold

/**
 * PGC 影视顶部导航项。
 *
 * 对应 6 个 PGC 分区。
 *
 * @property pgcType 对应的 PGC 分区类型。
 */
enum class PgcTabItem(
    val pgcType: PgcType,
    override val displayName: String,
) : TopNavItem {
    Anime(PgcType.Anime, "番剧"),
    GuoChuang(PgcType.GuoChuang, "国创"),
    Movie(PgcType.Movie, "电影"),
    Documentary(PgcType.Documentary, "纪录片"),
    Tv(PgcType.Tv, "电视剧"),
    Variety(PgcType.Variety, "综艺"),
}

/**
 * PGC 影视内容（TopNav + 通栏轮播图 + 卡片网格）。
 *
 * 顶部 Tab 切换分区，内容区上方为轮播图，下方为番剧/影视卡片网格 + 无限滚动。
 * 菜单键刷新当前分区数据。
 *
 * @param navFocusRequester 顶部 Tab 的焦点请求器。
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param onNavToRail 顶栏按左键的去向（聚焦拥有此顶栏的轨道条目，由 MainScreen 传入）。
 * @param viewModel PGC ViewModel。
 */
@Composable
fun PgcContent(
    navFocusRequester: FocusRequester,
    navController: NavController,
    focusSaver: FocusSaver,
    onNavToRail: () -> Boolean = { false },
    viewModel: PgcViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(PgcTabItem.Anime) }
    var focusOnContent by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 顶栏按下方向键的落点：显式指定，否则空间搜索会把焦点送进左侧轨道。
    val contentEntryRequester = remember { FocusRequester() }

    Material3Scaffold(
        topBar = {
            TopNav(
                modifier = Modifier.focusRequester(navFocusRequester),
                items = PgcTabItem.entries.toList(),
                selectedIndex = PgcTabItem.entries.indexOf(selectedTab),
                isLargePadding = !focusOnContent,
                onSelectedChanged = { nav ->
                    val tab = nav as PgcTabItem
                    if (tab != selectedTab) {
                        selectedTab = tab
                        viewModel.switchType(tab.pgcType)
                    }
                },
                onClick = { nav ->
                    val tab = nav as PgcTabItem
                    viewModel.refresh()
                },
                onNavigateDown = {
                    runCatching { contentEntryRequester.requestFocus() }.isSuccess
                },
                onNavigateLeft = onNavToRail,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    // 作为一组装配：requestFocus 会落到组内第一个可聚焦项（信息流首卡）。
                    .focusRequester(contentEntryRequester)
                    .focusGroup()
                    .onFocusChanged { focusOnContent = it.hasFocus }
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                            viewModel.refresh()
                            navFocusRequester.requestFocus()
                            return@onPreviewKeyEvent true
                        }
                        false
                    },
        ) {
            PgcGrid(
                viewModel = viewModel,
                navController = navController,
                focusSaver = focusSaver,
            )
        }
    }
}

/** 轮播图在 [FocusSaver] 中登记的焦点 key。 */
private const val CAROUSEL_FOCUS_KEY = "pgc_carousel"

/**
 * 信息流卡片在 [FocusSaver] 中登记的焦点 key。
 *
 * 横幅按下的落点要用它取首张卡片的 [FocusRequester]，两边必须同一个 key。
 */
private fun itemFocusKey(index: Int): String = "pgc_item_$index"

/** 横幅按下后，把焦点护送到首张卡片的重试间隔（毫秒）。 */
private const val CAROUSEL_DOWN_FOCUS_INTERVAL_MILLIS = 120L

/** 横幅按下后，把焦点护送到首张卡片的重试次数。 */
private const val CAROUSEL_DOWN_FOCUS_ATTEMPTS = 5

/** PGC 页面的布局尺寸。 */
private object PgcLayoutDefaults {
    /**
     * 轮播图的宽高比（宽 / 高）。
     *
     * 对齐 B 站影视页的版式：Hero 是通栏大图，横向铺满内容区，纵向吃掉约 5/6 个
     * 信息流视口，只在底部留出一条缝露出首行卡片，作为"下面还有内容"的提示。
     *
     * 2.25 是按 16:9 TV 反推的：内容区宽 ≈ 屏宽 - 轨道 56dp - 网格内边距 32dp，
     * 取 2.25 后横幅底边落在屏幕约 84% 处，与参考图的观感一致。
     *
     * 用宽高比而不是"屏幕高度的比例"：部分盒子/模拟器上报的 `screenHeightDp`
     * 与实际渲染密度不一致（实测有 617dp vs 真实 540dp 的情况），按屏幕高度取比例
     * 会算出明显偏大的横幅；宽高比只依赖真实布局约束，不受这个偏差影响。
     */
    const val CarouselAspectRatio = 2.25f

    /** 信息流卡片的缩放比例（原尺寸偏大，整体收到 0.8 倍）。 */
    const val CardScale = 0.8f
}

/**
 * PGC 番剧网格（含轮播图）。
 *
 * 第一行为通栏轮播图，后续为番剧卡片网格 + 无限滚动。
 * 距离底部 10 条时触发加载更多。
 */
@Composable
private fun PgcGrid(
    viewModel: PgcViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // 横幅按下的落点：显式指定首张卡片。
    // 横幅是通栏大图，下方 5 张卡片水平全部与它重叠，交给默认空间搜索会按"中心距离"
    // 挑一个，落哪张不确定（实测常落在中间那张）。这里直接接管下方向。
    val firstCardRequester = focusSaver.focusRequesterFor(itemFocusKey(0))
    val scope = rememberCoroutineScope()

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.distinctUntilChanged()
            .filter { index ->
                index != null && index >= state.items.size - 10
            }.collect {
                viewModel.loadMore()
            }
    }

    TvLazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 轮播图：通栏铺满内容区宽度，高度按宽高比算出（见 PgcLayoutDefaults）。
        // 加载中保留占位避免内容跳动；
        // 加载完却没有数据时必须把占位收掉——否则顶部会留一大块空白，
        // 把下面的番剧卡片整体顶出屏幕，看起来就像"信息流加载失败"。
        item(span = { GridItemSpan(maxLineSpan) }) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val carouselHeight = maxWidth / PgcLayoutDefaults.CarouselAspectRatio

                when {
                    state.carouselItems.isNotEmpty() -> {
                        PgcCarousel(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(carouselHeight)
                                    // 接管下方向：把焦点送到首张卡片。
                                    // 必须反复请求而不是只请求一次——请求会触发
                                    // bringIntoView 滚动，滚动过程中卡片会被懒列表
                                    // 回收再挂上，一次性请求很容易把焦点丢在半路。
                                    // 已经落在目标上时的重复请求是幂等的，不会抖。
                                    .onPreviewKeyEvent { event ->
                                        if (event.key == Key.DirectionDown &&
                                            event.type == KeyEventType.KeyDown
                                        ) {
                                            scope.launch {
                                                repeat(CAROUSEL_DOWN_FOCUS_ATTEMPTS) {
                                                    runCatching { firstCardRequester.requestFocus() }
                                                    delay(CAROUSEL_DOWN_FOCUS_INTERVAL_MILLIS)
                                                }
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }.focusSaverItem(focusSaver, CAROUSEL_FOCUS_KEY),
                            data = state.carouselItems,
                            onClick = { item -> navigateToCarouselItem(navController, item) },
                        )
                    }
                    state.carouselLoading -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(carouselHeight),
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = state.items,
            key = { index, _ -> index },
        ) { index, item ->
            val cardData =
                remember(item) {
                    SeasonCardData(
                        seasonId = item.seasonId,
                        title = item.title,
                        subTitle = item.subTitle,
                        cover = item.cover,
                        rating = item.rating,
                    )
                }
            SeasonCard(
                data = cardData,
                onClick = { navigateToPgcItem(navController, item) },
                onGoToDetailPage = { navigateToPgcItem(navController, item) },
                // 卡片整体缩到 0.8 倍：列宽变窄后封面已自动变小，
                // 这里让评分字号、内边距等固定尺寸同步等比缩放。
                scale = PgcLayoutDefaults.CardScale,
                modifier = Modifier.focusSaverItem(focusSaver, itemFocusKey(index)),
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ListFooterTip(
                isLoading = state.loading,
                isError = state.error,
                hasMore = state.hasMore,
                itemsIsEmpty = state.items.isEmpty(),
            )
        }
    }
}

/**
 * 跳转横幅条目对应的详情页。
 *
 * 电影/电视剧/综艺/纪录片的横幅（web_banner_v2 schema）常常只有剧集 id
 * （`/bangumi/play/ep{epId}`），没有季 id。若只按季 id 跳转，季 id 缺失时
 * 接口会拿到无效参数并返回空数据，表现为"点进去是一页空白"。
 *
 * 这里优先用季 id，没有则回退到剧集 id——两种 id 都能定位到同一季详情页。
 */
private fun navigateToCarouselItem(
    navController: NavController,
    item: CarouselData.CarouselItem,
) {
    val seasonId = item.seasonId?.takeIf { it > 0 }
    val episodeId = item.episodeId?.takeIf { it > 0 }
    when {
        seasonId != null ->
            navController.navigate(PgcFeatureRoute(seasonId = seasonId.toLong()))

        episodeId != null ->
            navController.navigate(PgcFeatureRoute(epid = episodeId.toLong()))
    }
}

/**
 * 跳转信息流卡片对应的详情页。
 *
 * Feed 条目的 `season_id` 允许缺失（此时为 -1 占位），此时用 `episode_id`
 * 兜底，否则详情页会拿到无效的 season_id 而显示为空。
 */
private fun navigateToPgcItem(
    navController: NavController,
    item: PgcItem,
) {
    val seasonId = item.seasonId.takeIf { it > 0 }
    val episodeId = item.episodeId.takeIf { it > 0 }
    when {
        seasonId != null ->
            navController.navigate(PgcFeatureRoute(seasonId = seasonId.toLong()))

        episodeId != null ->
            navController.navigate(PgcFeatureRoute(epid = episodeId.toLong()))
    }
}
