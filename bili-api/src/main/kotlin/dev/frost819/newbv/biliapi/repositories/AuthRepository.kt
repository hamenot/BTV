package dev.frost819.newbv.biliapi.repositories

class AuthRepository {
    var sessionData: String? = null
    var biliJct: String? = null
    var accessToken: String? = null
    var mid: Long? = null
    var buvid3: String? = null
    var buvid: String? = null
    var deviceCookies: String? = null

    /**
     * 当前是否处于有效的登录态。
     *
     * 不能用 `sessionData != null` 判断：应用启动时会把 `Prefs.sessData`
     * （未登录时为空字符串）无条件同步进来，null 判定会把游客误判为已登录
     * （例如详情页付费拦截对所有影片误提示"付费视频"）。
     * 空白字符串同样视为未登录。
     */
    fun hasValidSession(): Boolean = !sessionData.isNullOrBlank()
}
