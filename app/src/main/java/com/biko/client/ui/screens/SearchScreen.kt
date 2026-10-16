package com.biko.client.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.bangumiEpisodes
import com.biko.client.data.hotSearch
import com.biko.client.data.model.ArticleSummary
import com.biko.client.data.model.BangumiSeason
import com.biko.client.data.model.LiveRoom
import com.biko.client.data.model.SearchRow
import com.biko.client.data.model.SearchUser
import com.biko.client.data.model.Video
import com.biko.client.data.searchArticles
import com.biko.client.data.searchLiveRooms
import com.biko.client.data.searchMedia
import com.biko.client.data.searchSuggest
import com.biko.client.data.searchUsers
import com.biko.client.data.searchVideos
import com.biko.client.data.watchLaterAdd
import com.biko.client.ui.components.BangumiSeasonCard
import com.biko.client.ui.components.ChannelTabs
import com.biko.client.ui.components.LiveCard
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.components.RankRow
import com.biko.client.ui.components.VideoCard
import com.biko.client.ui.theme.biko
import com.biko.client.ui.theme.rankColor
import com.biko.client.util.formatCount
import com.biko.client.util.formatTimeAgo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 搜索结果的内容类型分类（对齐官方 App 的 Tab，仅保留有对应详情页、可跳转的类型）。 */
enum class SearchTab(val label: String) {
    VIDEO("视频"),
    LIVE("直播间"),
    USER("UP主"),
    ARTICLE("专栏"),
    BANGUMI("番剧"),
    MEDIA_FT("影视"),
}

/** 视频结果的排序方式（对齐 BiliPai / B 站官方：6 项）。 */
enum class SortOrder(val apiValue: String, val label: String) {
    DEFAULT("totalrank", "默认排序"),
    CLICK("click", "播放多"),
    PUBDATE("pubdate", "新发布"),
    DM("dm", "弹幕多"),
    STOW("stow", "收藏多"),
    SCORES("scores", "评论多"),
}

/** 视频筛选：内容时长。 */
enum class DurationFilter(val value: Int, val label: String) {
    ALL(0, "全部时长"),
    UNDER_10(1, "0-10分钟"),
    TEN_TO_30(2, "10-30分钟"),
    THIRTY_TO_60(3, "30-60分钟"),
    OVER_60(4, "60分钟+"),
}

/** 视频筛选：发布时间。 */
enum class PubTimeType(val label: String) {
    ALL("不限"),
    DAY("最近一天"),
    WEEK("最近一周"),
    HALF_YEAR("最近半年"),
    CUSTOM("自定义"),
}

/** 视频筛选：内容分区（tid → 名称，与 B 站分区一致）。 */
data class SearchZone(val tid: Int, val label: String)

private val SEARCH_ZONES = listOf(
    SearchZone(0, "全部"), SearchZone(1, "动画"), SearchZone(13, "番剧"), SearchZone(167, "国创"),
    SearchZone(3, "音乐"), SearchZone(129, "舞蹈"), SearchZone(4, "游戏"), SearchZone(36, "知识"),
    SearchZone(188, "科技"), SearchZone(234, "运动"), SearchZone(223, "汽车"), SearchZone(160, "生活"),
    SearchZone(221, "美食"), SearchZone(217, "动物"), SearchZone(119, "鬼畜"), SearchZone(115, "时尚"),
    SearchZone(202, "资讯"), SearchZone(5, "娱乐"), SearchZone(181, "影视"), SearchZone(177, "记录"),
    SearchZone(23, "电影"), SearchZone(11, "电视"),
)

/** 把发布时间选项换算成请求接口用的 epoch 秒区间（ALL 返回空）。 */
private fun resolvePubTimeRange(
    type: PubTimeType,
    customBegin: Long? = null,
    customEnd: Long? = null,
): Pair<Long?, Long?> {
    if (type == PubTimeType.ALL) return null to null
    if (type == PubTimeType.CUSTOM) return customBegin to customEnd
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 0)
    val end = cal.timeInMillis / 1000
    val daysBack = when (type) {
        PubTimeType.DAY -> 0
        PubTimeType.WEEK -> 6
        else -> 179
    }
    cal.add(Calendar.DAY_OF_YEAR, -daysBack)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 1000 to end
}

private val SEARCH_TABS = SearchTab.values().toList()

/** 视频 Tab 下单个排序各自的结果缓存（左右滑动切换排序时各页互不干扰）。 */
data class SearchPageState(
    val results: List<SearchRow> = emptyList(),
    val page: Int = 1,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val end: Boolean = false,
    /** 是否已经拉取过（用于区分「还没加载」和「确实无结果」）。 */
    val loaded: Boolean = false,
)

