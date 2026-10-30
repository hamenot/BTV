package dev.frost819.newbv.app.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.core.focus.isDpadRight
import dev.frost819.newbv.core.focus.isKeyDown
import dev.frost819.newbv.data.datastore.LeftNaviItem
import kotlinx.coroutines.delay

/**
 * 悬浮导航轨道的尺寸、动效与配色规格。
 *
 * 取值对齐 Animeko TV 的 `TvNavigationRailDefaults`：40dp 圆形条目、8dp 间距、
 * 展开 260ms、聚焦整条反色。轨道本身不占布局宽度，
 * 由调用方按 [CollapsedWidth] 给内容区留白。
 */
object SideRailDefaults {
    /** 条目距屏幕左侧的留白。 */
    val StartPadding = 16.dp

    /** 轨道纵向留白。 */
    val VerticalPadding = 24.dp

    /**
     * 焦点停留自动进入的时长。
     *
     * 比单击确认更快的翻页方式：焦点在某个条目上停够 0.5 秒即自动进入。
     * 0.5 秒是遥控器用户逐格移动时不会扫过误触的下限；长按方向键的
     * 重复间隔（约 50~100ms）远小于该值，快速滑过不会触发。
     */
    val DwellSelectMillis = 500L

    /** 条目触达尺寸（圆形 / 药丸高度）。 */
    val ItemSize = 40.dp

    /** 条目间距。 */
    val ItemSpacing = 8.dp

    /** 条目形状：50% 圆角，收起时即为圆形。 */
    val ItemShape = RoundedCornerShape(percent = 50)

    /** 收起态"当前页"强调框的高度：比条目矮一档，形成横向胶囊而非正圆。 */
    val SelectedCapsuleHeight = 28.dp

    /** 图标字形尺寸。 */
    val IconSize = 20.dp

    /** 头像尺寸（略大于图标，保持同一视觉重心）。 */
    val AvatarSize = 24.dp

    /** 展开时图标与文字间距。 */
    val LabelSpacing = 12.dp

    /** 展开时文字右侧留白。 */
    val LabelEndPadding = 12.dp

    /** 展开时药丸的最大宽度。 */
    val LabelMaxWidth = 200.dp

    /** 收起态轨道宽度：内容区按此值内缩。 */
    val CollapsedWidth: Dp = StartPadding + ItemSize

    /**
     * 展开态药丸宽度。
     *
     * 结构为「图标左侧内缩 + 图标 + 标签间距 + 文字 + 尾部留白」。
     * 头像条目（24dp）的左侧内缩比图标条目（20dp）更小、内容更宽，按头像那条算才够。
     */
    fun expandedPillWidth(labelWidth: Dp): Dp =
        (ItemSize - AvatarSize) / 2 + AvatarSize + LabelSpacing + labelWidth + LabelEndPadding

    /** 展开动画时长。 */
    const val ExpandDurationMillis = 260

    /**
     * 侧栏展开时整屏压暗的透明度。
     *
     * 展开态的药丸会盖在信息流上，只压 45% 时卡片标题仍然透得出来，
     * 一列文字压着一列文字，看着像"重叠"而不是"浮层"。
     */
    const val ScrimDimAlpha = 0.6f

    /**
     * 遮罩底色向主题主色（primary）混合的比例。
     *
     * 遮罩不是写死的黑色，而是 `lerp(surface, primary, 此值)`：浅色主题下得到
     * 浅色遮罩（深色图标才看得清），深色主题下维持深色压暗，同时遮罩会跟随
     * 主题色切换同步变色。取值要小——既要肉眼能感知主色倾向，又不能盖过内容。
     */
    const val ScrimAccentBlend = 0.18f

    /**
     * 药丸正下方那一段的不透明度。
     *
     * 遮罩不是一块均匀的色块：靠着轨道这条边接近实底，往右快速渐隐到透明。
     * 这样药丸上的文字永远落在实底上（读得清），而信息流只在远离轨道处露出一角，
     * 既有"磨砂压住背景"的观感，又不会把整页糊成一块。
     */
    const val ScrimEdgeAlpha = 0.96f

    /**
     * 靠近轨道这一侧保持实底的宽度。
     *
     * 260dp 是"能盖住整条展开药丸 + 一段缓冲"的宽度，实测压得太宽、把信息流中部也吃掉了；
     * 收窄到 0.8 倍（208dp）后正好贴住药丸外缘，过渡带也一起等比缩小。
     */
    val ScrimEdgeWidth = 208.dp

