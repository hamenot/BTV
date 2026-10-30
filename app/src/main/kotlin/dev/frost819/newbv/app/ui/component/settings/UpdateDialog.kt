package dev.frost819.newbv.app.ui.component.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.frost819.newbv.BuildConfig
import dev.frost819.newbv.app.network.GithubApi
import dev.frost819.newbv.app.network.entity.GithubRelease
import dev.frost819.newbv.app.util.ApkVerifier
import dev.frost819.newbv.app.util.CacheManager
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.log.Loggers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 更新检查弹窗。
 *
 * 打开后自动检查最新版本，支持下载 APK 并调起安装。
 * 下载进度通过 [LinearProgressIndicator] 实时显示。
 *
 * 下载走 [dev.frost819.newbv.app.network.GithubApi.DOWNLOAD_MIRRORS] 线路链，
 * 某条加速线路失效时会自动切换下一条；全部失败时把每条线路的失败原因
 * 展示给用户（只有「下载失败」四个字，用户和开发者都无从判断是网络还是线路问题）。
 *
 * Android 8.0 及以上系统会按来源应用管控「安装未知应用」权限。若未授权，
 * 安装器会被系统静默拦截（[android.content.Context.startActivity] 不抛异常），
 * 弹窗会停在「安装中」造成假死。因此调起安装前先检查该权限，未授权时明确
 * 提示用户自行到系统设置中授权后重试，不再展示无限等待状态。
 *
 * @param show 是否显示弹窗。
 * @param onHideDialog 关闭弹窗回调。
 */
