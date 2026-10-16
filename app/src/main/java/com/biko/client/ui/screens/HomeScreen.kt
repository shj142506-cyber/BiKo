package com.biko.client.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.bangumiEpisodes
import com.biko.client.data.bangumiIndex
import com.biko.client.data.feedRecommend
import com.biko.client.data.liveAreas
import com.biko.client.data.liveFeed
import com.biko.client.data.model.BangumiSeason
import com.biko.client.data.model.LiveArea
import com.biko.client.data.model.LiveRoom
import com.biko.client.data.model.Video
import com.biko.client.data.msgUnread
import com.biko.client.data.regionFeed
import com.biko.client.data.search
import com.biko.client.data.watchLaterAdd
import com.biko.client.ui.components.AssetImage
import com.biko.client.ui.components.BangumiSeasonCard
import com.biko.client.ui.components.ChannelTabs
import com.biko.client.ui.components.LiveCard
import com.biko.client.ui.components.VideoCard
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class HomeChannel(val name: String, val rid: Int)

private val CHANNELS = listOf(
    HomeChannel("推荐", 0), HomeChannel("动画", 1), HomeChannel("番剧", -2), HomeChannel("国创", 167),
    HomeChannel("音乐", 3), HomeChannel("舞蹈", 129), HomeChannel("游戏", 4), HomeChannel("知识", 36),
    HomeChannel("科技", 188), HomeChannel("运动", 234), HomeChannel("汽车", 223), HomeChannel("生活", 160),
    HomeChannel("美食", 211), HomeChannel("动物圈", 217), HomeChannel("鬼畜", 119), HomeChannel("时尚", 155),
    HomeChannel("娱乐", 5), HomeChannel("影视", -3), HomeChannel("纪录片", 177), HomeChannel("资讯", 202),
    HomeChannel("直播", -1),
)

private val CHANNEL_NAMES = CHANNELS.map { it.name }

/** 单个频道的独立数据（每个频道各自加载、翻页、滚动，互不影响）。 */
data class HomeChannelState(
    val videos: List<Video> = emptyList(),
    val lives: List<LiveRoom> = emptyList(),
    /** 直播频道的一级分区（顶部分类，含「全部」）。 */
    val liveAreas: List<LiveArea> = emptyList(),
    /** 当前选中的直播分区 id，0 表示「全部」。 */
    val liveAreaId: Int = 0,
    /** 当前选中的二级分区 id，0 表示不限（一级分区带子分区时才有意义）。 */
    val liveSubAreaId: Int = 0,
    /** 番剧 / 影视频道的 PGC 条目。 */
    val seasons: List<BangumiSeason> = emptyList(),
    /** 正在拉取某部番剧的整季分集（用于显示加载遮罩）。 */
    val seasonLoading: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** 是否已经成功加载过首页数据（用于避免重复请求）。 */
    val loaded: Boolean = false,
    val error: String? = null,
    val end: Boolean = false,
    val page: Int = 1,
)

data class HomeState(
    /** 每个频道（下标同 [CHANNELS]）各自的加载状态。 */
    val channels: Map<Int, HomeChannelState> = emptyMap(),
    /** 未读消息数（顶栏消息图标角标）。 */
    val msgUnread: Int = 0,
    /** 一次性提示（稍后再看 / 不感兴趣的结果），展示后由 UI 消费掉。 */
    val message: String? = null,
)

class HomeViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val dislike = AppGraph.dislike
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    /** 本地「不感兴趣」名单，加载列表时过滤掉。 */
    private var disliked: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            disliked = runCatching { dislike.load() }.getOrDefault(emptySet())
            refresh(0)
        }
        // 登录状态变化时刷新未读消息角标
        viewModelScope.launch {
            session.profile.collect { profile ->
                if (profile.isLogin) refreshUnread()
                else _state.update { it.copy(msgUnread = 0) }
            }
        }
    }

    /** 刷新未读消息数（顶栏角标）。 */
    fun refreshUnread() {
        viewModelScope.launch {
            runCatching { repo.msgUnread() }
                .onSuccess { u -> _state.update { it.copy(msgUnread = u.total) } }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun channel(index: Int): HomeChannelState =
        _state.value.channels[index] ?: HomeChannelState()

    /** 仅当该频道尚未加载过时才真正发起请求（滑动到该页时调用）。 */
    fun ensureLoaded(index: Int) {
        val c = channel(index)
        if (c.loaded || c.loading) return
        refresh(index)
    }

    /** 切换直播分区：清空当前列表并重新加载第一页。 */
    fun selectLiveArea(index: Int, areaId: Int) {
        if (channel(index).liveAreaId == areaId) return
        update(index) {
            it.copy(
                liveAreaId = areaId,
                liveSubAreaId = 0,
                lives = emptyList(),
                loaded = false,
                end = false,
            )
        }
        refresh(index)
    }

    /** 切换二级分区：清空当前列表并重新加载第一页。 */
    fun selectLiveSubArea(index: Int, areaId: Int) {
        if (channel(index).liveSubAreaId == areaId) return
        update(index) { it.copy(liveSubAreaId = areaId, lives = emptyList(), loaded = false, end = false) }
        refresh(index)
    }

    /** 加入稍后再看（真实写到 B 站账号的稍后再看列表）。 */
    fun watchLater(video: Video) {
        if (video.aid <= 0) return
        viewModelScope.launch {
            runCatching { repo.watchLaterAdd(video.aid) }
                .onSuccess { _state.update { it.copy(message = "已加入稍后再看") } }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "加入稍后再看失败") } }
        }
    }

    /**
     * 不感兴趣：立刻从当前频道列表移除，并写入本地屏蔽名单（之后加载不再出现）。
     * B 站 Web 端没有可用的屏蔽接口，这里按 BiliPai 的本地负反馈方式处理。
     */
    fun notInterested(index: Int, video: Video) {
        if (video.bvid.isEmpty()) return
        disliked = disliked + video.bvid
        update(index) { it.copy(videos = it.videos.filterNot { v -> v.bvid == video.bvid }) }
        _state.update { it.copy(message = "已减少此类推荐") }
        viewModelScope.launch { dislike.add(video.bvid) }
    }

    /**
     * 点番剧 / 影视卡片：拉取整季分集后直接进入第一集的详情页
     * （详情页自带「选集」，不再需要单独的分集弹层）。
     */
    fun openSeason(index: Int, season: BangumiSeason, onOpen: (String) -> Unit) {
        update(index) { it.copy(seasonLoading = true) }
        viewModelScope.launch {
            runCatching { repo.bangumiEpisodes(season.seasonId) }
                .onSuccess { (_, episodes) ->
                    update(index) { it.copy(seasonLoading = false) }
                    episodes.firstOrNull { it.bvid.isNotEmpty() }?.let { onOpen(it.bvid) }
                }
                .onFailure { e ->
                    update(index) { it.copy(seasonLoading = false) }
                    _state.update { it.copy(message = e.message) }
                }
        }
    }

    private fun List<Video>.filterDisliked(): List<Video> =
        if (disliked.isEmpty()) this else filterNot { it.bvid.isNotEmpty() && it.bvid in disliked }

    private fun update(index: Int, transform: (HomeChannelState) -> HomeChannelState) {
        _state.update { s ->
            s.copy(channels = s.channels + (index to transform(s.channels[index] ?: HomeChannelState())))
        }
    }

    fun refresh(index: Int) {
        val rid = CHANNELS[index].rid
        update(index) {
            it.copy(
                loading = true, error = null, end = false, page = 1,
                videos = emptyList(), lives = emptyList(), seasons = emptyList(),
            )
        }
        viewModelScope.launch {
            try {
                when (rid) {
                    // 直播也是视频流：按分区划分，按人气分页，能一直往下刷
                    -1 -> {
                        val areas = channel(index).liveAreas.ifEmpty { repo.liveAreas() }
                        val lives = repo.liveFeed(channel(index).liveAreaId, channel(index).liveSubAreaId, 1)
                        update(index) { it.copy(liveAreas = areas, lives = lives, loading = false, loaded = true) }
                    }
                    // 番剧 / 影视都是 PGC：走番剧索引（条目自带分集 bvid，可分集播放）
                    -2, -3 -> {
                        val seasonType = if (rid == -2) 1 else 2
                        val (seasons, hasNext) = repo.bangumiIndex(1, seasonType)
                        update(index) {
                            it.copy(seasons = seasons, end = !hasNext, loading = false, loaded = true)
                        }
                    }
                    // 「推荐」走推荐流：匿名可用，且能一页页往下刷
                    0 -> {
                        val videos = repo.feedRecommend(1).filterDisliked()
                        update(index) { it.copy(videos = videos, loading = false, loaded = true) }
                    }
                    // 其他分区改成走搜索：和直接在搜索栏搜该分区名得到的内容一致，
                    // 信息完整（播放 / 弹幕）且能一直翻页
                    else -> {
                        val list = repo.search(CHANNELS[index].name, 1).filterDisliked()
                        // 个别分区名搜不出内容时回退排行榜，避免出现空白分区
                        val videos = if (list.isEmpty()) repo.regionFeed(rid, 1) else list
                        update(index) { it.copy(videos = videos, loading = false, loaded = true) }
                    }
                }
            } catch (e: Exception) {
                update(index) { it.copy(loading = false, error = e.message ?: "加载失败，下拉重试") }
            }
        }
    }

    fun loadMore(index: Int) {
        val c = channel(index)
        // 所有频道（推荐 / 分区 / 直播 / 番剧）都可以继续往下翻
        if (c.loading || c.loadingMore || c.end || !c.loaded) return
        val rid = CHANNELS[index].rid
        val next = c.page + 1
        update(index) { it.copy(loadingMore = true, page = next) }
        viewModelScope.launch {
            try {
                if (rid == -1) {
                    val more = repo.liveFeed(c.liveAreaId, c.liveSubAreaId, next)
                    update(index) { it.copy(lives = it.lives + more, end = more.isEmpty()) }
                } else if (rid == -2 || rid == -3) {
                    val seasonType = if (rid == -2) 1 else 2
                    val (seasons, hasNext) = repo.bangumiIndex(next, seasonType)
                    update(index) { it.copy(seasons = it.seasons + seasons, end = !hasNext) }
                } else {
                    val raw = if (rid == 0) {
                        repo.feedRecommend(next)
                    } else {
                        repo.search(CHANNELS[index].name, next)
                    }
                    val more = raw.filterDisliked()
                    update(index) { it.copy(videos = it.videos + more, end = raw.isEmpty()) }
                }
            } catch (_: Exception) {
                update(index) { it.copy(page = next - 1) }
            } finally {
                update(index) { it.copy(loadingMore = false) }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenLive: (LiveRoom) -> Unit,
    /** 番剧 / 影视分集点开后进入详情（历史记录归入「番剧」分类）。 */
    onOpenBangumi: (String) -> Unit = {},
    onOpenShortVideo: () -> Unit,
    onOpenDynamic: () -> Unit,
    onOpenMessage: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val colors = biko
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // 频道左右滑动：整页切换
    val pagerState = rememberPagerState(pageCount = { CHANNELS.size })

    // 从动态 / 消息页返回时刷新未读角标（重新进入组合即触发）
    LaunchedEffect(Unit) { viewModel.refreshUnread() }

    // 稍后再看 / 不感兴趣 的结果提示
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        // 顶部固定不滑动区（logo + 搜索 + 频道栏）：灰底 + 收紧高度，给下面视频流多让位置
        Column(Modifier.fillMaxWidth().background(colors.card).statusBarsPadding()) {
            HomeTopBar(
                onOpenSearch = onOpenSearch,
                onOpenShortVideo = onOpenShortVideo,
                onOpenDynamic = onOpenDynamic,
                onOpenMessage = onOpenMessage,
                msgUnread = state.msgUnread,
            )
            ChannelTabs(
                names = CHANNEL_NAMES,
                selected = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        // 可滑动区：灰底，卡片用白底浮出来
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(colors.card),
        ) { page ->
            val channel = state.channels[page] ?: HomeChannelState()
            LaunchedEffect(page) { viewModel.ensureLoaded(page) }

            HomeChannelPage(
                index = page,
                channel = channel,
                onOpenVideo = onOpenVideo,
                onOpenLive = onOpenLive,
                onOpenBangumi = onOpenBangumi,
                viewModel = viewModel,
            )
        }
    }
}

/** 单个频道页：加载态 / 错误态 / 番剧网格 / 视频·直播网格。 */
@Composable
private fun HomeChannelPage(
    index: Int,
    channel: HomeChannelState,
    onOpenVideo: (String) -> Unit,
    onOpenLive: (LiveRoom) -> Unit,
    onOpenBangumi: (String) -> Unit,
    viewModel: HomeViewModel,
) {
    val colors = biko
    val rid = CHANNELS[index].rid
    val empty = channel.videos.isEmpty() && channel.lives.isEmpty() && channel.seasons.isEmpty()
    // 直播已加载出分区后，切换分区时保留顶部分类不整页转圈
    val liveWithAreas = rid == -1 && channel.liveAreas.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        when {
            empty && channel.error == null && !liveWithAreas && (channel.loading || !channel.loaded) -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            empty && channel.error != null -> {
                Text(
                    text = channel.error ?: "加载失败，下拉重试",
                    color = colors.t3,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable { viewModel.refresh(index) },
                )
            }

            rid == -2 || rid == -3 -> {
                // 番剧 / 影视：PGC 条目，点开看分集（分集用普通播放链路播放）
                val gridState = rememberLazyGridState()
                val count = channel.seasons.size
                val shouldLoadMore by remember(count) {
                    derivedStateOf {
                        count > 0 &&
                            (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= count - 6
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) viewModel.loadMore(index)
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(channel.seasons, key = { it.seasonId }) { season ->
                        BangumiSeasonCard(
                            season = season,
                            onClick = { viewModel.openSeason(index, season, onOpenBangumi) },
                            containerColor = colors.bg,
                            modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 14.dp),
                        )
                    }
                    if (channel.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            else -> {
                // 推荐、所有分区、直播统一都是可以一直往下刷的视频流
                val isLive = rid == -1
                val gridState = rememberLazyGridState()
                val count = if (isLive) channel.lives.size else channel.videos.size
                val shouldLoadMore by remember(count) {
                    derivedStateOf {
                        count > 0 &&
                            (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= count - 6
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) viewModel.loadMore(index)
                }
                Column(Modifier.fillMaxSize()) {
                    // 直播：顶部分区切换（全部 / 网游 / 手游 / 娱乐 …）
                    if (isLive && channel.liveAreas.size > 1) {
                        val areaIndex = channel.liveAreas
                            .indexOfFirst { it.id == channel.liveAreaId }
                            .coerceAtLeast(0)
                        // 一级分区下的二级分区（如「网游」下的「英雄联盟 / 我的世界 …」）
                        val subs = channel.liveAreas.getOrNull(areaIndex)?.children.orEmpty()
                        Column(Modifier.padding(bottom = 4.dp)) {
                            ChannelTabs(
                                names = channel.liveAreas.map { it.name },
                                selected = areaIndex,
                                onSelect = { viewModel.selectLiveArea(index, channel.liveAreas[it].id) },
                            )
                            if (subs.isNotEmpty()) {
                                ChannelTabs(
                                    names = listOf("推荐") + subs.map { it.name },
                                    selected = (subs.indexOfFirst { it.id == channel.liveSubAreaId } + 1)
                                        .coerceAtLeast(0),
                                    onSelect = { i ->
                                        viewModel.selectLiveSubArea(index, if (i == 0) 0 else subs[i - 1].id)
                                    },
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            if (isLive) {
                                itemsIndexed(channel.lives) { _, room ->
                                    LiveCard(
                                        room = room,
                                        onClick = { onOpenLive(room) },
                                        containerColor = colors.bg,
                                        modifier = Modifier.padding(5.dp),
                                    )
                                }
                            } else {
                                itemsIndexed(channel.videos) { _, video ->
                                    VideoCard(
                                        video = video,
                                        onClick = { if (video.bvid.isNotEmpty()) onOpenVideo(video.bvid) },
                                        onWatchLater = { viewModel.watchLater(video) },
                                        onNotInterested = { viewModel.notInterested(index, video) },
                                        containerColor = colors.bg,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp)
                                            .padding(bottom = 14.dp),
                                    )
                                }
                            }
                            if (channel.loadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                        // 切换分区 / 首次加载时在列表区域内转圈
                        if (isLive && channel.lives.isEmpty() && channel.error == null &&
                            (channel.loading || channel.loadingMore)
                        ) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
        // 拉取整季分集时盖一层遮罩，避免点了没反应
        if (channel.seasonLoading) {
            Box(
                modifier = Modifier.matchParentSize().background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/** 顶栏：品牌 logo + 搜索胶囊 + 右上角可收起的「动态 / 消息」。点 logo 进短视频模式。 */
@Composable
private fun HomeTopBar(
    onOpenSearch: () -> Unit,
    onOpenShortVideo: () -> Unit,
    onOpenDynamic: () -> Unit,
    onOpenMessage: () -> Unit,
    msgUnread: Int,
) {
    val colors = biko
    // 右上角图标默认收起，点开入口后往左展开
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左上角品牌 logo，点击进短视频模式。
        AssetImage(
            assetName = "biko_logo.png",
            modifier = Modifier
                .size(width = 32.dp, height = 27.dp)
                .clickable(onClick = onOpenShortVideo),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(36.dp)
                .background(colors.bg, RoundedCornerShape(18.dp))
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.t3),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "搜索视频、UP主",
                fontSize = 13.sp,
                color = colors.t3,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        // 收起态：只剩右侧一个入口图标；展开后动态 / 消息从入口左侧滑出（往左展开）
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(200)) +
                fadeIn(animationSpec = tween(200)),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(200)) +
                fadeOut(animationSpec = tween(200)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.DynamicFeed,
                    contentDescription = "动态",
                    tint = colors.text,
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(onClick = onOpenDynamic)
                        .padding(4.dp),
                )
                Box {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "消息",
                        tint = colors.text,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable(onClick = onOpenMessage)
                            .padding(4.dp),
                    )
                    if (msgUnread > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .background(colors.acc, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }
        // 入口图标：收起时是菜单图标，展开后变成关闭图标
        Icon(
            imageVector = if (expanded) Icons.Outlined.Close else Icons.Outlined.Menu,
            contentDescription = if (expanded) "收起" else "更多",
            tint = colors.text,
            modifier = Modifier
                .size(30.dp)
                .clickable { expanded = !expanded }
                .padding(4.dp),
        )
    }
}
