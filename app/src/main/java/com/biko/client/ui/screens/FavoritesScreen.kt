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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.biko.client.data.follow
import com.biko.client.data.followList
import com.biko.client.data.model.FollowedUp
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesState(
    val isLogin: Boolean = false,
    val total: Int = 0,
    val ups: List<FollowedUp> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    // attention 最近订阅 / frequent 最常访问
    val orderType: String = "attention",
)

class FavoritesViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    // 用当前登录态初始化，避免已登录时先闪一帧登录面板
    private val _state = MutableStateFlow(FavoritesState(isLogin = session.isLogin()))
    val state = _state.asStateFlow()

    private var page = 1

    init {
        viewModelScope.launch {
            session.profile.collect { profile ->
                val wasLogin = _state.value.isLogin
                _state.update { it.copy(isLogin = profile.isLogin) }
                // 从「未登录」切到「已登录」时补一次加载
                if (profile.isLogin && !wasLogin) refresh()
            }
        }
        // 首帧就已登录时，profile 不会发生「false→true」跳变，这里主动拉一次
        if (session.isLogin()) refresh()
    }

    fun refresh() {
        if (!session.isLogin()) return
        page = 1
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.followList(1, _state.value.orderType) }
                .onSuccess { (total, ups) ->
                    _state.update { it.copy(total = total, ups = ups, loading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.loading || s.ups.size >= s.total) return
        page += 1
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { repo.followList(page, s.orderType) }
                .onSuccess { (_, ups) ->
                    _state.update { it.copy(ups = it.ups + ups, loadingMore = false) }
                }
                .onFailure { page -= 1; _state.update { it.copy(loadingMore = false) } }
        }
    }

    /** 切换排序：最近订阅 ↔ 最常访问。 */
    fun toggleOrder() {
        val next = if (_state.value.orderType == "attention") "frequent" else "attention"
        _state.update { it.copy(orderType = next) }
        refresh()
    }

    /** 取消订阅：先移除，失败再塞回去。 */
    fun unfollow(up: FollowedUp) {
        val old = _state.value.ups
        _state.update { it.copy(ups = old - up, total = (it.total - 1).coerceAtLeast(0)) }
        viewModelScope.launch {
            runCatching { repo.follow(up.mid, false) }
                .onFailure {
                    _state.update { it.copy(ups = old, total = it.total + 1, error = it.error) }
                }
        }
    }
}

@Composable
fun FavoritesScreen(
    onOpenVideo: (String) -> Unit,
    onOpenSpace: (Long, String, String) -> Unit,
    onBack: () -> Unit,
    viewModel: FavoritesViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        // 顶部固定不滑动区（返回 + 标题 + 排序）：灰底 + 收紧高度
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
            )
            Text(
                text = "我的订阅",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                text = " ${state.total}人",
                fontSize = 13.sp,
                color = colors.t3,
                modifier = Modifier.padding(start = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            // 排序切换：最近订阅 / 最常访问
            Text(
                text = if (state.orderType == "attention") "≡ 最近订阅" else "≡ 最常访问",
                fontSize = 13.sp,
                color = colors.t2,
                modifier = Modifier
                    .clickable(onClick = viewModel::toggleOrder)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
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
                state.loading && state.ups.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.ups.isEmpty() -> {
                    Text(
                        text = state.error ?: "还没有订阅任何UP主\n去视频页点关注吧",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    val listState = rememberLazyListState()
                    // 滚到底自动加载下一页
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val info = listState.layoutInfo
                            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
                        }.collect { if (it) viewModel.loadMore() }
                    }
                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
                        itemsIndexed(state.ups, key = { _, up -> up.mid }) { index, up ->
                            FollowedUpRow(
                                up = up,
                                onClick = { onOpenSpace(up.mid, up.name, up.face) },
                                onUnfollow = { viewModel.unfollow(up) },
                            )
                            // 分隔线从头像右侧开始
                            if (index < state.ups.lastIndex) {
                                Box(
                                    Modifier
                                        .padding(start = 76.dp)
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(colors.card2),
                                )
                            }
                        }
                        if (state.loadingMore) {
                            item {
                                Text(
                                    text = "正在加载…",
                                    fontSize = 13.sp,
                                    color = colors.t3,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 订阅列表行：头像 + 名字/签名 + 右侧「已订阅」药丸（点击取消订阅）。 */
@Composable
private fun FollowedUpRow(up: FollowedUp, onClick: () -> Unit, onUnfollow: () -> Unit) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkImage(
            url = up.face,
            modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = up.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (up.sign.isNotBlank()) {
                Text(
                    text = up.sign,
                    fontSize = 12.sp,
                    color = colors.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // 已订阅药丸
        Text(
            text = "≡ 已订阅",
            fontSize = 13.sp,
            color = colors.t2,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.card2)
                .clickable(onClick = onUnfollow)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
