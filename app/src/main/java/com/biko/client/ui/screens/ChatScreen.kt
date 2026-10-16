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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.getPrivateMessages
import com.biko.client.data.markPrivateRead
import com.biko.client.data.model.ChatMessage
import com.biko.client.data.sendPrivateText
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    val loading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val hasMore: Boolean = false,
    val minSeqno: Long = 0,
    val loadingMore: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    /** 发送成功后自增，UI 收到变化即滚动到底部。 */
    val scrollSignal: Int = 0,
)

class ChatViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private var talkerId: Long = 0
    private var sessionType: Int = 1
    private var started = false
    private val _state = MutableStateFlow(ChatState())
    val state = _state.asStateFlow()

    val selfUid: Long get() = session.mid().toLongOrNull() ?: 0L

    fun start(talkerId: Long, sessionType: Int) {
        if (started && this.talkerId == talkerId) return
        this.talkerId = talkerId
        this.sessionType = sessionType
        started = true
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.messages.isEmpty(), error = null) }
            runCatching { repo.getPrivateMessages(talkerId, sessionType, size = 30, endSeqno = 0) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loading = false,
                            messages = page.messages,
                            hasMore = page.hasMore,
                            minSeqno = page.minSeqno,
                        )
                    }
                    if (page.maxSeqno > 0) {
                        runCatching { repo.markPrivateRead(talkerId, sessionType, page.maxSeqno) }
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore || s.minSeqno <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { repo.getPrivateMessages(talkerId, sessionType, size = 30, endSeqno = s.minSeqno) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            messages = page.messages + it.messages,
                            hasMore = page.hasMore,
                            minSeqno = page.minSeqno,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        val content = text.trim()
        if (content.isEmpty() || _state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            runCatching { repo.sendPrivateText(talkerId, content, sessionType) }
                .onSuccess {
                    _state.update { it.copy(sending = false, scrollSignal = it.scrollSignal + 1) }
                    onSent()
                    refresh()
                }
                .onFailure { e -> _state.update { it.copy(sending = false, error = e.message) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

/** 私信聊天页。 */
@Composable
fun ChatScreen(
    talkerId: Long,
    sessionType: Int,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val selfProfile by AppGraph.session.profile.collectAsState()
    val peerSession = com.biko.client.data.ChatSelection.current?.takeIf { it.talkerId == talkerId }
    val title = peerSession?.name.orEmpty()
    val peerFace = peerSession?.avatar.orEmpty()

    LaunchedEffect(talkerId, sessionType) { viewModel.start(talkerId, sessionType) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
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
                text = title.ifEmpty { "用户$talkerId" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        val listState = rememberLazyListState()
        // 倒序布局：index 0 是最新消息（底部），因此旧消息追加在列表尾部，翻页不会跳动
        val display = remember(state.messages) { state.messages.asReversed() }

        LaunchedEffect(state.scrollSignal) {
            if (state.scrollSignal > 0) listState.animateScrollToItem(0)
        }

        LaunchedEffect(listState, display.size, state.hasMore) {
            if (!state.hasMore) return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { last -> if (last >= display.size - 2) viewModel.loadMore() }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.messages.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.messages.isEmpty() -> {
                    Text(
                        text = state.error ?: "还没有消息，打个招呼吧",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(display, key = { it.seqno }) { msg ->
                            ChatBubble(
                                message = msg,
                                selfUid = viewModel.selfUid,
                                peerFace = peerFace,
                                selfFace = selfProfile.face,
                            )
                        }
                        if (state.hasMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.loadingMore) {
                                        CircularProgressIndicator(Modifier.size(18.dp))
                                    } else {
                                        Text("上拉加载更早消息", fontSize = 12.sp, color = colors.t3)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        var input by remember { mutableStateOf("") }
        val canSend = input.isNotBlank() && !state.sending

        state.error?.let { err ->
            Text(
                text = err,
                fontSize = 12.sp,
                color = Color(0xFFFA5A57),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.clearError() }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(color = colors.text, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.acc),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (canSend) viewModel.send(input) { input = "" }
                }),
                modifier = Modifier
                    .weight(1f)
                    .background(colors.card2, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text("发个消息…", fontSize = 15.sp, color = colors.t3)
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "发送",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (canSend) colors.acc else colors.t3,
                modifier = Modifier
                    .clickable(enabled = canSend) { viewModel.send(input) { input = "" } }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, selfUid: Long, peerFace: String, selfFace: String) {
    val colors = biko
    if (message.withdrawn) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text("[消息已撤回]", fontSize = 12.sp, color = colors.t3)
        }
        return
    }
    val mine = message.isMine(selfUid)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            MessageAvatar(peerFace)
            Spacer(Modifier.width(8.dp))
        }
        if (message.msgType == 2 && message.imageUrl.isNotEmpty()) {
            NetworkImage(
                url = message.imageUrl,
                modifier = Modifier.width(160.dp).height(160.dp).clip(RoundedCornerShape(10.dp)),
            )
        } else {
            val bubbleColor = if (mine) colors.acc else colors.card2
            val textColor = if (mine) Color.White else colors.text
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .background(bubbleColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(chatDisplayText(message), fontSize = 15.sp, color = textColor)
            }
        }
        if (mine) {
            Spacer(Modifier.width(8.dp))
            MessageAvatar(selfFace)
        }
    }
}

/** 聊天气泡旁的头像；为空时用默认头像占位。 */
@Composable
private fun MessageAvatar(face: String) {
    if (face.isEmpty()) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_default_avatar),
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape),
        )
    } else {
        NetworkImage(
            url = face,
            modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape),
        )
    }
}

private fun chatDisplayText(m: ChatMessage): String =
    m.text.ifEmpty {
        when (m.msgType) {
            2 -> "[图片]"
            5 -> "[消息已撤回]"
            6 -> "[表情]"
            10 -> "[通知]"
            11 -> "[视频]"
            7, 12, 13, 14 -> "[分享]"
            else -> "[消息]"
        }
    }