data class SearchState(
    val hot: List<String> = emptyList(),
    /** 本地搜索历史（最近在前）。 */
    val history: List<String> = emptyList(),
    /** 输入时的联想词。 */
    val suggestions: List<String> = emptyList(),
    val results: List<SearchRow> = emptyList(),
    /** 当前分类 Tab。 */
    val tab: SearchTab = SearchTab.VIDEO,
    /** 视频 Tab 的排序方式。 */
    val order: SortOrder = SortOrder.DEFAULT,
    /** 视频 Tab 是否用双列网格展示（false 为单列列表）。 */
    val grid: Boolean = true,
    /** 视频筛选：内容时长（[DurationFilter.value]）。 */
    val duration: Int = 0,
    /** 视频筛选：内容分区 tid（0 为全部）。 */
    val videoTid: Int = 0,
    /** 视频筛选：发布时间。 */
    val pubTime: PubTimeType = PubTimeType.ALL,
    val pubBegin: Long? = null,
    val pubEnd: Long? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val searched: Boolean = false,
    /** 已经翻到底（接口没有更多结果）。 */
    val end: Boolean = false,
    /** 视频 Tab 各排序的结果缓存（key 为排序方式，滑动切换时各自保留）。 */
    val orders: Map<SortOrder, SearchPageState> = emptyMap(),
    /** 正在拉取某部番剧 / 影视的整季分集（用于显示加载遮罩）。 */
    val seasonLoading: Boolean = false,
    /** 稍后再看 / 不感兴趣 的一次性提示。 */
    val message: String? = null,
)

class SearchViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val historyStore = AppGraph.searchHistory
    private val dislike = AppGraph.dislike
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    private var keyword = ""
    private var page = 1

    /** 联想词请求的防抖任务：输入变化时先取消上一次。 */
    private var suggestJob: Job? = null

    init {
        loadHot()
        loadHistory()
    }

    fun loadHot() {
        viewModelScope.launch {
            runCatching { repo.hotSearch() }.onSuccess { hot -> _state.value = _state.value.copy(hot = hot) }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            val list = runCatching { historyStore.load() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(history = list)
        }
    }

    /** 输入框内容变化：防抖后拉取联想词（空串直接清空）。 */
    fun onInputChange(text: String) {
        suggestJob?.cancel()
        if (text.isBlank()) {
            _state.value = _state.value.copy(suggestions = emptyList())
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val list = runCatching { repo.searchSuggest(text) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(suggestions = list)
        }
    }

    /** 发起新搜索：重置到视频 Tab、默认排序，从第一页开始。 */
    fun search(rawKeyword: String) {
        val kw = rawKeyword.trim()
        if (kw.isEmpty()) return
        keyword = kw
        page = 1
        suggestJob?.cancel()
        _state.value = _state.value.copy(
            loading = false,
            searched = true,
            end = false,
            suggestions = emptyList(),
            results = emptyList(),
            tab = SearchTab.VIDEO,
            order = SortOrder.DEFAULT,
            orders = emptyMap(),
            duration = 0,
            videoTid = 0,
            pubTime = PubTimeType.ALL,
            pubBegin = null,
            pubEnd = null,
        )
        ensureOrder(SortOrder.DEFAULT)
        // 无论成功与否都记录这次搜索（关键词仍然有效，方便下次复搜）
        viewModelScope.launch {
            historyStore.add(kw)
            loadHistory()
        }
    }

    /** 切换分类 Tab，并重新拉取第一页。 */
    fun selectTab(tab: SearchTab) {
        if (tab == _state.value.tab) return
        page = 1
        val video = tab == SearchTab.VIDEO
        _state.value = _state.value.copy(
            tab = tab,
            results = emptyList(),
            end = false,
            loading = !video,
            order = SortOrder.DEFAULT,
        )
        if (video) ensureOrder(SortOrder.DEFAULT) else loadPage(1, replace = true)
    }

    /** 切换视频排序方式；结果由各自的排序页自行缓存与加载。 */
    fun selectOrder(order: SortOrder) {
        if (order == _state.value.order) return
        _state.value = _state.value.copy(order = order)
        ensureOrder(order)
    }

    /** 视频 Tab：某排序还没拉取过时才拉它的第一页（滑动到该页时触发）。 */
    fun ensureOrder(order: SortOrder) {
        if (_state.value.orders[order]?.loaded == true) return
        loadOrder(order, 1, replace = true)
    }

    /** 视频 Tab：某排序滑到底部时继续加载下一页。 */
    fun loadMoreOrder(order: SortOrder) {
        val s = _state.value.orders[order] ?: return
        if (s.loading || s.loadingMore || s.end) return
        loadOrder(order, s.page + 1, replace = false)
    }

    /** 更新某排序的缓存。 */
    private fun updateOrder(order: SortOrder, transform: (SearchPageState) -> SearchPageState) {
        val current = _state.value.orders[order] ?: SearchPageState()
        _state.value = _state.value.copy(orders = _state.value.orders + (order to transform(current)))
    }

    /** 视频 Tab：按排序（叠加当前筛选）拉取指定页，结果写入该排序的独立缓存。 */
    private fun loadOrder(order: SortOrder, target: Int, replace: Boolean) {
        val kw = keyword
        val s0 = _state.value
        val (begin, end) = resolvePubTimeRange(s0.pubTime, s0.pubBegin, s0.pubEnd)
        val duration = s0.duration
        val tids = s0.videoTid
        viewModelScope.launch {
            updateOrder(order) { it.copy(loading = replace, loadingMore = !replace) }
            val rows = runCatching {
                repo.searchVideos(kw, target, order.apiValue, duration, tids, begin, end)
                    .map { SearchRow.VideoRow(it) }
            }.getOrDefault(emptyList())

            // 期间用户可能已发起新搜索，关键词变了就丢弃这次结果，避免串台
            if (keyword != kw) return@launch
            // 期间用户改了筛选条件也丢弃，避免旧筛选的结果覆盖新筛选
            val now = _state.value
            if (now.duration != duration || now.videoTid != tids || now.pubTime != s0.pubTime ||
                now.pubBegin != s0.pubBegin || now.pubEnd != s0.pubEnd
            ) return@launch

            updateOrder(order) { s ->
                if (replace) {
                    s.copy(
                        loading = false,
                        loadingMore = false,
                        results = rows,
                        end = rows.isEmpty(),
                        page = target,
                        loaded = true,
                    )
                } else {
                    val exist = s.results
                    val merged = exist + rows.filter { row -> exist.none { it.key == row.key } }
                    s.copy(
                        loading = false,
                        loadingMore = false,
                        results = merged,
                        end = rows.isEmpty() || merged.size == exist.size,
                        page = target,
                        loaded = true,
                    )
                }
            }
        }
    }

    /** 视频 Tab 在网格 / 列表之间切换。 */
    fun toggleGrid() = _state.value.let { _state.value = it.copy(grid = !it.grid) }

    /** 应用视频筛选：清空所有排序页缓存，并用当前排序重新拉取第一页。 */
    private fun applyVideoFilter(transform: (SearchState) -> SearchState) {
        val next = transform(_state.value).copy(orders = emptyMap())
        _state.value = next
        page = 1
        ensureOrder(next.order)
    }

    /** 视频筛选：内容时长。 */
    fun setDurationFilter(value: Int) {
        if (_state.value.duration == value) return
        applyVideoFilter { it.copy(duration = value) }
    }

    /** 视频筛选：内容分区。 */
    fun setZoneFilter(tid: Int) {
        if (_state.value.videoTid == tid) return
        applyVideoFilter { it.copy(videoTid = tid) }
    }

    /** 视频筛选：发布时间（[type] 为 CUSTOM 时用 [begin] / [end] 指定日期区间，单位毫秒）。 */
    fun setPubTimeFilter(type: PubTimeType, begin: Long? = null, end: Long? = null) {
        val s = _state.value
        if (s.pubTime == type && s.pubBegin == begin && s.pubEnd == end) return
        applyVideoFilter { it.copy(pubTime = type, pubBegin = begin, pubEnd = end) }
    }

    fun removeHistory(keyword: String) {
        viewModelScope.launch {
            historyStore.remove(keyword)
            loadHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyStore.clear()
            _state.value = _state.value.copy(history = emptyList())
        }
    }

    /** 一次性提示消费后清空，避免重复弹出。 */
    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** 搜索结果卡片：加入稍后再看。 */
    fun watchLater(video: Video) {
        if (video.aid <= 0) return
        viewModelScope.launch {
            runCatching { repo.watchLaterAdd(video.aid) }
                .onSuccess { _state.value = _state.value.copy(message = "已加入稍后再看") }
                .onFailure { e -> _state.value = _state.value.copy(message = e.message ?: "加入稍后再看失败") }
        }
    }

    /** 搜索结果卡片：不感兴趣（从当前排序结果移除并写入本地屏蔽名单）。 */
    fun notInterested(order: SortOrder, video: Video) {
        if (video.bvid.isEmpty()) return
        updateOrder(order) { s ->
            s.copy(results = s.results.filterNot { (it as? SearchRow.VideoRow)?.video?.bvid == video.bvid })
        }
        _state.value = _state.value.copy(message = "已减少此类推荐")
        viewModelScope.launch { dislike.add(video.bvid) }
    }

    /**
     * 点番剧 / 影视结果：拉取整季分集后直接进入第一集的详情页
     * （详情页自带「选集」，不再需要单独的分集弹层）。
     */
    fun openSeason(season: BangumiSeason, onOpen: (String) -> Unit) {
        _state.value = _state.value.copy(seasonLoading = true)
        viewModelScope.launch {
            runCatching { repo.bangumiEpisodes(season.seasonId) }
                .onSuccess { (_, episodes) ->
                    _state.value = _state.value.copy(seasonLoading = false)
                    episodes.firstOrNull { it.bvid.isNotEmpty() }?.let { onOpen(it.bvid) }
                }
                .onFailure {
                    _state.value = _state.value.copy(seasonLoading = false)
                }
        }
    }

    /** 滑到底部时继续加载下一页，实现信息流式无限下滑。 */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.end || !current.searched) return
        loadPage(page + 1, replace = false)
    }

    /** 按当前 Tab / 排序拉取指定页；[replace] 为 true 表示首屏（清空旧结果）。 */
    private fun loadPage(target: Int, replace: Boolean) {
        val s0 = _state.value
        val tab = s0.tab
        val order = s0.order.apiValue
        val (begin, end) = resolvePubTimeRange(s0.pubTime, s0.pubBegin, s0.pubEnd)
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = replace, loadingMore = !replace)
            val rows = runCatching {
                when (tab) {
                    SearchTab.VIDEO -> repo.searchVideos(
                        keyword, target, order, s0.duration, s0.videoTid, begin, end,
                    ).map { SearchRow.VideoRow(it) }
                    SearchTab.LIVE -> repo.searchLiveRooms(keyword, target).map { SearchRow.LiveRow(it) }
                    SearchTab.USER -> repo.searchUsers(keyword, target, live = false).map { SearchRow.UserRow(it) }
                    SearchTab.ARTICLE -> repo.searchArticles(keyword, target).map { SearchRow.ArticleRow(it) }
                    SearchTab.BANGUMI -> repo.searchMedia(keyword, target, bangumi = true).map { SearchRow.SeasonRow(it) }
                    SearchTab.MEDIA_FT -> repo.searchMedia(keyword, target, bangumi = false).map { SearchRow.SeasonRow(it) }
                }
            }.getOrDefault(emptyList())

            // 请求期间用户可能已切到别的 Tab，此时丢弃这次结果，避免串台
            if (_state.value.tab != tab) return@launch

            val current = _state.value
            if (replace) {
                page = target
                _state.value = current.copy(
                    loading = false,
                    loadingMore = false,
                    results = rows,
                    end = rows.isEmpty(),
                )
            } else {
                page = target
                val exist = current.results
                val merged = exist + rows.filter { row -> exist.none { it.key == row.key } }
                _state.value = current.copy(
                    loading = false,
                    loadingMore = false,
                    results = merged,
                    end = rows.isEmpty() || merged.size == exist.size,
                )
            }
        }
    }

    companion object {
        private const val SUGGEST_DEBOUNCE_MS = 250L
    }
}

