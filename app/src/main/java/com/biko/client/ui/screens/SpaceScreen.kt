package com.biko.client.ui.screens

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.SpaceSelection
import com.biko.client.data.blockUser
import com.biko.client.data.follow
import com.biko.client.data.isBlocked
import com.biko.client.data.isFollowing
import com.biko.client.data.model.ArticleSummary
import com.biko.client.data.model.DynamicPost
import com.biko.client.data.model.SpaceCollection
import com.biko.client.data.model.SpaceProfile
import com.biko.client.data.model.Video
import com.biko.client.data.reportUser
import com.biko.client.data.spaceDynamicFeed
import com.biko.client.data.spaceArticles
import com.biko.client.data.spaceCollections
import com.biko.client.data.spaceProfile
import com.biko.client.data.spaceVideos
import com.biko.client.data.upFans
import com.biko.client.data.upLikes
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.components.RankRow
import com.biko.client.ui.components.VideoCard
import com.biko.client.ui.theme.biko
import com.biko.client.util.formatCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 投稿排序：与 B 站 arc/search 的 order 参数一致。 */
private val SPACE_SORTS = listOf(
    "最新发布" to "pubdate",
    "最多播放" to "click",
    "最多收藏" to "stow",
)

/** 空间页标签页：动态 / 投稿 / 专栏 / 合集。 */
private val SPACE_TABS = listOf("动态", "投稿", "专栏", "合集")

/** UP 主昵称的大会员粉色。 */
private val VipPink = Color(0xFFFB7299)

/** 关注按钮未关注态的品牌深蓝。 */
private val FollowNavy = Color(0xFF3C5A80)

/** 单个排序（最新发布 / 最多播放 / 最多收藏）各自的投稿数据。 */
data class SortData(
    val videos: List<Video> = emptyList(),
    val page: Int = 1,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val end: Boolean = false,
    /** 是否已经成功加载过（避免重复请求）。 */
    val loaded: Boolean = false,
    val error: String? = null,
)

data class SpaceState(
    val profile: SpaceProfile = SpaceProfile(),
    /** 已选中的标签页：0 动态 / 1 投稿 / 2 专栏。 */
    val tab: Int = 0,
    /** 「动态」标签的数据。 */
    val dynamics: List<DynamicPost> = emptyList(),
    val dynOffset: String = "",
    val dynHasMore: Boolean = false,
    val dynLoading: Boolean = false,
    val dynLoaded: Boolean = false,
    /** 是否已关注该 UP 主。 */
    val following: Boolean = false,
    /** 是否已把该 UP 主加入黑名单。 */
    val blocked: Boolean = false,
    /** 投稿当前排序（与投稿页 HorizontalPager 的页码同步）。 */
    val sort: String = "pubdate",
    /** 各排序 order -> 投稿数据。 */
    val sorts: Map<String, SortData> = emptyMap(),
    /** 「专栏」标签的数据。 */
    val articles: List<ArticleSummary> = emptyList(),
    val articlePage: Int = 0,
    val articleEnd: Boolean = false,
    val articleLoading: Boolean = false,
    val articleLoaded: Boolean = false,
    /** 「合集」标签的数据（UP 主的合集与系列）。 */
    val collections: List<SpaceCollection> = emptyList(),
    val collectionLoading: Boolean = false,
    val collectionLoaded: Boolean = false,
    val message: String? = null,
)

class SpaceViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(SpaceState())
    val state = _state.asStateFlow()

    private var mid = 0L
    private var loaded = false

    fun load(mid: Long) {
        if (loaded) return
        loaded = true
        this.mid = mid
        // 先用列表页带过来的昵称/头像占位，避免白屏
        val seed = SpaceSelection.current
        _state.value = _state.value.copy(
            profile = SpaceProfile(name = seed?.name.orEmpty(), face = seed?.face.orEmpty()),
        )
        viewModelScope.launch {
            // 资料与关注状态并行加载；关注状态只对登录用户有意义
            launch {
                runCatching { repo.spaceProfile(mid) }
                    .onSuccess { p ->
                        _state.value = _state.value.copy(
                            profile = p.copy(
                                // 接口偶发空字段时保留列表页带过来的值
                                name = p.name.ifEmpty { _state.value.profile.name },
                                face = p.face.ifEmpty { _state.value.profile.face },
                            ),
                        )
                    }
            }
            // 粉丝 / 获赞用专门的统计接口取，保证和 B 站空间页一致（card 的数字可能滞后）
            launch {
                val fans = runCatching { repo.upFans(mid) }.getOrNull()
                val likes = runCatching { repo.upLikes(mid) }.getOrNull()
                if (fans != null || likes != null) {
                    _state.value = _state.value.copy(
                        profile = _state.value.profile.copy(
                            fans = fans ?: _state.value.profile.fans,
                            likes = likes ?: _state.value.profile.likes,
                        ),
                    )
                }
            }
            if (session.isLogin()) {
                launch {
                    runCatching { repo.isFollowing(mid) }
                        .onSuccess { on -> _state.value = _state.value.copy(following = on) }
                }
                launch {
                    runCatching { repo.isBlocked(mid) }
                        .onSuccess { on -> _state.value = _state.value.copy(blocked = on) }
                }
            }
            // 预加载默认排序的投稿；默认标签是「动态」，进入空间页即加载
            ensureSort(_state.value.sort)
            loadDynamics()
        }
    }

    fun setTab(index: Int) {
        if (_state.value.tab == index) return
        _state.value = _state.value.copy(tab = index)
    }

    /** 重新拉取动态（动态加载失败时重试用）。 */
    fun reloadDynamics() {
        _state.value = _state.value.copy(dynLoaded = false)
        loadDynamics()
    }

    private fun loadDynamics() {
        if (mid == 0L) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                dynLoading = true,
                dynLoaded = true,
                dynamics = emptyList(),
                dynHasMore = true,
                dynOffset = "",
            )
            runCatching { repo.spaceDynamicFeed(mid, "") }
                .onSuccess { p ->
                    _state.value = _state.value.copy(
                        dynamics = p.posts,
                        dynOffset = p.offset,
                        dynHasMore = p.hasMore,
                        dynLoading = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        dynLoading = false,
                        dynHasMore = false,
                        message = e.message,
                    )
                }
        }
    }

    fun loadMoreDynamics() {
        val s = _state.value
        if (s.dynLoading || !s.dynHasMore) return
        viewModelScope.launch {
            _state.value = s.copy(dynLoading = true)
            runCatching { repo.spaceDynamicFeed(mid, s.dynOffset) }
                .onSuccess { p ->
                    _state.value = _state.value.copy(
                        dynamics = _state.value.dynamics + p.posts,
                        dynOffset = p.offset,
                        dynHasMore = p.hasMore,
                        dynLoading = false,
                    )
                }
                .onFailure { _state.value = _state.value.copy(dynLoading = false) }
        }
    }

    /** 加载「专栏」标签第一页（首次进入 / 失败重试时调用）。 */
    fun ensureArticles() {
        if (mid == 0L) return
        val s = _state.value
        if (s.articleLoaded || s.articleLoading) return
        loadArticles(1, replace = true)
    }

    /** 「专栏」标签滚动到底部时加载下一页。 */
    fun loadMoreArticles() {
        val s = _state.value
        if (s.articleLoading || !s.articleLoaded || s.articleEnd) return
        loadArticles(s.articlePage + 1, replace = false)
    }

    private fun loadArticles(target: Int, replace: Boolean) {
        if (mid == 0L) return
        viewModelScope.launch {
            _state.value = _state.value.copy(articleLoading = true, articleLoaded = true)
            val result = runCatching {
                repo.spaceArticles(mid, target, _state.value.profile.name, _state.value.profile.face)
            }.getOrNull()
            val cur = _state.value
            if (result == null) {
                _state.value = cur.copy(articleLoading = false, articleEnd = true)
                return@launch
            }
            val list = result.second
            val merged = if (replace) list else cur.articles + list.filter { a -> cur.articles.none { it.id == a.id } }
            _state.value = cur.copy(
                articles = merged,
                articlePage = target,
                articleEnd = if (replace) list.isEmpty() else list.isEmpty() || merged.size == cur.articles.size,
                articleLoading = false,
            )
        }
    }

    /** 加载「合集」标签（首次进入 / 失败重试时调用）。 */
    fun ensureCollections() {
        if (mid == 0L) return
        val s = _state.value
        if (s.collectionLoaded || s.collectionLoading) return
        loadCollections()
    }

    private fun loadCollections() {
        if (mid == 0L) return
        viewModelScope.launch {
            _state.value = _state.value.copy(collectionLoading = true, collectionLoaded = true)
            runCatching { repo.spaceCollections(mid) }
                .onSuccess { list ->
                    _state.value = _state.value.copy(collections = list, collectionLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        collectionLoading = false,
                        collectionLoaded = false,
                        message = e.message,
                    )
                }
        }
    }

    /** 举报该 UP 主；[reason] 沿用 B 站举报原因码。 */
    fun reportUser(reason: Int) {
        if (mid == 0L) return
        viewModelScope.launch {
            runCatching { repo.reportUser(mid, reason) }
                .onSuccess { _state.value = _state.value.copy(message = "举报已提交") }
                .onFailure { e -> _state.value = _state.value.copy(message = e.message) }
        }
    }

    /** 屏蔽 / 解除屏蔽该 UP 主（先本地翻转，失败再回滚）。 */
    fun toggleBlock() {
        if (mid == 0L) return
        val target = !_state.value.blocked
        _state.value = _state.value.copy(blocked = target)
        viewModelScope.launch {
            runCatching { repo.blockUser(mid, target) }
                .onSuccess {
                    _state.value = _state.value.copy(message = if (target) "已屏蔽" else "已解除屏蔽")
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(blocked = !target, message = e.message)
                }
        }
    }

    /** 取某排序的数据（缺省为空）。 */
    private fun sortData(order: String): SortData =
        _state.value.sorts[order] ?: SortData()

    /** 写回某排序的数据。 */
    private fun updateSort(order: String, transform: (SortData) -> SortData) {
        val cur = sortData(order)
        _state.value = _state.value.copy(sorts = _state.value.sorts + (order to transform(cur)))
    }

    /** 切换到某排序并确保其数据已加载（翻页 / 点击排序项时调用）。 */
    fun setSort(order: String) {
        if (_state.value.sort != order) {
            _state.value = _state.value.copy(sort = order)
        }
        ensureSort(order)
    }

    /** 仅当该排序尚未加载过时才真正发起请求。 */
    fun ensureSort(order: String) {
        if (mid == 0L) return
        val d = sortData(order)
        if (d.loaded || d.loading) return
        loadFirstPage(order)
    }

    /** 拉第一页投稿（切换排序 / 失败重试时调用）。 */
    private fun loadFirstPage(order: String) {
        if (mid == 0L) return
        updateSort(order) {
            it.copy(loading = true, error = null, end = false, videos = emptyList(), page = 1)
        }
        viewModelScope.launch {
            runCatching { repo.spaceVideos(mid, 1, order, "") }
                .onSuccess { (total, list) ->
                    updateSort(order) {
                        it.copy(videos = list, end = list.isEmpty(), loading = false, loaded = true)
                    }
                    // 投稿总数以接口为准（资料接口的 archive_count 有时不更新）
                    if (total > 0) {
                        _state.value = _state.value.copy(
                            profile = _state.value.profile.copy(totalVideos = total),
                        )
                    }
                }
                .onFailure { e ->
                    updateSort(order) { it.copy(loading = false, error = e.message ?: "加载失败") }
                }
        }
    }

    /** 某排序滚动到底部时加载下一页。 */
    fun loadMoreSort(order: String) {
        val d = sortData(order)
        if (!d.loaded || d.loading || d.loadingMore || d.end) return
        val next = d.page + 1
        updateSort(order) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { repo.spaceVideos(mid, next, order, "") }
                .onSuccess { (_, more) ->
                    updateSort(order) {
                        it.copy(
                            videos = it.videos + more,
                            page = next,
                            end = more.isEmpty(),
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { updateSort(order) { it.copy(loadingMore = false) } }
        }
    }

    fun toggleFollow() {
        if (!session.isLogin()) {
            _state.value = _state.value.copy(message = "请先登录")
            return
        }
        if (mid == 0L) return
        val target = !_state.value.following
        _state.value = _state.value.copy(following = target)
        viewModelScope.launch {
            runCatching { repo.follow(mid, target) }
                .onSuccess { _state.value = _state.value.copy(message = if (target) "已关注" else "已取消关注") }
                .onFailure { e ->
                    _state.value = _state.value.copy(following = !target, message = e.message)
                }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

@Composable
fun SpaceScreen(
    mid: Long,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenChat: (Long, String, String) -> Unit = { _, _, _ -> },
    /** 专栏（文章）点开后进入阅读页。 */
    onOpenArticle: (Long) -> Unit = {},
    /** 合集 / 系列点开后进入详情页。 */
    onOpenCollection: (SpaceCollection) -> Unit = {},
    initialTab: Int = 0,
    viewModel: SpaceViewModel = viewModel(),
) {
    val colors = biko
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var moreMenu by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }

    LaunchedEffect(mid) {
        viewModel.load(mid)
        // 从「我的」页进入时可指定初始标签（0 动态 / 1 投稿）
        viewModel.setTab(initialTab)
    }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        // 顶栏：左侧返回箭头（无底），右侧「搜索 + 更多」
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                tint = colors.text,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onBack)
                    .padding(11.dp),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索TA的动态、视频",
                tint = colors.text,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onOpenSearch)
                    .padding(10.dp),
            )
            Box {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    tint = colors.text,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { moreMenu = true }
                        .padding(10.dp),
                )
                DropdownMenu(
                    expanded = moreMenu,
                    onDismissRequest = { moreMenu = false },
                    containerColor = colors.card,
                ) {
                    DropdownMenuItem(
                        text = { Text("分享主页", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        onClick = {
                            moreMenu = false
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://space.bilibili.com/$mid")
                            }
                            context.startActivity(Intent.createChooser(intent, "分享到"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("举报", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        onClick = {
                            moreMenu = false
                            reportOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (state.blocked) "解除屏蔽" else "屏蔽",
                                color = colors.acc,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        onClick = {
                            moreMenu = false
                            viewModel.toggleBlock()
                        },
                    )
                }
            }
        }

        // 资料区 + 昵称 + uid + 简介 + 标签行：作为列表首项随内容一起滚动
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // 小蓝条动画放在 when 之外：切换「动态/投稿」时此层不重组，
            // 动画状态得以保留，蓝条才能真正滑动而不是瞬移。
            val tabProgress by animateFloatAsState(
                targetValue = state.tab.toFloat(),
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 900f),
                label = "space_tab_indicator",
            )
            val header: @Composable () -> Unit = {
                SpaceHeader(
                    mid = mid,
                    state = state,
                    tabProgress = tabProgress,
                    onTab = viewModel::setTab,
                    onToggleFollow = viewModel::toggleFollow,
                    onMessage = { onOpenChat(mid, state.profile.name, state.profile.face) },
                )
            }
            when (state.tab) {
                0 -> DynamicTab(
                    state = state,
                    header = header,
                    onOpenVideo = onOpenVideo,
                    onLoadMore = viewModel::loadMoreDynamics,
                    onRetry = viewModel::reloadDynamics,
                )
                1 -> SubmitTab(
                    state = state,
                    header = header,
                    onOpenVideo = onOpenVideo,
                    onSort = viewModel::setSort,
                    onLoadMore = viewModel::loadMoreSort,
                )
                2 -> ArticleTab(
                    state = state,
                    header = header,
                    onOpenArticle = onOpenArticle,
                    onEnsure = viewModel::ensureArticles,
                    onLoadMore = viewModel::loadMoreArticles,
                )
                else -> CollectionTab(
                    state = state,
                    header = header,
                    onOpenCollection = onOpenCollection,
                    onEnsure = viewModel::ensureCollections,
                )
            }
        }
    }

    // 关注 / 取消关注 / 举报 / 屏蔽 的结果提示
    LaunchedEffect(state.message) {
        state.message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    // 举报原因选择
    if (reportOpen) {
        AlertDialog(
            onDismissRequest = { reportOpen = false },
            title = { Text("举报该用户") },
            text = {
                Column {
                    Text("请选择举报原因", fontSize = 13.sp, color = colors.t3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    USER_REPORT_REASONS.forEach { (label, code) ->
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = colors.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    reportOpen = false
                                    viewModel.reportUser(code)
                                }
                                .padding(vertical = 11.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { reportOpen = false }) { Text("取消") }
            },
        )
    }
}

/** 空间页上半部分：资料区 + 昵称/徽章 + uid + 简介 + 标签行，作为列表首项随内容滚动。 */
@Composable
private fun SpaceHeader(
    mid: Long,
    state: SpaceState,
    /** 小蓝条进度（0=动态，1=投稿），由外层持有，避免切换标签时被重组重置。 */
    tabProgress: Float,
    onTab: (Int) -> Unit,
    onToggleFollow: () -> Unit,
    onMessage: () -> Unit,
) {
    val colors = biko
    Column(Modifier.fillMaxWidth()) {
        // 资料区：左侧大头像；右侧三列统计 + 关注 / 发消息
        // 头像垂直居中于右侧整体（统计 + 两个药丸）的拦腰线
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetworkImage(
                url = state.profile.face.ifEmpty { null },
                modifier = Modifier.size(96.dp).clip(CircleShape),
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    // 三列等宽平分，中心间距一致（比居中聚拢更舒展）
                    StatColumn("关注", state.profile.following, Modifier.weight(1f))
                    StatColumn("粉丝", state.profile.fans, Modifier.weight(1f))
                    StatColumn("获赞", state.profile.likes, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FollowButton(
                        following = state.following,
                        onClick = onToggleFollow,
                        modifier = Modifier.weight(1f),
                    )
                    MessageButton(onClick = onMessage, modifier = Modifier.weight(1f))
                }
            }
        }

        // 昵称 + LV 徽章 + 大会员标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.profile.name.ifEmpty { "未知用户" },
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.profile.vipStatus > 0) VipPink else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (state.profile.level > 0) {
                Text(
                    text = "LV${state.profile.level}",
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    color = Color.White,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(com.biko.client.ui.theme.Level6)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            if (state.profile.vipLabel.isNotEmpty()) {
                Text(
                    text = state.profile.vipLabel,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        // 主色调为蓝色，会员徽章统一用主题蓝
                        .background(colors.acc)
                        .padding(horizontal = 7.dp, vertical = 2.5.dp),
                )
            }
        }

        // uid 胶囊
        Text(
            text = "uid: $mid",
            fontSize = 13.sp,
            color = colors.t2,
            modifier = Modifier
                .padding(start = 16.dp, top = 10.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.card2)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )

        // 简介
        if (state.profile.sign.isNotEmpty()) {
            Text(
                text = state.profile.sign,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp),
            )
        }

        // 三标签：动态 / 投稿 / 专栏
        // 标签间距令「投稿」左缘与下方排序行首项左缘对齐
        // 排序行首项占位 = 8(内左) + 文字 + 8(内右) + 4(项末)，叠加行首 8dp，
        // 故 投稿左缘 = 16 + 动态宽 + gap，令其等于 36 + 「最新发布」宽，得 gap = 20 + 最新发布宽 - 动态宽。
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val dynW = measurer
            .measure(SPACE_TABS[0], TextStyle(fontSize = 15.sp))
            .size.width
        val latestW = measurer
            .measure(SPACE_SORTS[0].first, TextStyle(fontSize = 13.5.sp))
            .size.width
        val gap = with(density) {
            (20.dp.toPx() + (latestW - dynW)).coerceAtLeast(0f).toDp()
        }
        // 记录各标签的实测宽度，用于小蓝条定位
        val tabWidths = remember { mutableStateListOf<Int>().apply { repeat(SPACE_TABS.size) { add(0) } } }
        var tabRowH by remember { mutableIntStateOf(0) }
        // 小蓝条进度由外层（SpaceScreen）驱动：切换标签时本组件会被重建，
        // 若在此处新建动画会直接落在目标值上、看起来像瞬移，故用外层传入的值。
        val progress = tabProgress
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().onSizeChanged { tabRowH = it.height },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SPACE_TABS.forEachIndexed { index, label ->
                    val active = state.tab == index
                    Box(
                        modifier = Modifier
                            .onSizeChanged { tabWidths[index] = it.width }
                            .clickable { onTab(index) },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) colors.text else colors.t2,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (index != SPACE_TABS.lastIndex) {
                        Spacer(Modifier.width(gap))
                    }
                }
            }
            // 小蓝条：在各标签之间平滑滑动，切换途中横向拉伸
            if (tabRowH > 0 && tabWidths.all { it > 0 }) {
                val gapPx = with(density) { gap.toPx() }
                val basePx = with(density) { 16.dp.toPx() }
                val barH = with(density) { 3.dp.toPx() }
                val p = progress.coerceIn(0f, (SPACE_TABS.size - 1).toFloat())
                val i0 = kotlin.math.floor(p).toInt().coerceIn(0, SPACE_TABS.lastIndex)
                val i1 = (i0 + 1).coerceAtMost(SPACE_TABS.lastIndex)
                val f = p - i0
                fun centerOf(i: Int): Float {
                    var acc = 0f
                    for (k in 0 until i) acc += tabWidths[k] + gapPx
                    return acc + tabWidths[i] / 2f
                }
                val mid = 1f - kotlin.math.abs(f * 2f - 1f)
                val barW = basePx * (1f + 0.35f * mid)
                val center = centerOf(i0) + (centerOf(i1) - centerOf(i0)) * f
                Box(
                    modifier = Modifier
                        .offset { IntOffset((center - barW / 2f).toInt(), (tabRowH - barH).toInt()) }
                        .width(with(density) { barW.toDp() })
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.acc),
                )
            }
        }
    }
}

