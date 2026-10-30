package dev.frost819.newbv.core.log

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃处理器。
 *
 * 通过 [Thread.setDefaultUncaughtExceptionHandler] 捕获未处理异常，
 * 将崩溃日志（含设备信息、应用日志和 logcat 输出）写入文件，
 * 然后转发给原始 handler 执行默认行为（如退出）。
 *
 * 文件命名：`logs_crash_YYYY-MM-dd_HH-mm-ss-mmm.log`（时间分隔用连字符，冒号在 Windows 上非法；
 * 末尾毫秒用于避免同一秒内连续崩溃时文件互相覆盖）
 * 日志目录：`filesDir/crash_logs`
 *
 * @param context 应用 Context（用于获取文件目录和包信息）。
 * @param maxLogCount 崩溃日志最大保留数量，超出删除最旧的。
 */
class CrashHandler(
    private val context: Context,
    private val maxLogCount: Int = 10,
) {
    private val logger = Loggers.get("CrashHandler")

    companion object {
        const val LOG_DIR = "crash_logs"
        const val CRASH_LOG_PREFIX = "logs_crash"
        const val MANUAL_LOG_PREFIX = "logs_manual"

        /** 日志文件后缀；`CrashUploader` 的待传 `.json` 不算日志，不进列表。 */
        const val LOG_SUFFIX = ".log"

        /** 收集的 logcat 行数上限。 */
        private const val LOGCAT_LINES = 10_000

        /**
         * logcat 文本大小上限（1 MB）。
         *
         * 10000 行 threadtime 通常几百 KB，但重复打印的异常/ANR 堆栈可能远超此值。
         * 没有上限时：写入 `.log` 的体积、上传的 JSON 体积（整个 logcat 会被
         * `JSONObject` 再复制一次）都会失控。
         */
        const val MAX_LOGCAT_CHARS = 1 shl 20
    }

    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * 崩溃上传器。
     *
     * 由 app 层通过 Hilt DI 注入后设置。
     * 为 null 表示上传功能不可用（token 未配置或未启用）。
     */
    var crashUploader: CrashUploader? = null

    /**
     * 安装崩溃处理器。
     *
     * 清空 logcat 缓冲，设置全局异常处理器。
     * 仅应调用一次。
     */
    fun install() {
        runCatching {
            Runtime.getRuntime().exec("logcat -c")
            logger.info { "Cleared logcat buffer" }
        }

        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            logger.error(exception) { "======== UncaughtException on ${thread.name} ========" }
            handleCrash(thread, exception)
            originalHandler?.uncaughtException(thread, exception)
        }

        cleanupOldLogFiles()
    }

    /**
     * 卸载崩溃处理器，恢复原始 handler。
     */
    fun uninstall() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    /**
     * 手动生成日志文件（含 logcat 输出）。
     *
     * @return 生成的日志文件，失败返回 null。
     */
    fun createManualLog(): File? =
        runCatching {
            val process = Runtime.getRuntime().exec("logcat -t $LOGCAT_LINES -v threadtime")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            val logDir = File(context.filesDir, LOG_DIR)
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, createFilename(manual = true))
            logFile.createNewFile()

            with(logFile.writer()) {
                writeDeviceInfo(this)
                appendLine("======== Logs ========")
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    appendLine(line)
                }
                flush()
                close()
                reader.close()
            }
            logFile
        }.onFailure {
            logger.error(it) { "Failed to create manual log" }
        }.getOrNull()

    /**
     * 列出所有崩溃日志文件。
     *
     * 只返回 `.log`：`CrashUploader` 会在同一目录写 `*.json` 待传文件，
     * 它们不该出现在日志列表里（服务端白名单也只放行 `.log`，否则 UI 里
     * 会列出一条点了就 403 的条目）。
     */
    fun listCrashLogs(): List<File> =
        File(context.filesDir, LOG_DIR)
            .listFiles { it.name.startsWith(CRASH_LOG_PREFIX) && it.name.endsWith(LOG_SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /**
     * 列出所有手动日志文件。
     */
    fun listManualLogs(): List<File> =
        File(context.filesDir, LOG_DIR)
            .listFiles { it.name.startsWith(MANUAL_LOG_PREFIX) && it.name.endsWith(LOG_SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /**
     * 刷新日志文件列表并清理超出 [maxLogCount] 的旧文件。
     *
     * 同时清理 `CrashUploader` 落盘的待传 `.json`：这些文件只由「下次启动时上传」
     * 消费，而上传失败会一直留着 —— 设备长期离线或服务端持续 4xx 时会无限堆积
     * （每份含最多 1MB logcat）。
     */
    fun cleanupOldLogFiles() {
        val crashFiles = listCrashLogs()
        if (crashFiles.size > maxLogCount) {
            crashFiles.takeLast(crashFiles.size - maxLogCount).forEach { it.delete() }
        }
        val manualFiles = listManualLogs()
        if (manualFiles.size > maxLogCount) {
            manualFiles.takeLast(manualFiles.size - maxLogCount).forEach { it.delete() }
        }
        cleanupPendingCrashJson(maxLogCount)
    }

    /** 清理超出 [maxCount] 的待上传 JSON（按时间保留最新的 [maxCount] 份）。 */
    private fun cleanupPendingCrashJson(maxCount: Int) {
        val jsonFiles =
            File(context.filesDir, LOG_DIR)
                .listFiles { it.name.startsWith(CRASH_LOG_PREFIX) && it.name.endsWith(".json") }
                ?.sortedBy { it.lastModified() }
                ?: return
        if (jsonFiles.size > maxCount) {
            jsonFiles.take(jsonFiles.size - maxCount).forEach { it.delete() }
        }
    }

    private fun handleCrash(
        thread: Thread,
        exception: Throwable,
    ) {
        runCatching {
            val logDir = File(context.filesDir, LOG_DIR)
            if (!logDir.exists()) logDir.mkdirs()

            val crashFile = File(logDir, createFilename(manual = false))
            crashFile.createNewFile()

            val deviceInfo = collectDeviceInfo()

            // 先写入设备信息和异常，再追加 Logcat，确保应用 Logger 输出也被保留。
            writeCrashContext(crashFile, deviceInfo, thread, exception)

            // 收集 logcat 文本（用于文本日志和 JSON 上传）
            val logcatText = collectLogcatText()

            // 追加 logcat 输出到文本日志
            appendLogcatText(crashFile, logcatText)

            // best-effort 同步上传崩溃日志
            crashUploader?.uploadCrash(deviceInfo, thread, exception, logcatText)
        }.onFailure { error ->
            logger.error(error) { "Failed to write crash log" }
        }
    }

    /**
     * 收集 logcat 输出为文本。
     *
     * 读取最近 10000 行 logcat（threadtime 格式）。
     *
     * @return logcat 文本，失败返回空字符串。
     */
    private fun collectLogcatText(): String =
        runCatching {
            val process = Runtime.getRuntime().exec("logcat -t $LOGCAT_LINES -v threadtime")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.appendLine(line)
                // 超过预算就截断：优先保留最新输出（越靠后越接近崩溃现场），
                // 避免 JSON 上传被一个疯打的循环日志撑到几十 MB。
                if (sb.length > MAX_LOGCAT_CHARS) break
            }
            reader.close()
            if (sb.length > MAX_LOGCAT_CHARS) {
                sb.setLength(MAX_LOGCAT_CHARS)
                sb.append("\n... (truncated)")
            }
            sb.toString()
        }.onFailure {
            logger.error(it) { "Failed to collect logcat" }
        }.getOrDefault("")

    /**
     * 将 logcat 文本追加到文件。
     */
    private fun appendLogcatText(
        file: File,
        logcatText: String,
    ) {
        OutputStreamWriter(FileOutputStream(file, true)).use { writer ->
            writer.appendLine("======== Logcat ========")
            writer.append(logcatText)
            writer.flush()
        }
    }

    private fun writeCrashContext(
        file: File,
        deviceInfo: DeviceInfo,
        thread: Thread,
        throwable: Throwable,
    ) {
        OutputStreamWriter(FileOutputStream(file, true)).use { writer ->
            writer.append(
                LogFormat.crashHeader(
                    appVersion = deviceInfo.appVersion,
                    appVersionCode = deviceInfo.appVersionCode,
                    androidVersion = deviceInfo.androidVersion,
                    androidSdk = deviceInfo.androidSdk,
                    device = deviceInfo.device,
                    model = deviceInfo.model,
                    manufacturer = deviceInfo.manufacturer,
                ),
            )
            writer.appendLine("======== Exception ========")
            writer.appendLine("Thread: ${thread.name}")
            writer.appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
            throwable.stackTrace.forEach { writer.appendLine("    at $it") }
            throwable.cause?.let { cause ->
                writer.appendLine("Caused by: ${cause.javaClass.name}: ${cause.message}")
                cause.stackTrace.forEach { writer.appendLine("    at $it") }
            }
            writer.appendLine("================================")
        }
    }

    private fun collectDeviceInfo(): DeviceInfo {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return DeviceInfo(
            appVersion = packageInfo.versionName ?: "unknown",
            appVersionCode = packageInfo.versionCodeCompat().toInt(),
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            androidSdk = Build.VERSION.SDK_INT,
            device = Build.DEVICE ?: "unknown",
            model = Build.MODEL ?: "unknown",
            manufacturer = Build.MANUFACTURER ?: "unknown",
        )
    }

    private fun writeDeviceInfo(writer: OutputStreamWriter) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        writer.apply {
            appendLine("======== Device info ========")
            appendLine("App Version: ${info.versionName} (${info.versionCodeCompat()})")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Product: ${Build.PRODUCT}")
        }
    }

    private fun createFilename(manual: Boolean): String {
        val prefix = if (manual) MANUAL_LOG_PREFIX else CRASH_LOG_PREFIX
        // 时间段用连字符而不是冒号：冒号在 Windows 上是非法文件名字符，
        // 会让依赖日志文件的单元测试（HttpServerTest）整体跑不起来，
        // 也让复制到 U 盘/网络共享这类 FAT 文件系统时失败。
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        // 毫秒后缀防碰撞：同一秒内连续两次崩溃（如 OOM 后被系统重启又立即崩）
        // 会生成同名文件，后者覆盖前者，直接丢掉一份现场。
        return "${prefix}_$date${System.currentTimeMillis() % 1000}.log"
    }
}

/**
 * 读取应用版本号，兼容 Android 9 以下设备。
 *
 * [PackageInfo.longVersionCode] 是 API 28 新增方法，Android 5.0 等旧系统上调用会抛
 * [NoSuchMethodError]，导致崩溃日志写入中断并留下 0 字节文件。这里按系统版本回退
 * 到已废弃的 [PackageInfo.versionCode]。
 *
 * @param sdkInt 系统 SDK 版本（默认取 [Build.VERSION.SDK_INT]，测试可显式传入）。
 * @return 应用版本号。
 */
@Suppress("DEPRECATION")
@SuppressLint("NewApi") // sdkInt 保证只在 API 28+ 才调用 longVersionCode
internal fun PackageInfo.versionCodeCompat(sdkInt: Int = Build.VERSION.SDK_INT): Long =
    if (sdkInt >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        versionCode.toLong()
    }

/**
 * 崩溃日志中的设备与应用信息。
 *
 * @property appVersion 应用版本名。
 * @property appVersionCode 应用版本号。
 * @property androidVersion Android 版本。
 * @property androidSdk Android SDK 版本。
 * @property device 设备代号。
 * @property model 设备型号。
 * @property manufacturer 设备制造商。
 */
data class DeviceInfo(
    val appVersion: String,
    val appVersionCode: Int,
    val androidVersion: String,
    val androidSdk: Int,
    val device: String,
    val model: String,
    val manufacturer: String,
)
