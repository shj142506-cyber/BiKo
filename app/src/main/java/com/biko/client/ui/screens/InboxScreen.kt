package com.biko.client.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.data.AppGraph
import com.biko.client.data.clearDustbinSessions
import com.biko.client.data.getPrivateSessions
import com.biko.client.data.markDustbinRead
import com.biko.client.data.model.PrivateSession
import com.biko.client.data.removePrivateSession
import com.biko.client.data.setPrivateSessionDnd
import com.biko.client.data.setPrivateSessionIntercept
import com.biko.client.data.setPrivateSessionTop
import com.biko.client.ui.components.ChannelTabs
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import com.biko.client.util.formatTimeAgo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val UnreadRed = Color(0xFFFA5A57)

/** 私信会话分类（apiSessionType 对应 get_sessions 的 session_type）。 */
private data class SessionCategory(val label: String, val apiSessionType: Int)

private val SESSION_CATEGORIES = listOf(
    SessionCategory("全部", 4),
    SessionCategory("关注", 9),
    SessionCategory("未关注", 2),
    SessionCategory("陌生人", 8),
    SessionCategory("粉丝团", 3),
    SessionCategory("拦截", 5),
)

/** 「拦截」分类下标。 */
private const val DUSTBIN_INDEX = 5

data class InboxState(
    val isLogin: Boolean = false,
    val loading: Boolean = false,
    val categoryIndex: Int = 0,
    val sessions: List<PrivateSession> = emptyList(),
    val error: String? = null,
)

class InboxViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(InboxState(isLogin = session.isLogin()))
    val state = _state.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    fun consumeToast() {
        _toast.value = null
    }

    fun refresh() {
        _state.update { it.copy(isLogin = session.isLogin()) }
        if (!session.isLogin()) return
        val type = SESSION_CATEGORIES[_state.value.categoryIndex].apiSessionType
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.getPrivateSessions(sessionType = type, size = 50) }
                .onSuccess { list -> _state.update { it.copy(loading = false, sessions = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun selectCategory(index: Int) {
        if (index == _state.value.categoryIndex) return
        _state.update { it.copy(categoryIndex = index, sessions = emptyList()) }
        refresh()
    }

    /** 乐观更新 + 失败回滚的通用操作包装。 */
    private fun runOp(
        optimistic: (List<PrivateSession>) -> List<PrivateSession>,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            val prev = _state.value.sessions
            _state.update { it.copy(sessions = optimistic(it.sessions)) }
            runCatching { block() }
                .onFailure { e ->
                    _state.update { it.copy(sessions = prev) }
                    _toast.value = e.message ?: "操作失败"
                }
        }
    }

    fun toggleTop(s: PrivateSession) {
        val top = !s.isTop
        runOp(
            optimistic = { list ->
                list.map {
                    if (it.talkerId == s.talkerId) {
                        it.copy(topTs = if (top) System.currentTimeMillis() / 1000 else 0)
                    } else it
                }
            },
            block = { repo.setPrivateSessionTop(s.talkerId, s.sessionType, top) },
        )
    }

    fun toggleDnd(s: PrivateSession) {
        val enable = !s.isDnd
        runOp(
            optimistic = { list ->
                list.map { if (it.talkerId == s.talkerId) it.copy(isDnd = enable) else it }
            },
            block = { repo.setPrivateSessionDnd(s.talkerId, s.sessionType, enable) },
        )
    }

    fun toggleIntercept(s: PrivateSession) {
        val intercept = !s.isIntercept
        viewModelScope.launch {
            runCatching { repo.setPrivateSessionIntercept(s.talkerId, s.sessionType, intercept) }
                .onSuccess { refresh() }
                .onFailure { e -> _toast.value = e.message ?: "操作失败" }
        }
    }

    fun removeSession(s: PrivateSession) {
        viewModelScope.launch {
            runCatching { repo.removePrivateSession(s.talkerId, s.sessionType) }
                .onSuccess {
                    _state.update { st ->
                        st.copy(sessions = st.sessions.filterNot { it.talkerId == s.talkerId })
                    }
                }
                .onFailure { e -> _toast.value = e.message ?: "删除失败" }
        }
    }

    fun markDustbinRead() {
        viewModelScope.launch {
            runCatching { repo.markDustbinRead() }
                .onSuccess { refresh() }
                .onFailure { e -> _toast.value = e.message ?: "操作失败" }
        }
    }

    fun clearDustbinSessions() {
        viewModelScope.launch {
            runCatching { repo.clearDustbinSessions() }
                .onSuccess { _state.update { it.copy(sessions = emptyList()) } }
                .onFailure { e -> _toast.value = e.message ?: "清空失败" }
        }
    }
}

/** 私信会话列表内容（消息页「私信」标签页内嵌使用）。 */
@Composable
fun InboxContent(
    modifier: Modifier = Modifier,
    onOpenChat: (PrivateSession) -> Unit,
    viewModel: InboxViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(toast) {
        val msg = toast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
    }

    var pendingRemove by remember { mutableStateOf<PrivateSession?>(null) }
    var pendingIntercept by remember { mutableStateOf<PrivateSession?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        ChannelTabs(
            names = SESSION_CATEGORIES.map { it.label },
            selected = state.categoryIndex,
            onSelect = { viewModel.selectCategory(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading && state.sessions.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.sessions.isEmpty() -> {
                    Text(
                        text = state.error ?: "暂无私信",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (state.categoryIndex == DUSTBIN_INDEX) {
                            item {
                                DustbinActionRow(
                                    onMarkRead = { viewModel.markDustbinRead() },
                                    onClear = { showClearConfirm = true },
                                )
                            }
                        }
                        items(state.sessions, key = { it.talkerId }) { s ->
                            SessionRow(
                                session = s,
                                onClick = { onOpenChat(s) },
                                onToggleTop = { viewModel.toggleTop(s) },
                                onToggleDnd = { viewModel.toggleDnd(s) },
                                onToggleIntercept = {
                                    if (s.isIntercept) viewModel.toggleIntercept(s)
                                    else pendingIntercept = s
                                },
                                onRemove = { pendingRemove = s },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRemove?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("删除会话") },
            text = { Text("会话会从列表中移除，但不会删除聊天记录。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSession(s)
                    pendingRemove = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("取消") }
            },
        )
    }

    pendingIntercept?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingIntercept = null },
            title = { Text("移入拦截") },
            text = { Text("后续这类会话会进入拦截分类，仍可在拦截列表中查看和恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleIntercept(s)
                    pendingIntercept = null
                }) { Text("移入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingIntercept = null }) { Text("取消") }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空拦截会话") },
            text = { Text("所有拦截会话会从列表中移除，聊天记录仍由服务端保留。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearDustbinSessions()
                    showClearConfirm = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 拦截分类下的批量操作行：全部已读 / 清空拦截。 */
@Composable
private fun DustbinActionRow(onMarkRead: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onMarkRead, modifier = Modifier.weight(1f)) {
            Text("全部已读", fontSize = 13.sp)
        }
        OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
            Text("清空拦截", fontSize = 13.sp)
        }
    }
}

@Composable
private fun SessionRow(
    session: PrivateSession,
    onClick: () -> Unit,
    onToggleTop: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleIntercept: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = biko
    var showMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                if (session.avatar.isEmpty()) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.biko.client.R.drawable.ic_default_avatar),
                        contentDescription = null,
                        modifier = Modifier.size(46.dp).clip(CircleShape),
                    )
                } else {
                    NetworkImage(
                        url = session.avatar,
                        modifier = Modifier.size(46.dp).clip(CircleShape),
                    )
                }
                if (session.unread > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(UnreadRed, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = if (session.unread > 99) "99+" else session.unread.toString(),
                            fontSize = 10.sp,
                            color = Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.name.ifEmpty { "用户${session.talkerId}" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (session.isTop) {
                        Spacer(Modifier.width(4.dp))
                        SessionFlag("置顶")
                    }
                    if (session.isDnd) {
                        Spacer(Modifier.width(4.dp))
                        SessionFlag("免打扰")
                    }
                    if (session.isIntercept) {
                        Spacer(Modifier.width(4.dp))
                        SessionFlag("已拦截")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTimeAgo(session.time),
                        fontSize = 11.sp,
                        color = colors.t3,
                    )
                }
                Text(
                    text = session.preview,
                    fontSize = 13.sp,
                    color = colors.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Box {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = colors.t3,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { showMenu = true },
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (session.isTop) "取消置顶" else "置顶", fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        onClick = {
                            showMenu = false
                            onToggleTop()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (session.isDnd) "关闭免打扰" else "开启免打扰", fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        onClick = {
                            showMenu = false
                            onToggleDnd()
                        },
                    )
                    if (session.sessionType == 1) {
                        DropdownMenuItem(
                            text = { Text(if (session.isIntercept) "移出拦截" else "移入拦截", fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            onClick = {
                                showMenu = false
                                onToggleIntercept()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除会话", fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        onClick = {
                            showMenu = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

/** 会话行上的小标签（置顶 / 免打扰 / 已拦截）。 */
@Composable
private fun SessionFlag(text: String) {
    val colors = biko
    Text(
        text = text,
        fontSize = 10.sp,
        color = colors.acc,
        modifier = Modifier
            .background(colors.acc.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
