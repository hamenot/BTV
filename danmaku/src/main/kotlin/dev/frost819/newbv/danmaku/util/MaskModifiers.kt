package dev.frost819.newbv.danmaku.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.caverock.androidsvg.SVG
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMaskFrame
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMobMaskFrame
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuWebMaskFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MaskModifiers"

/**
 * 单张蒙版位图的像素上限。
 *
 * 蒙版宽高直接来自网络解包，属于不可信输入。不做限制时，畸形数据可以声明出
 * 一个上亿像素的尺寸，让 `Bitmap.createBitmap` 直接 OOM 崩在 Composition 里。
 */
private const val MAX_MASK_PIXELS = 4_000_000L

/**
 * 蒙版帧 Bitmap 的三槽环形缓冲区。
 *
 * 播放中蒙版约每 33ms 换一帧，若只依赖 Java GC 回收，Bitmap 的像素内存位于
 * native heap，Dalvik 感知不到压力，回收会严重滞后 —— 实测几分钟即可堆到 OOM。
 *
 * 这里保留最近 2 张 Bitmap（留出 1 帧安全窗口，避免组合层刚换帧、绘制线程仍在
 * 用旧图时踩到 "trying to use a recycled bitmap"），更早的立即 [Bitmap.recycle]。
 */
private class MaskBitmapRing {
    private val lock = Any()
    private val ring = ArrayDeque<Bitmap>(3)

    fun push(bitmap: Bitmap) {
        var recycled: Bitmap? = null
        synchronized(lock) {
            ring.addLast(bitmap)
            while (ring.size > 2) recycled = ring.removeFirst()
        }
        recycled?.recycle()
    }

    fun clear() {
        val snapshot =
            synchronized(lock) {
                val all = ring.toList()
                ring.clear()
                all
            }
        snapshot.forEach { it.recycle() }
    }
}

/**
 * 在后台线程把蒙版帧渲染为 Bitmap。
 *
 * 原实现把这些工作放在 `remember {}` 的计算 lambda 里 —— 那是在**组合期间同步执行**的，
 * 也就是主线程。SVG 光栅化在低端电视盒子上单次可达数十毫秒，而蒙版约 33ms 换一帧，
 * 等于把主线程长期占满。
 *
 * 任何失败都退化为 null（不显示蒙版），绝不让异常冒泡到 Composition。
 */
private suspend fun renderMaskBitmap(frame: DanmakuMaskFrame): Bitmap? =
    runCatching {
        withContext(Dispatchers.Default) {
            when (frame) {
                is DanmakuWebMaskFrame -> renderWebMask(frame)
                is DanmakuMobMaskFrame -> decodeMobMask(frame)
            }
        }
    }.onFailure {
        Log.w(TAG, "Failed to render danmaku mask frame", it)
    }.getOrNull()

private fun renderWebMask(frame: DanmakuWebMaskFrame): Bitmap? {
    val svgDoc = runCatching { SVG.getFromString(frame.svg) }.getOrNull() ?: return null
    val width = svgDoc.documentWidth.toInt().coerceAtLeast(1)
    val height = svgDoc.documentHeight.toInt().coerceAtLeast(1)
    if (width.toLong() * height > MAX_MASK_PIXELS) return null

    val bmp =
        runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
            .getOrNull() ?: return null
    svgDoc.renderToCanvas(Canvas(bmp))
    return bmp
}

private fun decodeMobMask(frame: DanmakuMobMaskFrame): Bitmap? {
    val width = frame.width
    val height = frame.height
    if (width <= 0 || height <= 0) return null

    val pixels = width.toLong() * height
    if (pixels > MAX_MASK_PIXELS) return null

    // 不可信输入校验：image 是 1bit/pixel 的位图，理论长度 (w*h+7)/8。
    // 服务端数据截断或格式变更时若不做校验，frame.image[byteIndex] 会直接越界。
    val expectedBytes = (pixels + 7) / 8
    if (frame.image.size < expectedBytes) return null

    val bmp =
        runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
            .getOrNull() ?: return null

    val argb =
        IntArray(width * height) { i ->
            val byteIndex = i / 8
            val bit = (frame.image[byteIndex].toInt() shr (7 - (i % 8))) and 1
            if (bit == 1) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK
        }
    bmp.setPixels(argb, 0, width, 0, 0, width, height)
    return bmp
}