/** 「动态」标签：TA 的空间动态。 */
@Composable
private fun DynamicTab(
    state: SpaceState,
    header: @Composable () -> Unit,
    onOpenVideo: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = biko
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.dynamics.size, state.dynHasMore) {
        if (!state.dynHasMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                // 首项是 header，最后一条动态的下标为 dynamics.size
                if (last >= state.dynamics.size - 1) onLoadMore()
            }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
        item { header() }

        if (state.dynLoading && state.dynamics.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        } else if (state.dynamics.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有动态", fontSize = 13.sp, color = colors.t3)
                        Text(
                            text = "点击重试",
                            fontSize = 13.sp,
                            color = colors.acc,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .clickable(onClick = onRetry),
                        )
                    }
                }
            }
        } else {
            // 复用关注动态页的卡片；不使用 key，避免重复 id 崩溃
            lazyListItems(state.dynamics) { post ->
                DynamicCard(post = post, onOpenVideo = onOpenVideo)
            }
            if (state.dynHasMore) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.dynLoading) {
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

/** 「投稿」标签：上半部分（资料 / 标签 / 排序行）固定不动，仅下方网格左右滑动切换排序。 */
@Composable
private fun SubmitTab(
    state: SpaceState,
    header: @Composable () -> Unit,
    onOpenVideo: (String) -> Unit,
    onSort: (String) -> Unit,
    onLoadMore: (String) -> Unit,
) {
    val colors = biko
    val scope = rememberCoroutineScope()
    // 排序左右滑动切换（与首页 / 排行榜一致）
    val pagerState = rememberPagerState(
        initialPage = SPACE_SORTS.indexOfFirst { it.second == state.sort }.coerceAtLeast(0),
        pageCount = { SPACE_SORTS.size },
    )
    // 滑动到某页即切换排序（同时触发该排序第一页的加载）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onSort(SPACE_SORTS[page].second)
        }
    }
    Column(Modifier.fillMaxSize()) {
        // 上半部分（资料 + 动态/投稿标签）：固定不动
        header()

        // 排序行 + 投稿数：固定不动，高亮跟随当前页
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp)
                .padding(top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SPACE_SORTS.forEachIndexed { index, (label, _) ->
                    val active = index == pagerState.currentPage
                    Text(
                        text = label,
                        fontSize = 13.5.sp,
                        color = if (active) colors.acc else colors.t3,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
            if (state.profile.totalVideos > 0) {
                Text(
                    text = "${state.profile.totalVideos}个投稿",
                    fontSize = 12.5.sp,
                    color = colors.t3,
                )
            }
        }

        // 仅下方投稿网格左右滑动切换排序
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val order = SPACE_SORTS[page].second
            val data = state.sorts[order] ?: SortData()
            val gridState = rememberLazyGridState()
            val count = data.videos.size
            val shouldLoadMore by remember(count) {
                derivedStateOf {
                    val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    count > 0 && last >= count - 2
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore(order)
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    data.loading && data.videos.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }

                    data.videos.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = data.error ?: "没有找到投稿",
                            fontSize = 13.sp,
                            color = colors.t3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp),
                        )
                    }

                    else -> {
                        items(data.videos, key = { it.bvid }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { if (video.bvid.isNotEmpty()) onOpenVideo(video.bvid) },
                                modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 14.dp),
                            )
                        }
                        if (data.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 「专栏」标签：TA 的专栏文章列表，随内容滚动加载更多。 */
@Composable
private fun ArticleTab(
    state: SpaceState,
    header: @Composable () -> Unit,
    onOpenArticle: (Long) -> Unit,
    onEnsure: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = biko
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { onEnsure() }
    LaunchedEffect(listState, state.articles.size, state.articleEnd) {
        if (state.articleEnd) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                // 首项是 header，最后一条文章的下标为 articles.size
                if (last >= state.articles.size) onLoadMore()
            }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
        item { header() }

        when {
            state.articleLoading && state.articles.isEmpty() -> item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            state.articles.isEmpty() -> item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.articleLoaded) "还没有专栏" else "加载中…",
                        fontSize = 13.sp,
                        color = colors.t3,
                    )
                }
            }

            else -> {
                lazyListItems(state.articles, key = { it.id }) { article ->
                    ArticleRow(article = article, onClick = { onOpenArticle(article.id) })
                }
                if (!state.articleEnd) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.articleLoading) {
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

/** 「合集」标签：TA 的合集与系列，两列网格展示。 */
@Composable
private fun CollectionTab(
    state: SpaceState,
    header: @Composable () -> Unit,
    onOpenCollection: (SpaceCollection) -> Unit,
    onEnsure: () -> Unit,
) {
    val colors = biko
    LaunchedEffect(Unit) { onEnsure() }
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        when {
            state.collectionLoading && state.collections.isEmpty() -> item(
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            state.collections.isEmpty() -> item(
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.collectionLoaded) "还没有合集" else "加载中…",
                        fontSize = 13.sp,
                        color = colors.t3,
                    )
                }
            }

            else -> items(
                items = state.collections,
                key = { (if (it.isSeason) "s" else "r") + it.id },
            ) { collection ->
                CollectionCard(
                    collection = collection,
                    onClick = { onOpenCollection(collection) },
                )
            }
        }
    }
}

