package com.biko.client.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.MSGFEED_TP_AT
import com.biko.client.data.MSGFEED_TP_LIKE
import com.biko.client.data.MSGFEED_TP_REPLY
import com.biko.client.data.deleteMsgfeed
import com.biko.client.data.deleteSysNotice
import com.biko.client.data.model.MessageItem
import com.biko.client.data.model.MessageKind
import com.biko.client.data.model.PrivateSession
import com.biko.client.data.msgAts
import com.biko.client.data.msgLikes
import com.biko.client.data.msgReplies
import com.biko.client.data.setLikeNotice
import com.biko.client.data.systemNotices
import com.biko.client.ui.components.ChannelTabs
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import com.biko.client.util.formatTimeAgo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val MSG_TABS = listOf("回复我的", "@我的", "收到的赞", "系统通知", "私信")
private const val PRIVATE_TAB_INDEX = 4

data class MessageState(
    val isLogin: Boolean = false,
    val tab: Int = 0,
    val items: List<MessageItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val cursorId: Long = 0,
    val cursorTime: Long = 0,
    val error: String? = null,
)

class MessageViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(MessageState())
    val state = _state.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            session.profile.collect { profile ->
                _state.update { it.copy(isLogin = profile.isLogin) }
                if (profile.isLogin && _state.value.items.isEmpty()) refresh()
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    /** 长按删除一条消息（系统通知走独立接口）。 */
    fun deleteItem(item: MessageItem) {
        viewModelScope.launch {
            runCatching {
                when (item.kind) {
                    MessageKind.SYSTEM -> repo.deleteSysNotice(item.id)
                    MessageKind.REPLY -> repo.deleteMsgfeed(MSGFEED_TP_REPLY, item.id)
                    MessageKind.AT -> repo.deleteMsgfeed(MSGFEED_TP_AT, item.id)
                    MessageKind.LIKE -> repo.deleteMsgfeed(MSGFEED_TP_LIKE, item.id)
                }
            }
                .onSuccess { _state.update { s -> s.copy(items = s.items.filterNot { it.id == item.id && it.kind == item.kind }) } }
                .onFailure { e -> _toast.value = e.message ?: "删除失败" }
        }
    }

    /** 切换「收到的赞」通知开关。 */
    fun toggleLikeNotice(item: MessageItem) {
        val target = if (item.noticeState == 1) 0 else 1
        viewModelScope.launch {
            runCatching { repo.setLikeNotice(item.id, target) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(items = s.items.map { if (it.id == item.id) it.copy(noticeState = target) else it })
                    }
                }
                .onFailure { e -> _toast.value = e.message ?: "设置失败" }
        }
    }

    fun selectTab(index: Int) {
        if (index == _state.value.tab) return
        _state.update { it.copy(tab = index, items = emptyList(), cursorId = 0, cursorTime = 0, hasMore = false, error = null) }
        if (index != PRIVATE_TAB_INDEX) refresh()
    }

    fun refresh() {
        if (!session.isLogin()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { load(page = null) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = page.items,
                            cursorId = page.cursorId,
                            cursorTime = page.cursorTime,
                            hasMore = !page.isEnd,
                            loading = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!session.isLogin() || s.loadingMore || !s.hasMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { load(page = s) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = it.items + page.items,
                            cursorId = page.cursorId,
                            cursorTime = page.cursorTime,
                            hasMore = !page.isEnd,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    private suspend fun load(page: MessageState?) =
        when (_state.value.tab) {
            0 -> repo.msgReplies(page?.cursorId, page?.cursorTime)
            1 -> repo.msgAts(page?.cursorId, page?.cursorTime)
            2 -> repo.msgLikes(page?.cursorId, page?.cursorTime)
            else -> repo.systemNotices(page?.cursorId)
        }
}

/** 消息中心（主页「消息」入口，四类通知 + 私信分 Tab）。 */
@Composable
fun MessageScreen(
    onOpenComment: (String) -> Unit,
    onOpenLikeDetail: (MessageItem) -> Unit,
    onOpenChat: (PrivateSession) -> Unit,
    onBack: () -> Unit,
    viewModel: MessageViewModel = viewModel(),
    inboxViewModel: InboxViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(toast) {
        val msg = toast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
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
                text = "消息",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
        }

        ChannelTabs(
            names = MSG_TABS,
            selected = state.tab,
            onSelect = { viewModel.selectTab(it) },
            modifier = Modifier.padding(bottom = 6.dp),
        )

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

        if (state.tab == PRIVATE_TAB_INDEX) {
            InboxContent(
                modifier = Modifier.weight(1f),
                onOpenChat = onOpenChat,
                viewModel = inboxViewModel,
            )
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.items.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.items.isEmpty() -> {
                    Text(
                        text = state.error ?: "暂无消息",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    val listState = rememberLazyListState()
                    LaunchedEffect(listState, state.tab, state.items.size, state.hasMore) {
                        if (!state.hasMore) return@LaunchedEffect
                        snapshotFlow {
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        }.collect { last ->
                            if (last >= state.items.size - 2) viewModel.loadMore()
                        }
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        itemsIndexed(state.items, key = { _, it -> "${state.tab}-${it.id}" }) { index, item ->
                            MessageRow(
                                item = item,
                                onOpenComment = onOpenComment,
                                onOpenLikeDetail = onOpenLikeDetail,
                                onDelete = { viewModel.deleteItem(item) },
                                onToggleNotice = { viewModel.toggleLikeNotice(item) },
                            )
                            // 每条消息之间加一条细分隔线（最后一条不加）
                            if (index != state.items.lastIndex) {
                                Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    item: MessageItem,
    onOpenComment: (String) -> Unit,
    onOpenLikeDetail: (MessageItem) -> Unit,
    onDelete: () -> Unit,
    onToggleNotice: () -> Unit,
) {
    val colors = biko
    var showActions by remember { mutableStateOf(false) }
    val bvid = messageBvid(item.uri)
    val canOpenDetail = item.kind == MessageKind.LIKE && item.count > 1
    // 无可跳转目标时去掉点击水波纹，避免误以为可点（仍保留长按操作菜单）。
    val openable = canOpenDetail || bvid != null
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = if (openable) LocalIndication.current else null,
                onClick = {
                    when {
                        canOpenDetail -> onOpenLikeDetail(item)
                        bvid != null -> onOpenComment(bvid)
                    }
                },
                onLongClick = { showActions = true },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.kind == MessageKind.SYSTEM) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_notify_system),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(CircleShape),
                )
            } else {
                NetworkImage(
                    url = item.avatar,
                    modifier = Modifier.size(38.dp).clip(CircleShape),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = messageHeadline(item),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = messageTime(item),
                        fontSize = 11.sp,
                        color = colors.t3,
                    )
                }
                if (item.content.isNotEmpty()) {
                    Text(
                        text = item.content,
                        fontSize = 13.sp,
                        color = colors.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (item.quotedContent.isNotEmpty()) {
                    Text(
                        text = item.quotedContent,
                        fontSize = 12.5.sp,
                        color = colors.t2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.card)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }
        if (item.cover.isNotEmpty() || item.title.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.cover.isNotEmpty()) {
                    NetworkImage(
                        url = item.cover,
                        modifier = Modifier.width(72.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    color = colors.t2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            text = {
                Column {
                    Text(
                        text = "删除",
                        fontSize = 15.sp,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showActions = false
                                onDelete()
                            }
                            .padding(vertical = 12.dp),
                    )
                    if (item.kind == MessageKind.LIKE) {
                        Text(
                            text = if (item.noticeState == 1) "接收通知" else "不再通知",
                            fontSize = 15.sp,
                            color = colors.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showActions = false
                                    onToggleNotice()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }
}

private fun messageHeadline(item: MessageItem): String = when (item.kind) {
    MessageKind.LIKE -> buildString {
        append(item.userName.ifEmpty { "有人" })
        append(" 赞了你")
        if (item.count > 1) append(" 等 ${item.count} 人")
    }
    MessageKind.AT -> item.userName.ifEmpty { "有人" } + " 提到了你"
    MessageKind.SYSTEM -> item.title.ifEmpty { "系统通知" }
    else -> item.userName.ifEmpty { "有人" } + " 回复了你"
}

private fun messageTime(item: MessageItem): String =
    if (item.time > 0) formatTimeAgo(item.time) else item.timeText

/** 从消息 uri 里提取 bvid（有的回复/点赞消息只带 bilibili:// 评论地址，也含 BV 号）。 */
private fun messageBvid(uri: String): String? =
    Regex("(BV[0-9A-Za-z]{10})").find(uri)?.value
