package dev.frost819.newbv.app.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.tv.material3.MaterialTheme
import dev.frost819.newbv.app.ui.component.rememberDoublePressExit
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.component.user.UserPanel
import dev.frost819.newbv.app.ui.screen.home.HomeContent
import dev.frost819.newbv.app.viewmodel.user.UserViewModel
import dev.frost819.newbv.data.datastore.LeftNaviItem
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 主页面交互相关的时序常量。 */
private object MainScreenDefaults {
    /**
     * 点击轨道条目后，把焦点护送进顶栏的重试间隔（毫秒）。
     *
     * 换页要过一次 `AnimatedContent` 过渡，新页的顶栏在过渡结束前还没挂上树，
     * 一次性 `requestFocus` 会抛"未附着"异常，因此隔帧重试。
     */
    const val FocusEscortIntervalMillis = 180L

    /** 焦点护送重试次数：过渡约 300ms，多给两次余量。 */
    const val FocusEscortAttempts = 4
}

/**
 * 主页面框架。
 *
 * 左侧为[悬浮导航轨道][LeftNaviContent]（叠加层，不占布局宽度），右侧为内容区（[AnimatedContent] 切换）。
 *
 * 轨道：用户头像（点击显示 [UserPanel]）+ 6 导航项 + 设置。收起时只有图标，
 * 获焦时展开文字；内容区按 [SideRailDefaults.CollapsedWidth] 内缩，避免被图标列压住。
 *
 * 双击返回退出：首次返回显示 Toast，3 秒内再次返回退出 App。
 *
 * @param navController 导航控制器（跳转设置/登录等页面）。
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    userViewModel: UserViewModel = hiltViewModel(),
) {
    val userUiState by userViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showUserPanel by remember { mutableStateOf(false) }
    var selectedDrawerItem by rememberSaveable { mutableStateOf(Prefs.homeLeftNavItem) }
    var focusInitialized by rememberSaveable { mutableStateOf(false) }

    val homeFocusRequester = remember { FocusRequester() }
    val focusSaver = rememberFocusSaver()

    // 轨道展开**不再挤压内容区**：内容始终只让开收起态的图标列，
    // 展开出来的药丸压在内容之上，并配一层遮罩把背景压暗（Animeko TV 的做法）。
    // 早先是跟着把内容区右推，整页信息流会重新测量排版，焦点从左栏进出时肉眼可见地卡一下。
    var railExpanded by remember { mutableStateOf(false) }
    val contentStart = SideRailDefaults.CollapsedWidth

    // 仅"返回 MainScreen"时恢复焦点；首次进入不恢复，
    // 否则启动阶段系统自动聚焦左侧栏头像产生的 key 会把焦点抢回头像。
    if (focusInitialized) {
        focusSaver.RestoreFocus()
    }

    val handleBack =
        rememberDoublePressExit(
            onExit = { (context as? android.app.Activity)?.finish() },
            message = "再按一次退出",
        )

    // 返回 true 表示已把焦点移入内容区入口；返回 false 时由左侧栏回退到
    // 焦点系统的默认右向搜索（内容区入口被懒列表回收时，见 issue #287）。
    val onFocusToContent: () -> Boolean = {
        runCatching { homeFocusRequester.requestFocus() }.isSuccess
    }

    /**
     * 从顶栏按左键：把焦点送到「拥有这个顶栏」的轨道条目上。
     *
     * 顶栏属于当前选中的分区（[selectedDrawerItem]），按左键应该回到它的轨道入口，
     * 而不是交给空间搜索——空间搜索按纵向位置就近挑条目，落在谁身上全看运气。
     * 目标条目未挂上树时返回 false，回退默认搜索。
     */
    val onFocusToRail: () -> Boolean = {
        runCatching {
            focusSaver.focusRequesterFor("nav_${selectedDrawerItem.name}").requestFocus()
        }.isSuccess
    }

    val scope = rememberCoroutineScope()

    /**
     * 点击轨道条目后把焦点送到右侧顶栏。
     *
     * 轨道的展开态跟随自身焦点，焦点一离开条目它就自动收起，所以这里只需要把焦点
     * 交出去即可，不必再单独驱动收起动画。
     */
    val focusTopNavAfterRailClick: () -> Unit = {
        scope.launch {
            repeat(MainScreenDefaults.FocusEscortAttempts) {
                delay(MainScreenDefaults.FocusEscortIntervalMillis)
                if (runCatching { homeFocusRequester.requestFocus() }.isSuccess) return@launch
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!focusInitialized) {
            focusInitialized = true
            // 冷启动时 Compose 会默认聚焦首个可聚焦元素（左侧栏头像），
            // 该焦点会被 FocusSaver 记录，导致 RestoreFocus 随后把焦点抢回头像。
            // 首次进入时先清除这个被污染的 key，再强制聚焦内容区。
            focusSaver.clearFocusedKey()
            onFocusToContent()
        }
    }

    BackHandler {
        handleBack()
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // 与页面底色保持一致：各页面用 Material3Scaffold（containerColor = background）
                // 铺底，轨道所在的那条留白若不跟着铺，就会露出更亮的 colorScheme.surface，
                // 看起来像左侧多了一条竖灰带。
                .background(MaterialTheme.colorScheme.background),
    ) {
        // 内容区按轨道宽度内缩：收起态只让开图标列，展开态让开整条药丸。
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = contentStart),
        ) {
            AnimatedContent(
                targetState = selectedDrawerItem,
                label = "main-animated-content",
                transitionSpec = {
                    val coefficient = 20
                    if (targetState.ordinal < initialState.ordinal) {
                        fadeIn() + slideInVertically { -it / coefficient } togetherWith
                            fadeOut() + slideOutVertically { it / coefficient }
                    } else {
                        fadeIn() + slideInVertically { it / coefficient } togetherWith
                            fadeOut() + slideOutVertically { -it / coefficient }
                    }
                },
            ) { screen ->
                when (screen) {
                    LeftNaviItem.Home ->
                        HomeContent(
                            navFocusRequester = homeFocusRequester,
                            navController = navController,
                            focusSaver = focusSaver,
                            onNavToRail = onFocusToRail,
                        )
                    LeftNaviItem.Search -> {
                        val searchInputViewModel: dev.frost819.newbv.app.viewmodel.search.SearchInputViewModel =
                            androidx.hilt.navigation.compose
                                .hiltViewModel()
                        dev.frost819.newbv.app.ui.screen.search.SearchInputContent(
                            viewModel = searchInputViewModel,
                            focusRequester = homeFocusRequester,
                            focusSaver = focusSaver,
                            onSearch = { keyword ->
                                searchInputViewModel.commitSearch(keyword) {
                                    navController.navigate(
                                        dev.frost819.newbv.app.ui.navigation
                                            .SearchResultRoute(keyword = keyword),
                                    )
                                }
                            },
                        )
                    }
                    LeftNaviItem.Personal ->
                        dev.frost819.newbv.app.ui.screen.personal.PersonalContent(
                            navFocusRequester = homeFocusRequester,
                            navController = navController,
                            focusSaver = focusSaver,
                            onNavToRail = onFocusToRail,
                        )
                    LeftNaviItem.UGC ->
                        dev.frost819.newbv.app.ui.screen.ugc.UgcContent(
                            navFocusRequester = homeFocusRequester,
                            navController = navController,
                            focusSaver = focusSaver,
                            onNavToRail = onFocusToRail,
                        )
                    LeftNaviItem.PGC ->
                        dev.frost819.newbv.app.ui.screen.pgc.PgcContent(
                            navFocusRequester = homeFocusRequester,
                            navController = navController,
                            focusSaver = focusSaver,
                            onNavToRail = onFocusToRail,
                        )
                    LeftNaviItem.Live ->
                        dev.frost819.newbv.app.ui.screen.live.LiveContent(
                            navFocusRequester = homeFocusRequester,
                            navController = navController,
                            focusSaver = focusSaver,
                        )
                }
            }
        }

        // 展开态的遮罩：压在内容之上、轨道之下。
        // 没有它，展开出的文字会直接叠在信息流上，一列字压着一列字，看着就是"重叠"。
        //
        // 两层：整屏压暗 + 靠轨道一侧的实底渐变，药丸文字永远落在实底上。
        //
        // 颜色跟随当前主题而非写死黑色：轨道条目的内容色是 onSurface / primary，
        // 浅色主题下这些是深色，落在纯黑实底上就完全看不清。以 surface 为底、
        // 向 primary 做少量 lerp——浅色主题得到浅色遮罩、深色主题维持深色压暗，
        // 且切换主题色（accentSeed）时遮罩会跟着同步变色。18% 的混入量既能被
        // 肉眼感知到主色倾向，又不会喧宾夺主盖过内容。
        AnimatedVisibility(
            visible = railExpanded,
            enter = fadeIn(tween(SideRailDefaults.ScrimFadeMillis)),
            exit = fadeOut(tween(SideRailDefaults.ScrimFadeMillis)),
            label = "main-rail-scrim",
        ) {
            val scrimColor =
                lerp(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.primary,
                    SideRailDefaults.ScrimAccentBlend,
                )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // 实底段按真实宽度换算成渐变停靠点，别写死比例：
                // 屏幕宽度一变，固定比例就会让药丸有一截落在半透明区上。
                val edgeStop =
                    if (maxWidth > 0.dp) {
                        (SideRailDefaults.ScrimEdgeWidth / maxWidth).coerceIn(0f, 0.9f)
                    } else {
                        0.2f
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(scrimColor.copy(alpha = SideRailDefaults.ScrimDimAlpha)),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0f to scrimColor.copy(alpha = SideRailDefaults.ScrimEdgeAlpha),
                                    edgeStop to scrimColor.copy(alpha = SideRailDefaults.ScrimEdgeAlpha),
                                    (edgeStop + (1f - edgeStop) * 0.35f) to
                                        scrimColor.copy(alpha = 0.45f),
                                    (edgeStop + (1f - edgeStop) * 0.75f) to Color.Transparent,
                                ),
                            ),
                )
            }
        }

        LeftNaviContent(
            modifier = Modifier.fillMaxSize(),
            isLogin = userUiState.isLogin,
            avatar = userUiState.avatar,
            selectedItem = selectedDrawerItem,
            onLeftNaviItemChanged = { item ->
                selectedDrawerItem = item
                // 点击即视为"进入这一页"：焦点跟到右侧顶栏，轨道随之收起。
                focusTopNavAfterRailClick()
            },
            onOpenSettings = {
                navController.navigate(dev.frost819.newbv.app.ui.navigation.SettingsRoute)
            },
            onFocusToContent = onFocusToContent,
            onShowUserPanel = {
                showUserPanel = true
            },
            onLogin = {
                navController.navigate(dev.frost819.newbv.app.ui.navigation.LoginRoute)
            },
            focusSaver = focusSaver,
            onExpandedChange = { railExpanded = it },
        )

        if (showUserPanel) {
            val userPanelFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                runCatching { userPanelFocusRequester.requestFocus() }
            }
            BackHandler { showUserPanel = false }
            Dialog(
                onDismissRequest = { showUserPanel = false },
                properties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnBackPress = true,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    UserPanel(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .width(400.dp)
                                .padding(12.dp),
                        focusRequester = userPanelFocusRequester,
                        onHide = { showUserPanel = false },
                        onGoUserSwitch = {
                            showUserPanel = false
                            navController.navigate(dev.frost819.newbv.app.ui.navigation.UserSwitchRoute)
                        },
                        onGoFollowList = {
                            showUserPanel = false
                            val uid = userUiState.uid
                            if (uid != 0L) {
                                navController.navigate(
                                    dev.frost819.newbv.app.ui.navigation
                                        .FollowRoute(mid = uid),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 占位内容（UGC/PGC/Live 等未实现的页面）。
 */
@Composable
private fun PlaceholderContent(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.tv.material3.Text(
            text = "$title (待实现)",
            style = androidx.tv.material3.MaterialTheme.typography.displaySmall,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderContentPreview() {
    dev.frost819.newbv.core.theme.BVTheme {
        PlaceholderContent(title = "分区")
    }
}
