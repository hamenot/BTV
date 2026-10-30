package dev.frost819.newbv.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.biliapi.entity.CarouselData
import kotlinx.coroutines.delay
import kotlin.math.abs

/** 横向滑动翻页的位移阈值（像素），沿用原实现的手感。 */
private const val CarouselSwipeThresholdPx = 40f

/**
 * PGC 轮播图组件。
 *
 * 自动滚动展示番剧/影视封面，获焦点时暂停自动滚动。
 * D-Pad 左右切换，点击跳转详情页。
 *
 * @param data 轮播图数据列表。
 * @param onClick 点击回调，传入当前轮播项。
 * @param modifier Modifier。宽高由调用方决定（PGC 页按通栏大图铺满内容区宽度）。
 */
@Composable
fun PgcCarousel(
    data: List<CarouselData.CarouselItem>,
    onClick: (CarouselData.CarouselItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    CarouselContent(
        modifier = modifier,
        data = data,
        onClick = onClick,
    )
}

@Composable
private fun CarouselContent(
    data: List<CarouselData.CarouselItem>,
    onClick: (CarouselData.CarouselItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Carousel(
        itemCount = data.size,
        modifier = modifier,
        onClick = { itemIndex ->
            onClick(data[itemIndex])
        },
    ) { itemIndex, focused ->
        CarouselCard(data = data[itemIndex], focused = focused)
    }
}

/**
 * 番剧 Hero 卡片。
 *
 * 版式对齐 B 站番剧页：大图铺满，左侧与底部压暗做渐变遮罩，左下角依次是
 * 标题、评分/播放/弹幕等元信息、两行简介和「更多详情」胶囊；右下角圆点由轮播引擎统一绘制。
 *
 * 焦点反馈落在「更多详情」胶囊上（反白 → 主题强调色）。Hero 本身不再描边：
 * 描边会在大图四周形成一道生硬的框线，且焦点态一整圈变色会喧宾夺主。
 */
@Composable
private fun CarouselCard(
    data: CarouselData.CarouselItem,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    // 与页面背景同色：Hero 底部遮罩渐变到它，图片与背景自然衔接，不出现硬边。
    val pageBackground = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = data.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )

        // 左侧压暗：给文字一块可读的底
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = .88f),
                            .45f to Color.Black.copy(alpha = .48f),
                            1f to Color.Transparent,
                        ),
                    ),
        )
        // 底部压暗并渐变到页面背景色：让文字块与下方内容分离，
        // 同时使图片底边自然过渡到背景，而不是留下一条生硬的黑色边界。
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            .45f to Color.Black.copy(alpha = .3f),
                            .75f to Color.Black.copy(alpha = .62f),
                            1f to pageBackground,
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, end = 32.dp, bottom = 28.dp)
                    .widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                data.rating?.let { rating ->
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = .9f),
                    )
                }
                data.viewCount?.let { view ->
                    Text(
                        text = "播放 ${if (view >= 10000) "${view / 10000}万" else "$view"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = .78f),
                    )
                }
                data.danmakuCount?.let { danmaku ->
                    Text(
                        text = "弹幕 ${danmaku.toWanString()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = .78f),
                    )
                }
            }
            // 简介：优先真实剧情简介，其次宣传语（v2 schema），最后角标标签。
            (data.evaluate ?: data.subtitle)?.let { evaluate ->
                Text(
                    text = evaluate,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = .72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .background(
                            color = if (focused) MaterialTheme.colorScheme.primary else Color.White,
                            shape = CircleShape,
                        ).padding(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "更多详情",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focused) MaterialTheme.colorScheme.onPrimary else Color.Black,
                )
            }
        }
    }
}

