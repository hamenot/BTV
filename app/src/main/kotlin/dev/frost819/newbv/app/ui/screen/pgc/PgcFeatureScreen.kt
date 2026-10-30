package dev.frost819.newbv.app.ui.screen.pgc

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.ui.component.DetailScrollColumn
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.LoadingTip
import dev.frost819.newbv.app.ui.component.dialog.EpisodeGridButton
import dev.frost819.newbv.app.ui.component.dialog.EpisodeListDialog
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.navigation.PgcFeatureRoute
import dev.frost819.newbv.app.ui.navigation.VideoPlayerRoute
import dev.frost819.newbv.app.util.ToastUtils
import dev.frost819.newbv.app.viewmodel.pgc.SeasonDetailUiEffect
import dev.frost819.newbv.app.viewmodel.pgc.SeasonDetailUiState
import dev.frost819.newbv.app.viewmodel.pgc.SeasonDetailViewModel
import dev.frost819.newbv.biliapi.entity.video.season.Episode
import dev.frost819.newbv.biliapi.entity.video.season.SeasonDetail
import dev.frost819.newbv.core.focus.focusInvertedColors
import dev.frost819.newbv.core.focus.touchClickable
import kotlinx.coroutines.launch

/** 集数超过该值时显示网格快速选集按钮。 */
private const val SEASON_EPISODE_DIALOG_THRESHOLD = 20

/** 快速选集弹窗每页集数。 */
private const val SEASON_EPISODE_DIALOG_PAGE_SIZE = 50

/** 番剧详情页的版面留白。 */
private object SeasonDetailDefaults {
    /**
     * 内容顶部留白。
     *
     * 焦点回到顶部时页面要滚到最上方，若内容贴着屏幕上缘，封面会被顶边切掉；
     * 留出这一块后滚到顶仍有一圈空白，视觉上也不会"顶到头"。
     */
    val TopPadding = 24.dp

    /**
     * 补到目标底边上的一截高度。
     *
     * 本页的分集卡 = 封面 250px + 间距 + 最多两行标题 ≈ 314px，比上报的封面高度多约
     * 64px。用默认的 88dp（给 UGC 相关视频卡那种更高的卡片准备的）会让最后一行下方
     * 空出一大块，这里按本页实际卡片高度收窄。
     */
    val SizeAllowance = 32.dp
}

/**
 * 番剧详情页路由注册。
 *
 * [SeasonDetailViewModel] 通过 SavedStateHandle 读取 [PgcFeatureRoute.seasonId]/[PgcFeatureRoute.epid]。
 */
fun NavGraphBuilder.pgcFeatureScreen(navController: NavController) {
    composable<PgcFeatureRoute> {
        SeasonDetailScreen(navController = navController)
    }
}

/**
 * 番剧详情页。
 *
 * 布局自上而下：
 * 1. 封面 + 标题/简介/播放按钮/追番按钮
 * 2. 系列（切季，多季时才有）
 * 3. 正片列表
 * 4. 附加分集（PV/SP 等）
 */
