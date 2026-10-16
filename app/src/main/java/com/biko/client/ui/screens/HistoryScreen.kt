package com.biko.client.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.clearHistory
import com.biko.client.data.favourite
import com.biko.client.data.favourites
import com.biko.client.data.historyList
import com.biko.client.data.localHistory
import com.biko.client.data.model.LiveRoom
import com.biko.client.data.model.Video
import com.biko.client.data.removeHistory
import com.biko.client.ui.components.ChannelTabs
import com.biko.client.ui.components.RankRow
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryState(
    val items: List<Video> = emptyList(),
    val isLogin: Boolean = false,
    val loading: Boolean = false,
    val end: Boolean = false,
)

/**
 * 观看历史（对齐 BiliPai ListViewModel / HistoryRepository）：
 * - 按标签从服务器拉取：视频=archive，直播=live，番剧=null（服务端不支持 pgc，取全量后本地过滤）；
 * - 游标分页：max / view_at / business 原样回传上一页响应；
 * - 分类首屏为空时自动续拉，直到出现该分类条目或无更多。
 */
class HistoryViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()

    private var tab = 0                 // 0 全部 / 1 视频 / 2 直播 / 3 番剧
    private var cursorMax = 0L
    private var cursorViewAt = 0L
    private var cursorBusiness = ""
    private var raw: List<Video> = emptyList()
    private var loadingMore = false
    private var end = false

    init {
        viewModelScope.launch {
            session.profile.collect { profile ->
                val wasLogin = _state.value.isLogin
                _state.value = _state.value.copy(isLogin = profile.isLogin)
                if (profile.isLogin && !wasLogin) refresh()
            }
        }
    }

    fun selectTab(t: Int) {
        if (t == tab) return
        tab = t
        refresh()
    }

    fun refresh() = load(fromStart = true)

    fun loadMore() = load(fromStart = false)

    fun clear() {
        viewModelScope.launch {
            repo.clearHistory()
            raw = emptyList()
            end = true
            _state.value = _state.value.copy(items = emptyList(), end = true)
        }
    }

    fun remove(video: Video) {
        viewModelScope.launch {
            repo.removeHistory(video)
            raw = raw.filterNot { it.bvid == video.bvid }
            end = true
            _state.value = _state.value.copy(items = filter(raw), end = true)
        }
    }

    /** 服务端 type 参数；番剧(PGC)服务端不支持，传 null 取全量后本地过滤。 */
    private fun requestType(): String? = when (tab) {
        1 -> "archive"
        2 -> "live"
        else -> null
    }

    private fun keyOf(v: Video): String = when {
        v.bvid.isNotBlank() -> v.bvid
        v.roomId > 0 -> "live_${v.roomId}"
        v.seasonId > 0 -> "bangumi_${v.seasonId}_${v.epid}"
        else -> "id_${v.aid}"
    }

    private fun load(fromStart: Boolean) {
        if (loadingMore) return
        if (!fromStart && end) return
        if (!session.isLogin()) {
            viewModelScope.launch {
                raw = repo.localHistory()
                end = true
                _state.value = _state.value.copy(items = filter(raw), loading = false, end = true)
            }
            return
        }
        loadingMore = true
        if (fromStart) {
            cursorMax = 0L
            cursorViewAt = 0L
            cursorBusiness = ""
            raw = emptyList()
            end = false
            _state.value = _state.value.copy(items = emptyList(), loading = true, end = false)
        } else {
            _state.value = _state.value.copy(loading = true)
        }
        viewModelScope.launch {
            runCatching {
                var guard = 0
                while (true) {
                    val (list, cursor) = repo.historyList(
                        ps = 30,
                        max = cursorMax,
                        viewAt = cursorViewAt,
                        business = cursorBusiness.takeIf { it.isNotBlank() },
                        type = requestType(),
                    )
                    raw = (raw + list).distinctBy { keyOf(it) }
                    cursorMax = cursor?.max ?: 0L
                    cursorViewAt = cursor?.viewAt ?: 0L
                    cursorBusiness = cursor?.business.orEmpty()
                    val more = list.isNotEmpty() && cursorMax > 0L
                    end = !more
                    guard++
                    // 全部标签只取一页；分类标签首屏为空则自动续拉，直到出现该分类或无更多。
                    if (tab == 0 || filter(raw).isNotEmpty() || !more || guard >= 5) break
                }
                _state.value = _state.value.copy(items = filter(raw), loading = false, end = end)
            }.onFailure {
                end = true
                _state.value = _state.value.copy(loading = false, end = true)
            }
            loadingMore = false
        }
    }

    private fun filter(list: List<Video>): List<Video> {
        val byTab = when (tab) {
            1 -> list.filter { it.type == "video" }
            2 -> list.filter { it.type == "live" }
            3 -> list.filter { it.type == "bangumi" }
            else -> list
        }
        return byTab.filter { it.bvid.isNotBlank() || it.roomId > 0 || it.seasonId > 0 }
    }
}