/** 合集 / 系列卡片：封面 + 类型角标 + 稿件数 + 标题。 */
@Composable
private fun CollectionCard(collection: SpaceCollection, onClick: () -> Unit) {
    val colors = biko
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            NetworkImage(url = collection.cover.ifEmpty { null }, modifier = Modifier.fillMaxSize())
            Text(
                text = if (collection.isSeason) "合集" else "系列",
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(colors.acc, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
            )
            Text(
                text = "${collection.total}个视频",
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
            )
        }
        Text(
            text = collection.title,
            fontSize = 13.sp,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 统计列：大号数字 + 下方小标签。 */
@Composable
private fun StatColumn(label: String, value: Long, modifier: Modifier = Modifier) {
    val colors = biko
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatCount(value.toInt()),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text,
        )
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = colors.t3,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 关注 / 已关注 按钮。 */
@Composable
private fun FollowButton(
    following: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (following) biko.card2 else FollowNavy)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (following) "已关注" else "关注",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (following) biko.t2 else Color.White,
        )
    }
}

/** 发消息 按钮。 */
@Composable
private fun MessageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = biko
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.card2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "发消息",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.t2,
        )
    }
}

/** 举报原因码，与 B 站评论举报共用同一套。 */
private val USER_REPORT_REASONS = listOf(
    "垃圾广告" to 1,
    "色情低俗" to 2,
    "刷屏" to 3,
    "引战" to 4,
    "剧透" to 5,
    "政治敏感" to 6,
    "人身攻击" to 7,
    "其他" to 0,
)

