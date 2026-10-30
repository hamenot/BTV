package dev.frost819.newbv.biliapi.repositories

import dev.frost819.newbv.biliapi.http.BiliHttpApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths
import java.util.Properties

/**
 * 临时复现：冷启动后第一次进 UP 页（Web 空间投稿接口）失败、第二次成功的时序。
 * 复现完即删，不进版本库。
 */
class UpPageFirstLoadReproTest {
    companion object {
        private val localProperties =
            Properties().apply {
                val path = Paths.get("../local.properties").toAbsolutePath().toString()
                load(File(path).bufferedReader())
            }
        val SESSDATA: String =
            runCatching { localProperties.getProperty("test.sessdata") }.getOrNull() ?: ""
        val BILI_JCT: String =
            runCatching { localProperties.getProperty("test.bili_jct") }.getOrNull() ?: ""
        val UID: Long =
            runCatching { localProperties.getProperty("test.uid") }.getOrNull()?.toLongOrNull() ?: 2
        val ACCESS_TOKEN: String =
            runCatching { localProperties.getProperty("test.access_token") }.getOrNull() ?: ""
        val BUVID: String =
            runCatching { localProperties.getProperty("test.buvid") }.getOrNull() ?: ""
    }

    /** 模拟 App 冷启动：init 后立刻发起 UP 页请求（WBI keys 尚未就绪，与 App 时序一致）。 */
    @Test
    fun `cold start first up page request`() =
        runBlocking {
            // 不传 buvid3，模拟"本地 buvid 尚未注册"的最坏情况；也单独跑一次带 buvid3 的
            BiliHttpApi.init(
                buvid3 = BUVID,
                sessData = SESSDATA,
                biliJct = BILI_JCT,
                mid = UID,
                accessToken = ACCESS_TOKEN,
            )

            val repository = UserRepository(AuthRepository(), ChannelRepository())

            repeat(3) { attempt ->
                try {
                    val response = BiliHttpApi.getWebUserSpaceVideos(mid = 2)
                    println("attempt #$attempt -> code=${response.code}, message=${response.message}, " +
                        "vlist=${response.data?.list?.vlist?.size}")
                } catch (e: Exception) {
                    println("attempt #$attempt -> EXCEPTION: ${e::class.simpleName}: ${e.message}")
                }
                // 第二次起模拟用户退出再进（间隔 ~1s）
                kotlinx.coroutines.delay(1000)
            }

            println("wbiImgKey=${BiliHttpApi.wbiImgKey != null}, wbiSubKey=${BiliHttpApi.wbiSubKey != null}")
        }
}
