package dev.frost819.newbv.app.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowScope
import androidx.tv.material3.Text
import dev.frost819.newbv.core.focus.touchClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 顶部导航栏的尺寸规格，取值对齐 Animeko TV 的 `TvCollectionTabRow`。 */
private object TopNavDefaults {
    /** Tab 行左侧留白（内容区已避开左侧轨道，这里只留行内边距）。 */
    val StartPadding = 24.dp

    /** 内容区获焦后的上留白。 */
    val CompactTopPadding = 12.dp

    /** 内容区未获焦时的上留白。 */
    val ExpandedTopPadding = 24.dp

    /** Tab 行下留白。 */
    val BottomPadding = 8.dp

    /** Tab 之间的空隙（替代 TV TabRow 默认的竖线分隔符）。 */
    val TabSpacing = 12.dp

    /** Tab 文字左右内边距。 */
    val TabHorizontalPadding = 16.dp

    /** Tab 文字上下内边距。 */
    val TabVerticalPadding = 8.dp

    /** 焦点在某个 Tab 上停留多久才真正切分区（毫秒）。 */
    const val SelectDebounceMillis = 500L

    /** 离开顶栏后，把焦点护送进新信息流的重试间隔（毫秒）。 */
    const val FocusEscortIntervalMillis = 180L

    /** 护送重试次数：AnimatedContent 换台约 300ms，多给两次余量。 */
    const val FocusEscortAttempts = 4
}

/**
 * 顶部导航 Tab 栏。
 *
 * 视觉对齐 Animeko TV 的分类 TabRow：左对齐的纯文字 Tab + TabRow 自带的下划线指示器，
 * 不使用胶囊底色与描边——聚焦反馈交给 Tab 自身的选中指示与文字色，
 * 避免"选中态"与"聚焦态"两层高亮互相干扰。
 *
 * Tab 切换时触发 [onSelectedChanged]（延迟 [TopNavDefaults.SelectDebounceMillis] 再切，
 * 避免焦点快速扫过时逐项加载导致卡顿），点击同一 Tab 触发 [onClick]（用于刷新）。
 *
 * @param items Tab 项列表（已按首选项排序）。
 * @param selectedIndex 当前选中的 Tab 索引（由外部控制，用于导航返回后恢复）。
 * @param isLargePadding 内容区未获焦点时使用较大上留白。
 * @param onSelectedChanged Tab 焦点切换回调。
 * @param onClick Tab 点击回调。
 * @param onNavigateDown 按下方向键时的去向（聚焦信息流首项）。
 *   必须显式指定：轨道是覆盖在左侧的兄弟节点，走空间搜索会把焦点送进侧边栏。
 * @param onNavigateLeft 在最左 Tab 上按左键的去向（聚焦拥有该顶栏的轨道条目）。
 *   null 或返回 false 时回退默认的空间搜索。只在最左 Tab 上拦截：其余 Tab 按左
 *   仍走 TabRow 内的常规移动。
 * @param trailingLabel 末尾固定动作项的文字（如"修改"），null 表示不加。
 * @param onTrailingClick 末尾动作项的点击回调。
 */
