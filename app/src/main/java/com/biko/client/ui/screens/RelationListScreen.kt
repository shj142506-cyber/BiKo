package com.biko.client.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.biko.client.data.followGroupMembers
import com.biko.client.data.followGroups
import com.biko.client.data.model.FollowGroup
import com.biko.client.data.model.RelationUser
import com.biko.client.data.myFans
import com.biko.client.data.myFollowings
import com.biko.client.data.setUserFollowGroups
import com.biko.client.data.userFollowGroupIds
import com.biko.client.ui.components.ChannelTabs
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「全部分组」的哨兵值（非真实 tagid）。 */
private const val ALL_GROUP = -1L

data class RelationListState(
    val isLogin: Boolean = false,
    /** 0 = 关注，1 = 粉丝。 */
    val tab: Int = 0,
    val users: List<RelationUser> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** 关注分组（仅关注 Tab 用）。 */
    val groups: List<FollowGroup> = emptyList(),
    /** 当前选中的分组 tagid，[ALL_GROUP] 表示全部关注。 */
    val selectedTag: Long = ALL_GROUP,
    /** 正在编辑分组的 UP，null 表示未打开弹窗。 */
    val groupDialogUser: RelationUser? = null,
    /** 弹窗内已勾选的分组 id。 */
    val groupDialogChecked: Set<Long> = emptySet(),
    val groupDialogSaving: Boolean = false,
    val error: String? = null,
)

class RelationListViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    // 用当前登录态初始化，避免「已登录」时首发 emission 再触发一次多余刷新
    private val _state = MutableStateFlow(RelationListState(isLogin = session.isLogin()))
    val state = _state.asStateFlow()

    private var page = 1
    private var started = false

    init {
        viewModelScope.launch {
            session.profile.collect { profile ->
                val wasLogin = _state.value.isLogin
                _state.update { it.copy(isLogin = profile.isLogin) }
                // 从「未登录」切到「已登录」时补一次加载
                if (profile.isLogin && !wasLogin) {
                    loadGroups()
                    refresh()
                }
            }
        }
    }

    /** 首次进入时设定初始 Tab（0 关注 / 1 粉丝）并按需加载。 */
    fun start(initialTab: Int) {
        if (started) return
        started = true
        if (initialTab != _state.value.tab) _state.update { it.copy(tab = initialTab) }
        if (session.isLogin()) {
            loadGroups()
            refresh()
        }
    }

    /** 拉取关注分组（失败静默，不影响主列表）。 */
    private fun loadGroups() {
        viewModelScope.launch {
            runCatching { repo.followGroups() }
                .onSuccess { groups -> _state.update { it.copy(groups = groups) } }
        }
    }

    private suspend fun fetchPage(pn: Int): Pair<Int, List<RelationUser>> = when {
        _state.value.tab == 1 -> repo.myFans(pn)
        _state.value.selectedTag != ALL_GROUP -> {
            val members = repo.followGroupMembers(_state.value.selectedTag)
            members.size to members
        }
        else -> repo.myFollowings(pn)
    }

    fun selectTab(index: Int) {
        if (index == _state.value.tab) return
        _state.update { it.copy(tab = index, users = emptyList(), total = 0, error = null, selectedTag = ALL_GROUP) }
        refresh()
    }

    fun selectGroup(tagid: Long) {
        if (tagid == _state.value.selectedTag) return
        _state.update { it.copy(selectedTag = tagid, users = emptyList(), total = 0, error = null) }
        refresh()
    }

    fun refresh() {
        if (!session.isLogin()) return
        page = 1
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { fetchPage(1) }
                .onSuccess { (total, users) ->
                    _state.update { it.copy(total = total, users = users, loading = false) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        // 分组内一次性返回，不做翻页
        if (s.selectedTag != ALL_GROUP) return
        if (s.loadingMore || s.loading || s.users.size >= s.total) return
        page += 1
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { fetchPage(page) }
                .onSuccess { (_, users) ->
                    _state.update { it.copy(users = it.users + users, loadingMore = false) }
                }
                .onFailure { page -= 1; _state.update { it.copy(loadingMore = false) } }
        }
    }

    /** 关注 / 取关：先本地翻转，失败再翻回去。 */
    fun toggleFollow(user: RelationUser) {
        val old = _state.value.users
        val target = !user.isFollowing
        _state.update { it.copy(users = it.users.map { u -> if (u.mid == user.mid) u.copy(isFollowing = target) else u }) }
        viewModelScope.launch {
            runCatching { repo.follow(user.mid, target) }
                .onFailure { _state.update { it.copy(users = old, error = it.error) } }
        }
    }

    /** 打开「设置分组」弹窗，并拉取该 UP 当前所属分组。 */
    fun openGroupDialog(user: RelationUser) {
        _state.update { it.copy(groupDialogUser = user, groupDialogChecked = emptySet()) }
        viewModelScope.launch {
            runCatching { repo.userFollowGroupIds(user.mid) }
                .onSuccess { ids ->
                    _state.update {
                        // 仅在弹窗仍指向同一 UP 时回填，避免异步竞态
                        if (it.groupDialogUser?.mid == user.mid) it.copy(groupDialogChecked = ids) else it
                    }
                }
        }
    }

    fun toggleGroupChecked(tagid: Long) {
        _state.update {
            val checked = it.groupDialogChecked
            it.copy(groupDialogChecked = if (checked.contains(tagid)) checked - tagid else checked + tagid)
        }
    }

    fun dismissGroupDialog() = _state.update { it.copy(groupDialogUser = null, groupDialogChecked = emptySet()) }

    fun saveGroupDialog() {
        val user = _state.value.groupDialogUser ?: return
        val checked = _state.value.groupDialogChecked
        viewModelScope.launch {
            _state.update { it.copy(groupDialogSaving = true) }
            runCatching { repo.setUserFollowGroups(user.mid, checked) }
                .onSuccess {
                    _state.update { it.copy(groupDialogSaving = false, groupDialogUser = null, groupDialogChecked = emptySet()) }
                    // 若正停留在某分组视图，重新拉取以反映变化
                    if (_state.value.selectedTag != ALL_GROUP) refresh()
                }
                .onFailure { e -> _state.update { it.copy(groupDialogSaving = false, error = e.message) } }
        }
    }
}

