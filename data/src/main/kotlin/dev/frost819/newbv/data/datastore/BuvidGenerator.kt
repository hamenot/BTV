package dev.frost819.newbv.data.datastore

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * B 站设备标识（buvid）生成工具。
 *
 * 迁移自原版 `dev.aaa1115910.biliapi.http.util.Buvid`，算法保持一致以保证接口可用性。
 * buvid 用于 B 站接口的设备标识与风控关联，首次启动时自动生成并持久化。
 */
object BuvidGenerator {
    /**
     * 随机源。
     *
     * buvid 是设备的唯一身份标识，设备指纹的唯一性直接影响账号风控判定；
     * [Math.random] 是共享的伪随机序列（`Random`），在进程启动早期被并发调用时
     * 可预测且可能撞值，[SecureRandom] 则保证熵来源与线程安全。
     */
    private val random = SecureRandom()

    /**
     * 生成随机 buvid（`XY` 前缀格式）。
     *
     * 算法：随机生成 MAC 地址 → MD5 → 取固定位置字符拼接。
     * 结果形如 `XYxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`（共 35 字符）。
     *
     * @return 符合 B 站规则的随机 buvid 字符串。
     */
    fun generateBuvid(): String {
        val mac = mutableListOf<String>()
        repeat(6) {
            // %02x 补零：原来用 toString(16)，随机到 0x0a 以下时只产出 1 位，
            // 导致 MAC 段长度不一致、生成的 buvid 格式偏离 B 站预期。
            mac.add("%02x".format(random.nextInt(0x100)))
        }
        val md5 = md5(mac.joinToString(":"))
        val md5Arr = md5.split("").toTypedArray()
        return "XY${md5Arr[2]}${md5Arr[12]}${md5Arr[22]}$md5"
    }

    /**
     * 生成随机 buvid3（`infoc` 后缀格式）。
     *
     * 算法：UUID + 随机数字 + `infoc` 后缀。
     * 结果形如 `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxxNinfoc`。
     *
     * @return 符合 B 站 Web 端规则的随机 buvid3 字符串。
     */
    fun generateBuvid3(): String = "${UUID.randomUUID()}${random.nextInt(10)}infoc"

    /**
     * 计算 MD5 摘要（小写十六进制，32 位）。
     *
     * @param input 待计算字符串。
     * @return 32 位十六进制 MD5 摘要。
     */
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val messageDigest = md.digest(input.toByteArray())
        val no = BigInteger(1, messageDigest)
        var hashText = no.toString(16)
        while (hashText.length < 32) {
            hashText = "0$hashText"
        }
        return hashText
    }
}