@Composable
fun UpdateDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = Loggers.get("UpdateDialog")

    var updateStatus by remember { mutableStateOf(UpdateStatus.UpdatingInfo) }
    var bytesSentTotal by remember { mutableLongStateOf(0L) }
    var contentLength by remember { mutableLongStateOf(0L) }
    var targetProgress by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(targetValue = targetProgress, label = "update progress")
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var latestRelease by remember { mutableStateOf<GithubRelease?>(null) }
    // 暂存已下载的 APK，用户授权后重试安装时无需重新下载
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }
    // 下载失败的线路诊断信息，直接展示给用户，避免只看到「下载失败」四个字
    var downloadErrorMessage by remember { mutableStateOf("") }

    DisposableEffect(show) {
        if (!show) {
            downloadJob?.cancel()
        }
        onDispose {
            downloadJob?.cancel()
        }
    }

    /**
     * 从 release 中挑出 TV 版发布包。
     *
     * 与下载时的选包规则共用 [GithubRelease.tvAsset]，避免两处规则不一致导致
     * 「检查到更新但下载时找不到安装包」。返回可空类型而不是抛异常：
     * GitHub 未发布 release 或资产改名时，这里抛异常会直接崩掉设置页。
     */
    fun pickReleaseAsset(release: GithubRelease?) = release?.tvAsset()

    /**
     * 从资产名解析 versionCode。
     *
     * 命名约定 `BTV_<versionCode>_<versionName>_release.apk`，取第二段；
     * 命名改过时退化为「第一个纯数字段」；都解析不出则返回 null 而不是抛异常。
     */
    fun revisionOf(assetName: String): Int? {
        val segments = assetName.removeSuffix(".apk").split("_")
        return segments.getOrNull(1)?.toIntOrNull()
            ?: segments.firstNotNullOfOrNull { it.toIntOrNull() }
    }

    val checkUpdate: () -> Unit = {
        updateStatus = UpdateStatus.UpdatingInfo
        scope.launch(Dispatchers.IO) {
            val nextStatus =
                runCatching {
                    val release = GithubApi.getLatestBuild()
                    latestRelease = release
                    val asset = pickReleaseAsset(release) ?: error("未找到 TV 版发布包")
                    val revision =
                        revisionOf(asset.name)
                            ?: error("发布包版本号格式异常: ${asset.name}")
                    if (revision <= BuildConfig.VERSION_CODE) {
                        UpdateStatus.NoAvailableUpdate
                    } else {
                        UpdateStatus.Ready
                    }
                }.onFailure {
                    logger.error(it) { "Failed to get latest version" }
                }.getOrElse { UpdateStatus.CheckError }
            // Compose State 必须在主线程写：这里原本在 IO 线程直接赋值
            withContext(Dispatchers.Main) { updateStatus = nextStatus }
        }
    }

    /** 调起系统安装器安装已下载的 APK。 */
    val launchInstaller: (File) -> Unit = { file ->
        updateStatus = UpdateStatus.Installing
        runCatching {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.provider",
                    file,
                )
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(intent)
        }.onFailure {
            logger.error(it) { "Failed to launch installer" }
            updateStatus = UpdateStatus.InstallError
        }
    }

    /**
     * 校验签名后调起安装。
     *
     * 下载包先做签名比对，证书与本应用不一致时拒绝安装——更新包来自网络，
     * 只有签名比对能确认「这是同一个开发者签的包」。校验涉及读 APK 与解析证书，
     * 放到 IO 线程，避免在主线程做文件解析。
     */
    val installUpdate: (File) -> Unit = { file ->
        pendingInstallFile = file
        if (!context.canRequestPackageInstallPermission()) {
            // 未授权时系统会静默拦截安装器，直接进入「安装中」会假死
            updateStatus = UpdateStatus.InstallPermissionRequired
        } else {
            scope.launch {
                val verified = withContext(Dispatchers.IO) { ApkVerifier.isSignedBySelf(context, file) }
                if (verified) {
                    launchInstaller(file)
                } else {
                    logger.error { "APK signature mismatch, refuse to install: ${file.name}" }
                    updateStatus = UpdateStatus.SignatureMismatch
                }
            }
        }
    }

    /** 用户自行在系统设置中授权后，点击重试再次安装。 */
    val retryInstall: () -> Unit = {
        val file = pendingInstallFile
        if (file != null) installUpdate(file)
    }

    val startUpdate: () -> Unit = {
        updateStatus = UpdateStatus.Downloading
        downloadErrorMessage = ""
        downloadJob =
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val release = latestRelease
                    val asset = pickReleaseAsset(release) ?: error("未找到 TV 版发布包")
                    val tempDir = File(context.cacheDir, "update_downloader")
                    if (!tempDir.exists()) tempDir.mkdirs()
                    val tempFile = File(tempDir, asset.name)
                    // 先清掉上次的残留：下载器先写 .part 再改名，残留文件一定是坏的
                    tempFile.delete()
                    GithubApi.downloadUpdate(release!!, tempFile) { downloaded, total ->
                        // 进度回写主线程：回调在 IO 线程执行，直接写 Compose State 是跨线程写
                        scope.launch(Dispatchers.Main) {
                            bytesSentTotal = downloaded
                            contentLength = total
                            targetProgress =
                                if (total > 0) {
                                    downloaded.toFloat() / total
                                } else {
                                    0f
                                }
                        }
                    }
                    // 缓存写入后检查阈值，保留刚下载的 APK 待安装
                    CacheManager(context).checkCache(preserve = tempFile)
                    if (show) withContext(Dispatchers.Main) { installUpdate(tempFile) }
                }.onFailure {
                    // 用户关闭弹窗会取消下载任务，取消不是「下载失败」，也不该再写状态
                    if (it is CancellationException) return@onFailure
                    logger.error(it) { "Failed to download update" }
                    val reason = it.message ?: it::class.simpleName ?: "未知错误"
                    withContext(Dispatchers.Main) {
                        downloadErrorMessage = reason
                        updateStatus = UpdateStatus.DownloadError
                    }
                }
            }
    }

    LaunchedEffect(show) {
        if (show) {
            checkUpdate()
        } else {
            updateStatus = UpdateStatus.UpdatingInfo
        }
    }

    if (show) {
        AlertDialog(
            modifier = modifier.width(400.dp),
            onDismissRequest = onHideDialog,
            title = {
                Text(
                    text =
                        when (updateStatus) {
                            UpdateStatus.UpdatingInfo -> "获取更新信息中"
                            UpdateStatus.Ready -> latestRelease?.versionLabel ?: "Loading..."
                            UpdateStatus.Downloading -> "下载中"
                            UpdateStatus.Installing -> "安装中"
                            UpdateStatus.NoAvailableUpdate -> "无可用更新"
                            UpdateStatus.CheckError -> "检查更新失败"
                            UpdateStatus.DownloadError -> "下载失败"
                            UpdateStatus.InstallError -> "安装失败"
                            UpdateStatus.InstallPermissionRequired -> "需要安装权限"
                            UpdateStatus.SignatureMismatch -> "安装包签名异常"
                        },
                )
            },
            text = {
                when (updateStatus) {
                    UpdateStatus.UpdatingInfo -> {
                        Text(text = "检查更新中...")
                    }

                    UpdateStatus.Ready -> {
                        Text(text = latestRelease?.body ?: "Empty content")
                    }

                    UpdateStatus.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    text = "${bytesSentTotal.toMBString()}/${contentLength.toMBString()}",
                                )
                            }
                        }
                    }

                    UpdateStatus.Installing -> {
                        Text(text = "请坐和放宽")
                    }

                    UpdateStatus.DownloadError -> {
                        Text(
                            text =
                                if (downloadErrorMessage.isBlank()) {
                                    "下载失败"
                                } else {
                                    "下载失败：$downloadErrorMessage"
                                },
                        )
                    }

                    UpdateStatus.InstallError -> {
                        Text(text = "安装失败")
                    }

                    UpdateStatus.InstallPermissionRequired -> {
                        Text(
                            text =
                                "未授予「安装未知应用」权限，安装器无法启动。" +
                                    "请到系统设置中允许 BTV 安装应用，然后点击「重试」。",
                        )
                    }

                    UpdateStatus.SignatureMismatch -> {
                        Text(
                            text =
                                "下载的安装包签名与本应用不一致，已中止安装。" +
                                    "请确认网络环境安全后重新检查更新；若反复出现，请到项目主页手动下载。",
                        )
                    }

                    UpdateStatus.CheckError -> {
                        Text(text = "获取更新信息失败")
                    }

                    UpdateStatus.NoAvailableUpdate -> {
                        Text(text = "真没更新，骗你是小狗！")
                    }
                }
            },
            confirmButton = {
                when (updateStatus) {
                    UpdateStatus.UpdatingInfo, UpdateStatus.NoAvailableUpdate,
                    UpdateStatus.Downloading, UpdateStatus.Installing,
                    -> {}

                    UpdateStatus.Ready -> {
                        Button(
                            onClick = startUpdate,
                            modifier = Modifier.touchClickable(onClick = startUpdate),
                        ) {
                            Text(text = "立即更新")
                        }
                    }

                    UpdateStatus.InstallPermissionRequired -> {
                        Button(
                            onClick = retryInstall,
                            modifier = Modifier.touchClickable(onClick = retryInstall),
                        ) {
                            Text(text = "重试")
                        }
                    }

                    UpdateStatus.InstallError, UpdateStatus.DownloadError, UpdateStatus.CheckError,
                    UpdateStatus.SignatureMismatch,
                    -> {
                        Button(
                            onClick = checkUpdate,
                            modifier = Modifier.touchClickable(onClick = checkUpdate),
                        ) {
                            Text(text = "再试一次")
                        }
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled =
                        updateStatus !in
                            setOf(
                                UpdateStatus.Downloading,
                                UpdateStatus.Installing,
                            ),
                    onClick = onHideDialog,
                    modifier =
                        Modifier.touchClickable(onClick = {
                            if (updateStatus !in
                                setOf(
                                    UpdateStatus.Downloading,
                                    UpdateStatus.Installing,
                                )
                            ) {
                                onHideDialog()
                            }
                        }),
                ) {
                    Text(
                        text =
                            when (updateStatus) {
                                UpdateStatus.UpdatingInfo -> "我点错了"
                                UpdateStatus.Ready -> "打死不更"
                                UpdateStatus.NoAvailableUpdate -> "走了走了"
                                UpdateStatus.CheckError, UpdateStatus.DownloadError,
                                UpdateStatus.InstallError, UpdateStatus.InstallPermissionRequired,
                                UpdateStatus.SignatureMismatch,
                                -> "算了算了"
                                UpdateStatus.Downloading, UpdateStatus.Installing -> "你已经无路可逃！"
                            },
                    )
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        )
    }
}

/** 更新状态。 */
enum class UpdateStatus {
    UpdatingInfo,
    Ready,
    Downloading,
    Installing,
    NoAvailableUpdate,
    CheckError,
    DownloadError,
    InstallError,

    /** 缺少「安装未知应用」权限，需用户自行到系统设置授权后重试。 */
    InstallPermissionRequired,

    /** 下载包的签名证书与本应用不一致，已拒绝安装（疑似被替换的安装包）。 */
    SignatureMismatch,
}

/**
 * 判断当前应用是否已被允许安装 APK。
 *
 * Android 8.0（API 26）起系统按来源应用管控「安装未知应用」权限；
 * 更低版本只有全局开关，无法按应用查询，直接视为可安装。
 */
private fun Context.canRequestPackageInstallPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    return packageManager.canRequestPackageInstalls()
}

private fun Long.toMBString(): String =
    when {
        this >= 1_000_000 -> "%.1f MB".format(this / 1_000_000.0)
        this >= 1_000 -> "%.1f KB".format(this / 1_000.0)
        else -> "$this B"
    }
