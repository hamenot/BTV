package dev.frost819.newbv.app.ui.screen.ugc

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import dev.frost819.newbv.app.ui.component.TopNav
import dev.frost819.newbv.app.ui.component.TopNavEditorDialog
import dev.frost819.newbv.app.ui.component.TopNavItem
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.ugc.UgcViewModel
import dev.frost819.newbv.biliapi.entity.ugc.UgcTypeV2
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.material3.Scaffold as Material3Scaffold

/**
 * UGC 分区顶部导航项。
 *
 * 仅包含一级分区（有 channelId 的 [UgcTypeV2]）。
 *
 * @property ugcTypeV2 对应的 UGC 分区类型。
 */
enum class UgcTabItem(
    val ugcTypeV2: UgcTypeV2,
    override val displayName: String,
) : TopNavItem {
    Douga(UgcTypeV2.Douga, "动画"),
    Game(UgcTypeV2.Game, "游戏"),
    Kichiku(UgcTypeV2.Kichiku, "鬼畜"),
    Music(UgcTypeV2.Music, "音乐"),
    Dance(UgcTypeV2.Dance, "舞蹈"),
    Cinephile(UgcTypeV2.Cinephile, "影视"),
    Ent(UgcTypeV2.Ent, "娱乐"),
    Knowledge(UgcTypeV2.Knowledge, "知识"),
    Tech(UgcTypeV2.Tech, "科技数码"),
    Information(UgcTypeV2.Information, "资讯"),
    Food(UgcTypeV2.Food, "美食"),
    Car(UgcTypeV2.Car, "汽车"),
    Fashion(UgcTypeV2.Fashion, "时尚美妆"),
    Sports(UgcTypeV2.Sports, "体育运动"),
    Animal(UgcTypeV2.Animal, "动物"),
}

/**
 * UGC 分区内容（TopNav + 4 列网格）。
 *
 * 顶部 Tab 切换分区，内容区为视频网格 + 无限滚动。
 * 菜单键刷新当前分区数据。
 *
 * @param navFocusRequester 顶部 Tab 的焦点请求器。
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param onNavToRail 顶栏按左键的去向（聚焦拥有此顶栏的轨道条目，由 MainScreen 传入）。
 * @param viewModel UGC ViewModel。
 */
