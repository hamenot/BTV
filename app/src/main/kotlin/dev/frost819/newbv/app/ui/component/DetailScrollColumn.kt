package dev.frost819.newbv.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 详情页滚动容器的留白与滚动策略。 */
object DetailScrollDefaults {
    /**
     * 聚焦项贴顶时保留的上缘余量。
     *
     * 取 0：详情页滚到最上方时要能回到绝对顶部，留任何正余量都会让页面上移一点。
     */
    val TopMargin = 0.dp

    /** 向下滚动露出目标时，目标底边与视口下缘的留白：不让目标贴着屏幕底边。 */
    val BottomMargin = 24.dp

    /**
     * 计算目标底边时额外补上的一截高度（默认值，适用于 UGC 详情页）。
     *
     * **为什么需要**：焦点落在卡片上时，冒泡到本容器的 `size` 只是**封面**的高度——
     * 卡片里真正可聚焦的节点是封面那块 Surface，标题 / UP / 日期是它的兄弟节点，
     * 不在矩形里。于是"补到完全可见"只保证封面可见，下面的文字被屏幕下缘切掉，
     * 而页面自认为已经滚到位了（实测：番剧页 size=250 恒为封面高度）。
     *
     * 88dp 是按最高的一种卡片定的：UGC「相关视频」卡 = 封面 250px + 两行标题
     * （约 86px）+ UP 与日期（约 35px）+ 间距 ≈ 420px，比上报的 250px 多约 170px。
     * 卡片更矮的页面应当传更小的值，否则最后一行下方会空出一大块。
     */
    val SizeAllowance = 88.dp

    /**
     * 内容底部留白。
     *
     * 64dp 是页面本身需要的收尾留白，额外再加 25dp 是给焦点滚动留的可滚余量：
     * 补上 [SizeAllowance] 后最后一行要往下多滚一截，底部没有富余就滚不动。
     */
    val BottomPadding = 89.dp

    /**
     * 目标底边"正好贴住"视口下缘时，把目标顶到视口的这个比例处。
     *
     * 详见 [DetailBringIntoViewSpec]。
     */
    const val DownPivotFraction = 0.45f
}

/**
 * 详情页的焦点滚动策略。
 *
 * 平台默认只保证"目标露出来"：从下方一路按上时，首项只要露出一角就停下，页面停在半途；
 * 一路按下时最后一行也只能露出半截。这里在目标位于视口上方时把它贴到上缘
 * （留 [topMarginPx]），因此回到首项会自然回正到最顶；目标在视口下方时按最小距离下滚，
 * 并额外留出 [bottomMarginPx] 的下缘余量。
 *
 * **"贴住下缘"要单独处理**：传进来的 `size` 未必是目标真实高度。目标若在某个横向
 * `LazyRow` 里（如「正片」「相关视频」这类横滑行），冒泡到本容器的矩形是该 LazyRow
 * 自己被视口裁剪后的矩形，于是 `offset + size` 恰好等于 `containerSize`，最小滚动量
 * 算出 0，页面就停在半途——卡片只露出上半截，标题 / 日期等信息永远滚不出来。
 * 这种情况改用 pivot 滚动：把目标顶到视口 45% 处，一次性完整带进视口。
 */
@OptIn(ExperimentalFoundationApi::class)
private class DetailBringIntoViewSpec(
    private val topMarginPx: Float,
    private val bottomMarginPx: Float,
    private val sizeAllowancePx: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        // 越界量：> 0 表示目标底边（含补上的那一截）越过视口下缘。
        val overshoot = offset + size + sizeAllowancePx - containerSize
        return when {
            // 目标在视口上方：贴到上缘。
            offset < topMarginPx -> offset - topMarginPx
            // 底边正好压在下缘上（误差容差内）：size 不可信，改用 pivot。
            overshoot in -FLUSH_EPSILON_PX..0f ->
                offset - containerSize * DetailScrollDefaults.DownPivotFraction
            // 正常下越界：补上越界量，再多留一点下缘余量。
            overshoot > 0f -> overshoot + bottomMarginPx
            else -> 0f
        }
    }

    private companion object {
        /** 判定"底边正好贴住下缘"的容差（像素）。 */
        const val FLUSH_EPSILON_PX = 4f
    }
}

/**
 * 详情页竖向滚动容器：给整页套上"贴顶 / 露底"的焦点滚动策略。
 *
 * `CompositionLocalProvider` 必须包在 `verticalScroll` **外层**——滚动容器在组合时读取
 * 该 Local，写在内容里的 provider 不会生效。
 *
 * @param scrollState 滚动状态。
 * @param topPadding 内容顶部留白。滚到最上方时露出来的那块空白，避免顶栏内容贴着屏幕上缘。
 * @param bottomPadding 内容底部留白。
 * @param sizeAllowance 补到目标底边上的一截高度，用来抵消 `size` 只算封面带来的偏差；
 *   取值应等于「本页最高一种卡片减去封面高度」，见 [DetailScrollDefaults.SizeAllowance]。
 * @param content 页面内容。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScrollColumn(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = DetailScrollDefaults.BottomPadding,
    sizeAllowance: Dp = DetailScrollDefaults.SizeAllowance,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val bringIntoViewSpec =
        remember(density, sizeAllowance) {
            DetailBringIntoViewSpec(
                topMarginPx = with(density) { DetailScrollDefaults.TopMargin.toPx() },
                bottomMarginPx = with(density) { DetailScrollDefaults.BottomMargin.toPx() },
                sizeAllowancePx = with(density) { sizeAllowance.toPx() },
            )
        }

    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