/**
 * 通用轮播图引擎。
 *
 * 基于 [AnimatedContent] 实现自动滚动 + D-Pad 导航。
 * 自动滚动在获得焦点时暂停。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Carousel(
    itemCount: Int,
    onClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    autoScrollInterval: Long = CarouselDefaults.TimeToDisplayItemMillis,
    contentTransformStartToEnd: ContentTransform =
        fadeIn(tween(1000))
            .togetherWith(fadeOut(tween(1000))),
    contentTransformEndToStart: ContentTransform =
        fadeIn(tween(1000))
            .togetherWith(fadeOut(tween(1000))),
    content: @Composable AnimatedContentScope.(index: Int, focused: Boolean) -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    var isMovingBackward by remember { mutableStateOf(false) }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(currentIndex, itemCount) {
        while (true) {
            delay(autoScrollInterval)
            if (itemCount == 0 || hasFocus) continue
            isMovingBackward = false
            currentIndex = (currentIndex + 1) % itemCount
        }
    }

    Box(
        modifier =
            modifier
                // 四角圆角与全局卡片（SeasonCard / SmallVideoCard / LiveRoomCard）同源，
                // 都取 shapes.large；Hero 是通栏大图，不圆角会显得比下方卡片"硬"一档。
                .clip(MaterialTheme.shapes.large)
                .onFocusChanged { focusState ->
                    hasFocus = focusState.isFocused
                }
                // 可聚焦性原来由 .clickable 提供；改为显式 focusable 后
                // 点击/滑动由下方单一 pointerInput 统一处理（见下）。
                .focusable()
                .pointerInput(itemCount) {
                    if (itemCount == 0) return@pointerInput
                    // 单一 pointerInput 同时处理点击与横向滑动：
                    // 之前 .clickable 与 detectHorizontalDragGestures 分属两个
                    // pointer 处理器，手势互相竞争——手指稍带横向位移就判成翻页、
                    // 点击被取消，触屏点击命中率很低。合并在一个手势里：
                    // 位移越过阈值 → 翻页并吞掉剩余触摸；静止抬起 → 点击。
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDragX = 0f
                        var flipped = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change =
                                event.changes.firstOrNull { it.id == down.id } ?: break
                            if (flipped) {
                                // 翻页已触发：吞掉本次触摸的剩余移动，避免再派生点击
                                change.consume()
                            } else {
                                totalDragX += change.positionChange().x
                                when {
                                    abs(totalDragX) > CarouselSwipeThresholdPx -> {
                                        flipped = true
                                        if (totalDragX > 0f) {
                                            isMovingBackward = true
                                            currentIndex = (currentIndex - 1 + itemCount) % itemCount
                                        } else {
                                            isMovingBackward = false
                                            currentIndex = (currentIndex + 1) % itemCount
                                        }
                                        change.consume()
                                    }

                                    change.changedToUp() -> {
                                        onClick(currentIndex)
                                    }
                                }
                            }
                            if (change.changedToUp()) break
                        }
                    }
                }.onKeyEvent {
                    when {
                        itemCount == 0 -> false
                        it.type == KeyEventType.KeyUp -> false
                        it.key == Key.DirectionLeft -> {
                            isMovingBackward = true
                            currentIndex = (currentIndex - 1 + itemCount) % itemCount
                            true
                        }

                        it.key == Key.DirectionRight -> {
                            isMovingBackward = false
                            currentIndex = (currentIndex + 1) % itemCount
                            true
                        }

                        // OK 键进详情页：原来由 .clickable 承担，改 focusable 后需显式处理
                        it.key == Key.Enter || it.key == Key.DirectionCenter -> {
                            onClick(currentIndex)
                            true
                        }

                        else -> false
                    }
                },
    ) {
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                if (isMovingBackward) {
                    contentTransformEndToStart
                } else {
                    contentTransformStartToEnd
                }
            },
            label = "CarouselAnimation",
        ) { activeItemIndex ->
            if (itemCount > 0) content(activeItemIndex, hasFocus)
        }
        CarouselDefaults.IndicatorRow(
            itemCount = itemCount,
            activeItemIndex = currentIndex,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        )
    }
}