/** 专属搜索页：搜索某 UP 主空间内的动态与视频。 */
data class SpaceSearchState(
    /** 输入框里的文字。 */
    val query: String = "",
    /** 已提交的搜索词。 */
    val submitted: String = "",
    /** 是否已发起过搜索。 */
    val searched: Boolean = false,
    val videos: List<Video> = emptyList(),
    val dynamics: List<DynamicPost> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val end: Boolean = true,
    val error: String? = null,
)

class SpaceSearchViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val _state = MutableStateFlow(SpaceSearchState())
    val state = _state.asStateFlow()

    private var mid = 0L
    private var page = 1

    fun start(mid: Long) {
        if (this.mid == mid) return
        this.mid = mid
        _state.value = SpaceSearchState()
    }

    fun setQuery(text: String) {
        _state.value = _state.value.copy(query = text)
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty() || mid == 0L) return
        _state.value = _state.value.copy(
            submitted = q,
            searched = true,
            loading = true,
            error = null,
            videos = emptyList(),
            dynamics = emptyList(),
            end = false,
        )
        page = 1
        // 视频结果：走空间投稿接口的关键字搜索
        viewModelScope.launch {
            runCatching { repo.spaceVideos(mid, 1, "pubdate", q) }
                .onSuccess { (_, list) ->
                    _state.value = _state.value.copy(
                        videos = list,
                        end = list.isEmpty(),
                        loading = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        end = true,
                        error = e.message ?: "搜索失败",
                    )
                }
        }
        // 动态结果：取其空间动态后按关键字本地过滤
        viewModelScope.launch {
            runCatching { repo.spaceDynamicFeed(mid, "") }
                .onSuccess { p ->
                    val dyn = p.posts.filter {
                        it.text.contains(q, true) || it.videoTitle.contains(q, true)
                    }
                    _state.value = _state.value.copy(dynamics = dyn)
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.end || s.submitted.isEmpty()) return
        page++
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching { repo.spaceVideos(mid, page, "pubdate", s.submitted) }
                .onSuccess { (_, more) ->
                    _state.value = _state.value.copy(
                        videos = _state.value.videos + more,
                        end = more.isEmpty(),
                        loadingMore = false,
                    )
                }
                .onFailure {
                    page--
                    _state.value = _state.value.copy(loadingMore = false)
                }
        }
    }
}

