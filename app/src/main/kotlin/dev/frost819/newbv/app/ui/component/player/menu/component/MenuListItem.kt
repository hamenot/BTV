package dev.frost819.newbv.app.ui.component.player.menu.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanelItem
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanelItemTrailing

/**
 * 菜单列表项。
 *
 * 视觉由播放器弹出面板统一定义（[PlayerPanelItem]）：圆角行、聚焦整块反色、
 * 选中项主色淡底 + 主色文字 + 打勾。
 *
 * 早期版本这里是「聚焦时展开文字、失焦时收缩成图标」的宽度动画；
 * 换成固定宽度的面板卡之后，这段动画只会让面板左右抖动，故去掉。
 *
 * @param modifier 修饰符
 * @param text 文本内容
 * @param icon 前置图标
 * @param selected 是否选中
 * @param onFocus 获得焦点回调
 * @param onClick 点击回调
 * @param trailing 尾部形态，默认按 [selected] 自动取打勾
 */
@Composable
internal fun MenuListItem(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector? = null,
    selected: Boolean,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
    trailing: PlayerPanelItemTrailing =
        if (selected) PlayerPanelItemTrailing.Check else PlayerPanelItemTrailing.None,
) {
    PlayerPanelItem(
        modifier = modifier,
        text = text,
        selected = selected,
        onClick = onClick,
        leadingIcon = icon,
        onFocus = onFocus,
        trailing = trailing,
    )
}