@Composable
fun UgcContent(
    navFocusRequester: FocusRequester,
    navController: NavController,
    focusSaver: FocusSaver,
    onNavToRail: () -> Boolean = { false },
    viewModel: UgcViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(UgcTabItem.Douga) }
    var focusOnContent by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 顶栏顺序与隐藏项：立即写入 Prefs，下次启动沿用。
    var orderCsv by remember { mutableStateOf(Prefs.ugcTabOrder) }
    var hiddenCsv by remember { mutableStateOf(Prefs.ugcTabHidden) }
    var showTabEditor by remember { mutableStateOf(false) }

    val allTabs = remember(orderCsv) { orderedUgcTabs(orderCsv) }
    // 只认当前枚举里存在的项：改名/删项后残留的旧值不该继续影响过滤。
    val hiddenNames =
        remember(hiddenCsv) {
            val known = UgcTabItem.entries.map { it.name }.toSet()
            hiddenCsv.csvNames().filter { it in known }.toSet()
        }
    // 至少保留一项，否则顶栏会空掉。
    val visibleTabs =
        remember(allTabs, hiddenNames) {
            allTabs.filterNot { it.name in hiddenNames }.ifEmpty { allTabs.take(1) }
        }

    // 当前分区被隐藏时落到第一个可见项，避免顶栏出现"没有选中项"。
    LaunchedEffect(visibleTabs) {
        if (selectedTab !in visibleTabs) {
            val next = visibleTabs.first()
            selectedTab = next
            viewModel.switchType(next.ugcTypeV2)
        }
    }

    // 顶栏按下方向键的落点：显式指定，否则空间搜索会把焦点送进左侧轨道。
    val contentEntryRequester = remember { FocusRequester() }

    Material3Scaffold(
        topBar = {
            TopNav(
                modifier = Modifier.focusRequester(navFocusRequester),
                items = visibleTabs,
                selectedIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0),
                isLargePadding = !focusOnContent,
                onSelectedChanged = { nav ->
                    val tab = nav as UgcTabItem
                    if (tab != selectedTab) {
                        selectedTab = tab
                        viewModel.switchType(tab.ugcTypeV2)
                    }
                },
                onClick = { nav ->
                    val tab = nav as UgcTabItem
                    viewModel.refresh()
                },
                onNavigateDown = {
                    runCatching { contentEntryRequester.requestFocus() }.isSuccess
                },
                onNavigateLeft = onNavToRail,
                // 固定在顶栏末尾：入口本身不可排序、不可隐藏。
                trailingLabel = "修改",
                onTrailingClick = { showTabEditor = true },
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
            UgcGrid(
                viewModel = viewModel,
                navController = navController,
                focusSaver = focusSaver,
            )
        }
    }

    if (showTabEditor) {
        TopNavEditorDialog(
            title = "修改顶栏",
            items = allTabs,
            hiddenKeys = hiddenNames,
            keyOf = { (it as UgcTabItem).name },
            onMove = { index, delta ->
                val target = index + delta
                if (index in allTabs.indices && target in allTabs.indices) {
                    val moved =
                        allTabs.toMutableList().apply {
                            val swapping = this[index]
                            this[index] = this[target]
                            this[target] = swapping
                        }
                    val csv = moved.joinToString(",") { it.name }
                    orderCsv = csv
                    Prefs.ugcTabOrder = csv
                }
            },
            onToggleVisible = { name ->
                val next = hiddenNames.toMutableSet().apply { if (!add(name)) remove(name) }
                // 允许全隐藏会把顶栏变成空条，这里兜住最后一项。
                if (allTabs.any { it.name !in next }) {
                    val csv = next.joinToString(",")
                    hiddenCsv = csv
                    Prefs.ugcTabHidden = csv
                }
            },
            onDismiss = { showTabEditor = false },
        )
    }
}

/** 拆分逗号分隔的项名。 */
private fun String.csvNames(): List<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * 按保存的顺序展开全部分区。
 *
 * 未知项（改名/删项后残留）忽略，缺失项按枚举顺序补在末尾——
 * 这样新增分区不需要用户重新配置，App 升级后自动出现在最后。
 */
private fun orderedUgcTabs(orderCsv: String): List<UgcTabItem> {
    val saved =
        orderCsv.csvNames().mapNotNull { name ->
            UgcTabItem.entries.firstOrNull { it.name == name }
        }
    return saved + UgcTabItem.entries.filterNot { it in saved }
}

/**
 * UGC 视频网格。
 *
 * 4 列网格 + 无限滚动，距离底部 20 条时触发加载更多。
 */
@Composable
private fun UgcGrid(
    viewModel: UgcViewModel,
    navController: NavController,
    focusSaver: FocusSaver,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.distinctUntilChanged()
            .filter { index ->
                index != null && index >= state.items.size - 20
            }.collect {
                viewModel.loadMore()
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
            items = state.items,
            key = { index, _ -> index },
        ) { index, item ->
            val cardData =
                remember(item) {
                    VideoCardData(
                        avid = item.aid,
                        title = item.title,
                        cover = item.cover,
                        playString = item.play.takeIf { it != -1 }.toWanString(),
                        danmakuString = item.danmaku.takeIf { it != -1 }.toWanString(),
                        timeString = (item.duration * 1000L).formatHourMinSec(),
                        upName = item.author,
                        upMid = item.authorMid,
                        pubTime = item.pubTime,
                    )
                }
            SmallVideoCard(
                modifier = Modifier.focusSaverItem(focusSaver, "ugc_$index"),
                data = cardData,
                onClick = {
                    navController.navigateFromVideoCard(cardData)
                },
                onGoToDetailPage = {
                    navController.navigateFromVideoCard(cardData, forceDetail = true)
                },
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