@Composable
fun SpaceSearchScreen(
    mid: Long,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    viewModel: SpaceSearchViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(mid) { viewModel.start(mid) }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val doSearch: () -> Unit = {
        keyboard?.hide()
        viewModel.search()
    }
    val upName = SpaceSelection.current?.name.orEmpty()

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        // 顶栏：返回 + 圆角输入框（占位「搜索」+ 清空 X）+ 放大镜
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                tint = colors.text,
                modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .height(36.dp)
                    .background(colors.card, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.query.isEmpty()) {
                        Text("搜索", fontSize = 14.sp, color = colors.t3)
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                        cursorBrush = SolidColor(colors.acc),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                if (state.query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = colors.t3,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(16.dp)
                            .clickable {
                                viewModel.setQuery("")
                                focusRequester.requestFocus()
                            },
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索",
                tint = colors.acc,
                modifier = Modifier.size(36.dp).clickable { doSearch() }.padding(7.dp),
            )
        }

        if (!state.searched) {
            // 未搜索：居中提示
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "搜索「$upName」的动态、视频",
                    fontSize = 14.sp,
                    color = colors.t3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            SpaceSearchResults(
                state = state,
                onOpenVideo = onOpenVideo,
                onLoadMore = viewModel::loadMore,
            )
        }
    }
}

@Composable
private fun SpaceSearchResults(
    state: SpaceSearchState,
    onOpenVideo: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = biko
    val listState = rememberLazyListState()
    // 触底加载：只读 listState（快照状态），避免 remember 捕获首次组合的旧数据
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 2
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        if (state.dynamics.isNotEmpty()) {
            item { SpaceSearchSection("相关动态") }
            lazyListItems(state.dynamics) { post ->
                DynamicCard(post = post, onOpenVideo = onOpenVideo)
            }
        }
        if (state.videos.isNotEmpty()) {
            item { SpaceSearchSection("相关视频") }
            lazyListItems(state.videos) { video ->
                RankRow(
                    video = video,
                    onClick = { if (video.bvid.isNotEmpty()) onOpenVideo(video.bvid) },
                )
            }
        }

        when {
            state.loading -> item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            state.dynamics.isEmpty() && state.videos.isEmpty() -> item {
                Text(
                    text = state.error ?: "没有找到相关内容",
                    fontSize = 13.sp,
                    color = colors.t3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp),
                )
            }
        }

        if (state.loadingMore) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(22.dp)) }
            }
        }
    }
}

/** 专属搜索页的小节标题。 */
@Composable
private fun SpaceSearchSection(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = biko.text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