@Composable
fun HistoryScreen(
    onOpenVideo: (String) -> Unit,
    onOpenLive: (LiveRoom) -> Unit = {},
    onOpenBangumi: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    // 分类标签：全部 / 视频 / 直播 / 番剧
    val tabs = remember { listOf("全部", "视频", "直播", "番剧") }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
            )
            // 标题与「清空」基线对齐，整组再与左侧返回箭头垂直居中（同一 y 轴）
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "观看历史",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "清空",
                    fontSize = 13.sp,
                    color = colors.acc,
                    modifier = Modifier
                        .clickable(onClick = viewModel::clear)
                        .padding(horizontal = 8.dp)
                        .alignByBaseline(),
                )
            }
        }

        ChannelTabs(
            names = tabs,
            selected = selectedTab,
            onSelect = {
                selectedTab = it
                viewModel.selectTab(it)
            },
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
            items(state.items) { video ->
                RankRow(
                    video = video,
                    onClick = {
                        when {
                            video.type == "live" && video.roomId > 0 -> onOpenLive(
                                LiveRoom(
                                    roomId = video.roomId,
                                    title = video.title,
                                    uname = video.upName,
                                    cover = video.pic,
                                ),
                            )
                            video.type == "bangumi" && video.bvid.isNotBlank() -> onOpenBangumi(video.bvid)
                            video.bvid.isNotBlank() -> onOpenVideo(video.bvid)
                            else -> Unit // 无有效标识，忽略点击，避免闪退
                        }
                    },
                    showDuration = false,
                    trailingActions = listOf(
                        "从历史中删除" to { viewModel.remove(video) },
                        "分享" to {
                            val link = when {
                                video.bvid.isNotBlank() -> "https://www.bilibili.com/video/${video.bvid}"
                                video.roomId > 0 -> "https://live.bilibili.com/${video.roomId}"
                                else -> ""
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    listOf(video.title, link).filter { it.isNotEmpty() }.joinToString("\n"),
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "分享到"))
                        },
                    ),
                )
            }
            if (state.items.isEmpty() && !state.loading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = colors.t3,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = when {
                                selectedTab != 0 -> "该分类下暂无记录"
                                state.isLogin -> "还没有观看记录\n去首页逛逛吧"
                                else -> "登录后查看观看历史"
                            },
                            fontSize = 13.sp,
                            color = colors.t3,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            if (state.loading && state.items.isEmpty()) {
                item {
                    Text(
                        text = "正在加载…",
                        fontSize = 13.sp,
                        color = colors.t3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
            if (!state.end && state.items.isNotEmpty()) {
                item {
                    // 滚到底自动加载下一页（分类标签首屏为空时由 VM 自动续拉）
                    LaunchedEffect(state.items.size) { viewModel.loadMore() }
                    Text(
                        text = if (state.loading) "正在加载…" else "",
                        fontSize = 13.sp,
                        color = colors.t3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 我的收藏（B 站默认收藏夹）
// ---------------------------------------------------------------------------

data class MyFavoritesState(
    val items: List<Video> = emptyList(),
    val isLogin: Boolean = false,
    val loading: Boolean = false,
    val end: Boolean = false,
    val error: String? = null,
)

/** 「我的收藏」页：默认收藏夹内容，滚到底自动翻页。 */
class MyFavoritesViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(MyFavoritesState())
    val state = _state.asStateFlow()

    private var page = 1

    init {
        viewModelScope.launch {
            session.profile.collect { profile ->
                _state.update { it.copy(isLogin = profile.isLogin) }
                if (profile.isLogin) refresh()
            }
        }
    }

    fun refresh() {
        page = 1
        _state.update { it.copy(items = emptyList(), end = false, error = null) }
        loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (!s.isLogin || s.loading || s.end) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { repo.favourites(page) }
                .onSuccess { list ->
                    page++
                    _state.update {
                        it.copy(
                            items = it.items + list,
                            loading = false,
                            end = list.size < 20,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    /** 取消收藏（删除单条）。成功后就地从列表移除。 */
    fun remove(video: Video) {
        viewModelScope.launch {
            runCatching { repo.favourite(video.aid, false) }
                .onSuccess {
                    _state.update { s -> s.copy(items = s.items.filterNot { it.bvid == video.bvid }) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
        }
    }
}

@Composable
fun MyFavoritesScreen(
    onOpenVideo: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MyFavoritesViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
            )
            Text(
                text = "我的收藏",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(state.items) { video ->
                RankRow(
                    video = video,
                    onClick = { onOpenVideo(video.bvid) },
                    trailingActions = listOf(
                        "删除单条" to { viewModel.remove(video) },
                        "分享" to {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "${video.title}\nhttps://www.bilibili.com/video/${video.bvid}",
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "分享到"))
                        },
                    ),
                )
            }
            if (state.items.isEmpty() && !state.loading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            tint = colors.t3,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = if (state.isLogin) (state.error ?: "收藏夹是空的") else "登录后查看收藏",
                            fontSize = 13.sp,
                            color = colors.t3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            if (!state.end && state.items.isNotEmpty()) {
                item {
                    // 滚到底自动加载下一页
                    LaunchedEffect(state.items.size) { viewModel.loadMore() }
                    Text(
                        text = if (state.loading) "正在加载…" else "",
                        fontSize = 13.sp,
                        color = colors.t3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }
        }
    }
}
