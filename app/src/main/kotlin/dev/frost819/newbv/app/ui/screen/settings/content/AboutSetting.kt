package dev.frost819.newbv.app.ui.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.frost819.newbv.BuildConfig
import dev.frost819.newbv.app.network.GithubApi
import dev.frost819.newbv.app.ui.component.settings.SettingsOptionDefaults
import dev.frost819.newbv.app.ui.component.settings.UpdateDialog
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.log.Loggers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 关于页。
 *
 * 显示当前版本号、最新版本号，按钮打开更新弹窗。
 */
@Composable
fun AboutSetting(modifier: Modifier = Modifier) {
    val logger = Loggers.get("AboutSetting")

    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestVersionName by remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            runCatching {
                latestVersionName = GithubApi.getLatestBuild().versionLabel
                logger.info { "Find latest version $latestVersionName" }
            }.onFailure {
                logger.error(it) { "Failed to get latest version" }
                latestVersionName = "Error"
            }
        }
    }

    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(vertical = SettingsOptionDefaults.ListEdgePadding),
            verticalArrangement = Arrangement.spacedBy(SettingsOptionDefaults.ItemSpacing),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "当前版本：${BuildConfig.VERSION_NAME}")
                Text(text = "最新版本：$latestVersionName")
            }
            Button(
                onClick = { showUpdateDialog = true },
                modifier = Modifier.touchClickable(onClick = { showUpdateDialog = true }),
            ) {
                Text(text = "检查更新")
            }
        }
        Text(
            modifier = Modifier.align(Alignment.BottomCenter),
            text = "https://github.com/hamenot/BTV",
        )
    }

    UpdateDialog(
        show = showUpdateDialog,
        onHideDialog = { showUpdateDialog = false },
    )
}
