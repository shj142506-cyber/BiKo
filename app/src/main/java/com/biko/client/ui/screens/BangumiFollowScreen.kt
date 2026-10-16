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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.bangumiEpisodes
import com.biko.client.data.model.BangumiSeason
import com.biko.client.data.myBangumiFollow
import com.biko.client.ui.components.BangumiSeasonCard
import com.biko.client.ui.components.ChannelTabs
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BangumiFollowState(
    val isLogin: Boolean = false,
    /** 0 = 追番，1 = 追剧。 */
    val tab: Int = 0,
    val seasons: List<BangumiSeason> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

class BangumiFollowViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(BangumiFollowState())
    val state = _state.asStateFlow()

    private var page = 1

    init {
        viewModelScope.launch {
            session.profile.collect { profile ->
                _state.update { it.copy(isLogin = profile.isLogin) }
                if (profile.isLogin && _state.value.seasons.isEmpty()) refresh()
            }
        }
    }

    private suspend fun fetchPage(pn: Int): Pair<Int, List<BangumiSeason>> =
        repo.myBangumiFollow(type = _state.value.tab + 1, pn = pn)

    fun selectTab(index: Int) {
        if (index == _state.value.tab) return
        _state.update { it.copy(tab = index, seasons = emptyList(), total = 0, error = null) }
        refresh()
    }

    fun refresh() {
        if (!session.isLogin()) return
        page = 1
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { fetchPage(1) }
                .onSuccess { (total, seasons) ->
                    _state.update { it.copy(total = total, seasons = seasons, loading = false) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.loading || s.seasons.size >= s.total) return
        page += 1
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { fetchPage(page) }
                .onSuccess { (_, seasons) ->
                    _state.update { it.copy(seasons = it.seasons + seasons, loadingMore = false) }
                }
                .onFailure { page -= 1; _state.update { it.copy(loadingMore = false) } }
        }
    }

    /** 点卡片：拉取整季后进入第一集的详情页（详情页自带「选集」）。 */
    fun openSeason(season: BangumiSeason, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.bangumiEpisodes(season.seasonId) }
                .onSuccess { result ->
                    val episodes = result.second
                    val bvid = episodes.firstOrNull { it.bvid.isNotEmpty() }?.bvid
                    if (bvid != null) onOpen(bvid)
                }
        }
    }
}

/** 我的追番 / 追剧列表页（对应 B 站「我的追番」）。 */
@Composable
fun BangumiFollowScreen(
    onOpenBangumi: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BangumiFollowViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()

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
                text = if (state.tab == 0) "我的追番" else "我的追剧",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                text = " ${state.total}",
                fontSize = 13.sp,
                color = colors.t3,
                modifier = Modifier.padding(start = 2.dp),
            )
            Spacer(Modifier.weight(1f))
        }

        if (!state.isLogin) {
            // 未登录直接在页面内展示登录面板（扫码 / 短信）
            val loginViewModel: LoginViewModel = viewModel()
            val loginState by loginViewModel.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
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

        ChannelTabs(
            names = listOf("追番", "追剧"),
            selected = state.tab,
            onSelect = viewModel::selectTab,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.seasons.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.seasons.isEmpty() -> {
                    Text(
                        text = state.error
                            ?: if (state.tab == 0) "还没有追番\n去番剧页点追番吧" else "还没有追剧",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    val gridState = rememberLazyGridState()
                    // 滚到底自动加载下一页
                    LaunchedEffect(gridState, state.tab) {
                        snapshotFlow {
                            val info = gridState.layoutInfo
                            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
                        }.collect { if (it) viewModel.loadMore() }
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.seasons, key = { it.seasonId }) { season ->
                            BangumiSeasonCard(
                                season = season,
                                onClick = { viewModel.openSeason(season, onOpenBangumi) },
                            )
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
