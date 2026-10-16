package com.biko.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.biko.client.data.CollectionSelection
import com.biko.client.data.collectionVideos
import com.biko.client.data.model.Video
import com.biko.client.ui.components.RankRow
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionDetailState(
    val title: String = "",
    val videos: List<Video> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val end: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

class CollectionDetailViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val _state = MutableStateFlow(CollectionDetailState())
    val state = _state.asStateFlow()

    private var mid = 0L
    private var id = 0L
    private var isSeason = true
    private var page = 1
    private var loaded = false

    fun load(mid: Long, id: Long, isSeason: Boolean) {
        if (loaded) return
        loaded = true
        this.mid = mid
        this.id = id
        this.isSeason = isSeason
        // 顶栏标题先用列表页带过来的值占位，避免白屏
        _state.update { it.copy(title = CollectionSelection.current?.title.orEmpty()) }
        refresh()
    }

    fun refresh() {
        if (mid == 0L || id == 0L) return
        page = 1
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, end = false) }
            runCatching { repo.collectionVideos(mid, id, isSeason, 1) }
                .onSuccess { (total, list) ->
                    _state.update {
                        it.copy(
                            videos = list,
                            total = total,
                            loading = false,
                            loaded = true,
                            end = list.isEmpty(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, loaded = true, end = true, error = e.message) }
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.end || !s.loaded) return
        val next = page + 1
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { repo.collectionVideos(mid, id, isSeason, next) }
                .onSuccess { (_, list) ->
                    page = next
                    _state.update {
                        it.copy(
                            videos = it.videos + list,
                            loadingMore = false,
                            end = list.isEmpty(),
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }
}

/** 合集 / 系列详情页：展示合集内稿件，点击进入视频详情。 */
@Composable
fun CollectionDetailScreen(
    mid: Long,
    id: Long,
    isSeason: Boolean,
    onOpenVideo: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionDetailViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(mid, id, isSeason) { viewModel.load(mid, id, isSeason) }

    LaunchedEffect(listState, state.end, state.videos.size) {
        if (state.end) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= state.videos.size - 2) viewModel.loadMore()
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
                text = state.title.ifEmpty { if (isSeason) "合集" else "系列" },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            )
            if (state.total > 0) {
                Text(
                    text = " ${state.total}",
                    fontSize = 13.sp,
                    color = colors.t3,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.videos.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.videos.isEmpty() -> {
                    Text(
                        text = state.error ?: "还没有内容",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(state.videos, key = { it.bvid.ifEmpty { it.aid.toString() } }) { video ->
                            RankRow(video = video, onClick = { onOpenVideo(video.bvid) })
                        }
                        if (state.loadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
