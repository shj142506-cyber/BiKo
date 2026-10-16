package com.biko.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.biko.client.data.dynamicFeed
import com.biko.client.data.model.DynamicPost
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import com.biko.client.util.formatCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DynamicState(
    val isLogin: Boolean = false,
    val posts: List<DynamicPost> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val offset: String = "",
    val error: String? = null,
)

class DynamicViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(DynamicState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.profile.collect { profile ->
                _state.update { it.copy(isLogin = profile.isLogin) }
                if (profile.isLogin && _state.value.posts.isEmpty()) refresh()
            }
        }
    }

    fun refresh() {
        if (!session.isLogin()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.dynamicFeed() }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            posts = page.posts,
                            offset = page.offset,
                            hasMore = page.hasMore,
                            loading = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!session.isLogin() || s.loadingMore || !s.hasMore || s.offset.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { repo.dynamicFeed(offset = s.offset) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            posts = it.posts + page.posts,
                            offset = page.offset,
                            hasMore = page.hasMore,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }
}

/** 关注动态页（主页「动态」入口）。 */
@Composable
fun DynamicScreen(
    onOpenVideo: (String) -> Unit,
    onBack: () -> Unit,
    onOpenPublish: () -> Unit = {},
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: DynamicViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()

    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            viewModel.refresh()
            onRefreshConsumed()
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
                text = "动态",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            if (state.isLogin) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "发布动态",
                    tint = colors.text,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenPublish)
                        .padding(6.dp),
                )
            }
        }

        if (!state.isLogin) {
            // 未登录直接在页面内展示登录面板（扫码 / 短信），省掉「点登录再跳页」这一步
            val loginViewModel: LoginViewModel = viewModel()
            val loginState by loginViewModel.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
            ) {
                listOf("扫码登录" to 0, "短信登录" to 1).forEach { (label, mode) ->
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = if (loginState.mode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (loginState.mode == mode) colors.acc else colors.t3,
                        modifier = Modifier.clickable { loginViewModel.setMode(mode) },
                    )
                }
            }
            if (loginState.mode == 0) {
                QrLoginPanel(
                    modifier = Modifier.weight(1f).padding(bottom = 32.dp),
                    viewModel = loginViewModel,
                )
            } else {
                SmsLoginPanel(
                    state = loginState,
                    onPhoneChange = loginViewModel::onPhoneChange,
                    onCodeChange = loginViewModel::onCodeChange,
                    onSend = loginViewModel::sendSms,
                    onLogin = loginViewModel::loginBySms,
                    modifier = Modifier.weight(1f),
                )
            }
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.posts.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.posts.isEmpty() -> {
                    Text(
                        text = state.error ?: "还没有动态\n关注的 UP 主更新后会出现在这里",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    )
                }

                else -> {
                    val listState = rememberLazyListState()
                    // 触底加载下一页
                    LaunchedEffect(listState, state.posts.size, state.hasMore) {
                        if (!state.hasMore) return@LaunchedEffect
                        snapshotFlow {
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        }.collect { last ->
                            if (last >= state.posts.size - 2) viewModel.loadMore()
                        }
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(state.posts, key = { it.id }) { post ->
                            DynamicCard(post = post, onOpenVideo = onOpenVideo)
                        }
                        if (state.hasMore) {
                            item {
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

/** 单条动态卡片。 */
@Composable
internal fun DynamicCard(post: DynamicPost, onOpenVideo: (String) -> Unit) {
    val colors = biko
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // 作者行
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                url = post.authorFace,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = post.authorName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(post.pubAction, post.pubTime)
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    fontSize = 12.sp,
                    color = colors.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (post.text.isNotEmpty()) {
            Text(
                text = post.text,
                fontSize = 14.sp,
                color = colors.text,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (post.pics.isNotEmpty()) {
            DynamicImageGrid(post.pics, Modifier.padding(top = 8.dp))
        }

        if (post.bvid.isNotEmpty()) {
            DynamicVideoCard(post, onOpenVideo)
        }

        // 转发的原动态
        post.orig?.let { orig ->
            Column(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(colors.card2, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                Text(orig.authorName, fontSize = 13.sp, color = colors.t2)
                if (orig.text.isNotEmpty()) {
                    Text(
                        text = orig.text,
                        fontSize = 13.sp,
                        color = colors.text,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (orig.pics.isNotEmpty()) {
                    DynamicImageGrid(orig.pics, Modifier.padding(top = 6.dp))
                }
            }
        }

        // 统计
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatItem(Icons.Outlined.Repeat, post.forwardCount)
            Spacer(Modifier.width(18.dp))
            StatItem(Icons.Outlined.ChatBubbleOutline, post.commentCount)
            Spacer(Modifier.width(18.dp))
            StatItem(Icons.Outlined.FavoriteBorder, post.likeCount)
        }
    }
    Spacer(Modifier.height(1.dp).fillMaxWidth().background(colors.line))
}

/** 动态里的视频卡片（可点开播放）。 */
@Composable
private fun DynamicVideoCard(post: DynamicPost, onOpenVideo: (String) -> Unit) {
    val colors = biko
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.card2)
            .clickable { onOpenVideo(post.bvid) }
            .padding(6.dp),
    ) {
        Box {
            NetworkImage(
                url = post.videoCover,
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(6.dp)),
            )
            if (post.videoDuration.isNotEmpty()) {
                Text(
                    text = post.videoDuration,
                    fontSize = 10.5.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = post.videoTitle,
                fontSize = 13.sp,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = colors.t3,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = post.videoPlay,
                    fontSize = 11.sp,
                    color = colors.t3,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}

/** 动态配图宫格。 */
@Composable
private fun DynamicImageGrid(pics: List<String>, modifier: Modifier = Modifier) {
    val shown = pics.take(9)
    Column(modifier.fillMaxWidth()) {
        if (shown.size == 1) {
            NetworkImage(
                url = shown[0],
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else {
            val columns = if (shown.size <= 4) 2 else 3
            shown.chunked(columns).forEach { rowItems ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rowItems.forEach { url ->
                        NetworkImage(
                            url = url,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, count: Int) {
    val colors = biko
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.t3,
            modifier = Modifier.size(15.dp),
        )
        if (count > 0) {
            Text(
                text = formatCount(count),
                fontSize = 12.sp,
                color = colors.t3,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}