    /** 遮罩淡入淡出时长。 */
    const val ScrimFadeMillis = 160

    /** 头像与导航组之间、导航组与设置之间的分隔间距。 */
    val GroupSpacing = 16.dp
}

/**
 * 展开态轨道占用的宽度（屏幕左缘 → 药丸右缘）。
 *
 * 标签宽度用 [rememberTextMeasurer] 实测而非写死：字体、字号或 i18n 文案变化后，
 * 内容区仍然正好让开。内容区据此内缩，展开时文字就不会压到信息流上。
 *
 * @param isLogin 是否已登录，决定首项标签是「我的」还是「登录」。
 */
@Composable
internal fun rememberSideRailExpandedWidth(isLogin: Boolean): Dp {
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium
    val labelWidth =
        remember(textMeasurer, style, isLogin) {
            val labels =
                buildList {
                    add(if (isLogin) "我的" else "登录")
                    LeftNaviItem.entries.forEach { add(it.displayName) }
                    add("设置")
                }
            labels.maxOf { textMeasurer.measure(it, style, maxLines = 1).size.width }
        }
    return with(LocalDensity.current) {
        SideRailDefaults.StartPadding + SideRailDefaults.expandedPillWidth(labelWidth.toDp())
    }
}

/**
 * 左侧悬浮导航轨道。
 *
 * 与旧实现的 `NavigationDrawer` 不同，轨道不占用布局宽度，而是叠加在内容之上，
 * 由 [SideRailDefaults.CollapsedWidth] 决定内容区留白——这正是 Animeko TV 的
 * "悬浮胶囊"形态：收起时只剩图标列，获焦时向右展开成带文字的药丸。
 *
 * 焦点约定：
 * - 轨道内任意条目获焦 → 展开文字（260ms）；离开轨道后收起。
 * - D-Pad 右键优先交给 [onFocusToContent] 直接聚焦内容区入口；入口被懒列表回收时，
 *   回退到焦点系统的默认右向搜索（issue #287）。
 * - 所有条目接入 [FocusSaver]，保证从设置/详情返回后焦点落回原条目。
 *
 * @param isLogin 是否已登录。
 * @param avatar 头像 URL。
 * @param selectedItem 当前选中的导航项。
 * @param onLeftNaviItemChanged 导航项切换回调。
 * @param onOpenSettings 打开设置回调。
 * @param onShowUserPanel 显示用户面板回调。
 * @param onFocusToContent 聚焦内容区回调，返回是否成功。
 * @param onLogin 登录回调。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param onExpandedChange 展开状态变化回调（内容区据此同步内缩）。
 */