/** 关注 / 粉丝列表页（对应 B 站「我的关注」「我的粉丝」，关注 Tab 带分组过滤）。 */
@Composable
fun RelationListScreen(
    onOpenSpace: (Long, String, String) -> Unit,
    onBack: () -> Unit,
    initialTab: Int = 0,
    viewModel: RelationListViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.start(initialTab) }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
            )
            Text(
                text = if (state.tab == 0) "我的关注" else "我的粉丝",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                text = " ${state.total}人",
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
            names = listOf("关注", "粉丝"),
            selected = state.tab,
            onSelect = viewModel::selectTab,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // 关注 Tab 下的分组过滤条
        if (state.tab == 0 && state.groups.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RelationGroupChip(
                    text = "全部",
                    selected = state.selectedTag == ALL_GROUP,
                    onClick = { viewModel.selectGroup(ALL_GROUP) },
                )
                state.groups.forEach { group ->
                    RelationGroupChip(
                        text = if (group.count > 0) "${group.name} ${group.count}" else group.name,
                        selected = state.selectedTag == group.tagid,
                        onClick = { viewModel.selectGroup(group.tagid) },
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.users.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                state.users.isEmpty() -> {
                    Text(
                        text = state.error
                            ?: if (state.tab == 0) "还没有关注任何人\n去视频页点关注吧" else "还没有粉丝",
                        fontSize = 13.sp,
                        color = colors.t3,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    val listState = rememberLazyListState()
                    // 滚到底自动加载下一页
                    LaunchedEffect(listState, state.tab, state.selectedTag) {
                        snapshotFlow {
                            val info = listState.layoutInfo
                            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
                        }.collect { if (it) viewModel.loadMore() }
                    }
                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
                        itemsIndexed(state.users, key = { _, u -> u.mid }) { index, user ->
                            RelationRow(
                                user = user,
                                onClick = { onOpenSpace(user.mid, user.name, user.face) },
                                onToggleFollow = { viewModel.toggleFollow(user) },
                                onEditGroup = if (state.tab == 0) ({ viewModel.openGroupDialog(user) }) else null,
                            )
                            // 分隔线从头像右侧开始
                            if (index < state.users.lastIndex) {
                                Box(
                                    Modifier
                                        .padding(start = 74.dp)
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

    // 设置分组弹窗
    val dialogUser = state.groupDialogUser
    if (dialogUser != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissGroupDialog,
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.t2,
            title = {
                Text("设置分组", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = dialogUser.name,
                        fontSize = 13.sp,
                        color = colors.t3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    if (state.groups.isEmpty()) {
                        Text("还没有自建分组，请到 B 站客户端创建", fontSize = 13.sp, color = colors.t3)
                    } else {
                        state.groups.forEach { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleGroupChecked(group.tagid) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = state.groupDialogChecked.contains(group.tagid),
                                    onCheckedChange = { viewModel.toggleGroupChecked(group.tagid) },
                                )
                                Text(
                                    text = if (group.count > 0) "${group.name} (${group.count})" else group.name,
                                    fontSize = 14.sp,
                                    color = colors.text,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.groupDialogSaving,
                    onClick = viewModel::saveGroupDialog,
                ) {
                    Text(if (state.groupDialogSaving) "保存中…" else "确定", color = colors.acc)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissGroupDialog) {
                    Text("取消", color = colors.t3)
                }
            },
        )
    }
}

/** 分组过滤胶囊。 */
@Composable
private fun RelationGroupChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = biko
    Text(
        text = text,
        fontSize = 13.sp,
        color = if (selected) Color.White else colors.t2,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.acc else colors.card2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 关注 / 粉丝列表行：头像 + 名字/认证或签名 + 右侧关注药丸。 */
@Composable
private fun RelationRow(
    user: RelationUser,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
    onEditGroup: (() -> Unit)? = null,
) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkImage(url = user.face, modifier = Modifier.size(50.dp).clip(CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = user.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = user.verifyDesc.ifEmpty { user.sign }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (onEditGroup != null && user.isFollowing) {
            Text(
                text = "分组",
                fontSize = 12.sp,
                color = colors.t3,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onEditGroup)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        if (user.isFollowing) {
            Text(
                text = "已关注",
                fontSize = 13.sp,
                color = colors.t2,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.card2)
                    .clickable(onClick = onToggleFollow)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        } else {
            Text(
                text = "关注",
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.acc)
                    .clickable(onClick = onToggleFollow)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}
