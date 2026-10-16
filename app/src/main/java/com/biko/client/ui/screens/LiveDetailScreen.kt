package com.biko.client.ui.screens

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.LiveSelection
import com.biko.client.data.addHistory
import com.biko.client.data.emotePackages
import com.biko.client.data.liveDanmakuInfo
import com.biko.client.data.liveHistoryDanmaku
import com.biko.client.data.livePlayInfo
import com.biko.client.data.liveRoomInfo
import com.biko.client.data.sendLiveDanmaku
import com.biko.client.data.sendLiveLike
import com.biko.client.data.model.DanmakuItem
import com.biko.client.data.model.LiveChatItem
import com.biko.client.data.model.LiveRoom
import com.biko.client.data.model.LiveSuperChat
import com.biko.client.data.model.Video
import com.biko.client.player.LiveDanmakuClient
import com.biko.client.ui.components.BikoPlayer
import com.biko.client.ui.components.DanmakuSettings
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.components.VideoAspect
import com.biko.client.ui.components.liveQnLabel
import com.biko.client.ui.theme.Accent
import com.biko.client.ui.theme.biko
import com.biko.client.util.findActivity
import com.biko.client.util.formatCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val LIVE_REFERER = "https://live.bilibili.com/"

/** 直播清晰度：默认原画。 */
private const val DEFAULT_LIVE_QN = 10000

// 直播间整体常驻深色（与 PiliPlus 一致），不跟随 App 主题
private val LiveBg = Color(0xFF000000)
private val LiveText = Color(0xFFEEEEEE)
private val LiveSubText = Color(0x99FFFFFF)
// 纯黑底上，聊天胶囊改用浅灰半透明，避免黑底黑色气泡看不见（黑底灰色聊天）
private val LivePill = Color(0x33FFFFFF)
/** 直播间的强调色：品牌蓝。 */
private val LiveAccent = Accent

data class LiveDetailState(
    val info: LiveRoom? = null,
    val playUrl: String? = null,
    /** 当前直播清晰度（原画 / 蓝光 / 超清 / 高清）。 */
    val qn: Int = DEFAULT_LIVE_QN,
    val qualities: List<Int> = emptyList(),
    val danmaku: List<DanmakuItem> = emptyList(),
    /** 聊天列表（用户名 / 粉丝牌 / 等级）。 */
    val chats: List<LiveChatItem> = emptyList(),
    /** 醒目留言（SC），最新的在最后。 */
    val superChats: List<LiveSuperChat> = emptyList(),
    /** 聊天表情包：token（如「[大笑]」）→ 表情图 URL，用于把弹幕正文渲染成富文本。 */
    val emotes: Map<String, String> = emptyMap(),
    /** 高能观众数（ONLINE_RANK_COUNT）。 */
    val onlineRank: Int? = null,
    /** 开播时间戳（秒），来自 getRoomPlayInfo，用于「开播 X」。 */
    val liveTime: Long = 0,
    /** 主播 uid（点赞上报用）。 */
    val anchorUid: Long = 0,
    val error: String? = null,
)

class LiveDetailViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(LiveDetailState())
    val state = _state.asStateFlow()

    private var loaded = false
    private var roomId = 0L
    private var danmakuClient: LiveDanmakuClient? = null

    /**
     * [initial] 来自首页卡片（含主播昵称与头像，对应原版 Intent extras）。
     * 与 PiliPlus 一致：播放地址（getRoomPlayInfo，一次拿流 + uid + 开播时间）与
     * 直播间信息（getH5InfoByRoom，轻量无签名）互不依赖，并行发起，尽早出首屏。
     */
    fun load(roomId: Long, initial: LiveRoom?) {
        if (loaded) return
        loaded = true
        this.roomId = roomId
        val seed = initial ?: LiveRoom(roomId = roomId)
        _state.value = LiveDetailState(info = seed)

        viewModelScope.launch { loadStream(DEFAULT_LIVE_QN) }
        viewModelScope.launch {
            runCatching { repo.liveRoomInfo(roomId) }.onSuccess { fresh ->
                val cur = _state.value.info ?: seed
                _state.value = _state.value.copy(
                    info = cur.copy(
                        uid = fresh.uid.takeIf { it > 0 } ?: cur.uid,
                        title = fresh.title.ifEmpty { cur.title },
                        area = fresh.area.ifEmpty { cur.area },
                        uname = cur.uname.ifEmpty { fresh.uname },
                        face = cur.face.ifEmpty { fresh.face },
                        cover = fresh.cover.ifEmpty { cur.cover },
                        online = fresh.online,
                        watchedShow = fresh.watchedShow.takeIf { it > 0 } ?: cur.watchedShow,
                        watchedText = fresh.watchedText.ifEmpty { cur.watchedText },
                        liveStatus = fresh.liveStatus,
                    ),
                )
                runCatching {
                    repo.addHistory(
                        Video(
                            bvid = "live_$roomId",
                            title = fresh.title.ifEmpty { cur.title },
                            pic = fresh.cover.ifEmpty { cur.cover },
                            upName = fresh.uname.ifEmpty { cur.uname },
                            type = "live",
                            roomId = roomId,
                        ),
                    )
                }
            }
        }
        startDanmaku(roomId)
        // 进房先垫一批历史弹幕，避免聊天区一开始是空的
        viewModelScope.launch {
            runCatching { repo.liveHistoryDanmaku(roomId) }.onSuccess { history ->
                if (history.isNotEmpty()) {
                    _state.value = _state.value.copy(chats = history + _state.value.chats)
                }
            }
        }
        // 表情包：把聊天里的 [大笑] 之类 token 渲染成表情图（未登录/失败时为空，降级为纯文本）
        viewModelScope.launch {
            runCatching { repo.emotePackages() }.onSuccess { packages ->
                val map = packages.asSequence()
                    .flatMap { it.emote.asSequence() }
                    .filter { it.text.isNotBlank() && it.url.isNotBlank() }
                    .associate { it.text to it.url }
                if (map.isNotEmpty()) _state.value = _state.value.copy(emotes = map)
            }
        }
    }

    private suspend fun loadStream(qn: Int) {
        runCatching { repo.livePlayInfo(roomId, qn) }
            .onSuccess { play ->
                _state.value = _state.value.copy(
                    playUrl = play.url,
                    qn = play.quality,
                    qualities = play.acceptQuality.ifEmpty { DEFAULT_LIVE_QUALITIES }
                        .distinct()
                        .sortedDescending(),
                    liveTime = play.liveTime.takeIf { it > 0 } ?: _state.value.liveTime,
                    anchorUid = play.uid.takeIf { it > 0 } ?: _state.value.anchorUid,
                    error = null,
                )
            }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "获取直播流失败") }
    }

    fun setQuality(qn: Int) {
        if (_state.value.qn == qn) return
        _state.value = _state.value.copy(qn = qn)
        viewModelScope.launch { loadStream(qn) }
    }

    /** 直播弹幕（WebSocket）；连不上只是没有弹幕，不影响播放。 */
    private fun startDanmaku(roomId: Long) {
        viewModelScope.launch {
            val info = runCatching { repo.liveDanmakuInfo(roomId) }.getOrNull() ?: return@launch
            val client = LiveDanmakuClient(
                roomId = roomId,
                host = info.host,
                token = info.token,
                buvid = session.buvid3(),
                selfUid = session.mid().toLongOrNull() ?: 0L,
            )
            danmakuClient = client
            client.start()
            launch {
                client.messages.collect { item ->
                    val list = _state.value.danmaku + item
                    _state.value = _state.value.copy(
                        danmaku = if (list.size > MAX_LIVE_DANMAKU) list.takeLast(MAX_LIVE_DANMAKU) else list,
                    )
                }
            }
            launch {
                client.chats.collect { item ->
                    val list = _state.value.chats + item
                    _state.value = _state.value.copy(
                        chats = if (list.size > MAX_LIVE_DANMAKU) list.takeLast(MAX_LIVE_DANMAKU) else list,
                    )
                }
            }
            launch {
                client.superChats.collect { sc ->
                    val list = _state.value.superChats + sc
                    _state.value = _state.value.copy(
                        superChats = if (list.size > MAX_LIVE_SC) list.takeLast(MAX_LIVE_SC) else list,
                    )
                }
            }
            launch {
                // 实时人气 / 高能观众 / 看过 / 标题变更：合并进 info，UI 直接跟着刷新
                client.stats.collect { s ->
                    val info = _state.value.info
                    _state.value = _state.value.copy(
                        info = info?.copy(
                            online = s.online ?: info.online,
                            watchedShow = s.watched ?: info.watchedShow,
                            watchedText = s.watchedText ?: info.watchedText,
                            title = s.title ?: info.title,
                            area = s.area ?: info.area,
                        ),
                        onlineRank = s.onlineRank ?: _state.value.onlineRank,
                    )
                }
            }
        }
    }

    /** 发送直播弹幕，结果通过 [onResult] 回调（成功/失败的提示文案）。 */
    fun sendDanmaku(msg: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.sendLiveDanmaku(roomId, msg) }
                .onSuccess { onResult(it) }
                .onFailure { onResult(it.message ?: "发送失败") }
        }
    }

    /** 点赞（连点累积后一次性上报），失败静默。 */
    fun like(clickTime: Int) {
        val uid = session.mid().toLongOrNull() ?: 0L
        if (uid <= 0L) return
        viewModelScope.launch {
            runCatching { repo.sendLiveLike(roomId, uid, _state.value.anchorUid, clickTime) }
        }
    }

    override fun onCleared() {
        danmakuClient?.close()
    }

    private companion object {
        const val MAX_LIVE_DANMAKU = 200
        const val MAX_LIVE_SC = 50
        val DEFAULT_LIVE_QUALITIES = listOf(10000, 400, 250, 150)
    }
}

