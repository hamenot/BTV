package dev.frost819.newbv.app.ui.component.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.frost819.newbv.app.entity.player.SponsorBlockSegment
import dev.frost819.newbv.core.theme.BVTheme

/**
 * 视频进度条。
 *
 * 版式对齐 Animeko TV 的进度条：
 *
 * - 可视轨道只有 **6dp** 厚、两端全圆角；
 * - 触摸/对焦区高度 **24dp**，所以不需要再在外面套一圈描边来"表示可聚焦"——
 *   聚焦状态由**轨道上出现的拇指圆点**表达（圆点只在聚焦/拖动时出现，静止时是干净的一条线）；
 * - 三段颜色：已播放 = 品牌主色，已缓冲 = 白 40%，未缓冲 = 白 16%；
 * - 拇指是一枚 **半径 8dp 的实心主色圆**（Animeko `MediaProgressSlider` 的取值，
 *   他们的 thumb 画布是 12dp × 24dp、`drawCircle(radius = 8.dp)`），
 *   因为容器高 24dp，圆点正好完整落在里面。
 *
 * 早先这里是在进度条外面套 1dp 描边圆角框来表示焦点，方框紧贴一根 8dp 细线，
 * 四角永远对不上轨道的圆角，看起来就是一个多余的空心方框，故去掉。
 *
 * 常显模式（[isPersistentSeek] = true）用于播放器底部常驻的细进度条：
 * 6dp、无缓冲段、无拇指。
 *
 * @param modifier 修饰符
 * @param duration 视频总时长（毫秒）
 * @param position 当前播放位置（毫秒）
 * @param bufferedPercentage 缓冲百分比（0-100）
 * @param isPersistentSeek 是否为常显细条模式
 * @param focused 是否处于焦点（聚焦时显示拇指圆点）
 * @param sponsorSegments 空降片段（SponsorBlock）标记，按当前时长比例画在轨道上
 */
@Composable
fun VideoProgressSeek(
    modifier: Modifier = Modifier,
    duration: Long,
    position: Long,
    bufferedPercentage: Int,
    isPersistentSeek: Boolean,
    focused: Boolean = false,
    sponsorSegments: List<SponsorBlockSegment> = emptyList(),
) {
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val bufferedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

    val trackHeight =
        if (isPersistentSeek) {
            ProgressSeekDefaults.PersistentTrackHeight
        } else {
            ProgressSeekDefaults.TrackHeight
        }
    val containerHeight = if (isPersistentSeek) trackHeight else ProgressSeekDefaults.TouchHeight
    val progressRatio = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    // 空降片段标记：不按类别配色，统一用当前强调色（primary）加深 40%，
    // 视觉上比播放进度暗一档，可辨识但不抢眼。
    val sponsorMarkerColor = lerp(activeTrackColor, Color.Black, 0.4f)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(containerHeight),
    ) {
        val trackPx = trackHeight.toPx()
        val centerY = size.height / 2f

        // 背景轨道：铺满整宽。用 drawRoundRect 而非 drawLine(Round cap)——
        // 圆头会向两端各伸出 trackPx/2，铺满整宽时伸出画布被裁平角，
        // 常驻细条两端就丢了圆角；drawRoundRect 的圆角严格落在画布内
        drawRoundRect(
            color = inactiveTrackColor,
            topLeft = Offset(0f, centerY - trackPx / 2f),
            size = Size(size.width, trackPx),
            cornerRadius = CornerRadius(trackPx / 2f),
        )

        // 缓冲进度（常显细条不画，避免底部常驻条视觉过重）。
        // 同样用 drawRoundRect：drawLine 的圆头在 x=0 处伸出画布会被裁平，
        // 盖住底轨的左圆角；宽度不足一个线宽时保底为线宽（呈现为圆头小点）
        if (!isPersistentSeek && bufferedPercentage > 0) {
            val bufferedWidth = (size.width * bufferedPercentage / 100f).coerceAtLeast(trackPx)
            drawRoundRect(
                color = bufferedTrackColor,
                topLeft = Offset(0f, centerY - trackPx / 2f),
                size = Size(bufferedWidth, trackPx),
                cornerRadius = CornerRadius(trackPx / 2f),
            )
        }

        // 空降片段标记（SponsorBlock）：画在播放进度**下方**——已播放部分由进度色覆盖，
        // 未播放部分露出标记；短片段保底 2dp 可见
        if (duration > 0 && sponsorSegments.isNotEmpty()) {
            val minSpanPx = 2.dp.toPx()
            sponsorSegments.forEach { segment ->
                val startRatio = (segment.startSec * 1000.0 / duration).coerceIn(0.0, 1.0)
                val endRatio = (segment.endSec * 1000.0 / duration).coerceIn(0.0, 1.0)
                val left = (size.width * startRatio).toFloat()
                val right = (size.width * endRatio).toFloat().coerceAtLeast(left + minSpanPx)
                drawRoundRect(
                    color = sponsorMarkerColor,
                    topLeft = Offset(left, centerY - trackPx / 2f),
                    size = Size(right - left, trackPx),
                    cornerRadius = CornerRadius(trackPx / 2f),
                )
            }
        }

        // 播放进度（最后画：位于标记之上）。drawRoundRect 左端与底轨左圆角对齐，
        // 右端自然圆头；0% 不画（避免半个圆点贴在画布左缘）
        if (duration > 0 && progressRatio > 0f) {
            val progressWidth = (size.width * progressRatio).coerceAtLeast(trackPx)
            drawRoundRect(
                color = activeTrackColor,
                topLeft = Offset(0f, centerY - trackPx / 2f),
                size = Size(progressWidth, trackPx),
                cornerRadius = CornerRadius(trackPx / 2f),
            )
        }

        // 拇指：只在聚焦/拖动时出现（Animeko 的 `focused || scrubMillis != null`），
        // 端点处夹住，避免圆点在 0% / 100% 被画布裁掉半个。
        if (!isPersistentSeek && focused) {
            val radius = ProgressSeekDefaults.ThumbRadius.toPx()
            val x = (size.width * progressRatio).coerceIn(radius, size.width - radius)
            drawCircle(
                color = activeTrackColor,
                radius = radius,
                center = Offset(x, centerY),
            )
        }
    }
}

/** 进度条尺寸规格。 */
private object ProgressSeekDefaults {
    /** 交互模式下可视轨道厚度。 */
    val TrackHeight = 6.dp

    /** 交互模式下的对焦/触摸高度：比轨道高，保证遥控器与手指都好命中。 */
    val TouchHeight = 24.dp

    /** 常显细条模式下的轨道厚度（6dp，与交互模式一致）。 */
    val PersistentTrackHeight = 6.dp

    /** 聚焦时拇指圆的半径，取 Animeko 的同值（8dp）。 */
    val ThumbRadius = 8.dp
}

// region Previews

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VideoProgressSeekPersistentPreview() {
    BVTheme {
        VideoProgressSeek(
            duration = 100_000L,
            position = 35_000L,
            bufferedPercentage = 60,
            isPersistentSeek = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VideoProgressSeekFocusedPreview() {
    BVTheme {
        VideoProgressSeek(
            duration = 100_000L,
            position = 35_000L,
            bufferedPercentage = 60,
            isPersistentSeek = false,
            focused = true,
        )
    }
}

// endregion