@Composable
fun TopNav(
    modifier: Modifier = Modifier,
    items: List<TopNavItem>,
    selectedIndex: Int = 0,
    isLargePadding: Boolean,
    onSelectedChanged: (TopNavItem) -> Unit = {},
    onClick: (TopNavItem) -> Unit = {},
    onNavigateDown: (() -> Boolean)? = null,
    onNavigateLeft: (() -> Boolean)? = null,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    var selectedTabIndex by remember { mutableIntStateOf(selectedIndex) }

    // 待切换的 Tab：焦点扫过时只更新指示器，停留到时长后才真正切分区。
    // 快速连按时每次变更都会重启这个副作用，因此中途不会发生任何加载。
    var pendingTab by remember { mutableStateOf<TopNavItem?>(null) }

    LaunchedEffect(selectedIndex) {
        selectedTabIndex = selectedIndex
    }

    LaunchedEffect(pendingTab) {
        val tab = pendingTab ?: return@LaunchedEffect
        delay(TopNavDefaults.SelectDebounceMillis)
        onSelectedChanged(tab)
        pendingTab = null
    }

    // 用户按确认键 = 明确要进这个分区，立刻兑现，不再等防抖。
    val flushPending = {
        pendingTab?.let {
            onSelectedChanged(it)
            pendingTab = null
        }
    }

    // 是否"按下方向键离开顶栏"（而不是移去左侧轨道/弹窗）。
    // 只有这一种离开方式才需要把焦点护送进信息流。
    var leavingToContent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /**
     * 焦点还没停满时长就离开顶栏：照旧切到最后停留的那个分区，
     * 并把焦点带进它的信息流。
     *
     * 切换会替换整个列表（内容页各自用 AnimatedContent 换台），旧卡片的焦点节点
     * 在动画结束时被回收，所以一次性 requestFocus 会被打断——这里隔帧重试几次，
     * 最后一次必然落在新列表的首项上。
     */
    val commitAndEscortFocus: () -> Unit = {
        if (pendingTab != null) {
            flushPending()
            if (onNavigateDown != null) {
                scope.launch {
                    repeat(TopNavDefaults.FocusEscortAttempts) {
                        delay(TopNavDefaults.FocusEscortIntervalMillis)
                        onNavigateDown()
                    }
                }
            }
        }
    }

    val topPadding by animateDpAsState(
        targetValue =
            if (isLargePadding) {
                TopNavDefaults.ExpandedTopPadding
            } else {
                TopNavDefaults.CompactTopPadding
            },
        label = "top-nav-padding",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = TopNavDefaults.StartPadding,
                    end = TopNavDefaults.StartPadding,
                    top = topPadding,
                    bottom = TopNavDefaults.BottomPadding,
                ),
        horizontalArrangement = Arrangement.Start,
    ) {
        TabRow(
            modifier =
                Modifier
                    .focusRestorer(focusRequester)
                    .onFocusChanged {
                        if (it.hasFocus) {
                            leavingToContent = false
                        } else if (pendingTab != null) {
                            if (leavingToContent) {
                                // 按下离开：切到最后停留的分区，焦点跟着进信息流。
                                commitAndEscortFocus()
                            } else {
                                // 移去轨道/弹窗：视为放弃，指示器还原成真正的选中项，
                                // 避免"指示器在新分区、列表还是旧分区"。
                                pendingTab = null
                                selectedTabIndex = selectedIndex
                            }
                            leavingToContent = false
                        } else if (selectedTabIndex != selectedIndex) {
                            // 焦点停在末项"修改"上离开：它没有对应分区，
                            // 把胶囊还给真正的选中分区，而不是留在动作项上。
                            selectedTabIndex = selectedIndex
                        }
                    },
            selectedTabIndex = selectedTabIndex,
            separator = { Spacer(modifier = Modifier.width(TopNavDefaults.TabSpacing)) },
        ) {
            items.forEachIndexed { index, tab ->
                NavItemTab(
                    modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                    topNavItem = tab,
                    leftmost = index == 0,
                    onNavigateLeft = onNavigateLeft,
                    selected = index == selectedTabIndex,
                    onFocus = {
                        selectedTabIndex = index
                        pendingTab = tab
                    },
                    onClick = {
                        // 切换已经由 flushPending 触发时不再重复刷新，避免一次点击打两次请求。
                        if (pendingTab != null && pendingTab == tab) {
                            flushPending()
                        } else {
                            onClick(tab)
                        }
                    },
                    onNavigateDown =
                        onNavigateDown?.let { navigate ->
                            {
                                leavingToContent = true
                                navigate()
                            }
                        },
                )
            }

            if (trailingLabel != null && onTrailingClick != null) {
                // 固定在顶栏末尾的动作项（如"修改"）：不可排序、不可隐藏。
                //
                // 它必须作为一个真正的 Tab 参与 TabRow 的选中下标：指示器（胶囊）是按
                // selectedTabIndex 布局并做位移动画的，若这里写死 selected = false，
                // 聚焦到它时胶囊还停在上一个分区上，动效与其它 Tab 完全对不上。
                val trailingIndex = items.size
                val trailingTouchClick: () -> Unit = {
                    pendingTab = null
                    onTrailingClick()
                }
                Tab(
                    modifier =
                        Modifier
                            .touchClickable(onClick = trailingTouchClick)
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                                    onNavigateDown?.let {
                                        leavingToContent = true
                                        it()
                                    } ?: false
                                } else {
                                    false
                                }
                            },
                    selected = selectedTabIndex == trailingIndex,
                    onFocus = {
                        pendingTab = null
                        selectedTabIndex = trailingIndex
                    },
                    onClick = {
                        pendingTab = null
                        onTrailingClick()
                    },
                ) {
                    Text(
                        text = trailingLabel,
                        modifier =
                            Modifier.padding(
                                horizontal = TopNavDefaults.TabHorizontalPadding,
                                vertical = TopNavDefaults.TabVerticalPadding,
                            ),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 单个 Tab。
 *
 * 只保留文字：颜色由 `Tab` 的 selected 状态决定（选中为主色，未选中为次要文字色），
 * 聚焦时由 TV TabRow 自身的指示器动画承担反馈，与 Animeko TV 一致。
 *
 * 按下方向键必须显式转交 [onNavigateDown]：轨道覆盖在左侧且与 Tab 行同高，
 * 交给空间搜索会把焦点送去侧边栏而不是信息流。
 *
 * 最左 Tab（[leftmost]）按左键转交 [onNavigateLeft]：焦点应落到拥有该顶栏的
 * 轨道条目上，而空间搜索的落点取决于各条目的纵向位置，未必是对的那一个。
 */
@Composable
private fun TabRowScope.NavItemTab(
    modifier: Modifier = Modifier,
    topNavItem: TopNavItem,
    leftmost: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    onNavigateDown: (() -> Boolean)? = null,
    onNavigateLeft: (() -> Boolean)? = null,
) {
    Tab(
        modifier =
            modifier
                // tv-material 的 Tab 内部 tvClickable 只处理 D-Pad 确认键，
                // 触屏点击需要额外补 pointerInput 手势（与 SeasonChip 等同款处理）
                .touchClickable(onClick = onClick)
                .onPreviewKeyEvent { event ->
                    when {
                        onNavigateDown != null &&
                            event.key == Key.DirectionDown &&
                            event.type == KeyEventType.KeyDown -> {
                            // 目标未就绪（数据还在加载）时返回 false，交回默认的空间搜索。
                            onNavigateDown()
                        }

                        leftmost &&
                            onNavigateLeft != null &&
                            event.key == Key.DirectionLeft &&
                            event.type == KeyEventType.KeyDown -> {
                            // 目标未就绪时返回 false，交回默认的空间搜索。
                            onNavigateLeft()
                        }

                        else -> false
                    }
                },
        selected = selected,
        onFocus = onFocus,
        onClick = onClick,
    ) {
        Text(
            text = topNavItem.displayName,
            modifier =
                Modifier.padding(
                    horizontal = TopNavDefaults.TabHorizontalPadding,
                    vertical = TopNavDefaults.TabVerticalPadding,
                ),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
    }
}

/**
 * 顶部导航项接口。
 */
interface TopNavItem {
    val displayName: String
}

private data class DummyTopNavItem(
    override val displayName: String,
) : TopNavItem

@Preview(showBackground = true)
@Composable
private fun TopNavPreview() {
    dev.frost819.newbv.core.theme.BVTheme {
        TopNav(
            items =
                listOf(
                    DummyTopNavItem("推荐"),
                    DummyTopNavItem("热门"),
                    DummyTopNavItem("动态"),
                ),
            isLargePadding = true,
        )
    }
}