@Composable
fun SearchScreen(
    onOpenVideo: (String) -> Unit,
    onOpenLive: (LiveRoom) -> Unit,
    onOpenSpace: (Long, String, String) -> Unit,
    /** 专栏搜索结果点开后进入专栏阅读页。 */
    onOpenArticle: (Long) -> Unit = {},
    /** 番剧 / 影视分集点开后进入详情（历史记录归入「番剧」分类）。 */
    onOpenBangumi: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val colors = biko
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    /** 视频结果筛选弹层的显隐。 */
    var showFilter by remember { mutableStateOf(false) }

    // 稍后再看 / 不感兴趣 的结果提示
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 进入搜索页自动聚焦并弹出键盘，符合搜索场景的预期
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val doSearch: (String) -> Unit = { kw ->
        keyboard?.hide()
        viewModel.search(kw)
    }

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
                    if (input.isEmpty()) {
                        Text("搜索视频、UP主、番剧影视", fontSize = 14.sp, color = colors.t3)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            viewModel.onInputChange(it)
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                        cursorBrush = SolidColor(colors.acc),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doSearch(input) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                if (input.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = colors.t3,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(16.dp)
                            .clickable {
                                input = ""
                                viewModel.onInputChange("")
                                focusRequester.requestFocus()
                            },
                    )
                }
            }
            Text(
                text = "搜索",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.acc,
                modifier = Modifier.clickable { doSearch(input) }.padding(6.dp),
            )
        }

        // 已搜索后才出现内容分类 Tab
        if (state.searched) {
            ChannelTabs(
                names = SEARCH_TABS.map { it.label },
                selected = SEARCH_TABS.indexOf(state.tab),
                onSelect = { viewModel.selectTab(SEARCH_TABS[it]) },
            )
        }

        Box(Modifier.fillMaxSize()) {
            val showSuggest = input.isNotBlank() && state.suggestions.isNotEmpty()
            when {
                showSuggest -> SuggestPanel(suggestions = state.suggestions) { kw ->
                    input = kw
                    doSearch(kw)
                }

                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                // 视频 Tab 的空态由各排序页自己处理（默认排序外的排序可能还没加载）
                state.searched && state.results.isEmpty() && state.tab != SearchTab.VIDEO -> {
                    Text("无结果", fontSize = 13.sp, color = colors.t3, modifier = Modifier.align(Alignment.Center))
                }

                state.searched -> ResultsArea(
                    state = state,
                    onOpenVideo = onOpenVideo,
                    onOpenLive = onOpenLive,
                    onOpenSpace = onOpenSpace,
                    onOpenArticle = onOpenArticle,
                    onOpenSeason = { viewModel.openSeason(it, onOpenBangumi) },
                    onWatchLater = { viewModel.watchLater(it) },
                    onNotInterested = { order, video -> viewModel.notInterested(order, video) },
                    onSelectOrder = { viewModel.selectOrder(it) },
                    onToggleGrid = { viewModel.toggleGrid() },
                    onOpenFilter = { showFilter = true },
                    onLoadMore = { viewModel.loadMore() },
                    onEnsureOrder = { viewModel.ensureOrder(it) },
                    onLoadMoreOrder = { viewModel.loadMoreOrder(it) },
                )

                else -> LandingPanel(
                    history = state.history,
                    hot = state.hot,
                    onPick = { kw ->
                        input = kw
                        doSearch(kw)
                    },
                    onRemoveHistory = { viewModel.removeHistory(it) },
                    onClearHistory = { viewModel.clearHistory() },
                )
            }

            // 拉取整季分集时盖一层遮罩，避免点了没反应
            if (state.seasonLoading) {
                Box(
                    modifier = Modifier.matchParentSize().background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showFilter) {
        SearchFilterSheet(
            state = state,
            onDismiss = { showFilter = false },
            onDuration = { viewModel.setDurationFilter(it) },
            onZone = { viewModel.setZoneFilter(it) },
            onPubTime = { viewModel.setPubTimeFilter(it) },
            onCustomRange = { b, e -> viewModel.setPubTimeFilter(PubTimeType.CUSTOM, b, e) },
        )
    }
}

/** 搜索结果区：视频（排序可左右滑动）+ 直播间（双列），番剧/影视（双列），UP主（列表行）。 */
@Composable
private fun ResultsArea(
    state: SearchState,
    onOpenVideo: (String) -> Unit,
    onOpenLive: (LiveRoom) -> Unit,
    onOpenSpace: (Long, String, String) -> Unit,
    onOpenArticle: (Long) -> Unit,
    onOpenSeason: (BangumiSeason) -> Unit,
    onWatchLater: (Video) -> Unit,
    onNotInterested: (SortOrder, Video) -> Unit,
    onSelectOrder: (SortOrder) -> Unit,
    onToggleGrid: () -> Unit,
    onOpenFilter: () -> Unit,
    onLoadMore: () -> Unit,
    onEnsureOrder: (SortOrder) -> Unit,
    onLoadMoreOrder: (SortOrder) -> Unit,
) {
    val colors = biko
    val end = state.end
    val hasItems = state.results.isNotEmpty()
    val pageKey = state.results.size

    Column(Modifier.fillMaxSize()) {
        if (state.tab == SearchTab.VIDEO) {
            val orders = SortOrder.values()
            val pagerState = rememberPagerState(pageCount = { orders.size })

            // 左右滑动 pager（停稳后）→ 同步为当前排序
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    onSelectOrder(orders[page.coerceIn(0, orders.lastIndex)])
                }
            }
            // 点击排序栏 → pager 平滑滑到对应页
            LaunchedEffect(state.order) {
                val index = orders.indexOf(state.order).coerceAtLeast(0)
                if (pagerState.currentPage != index) pagerState.animateScrollToPage(index)
            }

            SortBar(
                order = state.order,
                grid = state.grid,
                pagerState = pagerState,
                filterActive = state.duration > 0 || state.videoTid > 0 || state.pubTime != PubTimeType.ALL,
                onSelectOrder = { onSelectOrder(it); onEnsureOrder(it) },
                onToggleGrid = onToggleGrid,
                onOpenFilter = onOpenFilter,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val item = orders[page]
                val os = state.orders[item] ?: SearchPageState()
                // 滑到该排序时按需加载它自己的第一页
                LaunchedEffect(item) { onEnsureOrder(item) }

                when {
                    os.results.isEmpty() && !os.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.acc)
                    }

                    os.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无结果", fontSize = 13.sp, color = colors.t3)
                    }

                    state.grid -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(os.results, key = { it.key }) { row ->
                            val video = (row as SearchRow.VideoRow).video
                            VideoCard(
                                video = video,
                                onClick = { onOpenVideo(video.bvid) },
                                onWatchLater = { onWatchLater(video) },
                                onNotInterested = { onNotInterested(item, video) },
                            )
                        }
                        gridFooter(os.end, true, os.results.size) { onLoadMoreOrder(item) }
                    }

                    // 单列列表：灰底 + 行间分隔线
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().background(colors.card),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    ) {
                        itemsIndexed(os.results, key = { _, row -> row.key }) { i, row ->
                            val video = (row as SearchRow.VideoRow).video
                            RankRow(
                                video = video,
                                onClick = { onOpenVideo(video.bvid) },
                                trailingActions = listOf(
                                    "稍后再看" to { onWatchLater(video) },
                                    "不感兴趣" to { onNotInterested(item, video) },
                                ),
                            )
                            if (i != os.results.lastIndex) {
                                HorizontalDivider(color = colors.line, thickness = 0.6.dp)
                            }
                        }
                        listFooter(os.end, true, os.results.size) { onLoadMoreOrder(item) }
                    }
                }
            }
        } else {
            when (state.tab) {
                SearchTab.LIVE -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.results, key = { it.key }) { row ->
                        val room = (row as SearchRow.LiveRow).room
                        LiveCard(room = room, onClick = { onOpenLive(room) })
                    }
                    gridFooter(end, hasItems, pageKey, onLoadMore)
                }

                SearchTab.BANGUMI, SearchTab.MEDIA_FT -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.results, key = { it.key }) { row ->
                        val season = (row as SearchRow.SeasonRow).season
                        BangumiSeasonCard(season = season, onClick = { onOpenSeason(season) })
                    }
                    gridFooter(end, hasItems, pageKey, onLoadMore)
                }

                SearchTab.ARTICLE -> LazyColumn(
                    modifier = Modifier.fillMaxSize().background(colors.card),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                ) {
                    itemsIndexed(state.results, key = { _, row -> row.key }) { i, row ->
                        val article = (row as SearchRow.ArticleRow).article
                        ArticleRow(article = article, onClick = { onOpenArticle(article.id) })
                        if (i != state.results.lastIndex) {
                            HorizontalDivider(color = colors.line, thickness = 0.6.dp)
                        }
                    }
                    listFooter(end, hasItems, pageKey, onLoadMore)
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().background(colors.card),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                ) {
                    itemsIndexed(state.results, key = { _, row -> row.key }) { i, row ->
                        val user = (row as SearchRow.UserRow).user
                        UserRow(user = user, onClick = { onOpenSpace(user.mid, user.name, user.face) })
                        if (i != state.results.lastIndex) {
                            HorizontalDivider(color = colors.line, thickness = 0.6.dp)
                        }
                    }
                    listFooter(end, hasItems, pageKey, onLoadMore)
                }
            }
        }
    }
}

