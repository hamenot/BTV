package dev.frost819.newbv.app.ui.component.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.core.focus.focusInvertedColors
import dev.frost819.newbv.core.focus.touchClickable
import kotlinx.coroutines.launch

/**
 * 分 P / 分集通用按钮。
 *
 * 文字按钮样式，无封面，可在底部叠加播放进度条。
 *
 * @param title 按钮标题。
 * @param duration 总时长（秒），用于计算进度条比例。
 * @param played 已播放时长（秒），0 表示无进度；传负数表示已看完（进度条拉满）。
 * @param isCurrent 是否为当前正在播放的项。
 * @param onClick 点击回调。
 * @param modifier 外部修饰符。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeListButton(
    title: String,
    duration: Int,
    played: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // 不要在外层用 .clip()：它会裁剪掉 Surface 内部的聚焦放大，导致没有漂浮效果
        modifier =
            modifier
                .width(200.dp)
                .height(64.dp)
                .touchClickable(onClick = onClick),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            focusInvertedColors(
                containerColor =
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (played != 0 && duration > 0) {
                val ratio = if (played < 0) 1f else (played.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxHeight()
                            .fillMaxWidth(ratio)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                )
            }
            Text(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 网格选集触发器。
 *
 * 分 P / 分集数量较多时显示，点击打开分页网格弹窗。
 *
 * @param onClick 点击回调。
 */
@Composable
fun EpisodeGridButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.touchClickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            focusInvertedColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = "网格列表",
            modifier =
                Modifier
                    .padding(4.dp)
                    .size(20.dp),
        )
    }
}

/**
 * 分 P / 分集分页网格弹窗。
 *
 * 使用 [TabRow] 按 [pageSize] 分页，每页两列网格。可通过 [keyOf] 为网格项提供
 * 稳定 key，避免列表复用错位。分集数较少（单页）时不显示 Tab。
 *
 * @param title 弹窗标题；为 `null` 时不显示标题行。
 * @param entries 全部条目。
 * @param pageSize 每页条目数。
 * @param keyOf 提取条目的稳定 key，用于网格项 key。
 * @param titleOf 提取条目显示标题。
 * @param durationOf 提取条目总时长（秒）。
 * @param playedOf 提取条目已播放时长（秒），0 表示无进度。
 * @param isCurrentOf 判断条目是否为当前播放项。
 * @param tabLabelOf 根据页码区间生成 Tab 文案，参数为 1-based 的起止序号。
 * @param onDismiss 关闭回调。
 * @param onSelect 选中条目回调。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> EpisodeListDialog(
    title: String?,
    entries: List<T>,
    pageSize: Int,
    keyOf: (T) -> Any,
    titleOf: (T) -> String,
    durationOf: (T) -> Int,
    playedOf: (T) -> Int,
    isCurrentOf: (T) -> Boolean,
    tabLabelOf: (start: Int, end: Int) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    val pageCount = (entries.size + pageSize - 1) / pageSize
    var selectedTab by remember { mutableStateOf(0) }
    val dialogFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { dialogFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .focusRequester(dialogFocusRequester)
                    .size(width = 640.dp, height = 360.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (title != null) {
                    Text(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (pageCount > 1) {
                    // 分页切换用与详情页控件一致的小芯片（surfaceVariant 底、选中 secondaryContainer
                    // + 主色描边、聚焦 inverseSurface 反色）。芯片**等宽**并横向滚动——参照顶栏
                    // 胶囊的溢出策略：超出一屏的藏在右侧，焦点移过去时自动滚入可见。
                    // 注意 horizontal 边距必须在 horizontalScroll **之前**（视口外侧）：
                    // 视口的裁切线内收 12dp，滚动中的芯片不会贴着弹窗边缘被切。
                    // focusRestorer 用无参重载：带 FocusRequester 的重载在其目标节点
                    // 未挂载（选中芯片重组瞬间）时会抛 IllegalStateException 直接闪退
                    val paginationScrollState = rememberScrollState()
                    val paginationScope = rememberCoroutineScope()
                    Row(
                        modifier =
                            Modifier
                                .focusRestorer()
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .horizontalScroll(paginationScrollState)
                                // 视口内侧的呼吸边距：焦点芯片的描边不贴着视口裁切线
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(pageCount) { index ->
                            val start = index * pageSize + 1
                            val end = minOf((index + 1) * pageSize, entries.size)
                            val isSelected = selectedTab == index
                            val chipInViewRequester = remember { BringIntoViewRequester() }
                            Surface(
                                onClick = { selectedTab = index },
                                modifier =
                                    Modifier
                                        .width(96.dp)
                                        .height(36.dp)
                                        .bringIntoViewRequester(chipInViewRequester)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                paginationScope.launch {
                                                    runCatching { chipInViewRequester.bringIntoView() }
                                                }
                                            }
                                        }.touchClickable(onClick = { selectedTab = index }),
                                shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                                // 头部分页芯片不做聚焦放大：与详情页头部按钮行为一致
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                border =
                                    if (isSelected) {
                                        ClickableSurfaceDefaults.border(
                                            focusedBorder =
                                                androidx.tv.material3.Border(
                                                    border =
                                                        androidx.compose.foundation.BorderStroke(
                                                            2.dp,
                                                            MaterialTheme.colorScheme.border,
                                                        ),
                                                    shape = MaterialTheme.shapes.small,
                                                ),
                                        )
                                    } else {
                                        ClickableSurfaceDefaults.border()
                                    },
                                colors =
                                    focusInvertedColors(
                                        containerColor =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        contentColor =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    ),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = tabLabelOf(start, end),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
                val start = (selectedTab * pageSize).coerceAtMost(entries.size)
                val end = minOf(start + pageSize, entries.size)
                val slice = entries.subList(start, end)
                TvLazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    // contentPadding 在网格视口**内侧**：滚动容器会在自身边界裁剪内容，
                    // modifier padding（视口外侧）救不了贴边的 item；聚焦放大 1.1 倍
                    // （200×64dp 约外溢 10×3dp）需要这份视口内的呼吸空间
                    contentPadding =
                        PaddingValues(
                            start = 20.dp,
                            top = 6.dp,
                            end = 20.dp,
                            bottom = 20.dp,
                        ),
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(slice, key = { keyOf(it) }) { entry ->
                        EpisodeListButton(
                            title = titleOf(entry),
                            duration = durationOf(entry),
                            played = playedOf(entry),
                            isCurrent = isCurrentOf(entry),
                            onClick = { onSelect(entry) },
                        )
                    }
                }
            }
        }
    }
}
