package com.biko.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
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
import com.biko.client.data.model.DynamicPost
import com.biko.client.data.model.TopicSortOption
import com.biko.client.data.resolveTopicId
import com.biko.client.data.topicFeed
import com.biko.client.data.topicInfo
import com.biko.client.ui.components.ChannelTabs
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import com.biko.client.util.formatCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TopicDetailState(
    val topicId: Long = 0,
    val name: String = "",
    val description: String = "",
    val sharePic: String = "",
    val view: Long = 0,
    val discuss: Long = 0,
    val dynamics: Long = 0,
    val creatorUid: Long = 0,
    val creatorName: String = "",
    val creatorFace: String = "",
    val posts: List<DynamicPost> = emptyList(),
    val sortOptions: List<TopicSortOption> = emptyList(),
    val selectedSortBy: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val offset: String = "",
    val error: String? = null,
)

class TopicDetailViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val _state = MutableStateFlow(TopicDetailState())
    val state = _state.asStateFlow()

    private var topicId = 0L
    private var loaded = false

    /**
     * 进入话题页。[id] 为话题 id；评论里点「#话题#」时只有名字，[id] 传 0，由页面内搜索解析。
     */
    fun load(id: Long, name: String) {
        if (loaded) return
        loaded = true
        _state.update { it.copy(topicId = id, name = name) }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val resolved = if (id > 0L) {
                id
            } else {
                runCatching { repo.resolveTopicId(name) }.getOrDefault(0L)
            }
            if (resolved <= 0L) {
                _state.update { it.copy(loading = false, error = "没有找到话题「$name」") }
                return@launch
            }
            topicId = resolved
            _state.update { it.copy(topicId = resolved) }
            // 头部信息失败不阻断：动态流才是主体内容
            runCatching { repo.topicInfo(resolved) }.getOrNull()?.let { info ->
                _state.update {
                    it.copy(
                        name = info.name.ifEmpty { it.name },
                        description = info.description,
                        sharePic = info.sharePic,
                        view = info.view,
                        discuss = info.discuss,
                        dynamics = info.dynamics,
                        creatorUid = info.creatorUid,
                        creatorName = info.creatorName,
                        creatorFace = info.creatorFace,
                    )
                }
            }
            val page = runCatching { repo.topicFeed(resolved) }.getOrNull()
            _state.update {
                it.copy(
                    posts = page?.posts.orEmpty(),
                    offset = page?.offset.orEmpty(),
                    hasMore = page?.hasMore == true,
                    sortOptions = page?.sortOptions.orEmpty(),
                    selectedSortBy = page?.selectedSortBy ?: 0,
                    loading = false,
                    error = if (page == null) "话题动态加载失败" else null,
                )
            }
        }
    }

    /** 切换排序（最新 / 最热…）。 */
    fun switchSort(sortBy: Int) {
        val s = _state.value
        if (topicId <= 0L || s.loading || s.selectedSortBy == sortBy) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, selectedSortBy = sortBy) }
            val page = runCatching { repo.topicFeed(topicId, offset = "", sortBy = sortBy) }.getOrNull()
            _state.update {
                it.copy(
                    posts = page?.posts.orEmpty(),
                    offset = page?.offset.orEmpty(),
                    hasMore = page?.hasMore == true,
                    selectedSortBy = page?.selectedSortBy ?: sortBy,
                    loading = false,
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (topicId <= 0L || s.loading || s.loadingMore || !s.hasMore || s.offset.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val page = runCatching { repo.topicFeed(topicId, s.offset, s.selectedSortBy) }.getOrNull()
            _state.update {
                it.copy(
                    posts = it.posts + page?.posts.orEmpty(),
                    offset = page?.offset ?: it.offset,
                    hasMore = page?.hasMore == true,
                    loadingMore = false,
                )
            }
        }
    }
}

/** 话题页：话题信息头部 + 排序 + 话题动态流。 */
@Composable
fun TopicDetailScreen(
    topicId: Long,
    topicName: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenSpace: (Long, String, String) -> Unit = { _, _, _ -> },
    viewModel: TopicDetailViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(topicId, topicName) { viewModel.load(topicId, topicName) }

    LaunchedEffect(listState, state.hasMore, state.posts.size, state.loading) {
        if (!state.hasMore || state.loading) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= state.posts.size - 2) viewModel.loadMore()
            }
    }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
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
                text = state.name.ifEmpty { topicName }.ifEmpty { "话题" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
        }

        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                TopicHeader(
                    state = state,
                    onOpenSpace = onOpenSpace,
                )
            }
            if (state.sortOptions.size > 1) {
                item {
                    ChannelTabs(
                        names = state.sortOptions.map { it.name },
                        selected = state.sortOptions
                            .indexOfFirst { it.sortBy == state.selectedSortBy }
                            .coerceAtLeast(0),
                        onSelect = { index ->
                            state.sortOptions.getOrNull(index)?.let { viewModel.switchSort(it.sortBy) }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            when {
                state.loading && state.posts.isEmpty() -> {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }

                state.posts.isEmpty() -> {
                    item {
                        Text(
                            text = state.error ?: "这个话题下还没有动态",
                            fontSize = 13.sp,
                            color = colors.t3,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }

                else -> {
                    items(state.posts, key = { it.id }) { post ->
                        DynamicCard(post = post, onOpenVideo = onOpenVideo)
                    }
                    if (state.hasMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                } else {
                                    Text("上拉加载更多", fontSize = 12.sp, color = colors.t3)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 话题头部：封面 + 名称 + 统计数据 + 简介 + 主持人。 */
@Composable
private fun TopicHeader(
    state: TopicDetailState,
    onOpenSpace: (Long, String, String) -> Unit,
) {
    val colors = biko
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.sharePic.isNotEmpty()) {
                NetworkImage(
                    url = state.sharePic,
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "#${state.name}#",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.view > 0) {
                        TopicStat("浏览", state.view)
                    }
                    if (state.discuss > 0) {
                        TopicStat("讨论", state.discuss)
                    }
                    if (state.dynamics > 0) {
                        TopicStat("动态", state.dynamics)
                    }
                }
            }
        }

        if (state.description.isNotEmpty()) {
            Text(
                text = state.description,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.t2,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (state.creatorName.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        if (state.creatorUid > 0) {
                            onOpenSpace(state.creatorUid, state.creatorName, state.creatorFace)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetworkImage(
                    url = state.creatorFace,
                    modifier = Modifier.size(22.dp).clip(CircleShape),
                )
                Text(
                    text = "话题主持人：${state.creatorName}",
                    fontSize = 12.sp,
                    color = colors.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(1.dp).fillMaxWidth().background(colors.line))
}

@Composable
private fun TopicStat(label: String, count: Long) {
    val colors = biko
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = formatCount(count.toInt()),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = colors.t3,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}
