package dev.frost819.newbv.danmaku.util

import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMask
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMaskFrame
import dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMaskSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 弹幕蒙版帧查找器。
 *
 * 根据当前播放位置查找对应的 [DanmakuMaskFrame]。
 * 内部按需解压蒙版数据（只解压当前 segment），避免一次性加载全部帧。
 *
 * **使用注意**：
 * - 切集或开关切换时调用 [reset] 清除缓存
 * - [findFrame] 是挂起函数，内部解压操作在 [Dispatchers.Default] 执行
 *
 * @see DanmakuMask
 * @see DanmakuMaskFrame
 */
class DanmakuMaskFinder {
    @Volatile
    private var cachedSegment: DanmakuMaskSegment? = null

    /**
     * 保护解压临界区。
     *
     * 没有它时，快速 seek 或并发调用会让多个协程同时对同一个 segment 做一遍
     * gzip 解压，白白浪费 CPU 并互相覆盖。
     */
    private val loadLock = Any()

    /**
     * 清除缓存的 segment。
     * 在切集、开关切换等导致蒙版数据变化时调用。
     */
    fun reset() {
        synchronized(loadLock) { cachedSegment = null }
    }

    /**
     * 根据当前播放时间查找对应帧。
     *
     * @param mask         [DanmakuMask] 蒙版对象（从 [dev.frost819.newbv.biliapi.entity.danmaku.DanmakuMask] 获取）
     * @param currentTime  当前播放时间（毫秒）
     * @return 当前应渲染的 [DanmakuMaskFrame]，无则返回 null
     */
    suspend fun findFrame(
        mask: DanmakuMask,
        currentTime: Long,
    ): DanmakuMaskFrame? {
        var cached = cachedSegment
        if (cached == null || currentTime !in cached.range) {
            cached =
                withContext(Dispatchers.Default) {
                    // 双检：等待锁期间可能已有别的协程解压好了目标 segment
                    cachedSegment?.takeIf { currentTime in it.range }
                        ?: synchronized(loadLock) {
                            cachedSegment?.takeIf { currentTime in it.range }
                                ?: mask.getSegmentAt(currentTime).also { cachedSegment = it }
                        }
                }
        }
        return cached?.findFrameAt(currentTime)
    }
}

/**
 * 在 segment 内按时间二分查找对应帧。
 *
 * 帧按时间升序且区间连续，可以二分。原实现用 `frames.lastOrNull { }` 做反向全扫，
 * 而一个 segment 覆盖约 10 秒 @ ~33ms/帧 ≈ 300 帧，轮询间隔最低只有 20ms，
 * 相当于每一帧都要跑一遍 300 次区间判断 —— 这是播放时 CPU 常驻占用的主要来源。
 */
private fun DanmakuMaskSegment.findFrameAt(currentTime: Long): DanmakuMaskFrame? {
    val frames = this.frames
    if (frames.isEmpty() || currentTime !in range) return null

    var lo = 0
    var hi = frames.lastIndex
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        when {
            currentTime < frames[mid].range.first -> hi = mid - 1
            currentTime > frames[mid].range.last -> lo = mid + 1
            else -> return frames[mid]
        }
    }
    return null
}

/**
 * 计算弹幕蒙版轮询的休眠时间。
 *
 * 逻辑：
 * - 有蒙版 + 播放中：休眠到蒙版结束（限制在 20~300ms 以便响应 Seek）
 * - 播放中无蒙版：正常轮询间隔 100ms
 * - 暂停或异常：降低频率 200ms
 *
 * @param currentFrame 当前蒙版帧，null 表示无有效蒙版
 * @param currentTime  当前播放时间（毫秒）
 * @param isPlaying    是否正在播放
 * @return 下次轮询前应休眠的毫秒数
 */
fun calculateMaskDelay(
    currentFrame: DanmakuMaskFrame?,
    currentTime: Long,
    isPlaying: Boolean,
): Long =
    when {
        currentFrame != null && isPlaying -> {
            (currentFrame.range.last - currentTime).coerceIn(20L, 300L)
        }
        isPlaying -> 100L
        else -> 200L
    }
