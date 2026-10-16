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
import com.biko.client.data.likeDetail
import com.biko.client.data.model.LikeDetailCard
import com.biko.client.data.model.LikeDetailUser
import com.biko.client.data.model.MessageItem
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import com.biko.client.util.formatTimeAgo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LikeDetailState(
    val card: LikeDetailCard? = null,
    val users: List<LikeDetailUser> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

/** 点赞详情：某个「收到的赞」条目下所有点赞者的分页列表。 */
class LikeDetailViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val _state = MutableStateFlow(LikeDetailState())
    val state = _state.asStateFlow()

    private var cardId: Long = 0
    private var counts: Int = 0
    private var pn: Int = 1
    private var lastMid: Long = 0

    /** 由页面在进入时调用，同一卡片只初始化一次。 */
    fun start(item: MessageItem) {
        if (cardId == item.id) return
        cardId = item.id
        counts = item.count
        refresh()
    }

    fun refresh() {
        if (cardId <= 0) return
        pn = 1
        lastMid = 0
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.likeDetail(cardId, pn, lastMid, counts) }
                .onSuccess { page ->
                    lastMid = page.lastMid
                    pn += 1
                    _state.update {
                        it.copy(
                            card = page.card ?: it.card,
                            users = page.users,
                            hasMore = !page.isEnd && (counts <= 0 || page.users.size < counts),
                            loading = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { repo.likeDetail(cardId, pn, lastMid, counts) }
                .onSuccess { page ->
                    lastMid = page.lastMid
                    pn += 1
                    _state.update {
                        val total = it.users.size + page.users.size
                        it.copy(
                            users = it.users + page.users,
                            hasMore = !page.isEnd && (counts <= 0 || total < counts),
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }
}

/** 点赞详情页。[item] 为来源消息（id / counts / uri）。 */
@Composable
fun LikeDetailScreen(
    item: MessageItem,
    onOpenContent: (String) -> Unit,
    onOpenUser: (mid: Long, name: String, face: String) -> Unit,
    onBack: () -> Unit,
    viewModel: LikeDetailViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()

    LaunchedEffect(item.id) { viewModel.start(item) }

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
                text = "点赞详情",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.users.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.users.isEmpty() -> {
                    Text(
                        text = state.error ?: "暂无点赞",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    val listState = rememberLazyListState()
                    LaunchedEffect(listState, state.users.size, state.hasMore) {
                        if (!state.hasMore) return@LaunchedEffect
                        snapshotFlow {
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        }.collect { last ->
                            if (last >= state.users.size - 2) viewModel.loadMore()
                        }
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        state.card?.let { card ->
                            item(key = "card") {
                                ContentCard(
                                    card = card,
                                    uri = item.uri,
                                    onOpenContent = onOpenContent,
                                )
                            }
                        }
                        items(state.users, key = { it.mid }) { user ->
                            LikeUserRow(
                                user = user,
                                onClick = { onOpenUser(user.mid, user.name, user.avatar) },
                            )
                        }
                        if (state.hasMore) {
                            item(key = "footer") {
                                Box(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
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
}

@Composable
private fun ContentCard(card: LikeDetailCard, uri: String, onOpenContent: (String) -> Unit) {
    val colors = biko
    val bvid = likeDetailBvid(uri)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(colors.card2, RoundedCornerShape(8.dp))
            .clickable(enabled = bvid != null) { bvid?.let(onOpenContent) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listOfNotNull(card.business.ifEmpty { null }, card.title.ifEmpty { null })
                .joinToString("："),
            fontSize = 13.sp,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(1.dp).fillMaxWidth().background(colors.line))
}

@Composable
private fun LikeUserRow(user: LikeDetailUser, onClick: () -> Unit) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkImage(
            url = user.avatar,
            modifier = Modifier.size(42.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.name.ifEmpty { "用户${user.mid}" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " 赞了我",
                    fontSize = 13.sp,
                    color = colors.t3,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (user.time > 0) formatTimeAgo(user.time) else "",
                    fontSize = 11.sp,
                    color = colors.t3,
                )
            }
        }
    }
    Spacer(Modifier.height(1.dp).fillMaxWidth().background(colors.line))
}

private fun likeDetailBvid(uri: String): String? =
    Regex("(BV[0-9A-Za-z]{10})").find(uri)?.value