@Composable
private fun SeasonDetailScreen(navController: NavController) {
    val viewModel: SeasonDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is SeasonDetailUiEffect.ShowToast -> {
                    ToastUtils.show(context, effect.message)
                }
                is SeasonDetailUiEffect.NavigateToPlayer -> {
                    navController.navigate(
                        VideoPlayerRoute(
                            aid = effect.aid,
                            cid = effect.cid,
                            epid = effect.epid?.toLong(),
                            title = effect.title,
                            cover = effect.cover,
                        ),
                    ) {
                        popUpTo<VideoPlayerRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    when {
        state.loading && state.seasonDetail == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingTip()
            }
        }
        state.error && state.seasonDetail == null -> {
            SeasonErrorScreen(
                message = state.errorTip,
                onRetry = { viewModel.loadSeasonDetail() },
            )
        }
        state.seasonDetail != null -> {
            SeasonDetailContent(
                detail = state.seasonDetail!!,
                state = state,
                viewModel = viewModel,
                navController = navController,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonDetailContent(
    detail: SeasonDetail,
    state: SeasonDetailUiState,
    viewModel: SeasonDetailViewModel,
    navController: NavController,
) {
    val focusSaver = rememberFocusSaver()
    focusSaver.RestoreFocus()
    val scrollState = rememberScrollState()

    // 顶部区域（封面/标题/播放/追番）任一元素获焦 → 页面回正到最上方。
    // 不能只靠 bringIntoView：它只保证"露出来"，播放按钮位于信息栏底部，
    // 贴顶后上方的封面与标题会被滚出屏幕，看起来就像"顶部信息丢了"。
    var topFocusSignal by remember { mutableStateOf(0) }
    LaunchedEffect(topFocusSignal) {
        if (topFocusSignal > 0 && scrollState.value != 0) {
            scrollState.animateScrollTo(0)
        }
    }

    // 快速选集弹窗状态：当前打开的是哪一行的剧集
    var showEpisodeDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogEpisodes by remember { mutableStateOf<List<Episode>>(emptyList()) }

    // 正片排序：true = 正序（B 站原始顺序），false = 倒序（新到旧）
    var episodesAscending by remember { mutableStateOf(true) }
    val displayEpisodes =
        remember(detail.episodes, episodesAscending) {
            if (episodesAscending) detail.episodes else detail.episodes.asReversed()
        }

    // 切换季时用 key 强制重组，重置所有 LazyRow 滚动位置
    key(detail.seasonId) {
        // 顶部留 24dp：滚到最上方时上缘有一块空白，封面不会贴着屏幕边。
        DetailScrollColumn(
            scrollState = scrollState,
            topPadding = SeasonDetailDefaults.TopPadding,
            sizeAllowance = SeasonDetailDefaults.SizeAllowance,
        ) {
            SeasonInfoHeader(
                detail = detail,
                isFollowing = state.isFollowing,
                onPlay = { viewModel.onPlay() },
                onToggleFollow = { viewModel.toggleFollow() },
                focusSaver = focusSaver,
                onTopAreaFocused = { topFocusSignal++ },
            )

            // 系列排在正片之前：切季是比选集更高一级的操作，先选季再看这一季的集数，
            // 放在正片下方要一路滚到底才能换季，顺序是反的。
            if (detail.seasons.size > 1) {
                SeasonSwitcherRow(
                    seasons = detail.seasons,
                    currentSeasonId = detail.seasonId,
                    onClick = { seasonId -> viewModel.onSwitchSeason(seasonId) },
                    focusSaver = focusSaver,
                )
            }

            if (detail.episodes.isNotEmpty()) {
                SeasonEpisodeRow(
                    title = "正片",
                    episodes = displayEpisodes,
                    lastPlayedCid = state.historyLastPlayedCid,
                    onClick = { episode -> viewModel.onPlayEpisode(episode) },
                    onShowListDialog = {
                        dialogTitle = "正片"
                        // 弹窗分页始终用 B 站原始顺序：排序切换只作用于详情页正片横排
                        dialogEpisodes = detail.episodes
                        showEpisodeDialog = true
                    },
                    focusSaver = focusSaver,
                    rowKey = "episodes",
                    // 正片旁的排序切换：点击按正序/倒序重排分集（选集弹窗同步使用同一顺序）
                    headerTrailing = {
                        EpisodeSortButton(
                            isAscending = episodesAscending,
                            onToggle = { episodesAscending = !episodesAscending },
                        )
                    },
                )
            }

            detail.sections.forEach { section ->
                SeasonEpisodeRow(
                    title = section.title,
                    episodes = section.episodes,
                    lastPlayedCid = state.historyLastPlayedCid,
                    onClick = { episode -> viewModel.onPlayEpisode(episode) },
                    onShowListDialog = {
                        dialogTitle = section.title
                        dialogEpisodes = section.episodes
                        showEpisodeDialog = true
                    },
                    focusSaver = focusSaver,
                    rowKey = "section_${section.id}",
                )
            }
        }
    }

    if (showEpisodeDialog) {
        val lastPlayedCid = state.historyLastPlayedCid
        val lastPlayedTime = state.historyLastPlayedTime
        EpisodeListDialog(
            title = dialogTitle,
            entries = dialogEpisodes,
            pageSize = SEASON_EPISODE_DIALOG_PAGE_SIZE,
            keyOf = { it.id },
            titleOf = { it.title },
            durationOf = { it.duration },
            playedOf = { episode ->
                // 仅记录最近一次观看的分集进度，其余分集无单集进度
                if (lastPlayedCid != 0L && episode.cid == lastPlayedCid) lastPlayedTime else 0
            },
            isCurrentOf = { episode -> lastPlayedCid != 0L && episode.cid == lastPlayedCid },
            tabLabelOf = { start, end -> "$start-$end" },
            onDismiss = { showEpisodeDialog = false },
            onSelect = { episode ->
                showEpisodeDialog = false
                viewModel.onPlayEpisode(episode)
            },
        )
    }
}

/**
 * 番剧详情页顶部信息区：封面 + 标题/简介 + 播放/追番。
 *
 * **封面不是交互点**：只做展示，不聚焦、不点击、不参与焦点记忆。从外部卡片点进来时
 * 焦点直接落在「播放」上，这也是本区第一个可聚焦元素。
 *
 * @param onPlay 播放回调，绑定在「播放」按钮上。
 * @param onToggleFollow 追番/取消追番回调。
 * @param focusSaver 焦点恢复器。
 * @param onTopAreaFocused 本区任一元素获焦时回调，用于把页面回正到最上方。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonInfoHeader(
    detail: SeasonDetail,
    isFollowing: Boolean,
    onPlay: () -> Unit,
    onToggleFollow: () -> Unit,
    focusSaver: FocusSaver,
    onTopAreaFocused: () -> Unit,
) {
    val playFocusRequester = focusSaver.focusRequesterFor("play")
    val followFocusRequester = focusSaver.focusRequesterFor("follow")

    // 从外部卡片点进来时焦点直接落在「播放」：封面不是交互点，页面上没有更靠前的
    // 可聚焦入口，而"播放"是这个页最主要的操作。
    LaunchedEffect(Unit) {
        if (focusSaver.savedKeyValue().isEmpty()) {
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 50.dp, vertical = 16.dp)
                // 顶部区域获焦即回正到页面最上方（见 SeasonDetailContent 的 topFocusSignal）。
                .onFocusChanged { if (it.hasFocus) onTopAreaFocused() },
    ) {
        // 封面只是展示，不是交互点：不聚焦、不点击，也不参与焦点记忆。
        AsyncImage(
            modifier =
                Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .aspectRatio(0.7f)
                    .clip(MaterialTheme.shapes.large),
            model = detail.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )

        Spacer(modifier = Modifier.width(24.dp))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (detail.styles.isNotEmpty()) {
                    Text(
                        text = detail.styles.joinToString(" / "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (detail.newEpDesc.isNotEmpty()) {
                    Text(
                        text = detail.newEpDesc,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = detail.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeasonActionButton(
                    text = "播放",
                    icon = Icons.Rounded.PlayArrow,
                    highlighted = true,
                    onClick = onPlay,
                    modifier =
                        Modifier
                            .focusRequester(playFocusRequester)
                            .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey("play") },
                )
                SeasonActionButton(
                    text = if (isFollowing) "已追番" else "追番",
                    icon = if (isFollowing) Icons.Rounded.Star else Icons.Outlined.StarBorder,
                    // 已追番不再整块高亮：底色/描边维持未追番时的样子，只把文字和图标染成强调色。
                    // 整块高亮会变成第二块"主按钮"，跟旁边的「播放」抢注意力，
                    // 也容易被误认为"这里有焦点"。
                    highlighted = false,
                    accentContent = isFollowing,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onToggleFollow,
                    modifier =
                        Modifier
                            .focusRequester(followFocusRequester)
                            .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey("follow") },
                )
            }
        }
    }
}

@Composable
private fun SeasonActionButton(
    text: String,
    icon: ImageVector,
    highlighted: Boolean,
    accentContent: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (highlighted) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.border,
                            MaterialTheme.shapes.small,
                        )
                    } else {
                        Modifier
                    },
                ).touchClickable(onClick = onClick),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            focusInvertedColors(
                containerColor =
                    if (highlighted) {
                        accentColor.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    when {
                        highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
                        // 仅文字用强调色，底色与未选中态一致
                        accentContent -> accentColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SeasonEpisodeRow(
    title: String,
    episodes: List<Episode>,
    lastPlayedCid: Long,
    onClick: (Episode) -> Unit,
    onShowListDialog: () -> Unit,
    focusSaver: FocusSaver,
    rowKey: String,
    headerTrailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // 标题旁的附加控件（如正片的正/倒序切换）
            headerTrailing?.invoke()
            if (episodes.size > SEASON_EPISODE_DIALOG_THRESHOLD) {
                EpisodeGridButton(onClick = onShowListDialog)
            }
            // 与 UGC 详情页一致：显示「上次播放到」按钮，一键回到断点分集
            if (episodes.size > 1 && lastPlayedCid != 0L) {
                val lastEpisode = episodes.find { it.cid == lastPlayedCid }
                if (lastEpisode != null) {
                    Surface(
                        onClick = { onClick(lastEpisode) },
                        modifier = Modifier.touchClickable(onClick = { onClick(lastEpisode) }),
                        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                        // 不做聚焦放大：否则会盖住左侧的网格列表按钮
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        colors =
                            focusInvertedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "历史",
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "上次播放到：${lastEpisode.title}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        val focusRequester = focusSaver.focusRequesterFor(rowKey)
        // 焦点回到第一张卡时把横排回正到起点：LazyRow 的 bringIntoView 只做
        // 最小滚动（卡片露出一部分就算可见），右移过再左移回来时首卡停在半截
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        LazyRow(
            state = listState,
            modifier =
                Modifier
                    .focusRestorer(focusRequester)
                    .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey(rowKey) }
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 50.dp),
        ) {
            items(episodes, key = { it.id }) { episode ->
                val epKey = "${rowKey}_${episode.id}"
                val isFirst = episode == episodes.first()
                var cardModifier: Modifier = Modifier
                if (isFirst) {
                    cardModifier =
                        cardModifier
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            }
                }
                EpisodeCard(
                    episode = episode,
                    isLastWatched = lastPlayedCid != 0L && episode.cid == lastPlayedCid,
                    onClick = { onClick(episode) },
                    modifier = cardModifier,
                    focusSaver = focusSaver,
                    epKey = epKey,
                )
            }
        }
    }
}

/**
 * 分集排序切换按钮（正片标题旁）。
 *
 * 按钮文案显示**将要执行的动作**：当前正序时显示「倒序」，反之显示「正序」。
 * 样式与「上次播放到」按钮一致：surfaceVariant 底、聚焦反色、不做聚焦放大
 * （否则会盖住相邻控件）。
 *
 * @param isAscending 当前是否为正序。
 * @param onToggle 点击回调（切换排序）。
 */
@Composable
private fun EpisodeSortButton(
    isAscending: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.touchClickable(onClick = onToggle),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors =
            focusInvertedColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = "排序",
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (isAscending) "倒序" else "正序",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    episode: Episode,
    isLastWatched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusSaver: FocusSaver,
    epKey: String,
) {
    Column(
        modifier =
            modifier
                .width(200.dp)
                .focusRequester(focusSaver.focusRequesterFor(epKey))
                .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey(epKey) },
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .touchClickable(onClick = onClick),
            onClick = onClick,
            shape = CardDefaults.shape(MaterialTheme.shapes.large),
            border =
                CardDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    3.dp,
                                    if (isLastWatched) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.border
                                    },
                                ),
                            shape = MaterialTheme.shapes.large,
                        ),
                ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = episode.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
                if (isLastWatched) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors =
                            SurfaceDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                            ),
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            text = "上次看到",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = episode.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeasonSwitcherRow(
    seasons: List<dev.frost819.newbv.biliapi.entity.video.season.PgcSeason>,
    currentSeasonId: Int,
    onClick: (Int) -> Unit,
    focusSaver: FocusSaver,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "系列",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 50.dp),
        )
        val focusRequester = focusSaver.focusRequesterFor("seasons")
        LazyRow(
            modifier =
                Modifier
                    .focusRestorer(focusRequester)
                    .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey("seasons") }
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 50.dp),
        ) {
            items(seasons, key = { it.seasonId }) { season ->
                val seasonKey = "season_${season.seasonId}"
                SeasonChip(
                    title = season.shortTitle,
                    isCurrent = season.seasonId == currentSeasonId,
                    onClick = { onClick(season.seasonId) },
                    modifier =
                        if (season == seasons.first()) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    focusSaver = focusSaver,
                    chipKey = seasonKey,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonChip(
    title: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusSaver: FocusSaver,
    chipKey: String,
) {
    Surface(
        modifier =
            modifier
                .focusRequester(focusSaver.focusRequesterFor(chipKey))
                .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey(chipKey) }
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (isCurrent) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.border,
                            MaterialTheme.shapes.small,
                        )
                    } else {
                        Modifier
                    },
                ).touchClickable(onClick = onClick),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            focusInvertedColors(
                containerColor =
                    if (isCurrent) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    if (isCurrent) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
    ) {
        // 高度统一 36dp、宽度随文案自适应（min 64dp 保证短标题不至于太窄），
        // 既保持一排芯片等高，又不让 "THE FINAL" 这类长文案溢出
        Box(
            modifier =
                Modifier
                    .height(36.dp)
                    .widthIn(min = 64.dp)
                    .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonErrorScreen(
    message: String,
    onRetry: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message.ifBlank { "加载失败" },
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                modifier =
                    Modifier
                        .focusRequester(focusRequester)
                        .touchClickable(onClick = onRetry),
                onClick = onRetry,
                shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder =
                            Border(
                                border =
                                    androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.border,
                                    ),
                                shape = MaterialTheme.shapes.medium,
                            ),
                    ),
                colors =
                    focusInvertedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    text = "重试",
                )
            }
        }
    }
}
