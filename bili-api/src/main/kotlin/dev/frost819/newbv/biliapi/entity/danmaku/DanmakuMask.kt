package dev.frost819.newbv.biliapi.entity.danmaku

import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.decodeBase64
import okio.GzipSource
import okio.buffer
import okio.source
import java.io.InputStream

data class DanmakuMaskSegment(
    val range: LongRange,
    val frames: List<DanmakuMaskFrame>,
)

sealed class DanmakuMaskFrame(
    open val range: LongRange,
)

data class DanmakuWebMaskFrame(
    override val range: LongRange,
    val svg: String,
) : DanmakuMaskFrame(range)

data class DanmakuMobMaskFrame(
    override val range: LongRange,
    val width: Int,
    val height: Int,
    val image: ByteArray,
) : DanmakuMaskFrame(range) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DanmakuMobMaskFrame) return false
        return range == other.range &&
            width == other.width &&
            height == other.height &&
            image.contentEquals(other.image)
    }

    override fun hashCode(): Int {
        var r = range.hashCode()
        r = 31 * r + width
        r = 31 * r + height
        r = 31 * r + image.contentHashCode()
        return r
    }
}

// ── 内部目录条目：只记录时间范围 + 压缩数据在文件中的字节偏移，不持有数据 ──
private data class SegmentEntry(
    // segment 对应的播放时间范围
    val segRange: LongRange,
    // 该 segment 的 gzip 压缩块（原始字节，未解压）
    val compressedBytes: ByteArray,
)

/**
 * 返回一个结束时间被延伸到 [end] 的帧副本。
 *
 * 仅用于补齐段尾空洞（见 [DanmakuMask.decompressEntry]），不对外暴露。
 */
private fun DanmakuMaskFrame.withEnd(end: Long): DanmakuMaskFrame =
    when (this) {
        is DanmakuWebMaskFrame -> copy(range = range.first until end)
        is DanmakuMobMaskFrame -> copy(range = range.first until end)
    }

// ── 主数据类 ─────────────────────────────────────────────────────────────────

/**
 * DanmakuMask 加载后只保存每个 segment 的压缩字节块（SegmentEntry），
 * 不预先解压全部帧。调用 [getSegments] 时按需解压、用完即可被 GC。
 *
 * 内存模型：
 *   旧方案：所有帧像素全部展开常驻堆  →  几百 MB
 *   新方案：只持有压缩字节（通常是展开后的 1/5~1/10）  →  可控
 */
