package com.biko.client.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.model.Video
import com.biko.client.data.ranking
import com.biko.client.data.watchLaterAdd
import com.biko.client.ui.components.ChannelTabs
import com.biko.client.ui.components.RankRow
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class RankChannel(val name: String, val rid: Int)

// 按分区 rid 从小到大（数字顺序）排列
private val CHANNELS = listOf(
    RankChannel("全站", 0), RankChannel("动画", 1), RankChannel("音乐", 3), RankChannel("游戏", 4),
    RankChannel("娱乐", 5), RankChannel("知识", 36), RankChannel("鬼畜", 119), RankChannel("舞蹈", 129),
    RankChannel("时尚", 155), RankChannel("生活", 160), RankChannel("影视", 181), RankChannel("科技", 188),
    RankChannel("资讯", 202), RankChannel("美食", 211), RankChannel("动物圈", 217), RankChannel("汽车", 223),
    RankChannel("运动", 234),
)

private val CHANNEL_NAMES = CHANNELS.map { it.name }

data class RankChannelState(
    val videos: List<Video> = emptyList(),
    val loading: Boolean = false,
    /** 是否已经成功加载过（用于避免重复请求）。 */
    val loaded: Boolean = false,
    val error: String? = null,
)

data class RankState(
    /** 每个频道（下标同 [CHANNELS]）各自的加载状态。 */
    val channels: Map<Int, RankChannelState> = emptyMap(),
)

class RankViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val dislike = AppGraph.dislike
    private val _state = MutableStateFlow(RankState())
    val state = _state.asStateFlow()

    /** 本地「不感兴趣」名单，加载列表时过滤掉（与首页一致）。 */
    private var disliked: Set<String> = emptySet()

    /** 名单就绪前不允许触发加载，避免首屏重复请求。 */
    private var ready = false

    init {
        viewModelScope.launch {
            disliked = runCatching { dislike.load() }.getOrDefault(emptySet())
            ready = true
            refresh(0)
        }
    }

    private fun channel(index: Int): RankChannelState =
        _state.value.channels[index] ?: RankChannelState()

    private fun update(index: Int, transform: (RankChannelState) -> RankChannelState) {
        _state.update { s ->
            s.copy(channels = s.channels + (index to transform(s.channels[index] ?: RankChannelState())))
        }
    }

    /** 仅当该频道尚未加载过时才真正发起请求（滑动到该页时调用）。 */
    fun ensureLoaded(index: Int) {
        if (!ready) return
        val c = channel(index)
        if (c.loaded || c.loading) return
        refresh(index)
    }

    fun refresh(index: Int) {
        val rid = CHANNELS[index].rid
        update(index) { it.copy(loading = true, error = null, videos = emptyList()) }
        viewModelScope.launch {
            try {
                val videos = repo.ranking(rid).filterNot { it.bvid.isNotEmpty() && it.bvid in disliked }
                update(index) { it.copy(videos = videos, loading = false, loaded = true) }
            } catch (e: Exception) {
                val message = e.message.orEmpty()
                update(index) {
                    it.copy(loading = false, error = message.ifBlank { "加载失败" })
                }
            }
        }
    }

    /** 稍后再看。 */
    fun watchLater(video: Video) {
        if (video.aid <= 0) return
        viewModelScope.launch { runCatching { repo.watchLaterAdd(video.aid) } }
    }

    /** 不感兴趣：立刻从当前榜单移除，并写入本地屏蔽名单（之后加载不再出现）。 */
    fun notInterested(index: Int, video: Video) {
        if (video.bvid.isEmpty()) return
        disliked = disliked + video.bvid
        update(index) { it.copy(videos = it.videos.filterNot { v -> v.bvid == video.bvid }) }
        viewModelScope.launch { dislike.add(video.bvid) }
    }
}

@Composable
fun RankScreen(
    onOpenVideo: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: RankViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // 频道左右滑动：整页切换（与首页一致）
    val pagerState = rememberPagerState(pageCount = { CHANNELS.size })

    Column(Modifier.fillMaxSize()) {
        // 顶部固定不滑动区（标题 + 频道栏）：灰底 + 收紧高度，给下面榜单多让位置
        Column(Modifier.fillMaxWidth().background(colors.card).statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "返回",
                    colorFilter = ColorFilter.tint(colors.text),
                    modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
                )
                Text(
                    text = "排行榜",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            ChannelTabs(
                names = CHANNEL_NAMES,
                selected = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val channel = state.channels[page] ?: RankChannelState()
            LaunchedEffect(page) { viewModel.ensureLoaded(page) }

            RankChannelPage(
                channel = channel,
                onOpenVideo = onOpenVideo,
                onRetry = { viewModel.refresh(page) },
                onWatchLater = { video -> viewModel.watchLater(video) },
                onNotInterested = { video -> viewModel.notInterested(page, video) },
            )
        }
    }
}

/** 单个榜单频道页：加载态 / 错误态 / 榜单列表。 */
@Composable
private fun RankChannelPage(
    channel: RankChannelState,
    onOpenVideo: (String) -> Unit,
    onRetry: () -> Unit,
    onWatchLater: (Video) -> Unit,
    onNotInterested: (Video) -> Unit,
) {
    val colors = biko
    Box(Modifier.fillMaxSize()) {
        when {
            channel.loading && channel.videos.isEmpty() -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            channel.videos.isEmpty() -> {
                Text(
                    text = channel.error ?: "暂无数据",
                    fontSize = 13.sp,
                    color = colors.t3,
                    modifier = Modifier.align(Alignment.Center).clickable { onRetry() },
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                ) {
                    itemsIndexed(channel.videos, key = { _, video -> video.bvid }) { index, video ->
                        RankRow(
                            video = video,
                            rank = index,
                            onClick = { onOpenVideo(video.bvid) },
                            trailingActions = listOf(
                                "稍后再看" to { onWatchLater(video) },
                                "不感兴趣" to { onNotInterested(video) },
                            ),
                        )
                        // 每条视频之间加一条细分隔线（最后一条不加）
                        if (index != channel.videos.lastIndex) {
                            Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                        }
                    }
                }
            }
        }
    }
}
