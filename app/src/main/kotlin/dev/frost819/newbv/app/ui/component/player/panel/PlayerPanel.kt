package dev.frost819.newbv.app.ui.component.player.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.core.focus.touchClickable

/**
 * 播放器弹出面板的版式规格。
 *
 * 对齐 Animeko TV 的 `TvOptionDefaults` / `TvOptionPanelDefaults`：
 * 面板是**浮在画面上的一块不透明深色圆角卡**（不是整屏半透明遮罩），
 * 列表项自带圆角，聚焦整块反色，选中项用主色淡底 + 主色文字区分。
 *
 * 取色写死而不走主题：面板永远压在视频画面上，必须是稳定的深色，
 * 跟随浅色主题会直接变成一块白板。
 */
internal object PlayerPanelDefaults {
    /** 面板底色。 */
    val Container = Color(0xFF202127)

    /** 面板内凸起元素（图标底座等）。 */
    val Raised = Color(0xFF2D2F37)

    /** 主内容色。 */
    val Content = Color(0xFFF2F2F6)

    /** 次要内容色。 */
    val Muted = Color(0xFFB9BBC6)

    /** 分隔线 / 面板描边。 */
    val Outline = Color.White.copy(alpha = 0.08f)

    /** 聚焦反色：底。 */
    val FocusedContainer = Color(0xFFF2F2F6)

    /** 聚焦反色：内容。 */
    val FocusedContent = Color(0xFF1B1B20)

    /** 面板圆角。 */
    val PanelShape = RoundedCornerShape(20.dp)

    /** 列表项圆角。 */
    val ItemShape = RoundedCornerShape(12.dp)

    /** 面板内边距。 */
    val PanelPadding = 12.dp

    /** 列表项最小高度。 */
    val ItemMinHeight = 44.dp

    /** 列表项之间的间距。 */
    val ItemSpacing = 4.dp

    /** 列表项左右内边距。 */
    val ItemHorizontalPadding = 16.dp

    /** 列表项图标尺寸。 */
    val ItemIconSize = 20.dp

    /** 面板与屏幕边缘的安全距离。 */
    val ScreenPadding = 24.dp

    /** 面板内容最大高度：超出后列表自己滚动。 */
    val PanelMaxHeight = 380.dp

    /**
     * 面板底边距离屏幕底缘的高度。
     *
     * = 控制条下留白 28 + 图标按钮 44 + 进度行/图标行间距 14 + 与按钮之间的 8dp 空隙。
     * 面板从按钮上方长出来，不压住按钮，也不遮住进度条。
     */
    val AnchorBottomInset = 94.dp

    /** 面板里值列表（分辨率、倍速这类选项）的宽度。 */
    val PanelContentWidth = 216.dp

    /** 面板里分类导航列的宽度。 */
    val PanelNavWidth = 168.dp

    /** 单列列表面板（分集、相关视频）的宽度。 */
    val PanelListWidth = 340.dp

    /**
     * 固定尺寸面板的宽度：给「子项列 + 值列」的三级版式（画质 / 弹幕 / 字幕）用。
     *
     * = 2 × 216 列 + 8 列间距 + 2 × 12 内边距。
     */
    val FixedPanelWidth = 464.dp

    /**
     * 固定尺寸面板的高度。
     *
     * 画质 4 行、字幕 4 行、弹幕 6 行，内容行数不一样，卡片跟着内容走就会忽高忽低；
     * 统一钉死，切分类时面板不长不短、位置也不跳。
     */
    val FixedPanelHeight = 368.dp

    /** 面板出现时长。 */
    const val EnterDurationMillis = 180

    /** 面板收起时长：比出现短一档，收起不拖沓。 */
    const val ExitDurationMillis = 130
}

/**
 * 面板卡外观：投影 + 深色底 + 一圈细描边。
 *
 * 投影必须有——面板压在视频画面上，没有投影会分不清哪里是画面、哪里是面板。
 */
internal fun Modifier.playerPanelSurface(): Modifier =
    shadow(elevation = 16.dp, shape = PlayerPanelDefaults.PanelShape)
        .background(PlayerPanelDefaults.Container, PlayerPanelDefaults.PanelShape)
        .border(1.dp, PlayerPanelDefaults.Outline, PlayerPanelDefaults.PanelShape)

/**
 * 播放器弹出面板。
 *
 * @param title 面板标题（分类名）
 * @param icon 标题前置图标，画在一枚主色淡底圆片里
 * @param subtitle 标题下方的一行说明，可空
 * @param modifier 修饰符
 * @param content 面板内容
 */
@Composable
internal fun PlayerPanel(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .playerPanelSurface()
                .padding(PlayerPanelDefaults.PanelPadding),
        verticalArrangement = Arrangement.spacedBy(PlayerPanelDefaults.ItemSpacing),
    ) {
        // 注意：这里**不能** fillMaxWidth。面板靠内容撑宽，标题行一旦撑满，
        // 整张卡就会变成全屏宽，锚定到按钮上方时内容会跑到屏幕外。
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    modifier = Modifier.widthIn(max = 200.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = PlayerPanelDefaults.Content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        modifier = Modifier.widthIn(max = 200.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = PlayerPanelDefaults.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        content()
    }
}

/** 列表项尾部形态。 */
internal enum class PlayerPanelItemTrailing {
    /** 不显示。 */
    None,

    /** 选中打勾。 */
    Check,

    /** 可进入下一级的箭头。 */
    Chevron,
}

/**
 * 面板列表项。
 *
 * 三种状态分开表达：**聚焦**整块反色（浅底深字），**选中**主色淡底 + 主色文字，
 * 其余为透明底浅色文字。不缩放——面板紧贴画面，缩放会连带糊边。
 *
 * @param text 主文本
 * @param selected 是否是当前生效的那一项
 * @param onClick 点击回调
 * @param modifier 修饰符
 * @param leadingIcon 前置图标
 * @param onFocus 获得焦点回调
 * @param trailing 尾部形态，默认按 [selected] 自动取打勾
 * @param trailingContent 自定义尾部内容；非空时优先于 [trailing]
 */
@Composable
internal fun PlayerPanelItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onFocus: () -> Unit = {},
    trailing: PlayerPanelItemTrailing =
        if (selected) PlayerPanelItemTrailing.Check else PlayerPanelItemTrailing.None,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier =
            modifier
                .onFocusChanged { if (it.isFocused) onFocus() }
                .touchClickable(onClick = onClick),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(PlayerPanelDefaults.ItemShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        PlayerPanelDefaults.Content
                    },
                focusedContainerColor = PlayerPanelDefaults.FocusedContainer,
                focusedContentColor = PlayerPanelDefaults.FocusedContent,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PlayerPanelDefaults.ItemMinHeight)
                    .padding(horizontal = PlayerPanelDefaults.ItemHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(PlayerPanelDefaults.ItemIconSize),
                )
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingContent != null) {
                trailingContent()
            } else {
                when (trailing) {
                    PlayerPanelItemTrailing.Check ->
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(PlayerPanelDefaults.ItemIconSize),
                        )

                    PlayerPanelItemTrailing.Chevron ->
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(PlayerPanelDefaults.ItemIconSize),
                        )

                    PlayerPanelItemTrailing.None -> Unit
                }
            }
        }
    }
}

/** 面板内的横向分隔线。 */
@Composable
internal fun PlayerPanelDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(PlayerPanelDefaults.Outline),
    )
}