/** 视频 Tab 顶部的排序 + 筛选 + 网格/列表切换栏（选中态为可滑动的灰色药丸，排序项过多时可横向滚动）。 */
@Composable
private fun SortBar(
    order: SortOrder,
    grid: Boolean,
    pagerState: PagerState,
    filterActive: Boolean,
    onSelectOrder: (SortOrder) -> Unit,
    onToggleGrid: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    val colors = biko
    val density = LocalDensity.current
    val items = SortOrder.values()
    val gap = 6.dp
    val gapPx = with(density) { gap.toPx() }
    val scroll = rememberScrollState()
    // 记录各排序项的实测宽度（px，含内边距），用于给药丸定位与插值
    val widths = remember { mutableStateListOf<Int>().apply { repeat(items.size) { add(0) } } }
    var rowH by remember { mutableIntStateOf(0) }
    // 药丸位置实时跟随 pager 的连续页码（currentPage + 偏移），滑动即起跟，无停顿延迟
    val progress = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (items.size - 1).toFloat())

    // 排序项较多时会横向溢出：切换排序时把选中项滚进可见区
    LaunchedEffect(order, widths.toList()) {
        if (widths.all { it > 0 }) {
            val i = items.indexOf(order).coerceAtLeast(0)
            var target = 0f
            for (k in 0 until i) target += widths[k] + gapPx
            scroll.animateScrollTo(target.toInt())
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scroll)
                .onSizeChanged { rowH = it.height },
        ) {
            Box {
                // 药丸声明在文字之前，位于文字下层；按 progress 在两段文字区间平滑滑动
                if (rowH > 0 && widths.all { it > 0 }) {
                    val p = progress
                    val i0 = floor(p).toInt().coerceIn(0, items.size - 1)
                    val i1 = (i0 + 1).coerceAtMost(items.size - 1)
                    val f = p - i0
                    fun startOf(i: Int): Float {
                        var acc = 0f
                        for (k in 0 until i) acc += widths[k] + gapPx
                        return acc
                    }
                    val w0 = widths[i0].toFloat()
                    val w1 = widths[i1].toFloat()
                    val mid = 1f - kotlin.math.abs(f * 2f - 1f)
                    val pillW = (w0 + (w1 - w0) * f) * (1f + 0.08f * mid)
                    val c0 = startOf(i0) + w0 / 2f
                    val c1 = startOf(i1) + w1 / 2f
                    val center = c0 + (c1 - c0) * f
                    val left = center - pillW / 2f
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(left.toInt(), 0) }
                            .width(with(density) { pillW.toDp() })
                            .height(with(density) { rowH.toDp() })
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.card2),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = item == order
                        Box(
                            modifier = Modifier
                                .onSizeChanged { widths[index] = it.width }
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectOrder(item) }
                                .padding(horizontal = 13.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = item.label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) colors.acc else colors.t2,
                            )
                        }
                    }
                }
            }
        }
        Icon(
            imageVector = Icons.Outlined.FilterList,
            contentDescription = "筛选",
            tint = if (filterActive) colors.acc else colors.t2,
            modifier = Modifier.padding(start = 6.dp).size(22.dp).clickable(onClick = onOpenFilter),
        )
        Icon(
            imageVector = if (grid) Icons.Filled.GridView else Icons.Filled.ViewList,
            contentDescription = "切换视图",
            tint = colors.t2,
            modifier = Modifier.padding(start = 6.dp).size(22.dp).clickable(onClick = onToggleGrid),
        )
    }
}

