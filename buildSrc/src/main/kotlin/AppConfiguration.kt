/**
 * 应用全局配置。
 *
 * 集中管理包名、SDK 版本、版本号等构建配置，
 * 供各模块 build.gradle.kts 引用。
 */
object AppConfiguration {
    /** 源码命名空间（R 类 / BuildConfig 所在包），与源码包结构保持一致，不要随意改动。 */
    const val appId = "dev.frost819.newbv"

    /** 对外应用包名（设备上看到的 applicationId）。 */
    const val applicationId = "cn.bvtv.beta"
    const val compileSdk = 36
    const val minSdk = 21
    const val targetSdk = 36

    /** 对外展示的版本名。 */
    const val versionName = "0.1Beta1"

    /** 内部版本号。 */
    const val versionCode = 1
}
