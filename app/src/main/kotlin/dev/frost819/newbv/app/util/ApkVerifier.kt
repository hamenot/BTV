package dev.frost819.newbv.app.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * 安装包签名校验器。
 *
 * 自动更新流程会把下载到 `cacheDir/update_downloader` 的 APK 交给系统安装器，
 * 在此之前必须确认该 APK 的签名证书与**当前运行的应用**一致。
 *
 * 为什么必须校验：更新包来自网络，下载链路（系统 CA 信任范围、CDN 主机名校验、
 * GitHub 资源跳转）任一环节被突破，都会让用户装上第三方签名的「同名应用」，
 * 即使用户手动确认安装也无法从系统层面识别。签名比对是唯一能把关的位置。
 *
 * 只比对签名证书存在性而不比对版本号：版本号在 [dev.frost819.newbv.app.network.GithubApi]
 * 的更新检查里已经判过，这里只关心「是不是同一个开发者签的」。
 */
object ApkVerifier {
    /**
     * 判断 [apkFile] 的签名证书是否与当前应用一致。
     *
     * @param context 任意 Context（内部取 applicationContext 的 PackageManager）
     * @param apkFile 待校验的 APK 文件
     * @return `true` 表示至少有一个签名证书与当前应用相同；文件缺失、解析失败、
     *   未签名或签名不匹配均返回 `false`
     */
    fun isSignedBySelf(
        context: Context,
        apkFile: File,
    ): Boolean {
        if (!apkFile.isFile || apkFile.length() == 0L) return false

        val packageManager = context.applicationContext.packageManager
        val flags = signFlag()
        val archiveInfo =
            runCatching {
                packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }.getOrNull() ?: return false
        val selfInfo =
            runCatching {
                packageManager.getPackageInfo(context.applicationContext.packageName, flags)
            }.getOrNull() ?: return false

        val archiveDigests = signerDigests(archiveInfo)
        if (archiveDigests.isEmpty()) return false
        // 交集非空即视为同一签名者：签名密钥轮转后新旧证书可能同时存在
        return archiveDigests.any { it in signerDigests(selfInfo) }
    }

    /** 按系统版本选择签名查询 flag（[PackageManager.GET_SIGNING_CERTIFICATES] 需 API 28）。 */
    private fun signFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    /**
     * 提取 [info] 中所有签名证书的 SHA-256 摘要。
     *
     * API 28 起走 [PackageInfo.signingInfo]（支持密钥轮转），更低版本回退到
     * 已废弃的 [PackageInfo.signatures]。
     */
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                info.signatures.orEmpty()
            }
        return signatures
            .map { signature ->
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString(separator = "") { byte -> "%02x".format(byte) }
            }.toSet()
    }
}