@Composable
fun LeftNaviContent(
    modifier: Modifier = Modifier,
    isLogin: Boolean = false,
    avatar: String = "",
    selectedItem: LeftNaviItem,
    onLeftNaviItemChanged: (LeftNaviItem) -> Unit,
    onOpenSettings: () -> Unit,
    onShowUserPanel: () -> Unit,
    onFocusToContent: () -> Boolean,
    onLogin: () -> Unit,
    focusSaver: FocusSaver,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .onFocusChanged {
                    if (expanded != it.hasFocus) {
                        expanded = it.hasFocus
                        // 内容区要跟着一起右移，展开状态必须让调用方知道。
                        onExpandedChange(it.hasFocus)
                    }
                }.onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.isDpadRight() && keyEvent.isKeyDown()) {
                        // 入口成功聚焦则消费事件；否则（入口被回收）回退到默认右向搜索，
                        // 让焦点进入内容区最近的可聚焦元素。
                        if (onFocusToContent() || focusManager.moveFocus(FocusDirection.Right)) {
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(
                        start = SideRailDefaults.StartPadding,
                        top = SideRailDefaults.VerticalPadding,
                        bottom = SideRailDefaults.VerticalPadding,
                    ).widthIn(max = SideRailDefaults.LabelMaxWidth + SideRailDefaults.ItemSize),
            verticalArrangement = Arrangement.spacedBy(SideRailDefaults.ItemSpacing),
            horizontalAlignment = Alignment.Start,
        ) {
            val userLabel = if (isLogin) "我的" else "登录"
            SideRailItem(
                label = userLabel,
                onClick = { if (isLogin) onShowUserPanel() else onLogin() },
                selected = false,
                expanded = expanded,
                avatarUrl = if (isLogin) avatar else null,
                icon = if (isLogin) null else Icons.Default.AccountCircle,
                modifier = Modifier.focusSaverItem(focusSaver, "user"),
            )

            Spacer(modifier = Modifier.height(SideRailDefaults.GroupSpacing))

            // 中间导航组：空间足够时垂直居中，不够时（如 720p 电视）内部滚动，
            // 不能用单纯的 weight 居中——7 个 40dp 条目在 360dp 高的屏幕上会溢出，
            // 把底部"设置"顶出屏幕、焦点高亮只剩半截。
            //
            // 注意两层都不能 fillMaxWidth：verticalScroll 会给滚动容器挂 pointerInput，
            // 占多宽就命中多宽的触摸（z 序在信息流之上），撑满 240dp 会把信息流
            // 第一列卡片左半部的触摸全部劫持掉（tap 不再下发给卡片）。
            // 让宽度收缩到内容实际尺寸（收起 40dp），滚动区只覆盖轨道本身。
            BoxWithConstraints(
                modifier = Modifier.weight(1f),
            ) {
                val viewportHeight = maxHeight
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                ) {
                    LeftNaviItem.entries.forEach { item ->
                        SideRailItem(
                            label = item.displayName,
                            onClick = { onLeftNaviItemChanged(item) },
                            selected = item == selectedItem,
                            expanded = expanded,
                            icon = item.displayIcon,
                            // 停留自动进入：已选中的条目无需再次进入，不启用。
                            dwellMillis =
                                if (item == selectedItem) {
                                    null
                                } else {
                                    SideRailDefaults.DwellSelectMillis
                                },
                            modifier = Modifier.focusSaverItem(focusSaver, "nav_${item.name}"),
                        )
                    }
                }
            }

            SideRailItem(
                label = "设置",
                onClick = onOpenSettings,
                selected = false,
                expanded = expanded,
                icon = Icons.Default.Settings,
                modifier = Modifier.focusSaverItem(focusSaver, "settings"),
            )
        }
    }
}