/** 用户（UP主 / 主播）列表行。 */
@Composable
private fun UserRow(user: SearchUser, onClick: () -> Unit) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkImage(url = user.face, modifier = Modifier.size(50.dp).clip(CircleShape))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.name,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.isLive) {
                    Text(
                        text = "直播中",
                        fontSize = 9.5.sp,
                        color = Color.White,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .background(colors.acc, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
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
            Text(
                text = buildString {
                    append("${formatCount(user.fans)}粉丝")
                    if (user.videos > 0) append(" · ${user.videos}投稿")
                },
                fontSize = 11.5.sp,
                color = colors.t3,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 专栏（文章）搜索结果行：左封面 + 右标题/作者/数据。 */
@Composable
internal fun ArticleRow(article: ArticleSummary, onClick: () -> Unit) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (article.cover.isNotEmpty()) {
            NetworkImage(
                url = article.cover,
                modifier = Modifier.width(112.dp).height(70.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = article.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (article.summary.isNotEmpty()) {
                Text(
                    text = article.summary,
                    fontSize = 12.sp,
                    color = colors.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Text(
                text = buildString {
                    if (article.authorName.isNotEmpty()) append("${article.authorName} · ")
                    append("${formatCount(article.views)}阅读")
                    if (article.likes > 0) append(" · ${formatCount(article.likes)}点赞")
                    if (article.pubTime > 0) append(" · ${formatTimeAgo(article.pubTime)}")
                },
                fontSize = 11.5.sp,
                color = colors.t3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 列表（LazyColumn）的翻页页脚。 */
private fun LazyListScope.listFooter(end: Boolean, hasItems: Boolean, pageKey: Int, onLoadMore: () -> Unit) {
    item { LoadMoreFooter(end = end, hasItems = hasItems, pageKey = pageKey, onLoadMore = onLoadMore) }
}

/** 网格（LazyVerticalGrid）的翻页页脚：跨满整行。 */
private fun LazyGridScope.gridFooter(end: Boolean, hasItems: Boolean, pageKey: Int, onLoadMore: () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        LoadMoreFooter(end = end, hasItems = hasItems, pageKey = pageKey, onLoadMore = onLoadMore)
    }
}

@Composable
private fun LoadMoreFooter(end: Boolean, hasItems: Boolean, pageKey: Int, onLoadMore: () -> Unit) {
    val colors = biko
    if (end) {
        if (hasItems) {
            Text(
                text = "没有更多了",
                fontSize = 12.5.sp,
                color = colors.t3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            )
        }
    } else {
        // 列表滑到这里就继续加载下一页
        LaunchedEffect(pageKey) { onLoadMore() }
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.acc, strokeWidth = 2.dp)
        }
    }
}

/** 输入联想词下拉列表。 */
@Composable
private fun SuggestPanel(suggestions: List<String>, onPick: (String) -> Unit) {
    val colors = biko
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        items(suggestions) { keyword ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(keyword) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = colors.t3,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = keyword,
                    fontSize = 14.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/** 搜索落地页：搜索历史（可删除 / 清空）+ 哔哩热搜。 */
@Composable
private fun LandingPanel(
    history: List<String>,
    hot: List<String>,
    onPick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val colors = biko
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        if (history.isNotEmpty()) {
            item {
                // 搜索历史不再用灰底卡片，直接铺在页面上（关键词药丸保留）
                Column(Modifier.fillMaxWidth()) {
                    CardHeader(title = "搜索历史", horizontalPadding = 15.dp) {
                        Text(
                            text = "清空",
                            fontSize = 12.5.sp,
                            color = colors.t3,
                            modifier = Modifier.clickable(onClick = onClearHistory),
                        )
                    }
                    HistoryChips(history = history, onPick = onPick, onRemove = onRemoveHistory)
                }
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (history.isNotEmpty()) 12.dp else 0.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.card),
            ) {
                CardHeader(title = "哔哩热搜")
                hot.forEachIndexed { index, keyword -> HotRow(index = index, keyword = keyword, onPick = onPick) }
                Box(Modifier.height(6.dp))
            }
        }
    }
}

/** 搜索历史关键词标签，一行放不下自动换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryChips(history: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit) {
    val colors = biko
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 15.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        history.forEach { keyword ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.card2)
                    .clickable { onPick(keyword) }
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = keyword,
                    fontSize = 13.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "删除",
                    tint = colors.t3,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(15.dp)
                        .clickable { onRemove(keyword) },
                )
            }
        }
    }
}

@Composable
private fun CardHeader(
    title: String,
    horizontalPadding: Dp = 15.dp,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = biko
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text)
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun HotRow(index: Int, keyword: String, onPick: (String) -> Unit) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(keyword) }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 名次列：固定 20dp 并左对齐，保证与上方「哔哩热搜」标题左边缘对齐
        Text(
            text = (index + 1).toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = rankColor(index),
            textAlign = TextAlign.Start,
            modifier = Modifier.width(20.dp),
        )
        Text(
            text = keyword,
            fontSize = 14.sp,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 11.dp),
        )
    }
}

/** 视频结果筛选弹层：发布时间 / 内容时长 / 内容分区（对齐 BiliPai）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SearchFilterSheet(
    state: SearchState,
    onDismiss: () -> Unit,
    onDuration: (Int) -> Unit,
    onZone: (Int) -> Unit,
    onPubTime: (PubTimeType) -> Unit,
    onCustomRange: (Long?, Long?) -> Unit,
) {
    val colors = biko
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 自定义日期区间（毫秒），本地暂存，选完即时落库到 ViewModel
    var customBegin by remember { mutableStateOf(state.pubBegin) }
    var customEnd by remember { mutableStateOf(state.pubEnd) }
    // 正在选日期：0 = 开始日期，1 = 结束日期，null = 未打开
    var dateTarget by remember { mutableStateOf<Int?>(null) }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "筛选",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp),
            )
            FilterSection(title = "发布时间") {
                PubTimeType.entries.forEach { type ->
                    SelectableChip(
                        label = type.label,
                        selected = state.pubTime == type,
                        onClick = { onPubTime(type) },
                    )
                }
                // 自定义区间：两个可点 chip，分别拉起日期选择
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectableChip(
                        label = customBegin?.let { dateFmt.format(Date(it)) } ?: "开始日期",
                        selected = state.pubTime == PubTimeType.CUSTOM && customBegin != null,
                        onClick = { dateTarget = 0 },
                    )
                    Text("至", fontSize = 13.sp, color = colors.t3)
                    SelectableChip(
                        label = customEnd?.let { dateFmt.format(Date(it)) } ?: "结束日期",
                        selected = state.pubTime == PubTimeType.CUSTOM && customEnd != null,
                        onClick = { dateTarget = 1 },
                    )
                }
            }
            FilterSection(title = "内容时长") {
                DurationFilter.entries.forEach { d ->
                    SelectableChip(
                        label = d.label,
                        selected = state.duration == d.value,
                        onClick = { onDuration(d.value) },
                    )
                }
            }
            FilterSection(title = "内容分区") {
                SEARCH_ZONES.forEach { zone ->
                    SelectableChip(
                        label = zone.label,
                        selected = state.videoTid == zone.tid,
                        onClick = { onZone(zone.tid) },
                    )
                }
            }
        }
    }

    dateTarget?.let { target ->
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = if (target == 0) customBegin else customEnd,
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        // 开始日期取当天 00:00:00，结束日期取当天 23:59:59
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = millis
                            set(Calendar.HOUR_OF_DAY, if (target == 0) 0 else 23)
                            set(Calendar.MINUTE, if (target == 0) 0 else 59)
                            set(Calendar.SECOND, if (target == 0) 0 else 59)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (target == 0) customBegin = cal.timeInMillis else customEnd = cal.timeInMillis
                        onCustomRange(customBegin, customEnd)
                    }
                    dateTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { dateTarget = null }) { Text("取消") }
            },
        ) {
            DatePicker(state = picker)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    val colors = biko
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.t2)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = biko
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) colors.acc else colors.t2,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.acc.copy(alpha = 0.14f) else colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