class DanmakuMask private constructor(
    val type: DanmakuMaskType,
    private val entries: List<SegmentEntry>,
) {
    /**
     * 解压并返回当前播放时间（ms）对应的 segment。
     * 每次调用只解压一个 segment，解压结果不被 DanmakuMask 持有，用完可被 GC。
     */
    fun getSegmentAt(positionMs: Long): DanmakuMaskSegment? {
        val entry = entries.firstOrNull { positionMs in it.segRange } ?: return null
        return decompressEntry(entry, type)
    }

    val segmentCount: Int get() = entries.size

    /**
     * 兼容旧接口：返回所有 segments（逐个按需解压）。
     * ⚠️ 对超长视频慎用，会把所有 segment 同时留在内存。
     * 建议改用 [getSegmentAt]。
     */
    val segments: List<DanmakuMaskSegment> by lazy {
        entries.map { decompressEntry(it, type) }
    }

    // ── 解压单个 segment ────────────────────────────────────────────────────
    private fun decompressEntry(
        entry: SegmentEntry,
        type: DanmakuMaskType,
    ): DanmakuMaskSegment {
        val compressedBuffer = Buffer().write(entry.compressedBytes)
        val frames = mutableListOf<DanmakuMaskFrame>()
        var lastTime = entry.segRange.first // 注：segment 内帧时间是连续的，首帧 range 起点在解压时确定

        // 解压总量预算。
        // gzip 是压缩格式，服务端数据被污染（或中间人篡改）时，几 KB 压缩块可以
        // 展开成上百 MB；没有预算的话 readByteArray 会在 IO 线程直接把堆撑爆。
        // 正常数据：一个 segment 约 10s @ 30fps，MobMask 单帧 ≤ 1MB，总量远低于此上限。
        var decompressedBytes = 0L

        GzipSource(compressedBuffer).buffer().use { gz ->
            when (type) {
                DanmakuMaskType.WebMask -> {
                    while (!gz.exhausted() && frames.size < MAX_FRAMES_PER_SEGMENT) {
                        val svgLength = gz.readInt().toLong()
                        val time = gz.readLong()
                        // 长度字段直接来自网络，负数/超大值都是非法数据：
                        // require() 对负数会抛，readUtf8(巨大值) 会直接 OOM。
                        if (svgLength <= 0 || svgLength > MAX_SVG_BYTES) break
                        decompressedBytes += svgLength
                        if (decompressedBytes > MAX_DECOMPRESSED_BYTES) break
                        gz.require(svgLength)
                        val raw = gz.readUtf8(svgLength)
                        val commaIdx = raw.indexOf(',')
                        val b64 =
                            (if (commaIdx != -1) raw.substring(commaIdx + 1) else raw)
                                .replace("\n", "")
                        val svg = b64.decodeBase64()?.utf8() ?: ""
                        if (time <= lastTime) continue // 时间戳回退：丢弃该帧而不是生成非法区间
                        frames.add(DanmakuWebMaskFrame(range = lastTime until time, svg = svg))
                        lastTime = time
                    }
                }

                DanmakuMaskType.MobMask -> {
                    while (!gz.exhausted() && frames.size < MAX_FRAMES_PER_SEGMENT) {
                        val width = gz.readShort().toInt()
                        val height = gz.readShort().toInt()
                        val time = gz.readLong()
                        // 用 Long 计算：width/height 各自可达 32767，
                        // 用 Int 相乘再 +7 在极端组合下会溢出成负数。
                        val pixelCount = width.toLong() * height
                        val imageSize = (pixelCount + 7) / 8
                        // 尺寸上限与 MaskModifiers 的渲染端保持一致，
                        // 防止伪造的声明尺寸在解压阶段就把内存吃光。
                        if (width <= 0 || height <= 0) break
                        if (pixelCount > MAX_MASK_PIXELS || imageSize > MAX_IMAGE_BYTES) break
                        decompressedBytes += imageSize
                        if (decompressedBytes > MAX_DECOMPRESSED_BYTES) break
                        gz.require(imageSize)
                        val image = gz.readByteArray(imageSize) // 仅当前帧
                        if (time <= lastTime) continue // 时间戳回退：丢弃该帧而不是生成非法区间
                        frames.add(DanmakuMobMaskFrame(lastTime until time, width, height, image))
                        lastTime = time
                    }
                }
            }
        }

        // 关闭段尾空洞：webmask 每段约 10s，段内帧按 ~33ms（30fps）推进，但服务端在该段
        // 通常少生成最后一帧，使最后帧结束时间比下一段起始时间早一帧（~33ms）。若不补齐，
        // findFrame 在该空洞内返回 null，蒙版会在每段边界短暂消失（表现为每隔数秒闪烁一下）。
        // segRange.last + 1 恰为下一段起始时间；仅末段等于 Long.MAX_VALUE，此时不能延伸，
        // 否则蒙版会在蒙版数据结束后一直残留。
        val segmentEnd = entry.segRange.last + 1
        if (segmentEnd != Long.MAX_VALUE && frames.isNotEmpty()) {
            val last = frames.last()
            frames[frames.lastIndex] = last.withEnd(maxOf(last.range.last + 1, segmentEnd))
        }

        return DanmakuMaskSegment(range = entry.segRange, frames = frames)
    }

    companion object {
        /** segment 数量上限：蒙版按 ~10s 一段，超长视频也不会超过几百段。 */
        private const val MAX_SEGMENTS = 5_000

        /** 单个 segment 的帧数上限：正常约 300 帧（10s @ 30fps），留出余量。 */
        private const val MAX_FRAMES_PER_SEGMENT = 1_000

        /** 单帧 SVG 的字节数上限（8 MB）。 */
        private const val MAX_SVG_BYTES = 8L * 1024 * 1024

        /** 单帧位图的字节数上限（4 MB，即 1bpp 下约 3200 万像素）。 */
        private const val MAX_IMAGE_BYTES = 4L * 1024 * 1024

        /** 单帧声明的像素数上限，与 `MaskModifiers.MAX_MASK_PIXELS` 保持一致。 */
        private const val MAX_MASK_PIXELS = 4_000_000L

        /** 单个 segment 解压总量的预算（64 MB）。 */
        private const val MAX_DECOMPRESSED_BYTES = 64L * 1024 * 1024

        /** 推荐：流式读取，只把每个 segment 的压缩块存入内存，不解压 */
        fun fromStream(
            input: InputStream,
            type: DanmakuMaskType,
        ): DanmakuMask = input.source().buffer().use { parseFromSource(it, type) }

        /** 兼容旧调用 */
        fun fromBinary(
            binary: ByteArray,
            type: DanmakuMaskType,
        ): DanmakuMask = parseFromSource(Buffer().write(binary), type)

        private fun parseFromSource(
            source: BufferedSource,
            type: DanmakuMaskType,
        ): DanmakuMask {
            val magic = source.readByteString(4)
            require(magic.utf8() == "MASK") { "Not a mask file" }

            val version = source.readInt()
            source.skip(4)
            val size = source.readInt()

            // size 直接来自文件头，属于不可信输入：
            // 负数会让 LongArray 抛 NegativeArraySizeException，超大值则按 16B/项
            // 白白吃掉堆内存（10 分钟视频约 60 段，正常值远小于此上限）。
            require(size in 0..MAX_SEGMENTS) { "Invalid mask segment count: $size" }

            val times = LongArray(size)
            val offsets = LongArray(size)
            for (i in 0 until size) {
                times[i] = source.readLong()
                offsets[i] = source.readLong()
            }

            val entries = ArrayList<SegmentEntry>(size)
            var segLastTime = 0L

            for (i in 0 until size) {
                // 只读压缩字节，不解压
                val compressedBuffer = Buffer()
                if (i == size - 1) {
                    source.readAll(compressedBuffer)
                } else {
                    // offset 必须单调递增，否则差值可能为负，require 会直接抛
                    val compressedSize = offsets[i + 1] - offsets[i]
                    if (compressedSize <= 0) continue
                    source.require(compressedSize)
                    source.read(compressedBuffer, compressedSize)
                }

                // 压缩块通常只有解压后的 1/5~1/10，保存这个而非展开数据
                val compressedBytes = compressedBuffer.readByteArray()

                val startTime = segLastTime
                val endTime = if (i == size - 1) Long.MAX_VALUE else times[i + 1]
                entries.add(SegmentEntry(startTime until endTime, compressedBytes))
                segLastTime = endTime
            }

            return DanmakuMask(type, entries)
        }
    }
}

enum class DanmakuMaskType {
    WebMask,
    MobMask,
}
