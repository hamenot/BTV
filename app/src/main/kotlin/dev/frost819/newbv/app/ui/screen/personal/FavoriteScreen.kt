package dev.frost819.newbv.app.ui.screen.personal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.UserSpaceRoute
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.viewmodel.common.CollectWatchLaterEffects
import dev.frost819.newbv.app.viewmodel.common.WatchLaterViewModel
import dev.frost819.newbv.app.viewmodel.personal.PersonalViewModel
import dev.frost819.newbv.core.focus.focusInvertedColors
import dev.frost819.newbv.core.focus.touchClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * 收藏页面。
 *
 * 顶部收藏夹 Tab 栏 + 下方 4 列视频网格。
 * 切换收藏夹时自动加载该夹视频列表，无限滚动加载更多。
 *
 * @param viewModel 个人页 ViewModel。
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 */
@Composable
fun FavoriteScreen(
    modifier: Modifier = Modifier,
    viewModel: PersonalViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val watchLaterViewModel: WatchLaterViewModel = hiltViewModel()

    CollectWatchLaterEffects(watchLaterViewModel)

    if (state.favoriteFolders.isEmpty() && !state.favoriteLoading && !state.favoriteError) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.tv.material3.Text(
                text = "没有收藏夹",
                color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.distinctUntilChanged()
            .filter { index ->
                index != null && index >= state.favoriteItems.size - 20
            }.collect {
                if (state.currentFolderId != -1L) {
                    viewModel.loadFavoriteItems(state.currentFolderId)
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (state.favoriteFolders.isNotEmpty()) {
            // 收藏夹分类芯片行：非 lazy 的 Row + horizontalScroll（与选集弹窗分页行同款）。
            // 不用 LazyRow 的原因：LazyList 的 bringIntoView 不考虑 contentPadding，
            // 聚焦芯片总被 snap 到视口边缘 → 放大描边贴边被裁；且焦点恢复时系统
            // bringIntoView 与主动滚动动画竞争，animateScrollToItem 会被覆盖。
            // 非 lazy 滚动的呼吸 padding 是内容的一部分，滚动范围被内容边界天然限制，
            // 两端永不贴裁切线；获焦时用 BringIntoViewRequester 显式滚入可见。
            val chipScrollState = rememberScrollState()
            val chipScope = rememberCoroutineScope()
            val chipInViewRequesters =
                remember(state.favoriteFolders) {
                    List(state.favoriteFolders.size) { BringIntoViewRequester() }
                }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(chipScrollState)
                        .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.favoriteFolders.forEachIndexed { index, folder ->
                    androidx.tv.material3.Surface(
                        onClick = {
                            viewModel.loadFavoriteItems(folder.id, forceRefresh = true)
                        },
                        modifier =
                            Modifier
                                .bringIntoViewRequester(chipInViewRequesters[index])
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        chipScope.launch {
                                            runCatching { chipInViewRequesters[index].bringIntoView() }
                                        }
                                    }
                                }.touchClickable(onClick = {
                                    viewModel.loadFavoriteItems(folder.id, forceRefresh = true)
                                }),
                        shape =
                            androidx.tv.material3.ClickableSurfaceDefaults.shape(
                                shape =
                                    androidx.compose.foundation.shape
                                        .RoundedCornerShape(50),
                            ),
                        // 聚焦不缩放（与选集弹窗分页芯片一致）：tv Surface 默认聚焦
                        // 放大 1.2 倍，150dp 宽的芯片每侧溢出约 15dp，必然撞上滚动
                        // 视口与屏幕边缘被裁；焦点提示由 focusInvertedColors 反色承担
                        scale =
                            androidx.tv.material3.ClickableSurfaceDefaults.scale(
                                focusedScale = 1f,
                            ),
                        colors =
                            focusInvertedColors(
                                containerColor =
                                    if (state.currentFolderId == folder.id) {
                                        androidx.tv.material3.MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.tv.material3.MaterialTheme.colorScheme.surface
                                    },
                                contentColor =
                                    if (state.currentFolderId == folder.id) {
                                        androidx.tv.material3.MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        androidx.tv.material3.MaterialTheme.colorScheme.onSurface
                                    },
                            ),
                    ) {
                        androidx.tv.material3.Text(
                            text = "${folder.title} (${folder.mediaCount})",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = androidx.tv.material3.MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        TvLazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = state.favoriteItems,
                key = { _, item -> item.id },
            ) { index, item ->
                val cardData =
                    remember(item) {
                        VideoCardData(
                            avid = item.id,
                            title = item.title,
                            cover = item.cover,
                            playString = "",
                            danmakuString = "",
                            timeString = (item.duration * 1000L).formatHourMinSec(),
                            upName = item.upper.name,
                            upMid = item.upper.mid,
                        )
                    }
                SmallVideoCard(
                    modifier = Modifier.focusSaverItem(focusSaver, "favorite_$index"),
                    data = cardData,
                    onClick = {
                        navController.navigateFromVideoCard(cardData)
                    },
                    onGoToDetailPage = {
                        navController.navigateFromVideoCard(cardData, forceDetail = true)
                    },
                    onGoToUpPage = {
                        navController.navigate(UserSpaceRoute(mid = item.upper.mid, name = item.upper.name))
                    },
                    onAddWatchLater = { watchLaterViewModel.addToView(aid = item.id) },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                ListFooterTip(
                    isLoading = state.favoriteLoading,
                    isError = state.favoriteError,
                    hasMore = state.favoriteHasMore,
                    itemsIsEmpty = state.favoriteItems.isEmpty(),
                )
            }
        }
    }
}
