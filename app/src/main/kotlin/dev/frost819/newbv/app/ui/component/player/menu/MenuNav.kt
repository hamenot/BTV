package dev.frost819.newbv.app.ui.component.player.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.app.ui.component.player.ifElse
import dev.frost819.newbv.app.ui.component.player.menu.component.MenuListItem
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanelDefaults

/**
 * 菜单导航列表。
 *
 * 渲染分类 Tab，焦点移动即切换菜单面板。视频播放器与直播播放器共用，
 * 通过 [label]/[icon] 访问器适配各自的导航枚举。
 *
 * 分类项常驻「图标 + 文字」：面板卡宽度是固定的，早先那种"失焦收缩成图标"的
 * 宽度动画会让整块面板跟着变宽变窄，反而更难看清当前分类。
 *
 * @param items 导航项列表
 * @param selected 当前选中的导航项
 * @param label 导航项文本访问器
 * @param icon 导航项图标访问器
 * @param onSelectedChanged 选中变化回调
 * @param modifier 修饰符
 * @param focusRequester 导航列表焦点请求器，默认内部创建；直播侧需与值面板共享时由外部传入
 */
@Composable
fun <T> MenuNavList(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    onSelectedChanged: (T) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val restorerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LazyColumn(
        modifier =
            modifier
                .focusRestorer(restorerFocusRequester)
                .focusRequester(focusRequester),
        verticalArrangement = Arrangement.spacedBy(PlayerPanelDefaults.ItemSpacing),
        contentPadding = PaddingValues(0.dp),
    ) {
        itemsIndexed(items) { _, item ->
            // 焦点着陆点必须跟着"当前选中项"走，不能写死第一项：
            // 面板一打开导航列表就拿焦点，若落在第一项，onFocus 会立刻把选中态改成第一项，
            // 从控制条指定分类打开（比如画质）就会被悄悄重置回倍速。
            MenuListItem(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .ifElse(selected == item, Modifier.focusRequester(restorerFocusRequester)),
                text = label(item),
                icon = icon(item),
                selected = selected == item,
                onClick = { onSelectedChanged(item) },
                onFocus = { onSelectedChanged(item) },
            )
        }
    }
}