/**
 * 轨道条目。
 *
 * 收起态是内容尺寸的圆形（图标 20dp → 40dp 圆），展开态是带文字的药丸。
 * 聚焦时整条反色（`inverseSurface` / `inverseOnSurface`），缩放保持 1f——
 * TV 上缩放会让相邻条目抖动，改用"实心反色"承担焦点提示。
 *
 * @param label 条目文字，仅在展开态显示。
 * @param onClick 点击回调。
 * @param selected 是否为当前页面（用主题主色区分，不与聚焦反色冲突）。
 * @param expanded 轨道是否处于展开态。
 * @param icon 图标，与 [avatarUrl] 二选一。
 * @param avatarUrl 头像地址，非空时优先于 [icon]。
 * @param dwellMillis 焦点停留自动触发 [onClick] 的时长；null 表示不启用。
 * @param modifier 外部修饰符（承载 FocusSaver 的 key）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SideRailItem(
    label: String,
    onClick: () -> Unit,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    avatarUrl: String? = null,
    dwellMillis: Long? = null,
) {
    val contentSize = if (avatarUrl != null) SideRailDefaults.AvatarSize else SideRailDefaults.IconSize
    // 收起态左右留白相等，保证条目是正圆；展开后由文字撑出药丸。
    val contentInset = (SideRailDefaults.ItemSize - contentSize) / 2

    var focused by remember { mutableStateOf(false) }

    // 停留自动进入：焦点停留 dwellMillis 后触发 onClick。LaunchedEffect 以 focused 为 key——
    // 焦点移走即取消计时，焦点换到新条目则重新计时；长按方向键快速扫过时
    // 每个条目只停留一个重复间隔，不会误触。
    if (dwellMillis != null) {
        val currentOnClick by rememberUpdatedState(onClick)
        LaunchedEffect(focused) {
            if (focused) {
                delay(dwellMillis)
                currentOnClick()
            }
        }
    }

    // 背景由内层 Row 自己绘制（收起态 40dp / 展开为药丸），Surface 只负责点击与焦点：
    // TV Material3 会给可点击 Surface 套最小触达尺寸（48dp），若把背景画在 Surface 上，
    // 收起态的"圆"会被撑成椭圆。触达区域仍然由 Surface 保留为 48dp，对遥控器更友好。
    val focusColor = MaterialTheme.colorScheme.inverseSurface
    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = .16f)
    val contentColor =
        when {
            focused -> MaterialTheme.colorScheme.inverseOnSurface
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .78f)
        }

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .onFocusChanged { focused = it.hasFocus }
                .railTouchTap(onClick),
        shape = ClickableSurfaceDefaults.shape(SideRailDefaults.ItemShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = .96f),
    ) {
        Row(
            modifier =
                Modifier
                    .height(SideRailDefaults.ItemSize)
                    // 自绘背景：聚焦 → 与条目等高的圆形/药丸；选中 → 收起态用胶囊，
                    // 比圆形更贴 i18n 长文本的观感，也不会在图标四周留出过多色块。
                    .drawBehind {
                        val shapeHeight =
                            if (expanded) {
                                size.height
                            } else {
                                SideRailDefaults.SelectedCapsuleHeight.toPx()
                            }
                        val color =
                            when {
                                focused -> focusColor
                                selected -> selectedColor
                                else -> Color.Transparent
                            }
                        if (color != Color.Transparent) {
                            val radius = shapeHeight / 2f
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(0f, (size.height - shapeHeight) / 2f),
                                size = Size(size.width, shapeHeight),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                    }.padding(
                        start = contentInset,
                        end = if (expanded) SideRailDefaults.LabelEndPadding else contentInset,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(SideRailDefaults.AvatarSize)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(SideRailDefaults.IconSize),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter =
                    fadeIn(
                        animationSpec =
                            tween(
                                durationMillis = 140,
                                delayMillis = 80,
                            ),
                    ) +
                        expandHorizontally(
                            animationSpec = tween(SideRailDefaults.ExpandDurationMillis),
                            expandFrom = Alignment.Start,
                        ),
                exit =
                    fadeOut(animationSpec = tween(durationMillis = 80)) +
                        shrinkHorizontally(
                            animationSpec = tween(durationMillis = 160),
                            shrinkTowards = Alignment.Start,
                        ),
            ) {
                Row(
                    modifier = Modifier.widthIn(max = SideRailDefaults.LabelMaxWidth),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(SideRailDefaults.LabelSpacing))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 触屏点击支持。
 *
 * TV Material3 的 `Surface(onClick)` 只处理 D-Pad 确认键，触屏点击需要额外的指针手势。
 * 这里不请求焦点（与 `touchClickable` 不同）——条目上已有 [FocusSaver] 的
 * `FocusRequester`，再叠一个会与之竞争同一个焦点节点。
 */
private fun Modifier.railTouchTap(onClick: () -> Unit): Modifier =
    composed {
        val currentOnClick by rememberUpdatedState(onClick)
        pointerInput(Unit) {
            detectTapGestures { currentOnClick() }
        }
    }

/** 左侧导航项的图标与显示名称扩展。 */
val LeftNaviItem.displayIcon: ImageVector
    get() =
        when (this) {
            LeftNaviItem.Search -> Icons.Default.Search
            LeftNaviItem.Personal -> Icons.Default.Person
            LeftNaviItem.Home -> Icons.Default.Home
            LeftNaviItem.UGC -> Icons.Default.OndemandVideo
            LeftNaviItem.PGC -> Icons.Default.Movie
            LeftNaviItem.Live -> Icons.Default.LiveTv
        }

/** 左侧导航项的显示名称扩展。 */
val LeftNaviItem.displayName: String
    get() =
        when (this) {
            LeftNaviItem.Search -> "搜索"
            LeftNaviItem.Personal -> "个人"
            LeftNaviItem.Home -> "主页"
            LeftNaviItem.UGC -> "分区"
            LeftNaviItem.PGC -> "影视"
            LeftNaviItem.Live -> "直播"
        }

@Preview(showBackground = true, widthDp = 300, heightDp = 600)
@Composable
private fun LeftNaviContentPreview() {
    dev.frost819.newbv.core.theme.BVTheme {
        LeftNaviContent(
            isLogin = true,
            avatar = "",
            selectedItem = LeftNaviItem.Home,
            onLeftNaviItemChanged = {},
            onOpenSettings = {},
            onShowUserPanel = {},
            onFocusToContent = { true },
            onLogin = {},
            focusSaver =
                dev.frost819.newbv.app.ui.component
                    .rememberFocusSaver(),
        )
    }
}