/**
 * 基础蒙版 Modifier：将 Bitmap 以 [BlendMode.DstIn] 方式叠加到内容上。
 *
 * 通过 saveLayer + DstIn 混合实现"挖空"效果，仅保留 Bitmap 中非透明区域的内容。
 *
 * @param bitmap      蒙版 Bitmap（ARGB_8888）
 * @param videoAspectRatio 视频宽高比（如 1920/1080 ≈ 1.777），用于将蒙版对齐到视频区域
 */
fun Modifier.bitmapMask(
    bitmap: Bitmap,
    videoAspectRatio: Float,
): Modifier =
    composed {
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        // Paint 提到 remember：原实现写在 drawIntoCanvas 里，每个绘制周期 new 一个，
        // 60fps 下就是每秒 60 次无谓分配。
        val layerPaint = remember { Paint() }

        drawWithContent {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                drawContent()

                val screenWidth = size.width
                val screenHeight = size.height
                val screenAspectRatio = screenWidth / screenHeight

                val dstWidth: Float
                val dstHeight: Float
                val offsetX: Float
                val offsetY: Float

                if (videoAspectRatio > screenAspectRatio) {
                    dstWidth = screenWidth
                    dstHeight = dstWidth / videoAspectRatio
                    offsetX = 0f
                    offsetY = (screenHeight - dstHeight) / 2f
                } else {
                    dstHeight = screenHeight
                    dstWidth = dstHeight * videoAspectRatio
                    offsetY = 0f
                    offsetX = (screenWidth - dstWidth) / 2f
                }

                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                    dstSize = IntSize(dstWidth.toInt(), dstHeight.toInt()),
                    blendMode = BlendMode.DstIn,
                )

                canvas.restore()
            }
        }
    }

/**
 * WebMask 蒙版 Modifier：将 SVG 数据渲染为 Bitmap 后应用蒙版。
 *
 * 渲染在后台线程完成，详见 [danmakuMask]。
 *
 * @param frame      WebMask 帧数据
 * @param aspectRatio 视频宽高比（用于蒙版对齐）
 */
fun Modifier.danmakuWebMask(
    frame: DanmakuWebMaskFrame,
    aspectRatio: Float,
): Modifier = danmakuMask(frame, aspectRatio)

/**
 * MobMask 蒙版 Modifier：将 1bpp 像素数据解码为 Bitmap 后应用蒙版。
 *
 * 解码在后台线程完成，且会校验 [DanmakuMobMaskFrame.image] 长度是否合法，
 * 详见 [danmakuMask]。
 *
 * @param frame      MobMask 帧数据
 * @param aspectRatio 视频宽高比（用于蒙版对齐）
 */
fun Modifier.danmakuMobMask(
    frame: DanmakuMobMaskFrame,
    aspectRatio: Float,
): Modifier = danmakuMask(frame, aspectRatio)

/**
 * 弹幕蒙版 Modifier（统一入口）。
 *
 * 自动根据 [DanmakuMaskFrame] 的具体类型（[DanmakuWebMaskFrame] / [DanmakuMobMaskFrame]）
 * 分发到对应的渲染实现。
 *
 * 相比旧实现的两点关键变化：
 * 1. 解码/渲染挪到 [Dispatchers.Default]，不再在组合期间阻塞主线程；
 * 2. 换帧时旧 Bitmap 由 [MaskBitmapRing] 主动 [Bitmap.recycle]，不再只等 GC。
 *
 * @param frame      蒙版帧数据，null 时不应用任何效果
 * @param aspectRatio 视频宽高比（用于蒙版对齐到视频区域）
 */
fun Modifier.danmakuMask(
    frame: DanmakuMaskFrame?,
    aspectRatio: Float,
): Modifier =
    composed {
        if (frame == null) return@composed this

        // 用时间区间起点做身份 key。
        // 旧实现用 frame 对象本身做 key，而 DanmakuMobMaskFrame.hashCode() 会对
        // 整段像素 ByteArray 做 contentHashCode() —— 每次重组都是一次 O(n) 哈希。
        val frameKey = frame.range.first

        val ring = remember { MaskBitmapRing() }
        DisposableEffect(Unit) {
            onDispose { ring.clear() }
        }

        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = frameKey) {
            val rendered = renderMaskBitmap(frame)
            if (rendered != null) {
                ring.push(rendered)
                value = rendered
            }
        }

        val current = bitmap ?: return@composed this
        bitmapMask(current, aspectRatio)
    }
