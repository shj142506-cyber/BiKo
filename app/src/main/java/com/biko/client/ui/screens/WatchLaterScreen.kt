package com.biko.client.ui.screens

import android.widget.Toast
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
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
import com.biko.client.data.watchLaterList
import com.biko.client.data.watchLaterRemove
import com.biko.client.ui.components.RankRow
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchLaterState(
    val isLogin: Boolean = false,
    val videos: List<Video> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 一次性提示（移出结果）。 */
    val message: String? = null,
)

class WatchLaterViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(WatchLaterState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.profile.collect { profile ->
                _state.update { it.copy(isLogin = profile.isLogin) }
                if (profile.isLogin) refresh()
            }
        }
    }

    fun refresh() {
        if (!session.isLogin()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.watchLaterList() }
                .onSuccess { list -> _state.update { it.copy(videos = list, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    /** 移出稍后再看：先本地移除，失败再塞回去。 */
    fun remove(video: Video) {
        val old = _state.value.videos
        _state.update { it.copy(videos = old - video, message = "已移出稍后再看") }
        viewModelScope.launch {
            runCatching { repo.watchLaterRemove(video.aid) }
                .onFailure { e -> _state.update { it.copy(videos = old, message = e.message ?: "移出失败") } }
        }
    }
}

/** 稍后再看列表页（对应 B 站「稍后再看」，长按某条可移出）。 */
@Composable
fun WatchLaterScreen(
    onOpenVideo: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: WatchLaterViewModel = viewModel(),
) {
    val colors = biko
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
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
                text = "稍后再看",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                text = " ${state.videos.size}",
                fontSize = 13.sp,
                color = colors.t3,
                modifier = Modifier.padding(start = 2.dp),
            )
            Spacer(Modifier.weight(1f))
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
                state.loading && state.videos.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.videos.isEmpty() -> {
                    Text(
                        text = state.error ?: "稍后再看还是空的\n在视频卡片上长按「稍后再看」试试",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    )
                }

                else -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(state.videos, key = { it.aid }) { video ->
                            RankRow(
                                video = video,
                                onClick = { if (video.bvid.isNotEmpty()) onOpenVideo(video.bvid) },
                                onLongClick = { viewModel.remove(video) },
                                longClickLabel = "移出稍后再看",
                            )
                        }
                    }
                }
            }
        }
    }
}
