package dev.frost819.newbv.app.ui.component.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import dev.frost819.newbv.core.focus.focusInvertedColors
import dev.frost819.newbv.core.focus.touchClickable

/**
 * 图标字形来源：工程内的 drawable 或 Material 图标。
 *
 * 设置类按钮直接用 Material 图标，和设置面板左侧导航用的是同一套图，
 * 不会出现"按钮一个样、面板里另一个样"。
 */
internal sealed interface PlayerIconGraphic {
    /** 工程内的矢量 drawable。 */
    data class Resource(
        @param:DrawableRes val id: Int,
    ) : PlayerIconGraphic

    /** Material 图标。 */
    data class Vector(
        val image: ImageVector,
    ) : PlayerIconGraphic
}

/**
 * 控制条上的一个操作按钮。
 *
 * @param graphic 字形
 * @param description 无障碍描述，同时作为 key 用
 * @param active 开关类按钮的"已开启"状态，开启时字形染主色
 */
internal data class PlayerIcon(
    val graphic: PlayerIconGraphic,
    val description: String,
    val active: Boolean = false,
    val action: () -> Unit,
)

/** 用 drawable 资源构造按钮。 */
internal fun drawableIcon(
    @DrawableRes id: Int,
    description: String,
    action: () -> Unit,
    active: Boolean = false,
): PlayerIcon = PlayerIcon(PlayerIconGraphic.Resource(id), description, active, action)

/** 用 Material 图标构造按钮。 */
internal fun vectorIcon(
    image: ImageVector,
    description: String,
    active: Boolean = false,
    action: () -> Unit,
): PlayerIcon = PlayerIcon(PlayerIconGraphic.Vector(image), description, active, action)

/**
 * 播放器控制条图标按钮：44dp 正圆、透明底，聚焦时整块反色（浅底深色图标）。
 *
 * 点播与直播两个播放器共用本组件，保证两边手感一致。
 *
 * 不做缩放——播放器画面里缩放会让按钮边缘和视频糊在一起，反色本身已经足够醒目。
 * 开关类按钮"已开启"时把字形染成主色而不是换字形：换字形很容易把描边体图标
 * 换成实心块（比如 Material 的 `_on` 系列），一行图标就会轻重不一。
 */
@Composable
internal fun PlayerIconButton(
    item: PlayerIcon,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.touchClickable(onClick = item.action),
        onClick = item.action,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            focusInvertedColors(
                containerColor = Color.Transparent,
                contentColor = PlayerControlsDefaults.Content,
            ),
        // 图标颜色必须由 Surface 下传，聚焦时才会跟着容器一起反色。
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        val tint = if (item.active) MaterialTheme.colorScheme.primary else LocalContentColor.current
        Box(
            modifier =
                Modifier
                    .size(PlayerControlsDefaults.IconButtonSize)
                    .padding(PlayerControlsDefaults.IconPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val graphic = item.graphic) {
                is PlayerIconGraphic.Resource ->
                    Icon(
                        painter = painterResource(id = graphic.id),
                        contentDescription = item.description,
                        modifier = Modifier.size(PlayerControlsDefaults.IconSize),
                        tint = tint,
                    )

                is PlayerIconGraphic.Vector ->
                    Icon(
                        imageVector = graphic.image,
                        contentDescription = item.description,
                        modifier = Modifier.size(PlayerControlsDefaults.IconSize),
                        tint = tint,
                    )
            }
        }
    }
}