@Composable
fun LiveDetailScreen(
    roomId: Long,
    onBack: () -> Unit,
    onOpenSpace: (Long, String, String) -> Unit = { _, _, _ -> },
    viewModel: LiveDetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(context).build() }
    val activity = remember(context) { context.findActivity() }
    val initial = remember { LiveSelection.current }
    val link = remember(roomId) { "https://live.bilibili.com/$roomId" }

    // 直播播放器的设置（与视频播放器同一套 UI）
    var speed by remember { mutableStateOf(1f) }
    var aspect by remember { mutableStateOf(VideoAspect.FIT) }
    var danmakuSettings by remember { mutableStateOf(DanmakuSettings()) }

    // 直播画面是否已渲染出首帧：未出首帧前一直转圈，避免拿到播放地址后只看到一块黑屏
    var firstFrameRendered by remember { mutableStateOf(false) }
    LaunchedEffect(state.playUrl) { firstFrameRendered = false }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrameRendered = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val enterPip: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                )
            }
        }
    }

    LaunchedEffect(roomId) { viewModel.load(roomId, initial) }

    // 页面常驻深色，状态栏图标必须切成白色，否则浅色主题下黑字压深底看不见
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }

    // 退到桌面或离开本页时立刻停声
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        player.pause()
    }

    LaunchedEffect(fullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let {
                WindowCompat.getInsetsController(it, it.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (fullscreen) {
        Box(Modifier.fillMaxSize()) {
            LivePlayerPane(
                player = player,
                state = state,
                viewModel = viewModel,
                speed = speed,
                onSelectSpeed = { speed = it },
                aspect = aspect,
                onSelectAspect = { aspect = it },
                danmakuSettings = danmakuSettings,
                onDanmakuSettingsChange = { danmakuSettings = it },
                modifier = Modifier.fillMaxSize().background(Color.Black),
                fullscreen = true,
                onToggleFullscreen = { fullscreen = false },
                onPictureInPicture = enterPip,
            )
            if (state.playUrl != null && !firstFrameRendered) LiveBufferingOverlay()
        }
        return
    }

    val info = state.info
    val clipboard = LocalClipboardManager.current

    // 「开播 X」需要跟着时间走，每 30 秒刷新一次
    var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(state.liveTime) {
        if (state.liveTime > 0) {
            while (true) {
                nowSec = System.currentTimeMillis() / 1000
                delay(30_000)
            }
        }
    }

    // 去掉模糊封面铺底，整个空白区就是纯黑
    Box(Modifier.fillMaxSize().background(LiveBg)) {
        Column(Modifier.fillMaxSize()) {
            LiveRoomTopBar(
                info = info,
                onlineRank = state.onlineRank,
                liveTime = state.liveTime,
                nowSec = nowSec,
                onBack = onBack,
                onOpenSpace = {
                    val mid = info?.uid ?: 0L
                    if (mid > 0L) onOpenSpace(mid, info?.uname.orEmpty(), info?.face.orEmpty())
                },
                onCopyLink = {
                clipboard.setText(AnnotatedString(link))
                Toast.makeText(context, "已复制直播间链接", Toast.LENGTH_SHORT).show()
            },
            onShare = {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, info?.title.orEmpty())
                                putExtra(Intent.EXTRA_TEXT, "$link\n${info?.title.orEmpty()}")
                            },
                            "分享直播间",
                        ),
                    )
                }
            },
            onOpenBrowser = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                }.onFailure { Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show() }
            },
        )

        // 播放器满宽 16:9，直接贴顶栏下方
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            if (state.playUrl == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = LiveAccent,
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = if (state.error != null) "直播流加载失败" else "直播加载中…",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            } else {
                LivePlayerPane(
                    player = player,
                    state = state,
                    viewModel = viewModel,
                    speed = speed,
                    onSelectSpeed = { speed = it },
                    aspect = aspect,
                    onSelectAspect = { aspect = it },
                    danmakuSettings = danmakuSettings,
                    onDanmakuSettingsChange = { danmakuSettings = it },
                    modifier = Modifier.fillMaxSize(),
                    fullscreen = false,
                    onToggleFullscreen = { fullscreen = true },
                    onPictureInPicture = enterPip,
                )
                if (!firstFrameRendered) LiveBufferingOverlay()
            }
        }

        if (info != null && info.title.isNotEmpty()) {
            Text(
                text = info.title,
                fontSize = 14.sp,
                color = LiveText,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (state.error != null) {
            Text(
                text = state.error ?: "",
                fontSize = 13.sp,
                color = LiveSubText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // 醒目留言（SC）：有则展示最新一条
        state.superChats.lastOrNull()?.let { sc ->
            LiveSuperChatCard(sc, modifier = Modifier.padding(horizontal = 12.dp))
        }

        // 直播间聊天列表（占满剩余高度，可滚动）
        LiveChatSection(
            chats = state.chats,
            emotes = state.emotes,
            modifier = Modifier.weight(1f),
        )

        // 底部输入区：弹幕开关 / 发送弹幕 / 点赞 / 表情
        LiveRoomInputBar(
            danmakuEnabled = danmakuSettings.enabled,
            onToggleDanmaku = { danmakuSettings = danmakuSettings.copy(enabled = !danmakuSettings.enabled) },
            emotes = state.emotes,
            loggedIn = AppGraph.session.isLogin(),
            onSend = { text, done -> viewModel.sendDanmaku(text) { done(it) } },
            onLike = viewModel::like,
        )
        }
    }
}

/** 直播间顶部栏：返回 / 主播头像 / 昵称 + 高能观众 / 看过 + 开播时长 / ⋮ 菜单。 */
@Composable
private fun LiveRoomTopBar(
    info: LiveRoom?,
    onlineRank: Int?,
    liveTime: Long,
    nowSec: Long,
    onBack: () -> Unit,
    onOpenSpace: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiveBg)
            .statusBarsPadding()
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_back),
            contentDescription = "返回",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier
                .size(34.dp)
                .clickable(onClick = onBack)
                .padding(7.dp),
        )
        NetworkImage(
            url = info?.face.orEmpty(),
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(enabled = (info?.uid ?: 0L) > 0L) { onOpenSpace() },
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = info?.uname.orEmpty().ifEmpty { "直播间" },
                    fontSize = 14.sp,
                    color = LiveText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onlineRank != null && onlineRank > 0) {
                    Text(
                        text = "高能观众(${formatCount(onlineRank)})",
                        fontSize = 11.sp,
                        color = LiveSubText,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            val watched = info?.watchedText.orEmpty()
            val liveLabel = liveTimeLabel(liveTime, nowSec)
            if (watched.isNotEmpty() || liveLabel.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (watched.isNotEmpty()) {
                        Text(
                            text = watched,
                            fontSize = 11.sp,
                            color = LiveSubText,
                            maxLines = 1,
                        )
                    }
                    if (liveLabel.isNotEmpty()) {
                        Text(
                            text = liveLabel,
                            fontSize = 11.sp,
                            color = LiveSubText,
                            maxLines = 1,
                            modifier = Modifier.padding(start = if (watched.isNotEmpty()) 8.dp else 0.dp),
                        )
                    }
                }
            }
        }
        Box {
            LiveIconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            // 与视频播放器的展开面板同风格：深色圆角卡片 + 白字条目
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(14.dp),
                containerColor = Color(0xF21C1C1E),
                shadowElevation = 10.dp,
            ) {
                DropdownMenuItem(
                    text = { Text("复制链接", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    onClick = { menuOpen = false; onCopyLink() },
                )
                DropdownMenuItem(
                    text = { Text("分享直播间", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    onClick = { menuOpen = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text("浏览器打开", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    onClick = { menuOpen = false; onOpenBrowser() },
                )
            }
        }
    }
}

/** 「开播 X」文案：不足 1 分钟显示「刚刚开播」，否则显示小时/分钟。 */
private fun liveTimeLabel(liveTime: Long, nowSec: Long): String {
    if (liveTime <= 0) return ""
    val elapsed = nowSec - liveTime
    if (elapsed <= 60) return "刚刚开播"
    val minutes = elapsed / 60
    val hours = minutes / 60
    return if (hours > 0) "开播${hours}小时${minutes % 60}分钟" else "开播${minutes}分钟"
}

/** 34dp 圆形图标按钮（不用 IconButton，避免 Material 的最小触摸尺寸撑开布局）。 */
@Composable
private fun LiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 直播间聊天列表：每条弹幕一个圆角胶囊，底色半透明黑。 */
@Composable
private fun LiveChatSection(
    chats: List<LiveChatItem>,
    emotes: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 有新消息时自动滚到底部
    LaunchedEffect(chats.size) {
        if (chats.isNotEmpty()) listState.animateScrollToItem(chats.size - 1)
    }

    if (chats.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text = "暂无聊天消息", fontSize = 13.sp, color = LiveSubText)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chats) { chat ->
            LiveChatRow(chat, emotes)
        }
    }
}

/** 醒目留言（SC）卡片：发送者 / 价格 / 正文，背景用 SC 原色。 */
@Composable
private fun LiveSuperChatCard(sc: LiveSuperChat, modifier: Modifier = Modifier) {
    val colors = biko
    val bg = if (sc.bgColor in 1..0xFFFFFF) Color(sc.bgColor or 0xFF000000.toInt()) else colors.acc
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                url = sc.face,
                modifier = Modifier.size(20.dp).clip(CircleShape),
            )
            Text(
                text = sc.uname,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            Text(
                text = "¥${sc.price}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Text(
            text = sc.text,
            fontSize = 13.sp,
            color = Color.White,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 单条弹幕：勋章 / 大航海 / 昵称 / 正文（含内联表情）合成一段富文本，包在半透明胶囊里。 */
@Composable
private fun LiveChatRow(chat: LiveChatItem, emotes: Map<String, String>) {
    // 进房提示：弱化为一行灰色小字
    if (chat.enter) {
        Text(
            text = "${chat.uname} ${chat.text}",
            fontSize = 11.5.sp,
            color = Color(0x73FFFFFF),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    val (annotated, urls) = remember(chat, emotes) { buildLiveChatRow(chat, emotes) }
    val inlineContent = remember(urls) {
        urls.mapValues { (_, url) ->
            InlineTextContent(
                Placeholder(
                    width = 1.4f.em,
                    height = 1.4f.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                NetworkImage(url = url, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
    val badges = remember(chat) { chatBadges(chat) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LivePill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        badges.forEach { (label, color) ->
            LiveChatBadge(label, color, Modifier.padding(end = 4.dp, top = 1.dp))
        }
        Text(
            text = annotated,
            inlineContent = inlineContent,
            style = TextStyle(fontSize = 13.sp, color = LiveText, lineHeight = 19.sp),
        )
    }
}

/** 用户名左侧的勋章节点：带 R 角的色框（原先用 SpanStyle 底色，无法加圆角）。 */
@Composable
private fun LiveChatBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 10.sp, color = Color.White, maxLines = 1)
    }
}

/** 一条弹幕需要显示在用户名左侧的徽章列表（粉丝勋章 / 大航海 / 房管）。 */
private fun chatBadges(chat: LiveChatItem): List<Pair<String, Color>> {
    val list = mutableListOf<Pair<String, Color>>()
    if (chat.medalName.isNotEmpty()) {
        val medalColor = if (chat.medalColor != 0) {
            Color(chat.medalColor or 0xFF000000.toInt())
        } else {
            LiveAccent
        }
        list += "${chat.medalName} ${chat.medalLevel}" to medalColor
    }
    if (chat.isAdmin || chat.guardLevel > 0) {
        val (label, bg) = when {
            chat.guardLevel == 1 -> "总督" to Color(0xFFE0533D)
            chat.guardLevel == 2 -> "提督" to Color(0xFFD9A21B)
            chat.guardLevel == 3 -> "舰长" to Color(0xFF4A90D9)
            else -> "房管" to LiveAccent
        }
        list += label to bg
    }
    return list
}

/** 直播弹幕里的表情 token，形如「[大笑]」。 */
private val LIVE_EMOTE_PATTERN = Regex("""\[(.*?)]""")

/**
 * 把一条弹幕拼成富文本：昵称用主播色（房管/大航海高亮），正文里的 [xxx] 渲染为内联表情图。
 * 勋章 / 大航海 / 房管的色框由 LiveChatBadge 单独渲染（SpanStyle 不支持背景圆角）。
 * 同时返回「占位符 id → 表情图 URL」映射，供 Text 的 inlineContent 使用。
 */
private fun buildLiveChatRow(
    chat: LiveChatItem,
    emotes: Map<String, String>,
): Pair<AnnotatedString, Map<String, String>> {
    val used = linkedMapOf<String, String>()
    val highlighted = chat.isAdmin || chat.guardLevel > 0
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = if (highlighted) LiveAccent else Color(0xB3FFFFFF))) {
            append("${chat.uname}：")
        }
        withStyle(SpanStyle(color = chatTextColor(chat.color, LiveBg, LiveText))) {
            appendLiveBody(chat.text, chat.emoteUrl, emotes, used)
        }
    }
    return annotated to used
}

/** 把弹幕正文追加进 builder：整条是表情图时直接用图，否则把 [xxx] token 换成内联表情。 */
private fun AnnotatedString.Builder.appendLiveBody(
    text: String,
    emoteUrl: String?,
    emotes: Map<String, String>,
    used: MutableMap<String, String>,
) {
    // 整条弹幕就是一张表情图（DANMU_MSG 的 info[0][13]）
    if (!emoteUrl.isNullOrBlank()) {
        used[emoteUrl] = emoteUrl
        appendInlineContent(emoteUrl, text.ifBlank { "[表情]" })
        return
    }
    if (emotes.isEmpty() || '[' !in text) {
        append(text)
        return
    }
    var i = 0
    while (i < text.length) {
        val m = LIVE_EMOTE_PATTERN.find(text, i)
        val url = if (m != null && m.range.first == i) emotes[m.value] else null
        if (url != null && m != null) {
            used[m.value] = url
            appendInlineContent(m.value, m.value)
            i = m.range.last + 1
        } else {
            append(text[i])
            i++
        }
    }
}

/**
 * 聊天列表里的弹幕正文颜色。
 * 弹幕原色里有接近黑/深色的值，落在深色聊天底上会看不清；
 * 与原色亮度差过小时回退为正文色（浅色）。
 */
private fun chatTextColor(argb: Int, background: Color, fallback: Color): Color {
    if (argb !in 1..0xFFFFFF) return fallback
    val color = Color(argb or 0xFF000000.toInt())
    fun luminance(c: Color) = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return if (kotlin.math.abs(luminance(color) - luminance(background)) < 0.25f) fallback else color
}

/**
 * 底部输入区：整体复刻竖屏播放的「发弹幕」长药丸——点药丸就地进入输入态，
 * 右侧「弹」（弹幕开关）原地淡出、换成蓝色纸飞机「发送」，动画时长与竖屏一致（180ms）。
 * 药丸右侧保留点赞与表情按钮，功能不丢。
 */
@Composable
private fun LiveRoomInputBar(
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    emotes: Map<String, String>,
    loggedIn: Boolean,
    onSend: (String, (String) -> Unit) -> Unit,
    onLike: (Int) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var inputActive by remember { mutableStateOf(false) }
    var showEmotes by remember { mutableStateOf(false) }
    var likeCount by remember { mutableStateOf(0) }

    // 连点累积：停止点击 800ms 后一次性上报（与 PiliPlus 的防抖一致）
    LaunchedEffect(likeCount) {
        if (likeCount > 0) {
            delay(800)
            val n = likeCount
            likeCount = 0
            onLike(n)
        }
    }

    // 键盘收起时退出输入态，恢复成药丸（与竖屏弹幕输入框一致）
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 以 inputActive 为键重置，避免上次输入后残留「键盘已收起」状态导致输入框只能用一次
    var imeWasVisible by remember(inputActive) { mutableStateOf(false) }
    if (inputActive) {
        LaunchedEffect(imeVisible) {
            if (imeVisible) {
                imeWasVisible = true
            } else if (imeWasVisible) {
                inputActive = false
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    // 点药丸进入输入态：未登录先提示（与点赞按钮一致）
    val activateInput: () -> Unit = {
        if (!loggedIn) {
            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
        } else {
            inputActive = true
        }
    }
    // 发送：空内容忽略；发送后收起输入态
    val submit: () -> Unit = {
        val text = draft
        if (text.isNotBlank()) {
            draft = ""
            onSend(text) { result ->
                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
            }
            inputActive = false
        }
    }

    val emoteList = remember(emotes) { emotes.entries.map { it.key to it.value } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiveBg)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        if (showEmotes) {
            if (emoteList.isEmpty()) {
                Text(
                    text = "暂无可用表情",
                    fontSize = 12.sp,
                    color = LiveSubText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(emoteList) { (token, url) ->
                        NetworkImage(
                            url = url,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { draft += token },
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 复刻竖屏播放的发弹幕长药丸：右侧内嵌「弹 / 发送」原地换形
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (inputActive) Color(0xFF2B2B2B) else Color(0xFF333333))
                    .padding(horizontal = 13.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (!inputActive) {
                        Text(
                            text = "发送信息",
                            fontSize = 13.sp,
                            color = Color(0xFF9AA0A6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable(onClick = activateInput),
                        )
                    } else {
                        if (draft.isEmpty()) {
                            Text("发送信息", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                            cursorBrush = SolidColor(LiveAccent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                }
                // 右侧同一位置原地换形：药丸态是「弹」（弹幕开关），
                // 激活输入后换成蓝色纸飞机「发送」图标（与竖屏动画一致，180ms 淡入淡出）。
                val sendIconAlpha by animateFloatAsState(
                    targetValue = if (inputActive) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "liveDmSendIconAlpha",
                )
                val toggleAlpha by animateFloatAsState(
                    targetValue = if (inputActive) 0f else 1f,
                    animationSpec = tween(durationMillis = 180),
                    label = "liveDmToggleAlpha",
                )
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { if (inputActive) submit() else onToggleDanmaku() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = DmSendPlane,
                        contentDescription = "发送",
                        tint = LiveAccent,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { alpha = sendIconAlpha },
                    )
                    Text(
                        text = "弹",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (danmakuEnabled) LiveAccent else Color(0xFF9AA0A6),
                        modifier = Modifier.graphicsLayer { alpha = toggleAlpha },
                    )
                }
            }

            Box(
                modifier = Modifier.padding(start = 6.dp).size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                LiveIconButton(
                    onClick = {
                        if (!loggedIn) {
                            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                        } else {
                            likeCount++
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUpOffAlt,
                        contentDescription = "点赞",
                        tint = LiveAccent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (likeCount > 0) {
                    Text(
                        text = "x$likeCount",
                        fontSize = 10.sp,
                        color = LiveAccent,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }

            LiveIconButton(onClick = { showEmotes = !showEmotes }) {
                Icon(
                    imageVector = Icons.Outlined.EmojiEmotions,
                    contentDescription = "表情",
                    tint = if (showEmotes) LiveAccent else LiveSubText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** 首帧渲染前的转圈遮罩，叠在播放器之上。 */
@Composable
private fun LiveBufferingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = LiveAccent,
                strokeWidth = 3.dp,
            )
            Text(
                text = "直播加载中…",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** 直播播放器：与视频播放器共用组件，直播用清晰度档位与实时弹幕。 */
@Composable
private fun LivePlayerPane(
    player: ExoPlayer,
    state: LiveDetailState,
    viewModel: LiveDetailViewModel,
    speed: Float,
    onSelectSpeed: (Float) -> Unit,
    aspect: VideoAspect,
    onSelectAspect: (VideoAspect) -> Unit,
    danmakuSettings: DanmakuSettings,
    onDanmakuSettingsChange: (DanmakuSettings) -> Unit,
    modifier: Modifier,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onPictureInPicture: () -> Unit,
) {
    BikoPlayer(
        player = player,
        modifier = modifier,
        url = state.playUrl,
        referer = LIVE_REFERER,
        cookie = AppGraph.session.cookie(),
        title = state.info?.title.orEmpty(),
        live = true,
        quality = state.qn,
        onSelectQuality = viewModel::setQuality,
        qualityOptions = state.qualities,
        qualityLabel = { liveQnLabel(it) },
        speed = speed,
        onSelectSpeed = onSelectSpeed,
        aspect = aspect,
        onSelectAspect = onSelectAspect,
        danmaku = state.danmaku,
        danmakuSettings = danmakuSettings,
        onDanmakuSettingsChange = onDanmakuSettingsChange,
        fullscreen = fullscreen,
        onToggleFullscreen = onToggleFullscreen,
        onPictureInPicture = onPictureInPicture,
    )
}
