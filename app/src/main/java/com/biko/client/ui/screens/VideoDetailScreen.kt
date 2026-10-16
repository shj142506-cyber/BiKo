package com.biko.client.ui.screens

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.DownloadManager
import com.biko.client.data.model.Comment
import com.biko.client.data.model.DownloadTask
import com.biko.client.data.model.Video
import com.biko.client.data.model.VideoNote
import com.biko.client.player.MediaCard
import com.biko.client.ui.components.ActionItem
import com.biko.client.ui.components.BikoPlayer
import com.biko.client.ui.components.CommentRow
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.components.RankRow
import com.biko.client.ui.theme.biko
import com.biko.client.data.model.EmotePackage
import com.biko.client.data.model.MentionUser
import com.biko.client.util.findActivity
import com.biko.client.util.formatCount
import com.biko.client.util.formatDuration
import com.biko.client.util.formatTimeAgo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import android.net.Uri

@Composable
fun VideoDetailScreen(
    bvid: String,
    onBack: () -> Unit,
    /** 顶栏房子按钮：跳回首页（与返回键是两个不同功能）。 */
    onHome: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSpace: (Long, String, String) -> Unit,
    /** true 时直接以竖屏（短视频）模式进入，可上下滑切换推荐视频。 */
    startPortrait: Boolean = false,
    /** 写入历史记录时的类型："video" / "bangumi"（番剧、影视）。 */
    historyType: String = "video",
    /** 进入后默认停留的页签：0=简介/视频，1=评论（消息中心跳评论区用）。 */
    startTab: Int = 0,
    /** 评论里点 bvid 时打开对应视频。 */
    onOpenVideo: (String) -> Unit = {},
    /** 评论里点 #话题# 时打开话题页（为 null 时回退外部浏览器搜索）。 */
    onOpenTopic: ((String) -> Unit)? = null,
    viewModel: VideoDetailViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var reply by remember { mutableStateOf("") }
    // 底栏输入模式：false 发评论，true 发弹幕
    var danmakuMode by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var portrait by remember { mutableStateOf(startPortrait) }
    // 竖屏播放页的发弹幕输入条
    var portraitDmInput by remember { mutableStateOf(false) }
    var portraitDmText by remember { mutableStateOf("") }
    // 播放器设置面板是否展开：展开时锁住竖屏 pager，避免在面板上下滑时视频跟着一起滑
    var playerPanelOpen by remember { mutableStateOf(false) }
    // 竖屏评论弹层
    var portraitComments by remember { mutableStateOf(false) }
    // 评论弹层开合进度：弹层滑入滑出与画面让位共用同一条进度，避免两段动画不同步。
    // 用带轻微回弹的弹簧动画，贴近 B 站评论区弹层「快速上滑 + 轻微落定」的手感。
    val commentSheetProgress by animateFloatAsState(
        targetValue = if (portrait && portraitComments) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = 420f,
            visibilityThreshold = 0.001f,
        ),
        label = "portrait_comment_sheet",
    )
    // 进入竖屏播放页的过渡：整页从 92% 放大到 100% 并淡入，避免点「竖屏」时生硬硬切
    val portraitEnter by animateFloatAsState(
        targetValue = if (portrait) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
        label = "portrait_enter",
    )

    val player = remember { ExoPlayer.Builder(context).build() }
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(bvid, startTab) {
        viewModel.load(bvid, historyType)
        // 从消息中心进来时直接落到评论区
        viewModel.selectTab(startTab)
    }

    // 顶部那条是黑底，状态栏图标必须切成白色，否则浅色主题下黑字压黑底看不见
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }

    // 退到桌面（App 进入后台）时写入播放记忆；界面完全不可见时暂停并静音。
    // 画中画只会走到 ON_PAUSE、不触发 ON_STOP，所以不会被误停。
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.saveProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // 开启「后台听视频」时退到后台继续播放声音（不暂停、不静音）；否则暂停并静音。
        if (!state.backgroundAudio) {
            player.pause()
            player.volume = 0f
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        player.volume = 1f
    }

    val enterPip: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build(),
                )
            }
        }
    }

    // 全屏：横屏 + 隐藏系统栏；竖屏播放：保持竖屏铺满，但状态栏照常显示（白字压黑底）
    LaunchedEffect(fullscreen, portrait) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        when {
            fullscreen -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            portrait -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                // 只隐藏底部导航栏，状态栏保留（顶栏已做 statusBarsPadding，不会重叠）
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            else -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            MediaCard.release()
            player.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let {
                WindowCompat.getInsetsController(it, it.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 原版用 Toast 提示，这里用简单的文本提示条代替
    LaunchedEffect(state.message) {
        if (state.message != null) {
            android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    // 全屏 / 竖屏播放时只渲染播放器
    // 全屏 / 竖屏完全手动：全屏键只进横屏，竖屏键只进竖屏，不再按视频画幅自动判断
    val enterAutoFullscreen = {
        portrait = false
        fullscreen = true
    }
    // 系统返回键在竖屏/全屏下只退出大屏模式，回到详情页，而不是直接退出到上级列表
    BackHandler(enabled = fullscreen || portrait) {
        fullscreen = false
        portrait = false
    }
    // 竖屏评论弹层开着时，返回键先收弹层
    BackHandler(enabled = portrait && portraitComments) {
        portraitComments = false
    }
    // 正在回复评论时，返回键只收起键盘并回到普通评论，不退出页面
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = state.replyTarget != null) {
        viewModel.setReplyTarget(null)
        keyboard?.hide()
    }
    // 弹幕模式下，返回键先取消发弹幕（收起键盘并切回评论）
    BackHandler(enabled = danmakuMode) {
        danmakuMode = false
        keyboard?.hide()
    }
    // 竖屏上下滑：进入竖屏时准备「当前视频 + 推荐流」的列表
    val portraitPagerState = rememberPagerState(pageCount = { state.portraitFeed.size.coerceAtLeast(1) })
    LaunchedEffect(portrait, state.video?.bvid) {
        if (portrait) viewModel.startPortraitFeed()
    }
    LaunchedEffect(portraitPagerState) {
        snapshotFlow { portraitPagerState.currentPage }.collect { page ->
            val feed = viewModel.state.value.portraitFeed
            val item = feed.getOrNull(page) ?: return@collect
            if (item.bvid != viewModel.state.value.video?.bvid) {
                // 换到下一页：先清掉播放器，避免把上一个视频的进度 seek 到新视频上
                player.stop()
                player.clearMediaItems()
                viewModel.load(item.bvid)
            }
            if (page >= feed.size - 2) viewModel.loadMorePortraitFeed()
        }
    }
    if (fullscreen || portrait) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 竖屏播放页进场：从上往下拉开（横屏全屏走系统转屏，不做位移）
                    val enter = if (fullscreen) 1f else portraitEnter
                    translationY = -(1f - enter) * size.height
                },
        ) {
        // 评论区展开固定占全屏 60%，剩下的 40% 给视频：视频按原始尺寸居中显示，
        // 只有竖版视频（40% 高度放不下）才由播放器的 FIT 自动缩小。
        val portraitSheetFraction = PORTRAIT_SHEET_FRACTION
        val playerHeightFraction =
            if (portrait && !fullscreen) 1f - portraitSheetFraction * commentSheetProgress else 1f
        // 单个视频页：只含播放器（UP 信息/三连经 portraitExtra 槽位进播放器内部）；
        // 全屏弹层（评论/楼中楼/投币/发弹幕输入）放在 Box 顶层，与 pager 解耦。
        val currentPageContent: @Composable () -> Unit = {
        VideoPlayerPane(
            player = player,
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(playerHeightFraction)
                .align(Alignment.TopCenter)
                .background(Color.Black),
            fullscreen = fullscreen,
            onToggleFullscreen = {
                if (fullscreen || portrait) {
                    fullscreen = false
                    portrait = false
                } else {
                    enterAutoFullscreen()
                }
            },
            portrait = portrait,
            onTogglePortrait = {
                if (portrait) {
                    portrait = false
                    portraitComments = false
                } else {
                    fullscreen = false
                    portrait = true
                }
            },
            onPictureInPicture = enterPip,
            onBack = onBack,
            onHome = onHome,
            onEnterLandscape = {
                portrait = false
                fullscreen = true
            },
            // 弹幕输入激活期间隐藏播放器顶栏/底部 UI，键盘收起输入条消失后才恢复
            portraitInputActive = portraitDmInput,
            // 评论弹层打开时播放器整体收进弹层上方，画面始终可见
            portraitSheetProgress = commentSheetProgress,
            onPanelOpenChange = { playerPanelOpen = it },
            portraitExtra = if (portrait && state.video != null) {
                {
                    PortraitOverlay(
                        video = state.video!!,
                        following = state.following,
                        upFans = state.upFans,
                        liked = state.liked,
                        coined = state.coinCount > 0,
                        faved = state.faved,
                        onFollow = viewModel::toggleFollow,
                        onAvatarClick = {
                            state.video?.let { onOpenSpace(it.upMid, it.upName, it.upFace) }
                        },
                        onLike = viewModel::toggleLike,
                        onComment = { portraitComments = true },
                        onCoin = viewModel::openCoinDialog,
                        onFav = viewModel::toggleFav,
                        onShare = { state.video?.let { v -> shareVideo(context, v) } },
                    )
                }
            } else {
                null
            },
            portraitBottom = if (portrait) {
                {
                    PortraitDanmakuEntry(
                        inputActive = portraitDmInput,
                        value = portraitDmText,
                        onValueChange = { portraitDmText = it },
                        // 点药丸原地进入输入态：未登录先走登录
                        onActivate = {
                            if (!AppGraph.session.isLogin()) {
                                onOpenLogin()
                            } else {
                                portraitDmInput = true
                            }
                        },
                        onSend = {
                            viewModel.sendDanmaku(portraitDmText, player.currentPosition)
                            portraitDmText = ""
                            portraitDmInput = false
                        },
                        onDismiss = { portraitDmInput = false },
                        danmakuEnabled = state.danmakuSettings.enabled,
                        onToggleDanmaku = {
                            viewModel.setDanmakuSettings(
                                state.danmakuSettings.copy(enabled = !state.danmakuSettings.enabled),
                            )
                        },
                    )
                }
            } else {
                null
            },
        )
        }

        // 竖屏：一页一个视频，上下滑切换推荐；非当前页只画封面。全屏（横屏）沿用单页逻辑。
        // 必须放在全屏弹层之前渲染，弹层才能盖在 pager 之上（否则弹层被整屏 pager 盖住）。
        if (portrait && !fullscreen) {
            VerticalPager(
                state = portraitPagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !portraitComments && !portraitDmInput &&
                    state.subRoot == null && !playerPanelOpen,
            ) { page ->
                val item = state.portraitFeed.getOrNull(page)
                // 播放器只挂在当前页：播放器全局只有一个实例，挂当前页才能保证
                // 画面与弹幕一定可见（按 bvid 比较的话，换页加载失败时会只剩封面）
                if (page == portraitPagerState.currentPage) {
                    currentPageContent()
                } else {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        NetworkImage(
                            url = item?.pic,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        } else {
            currentPageContent()
        }

        // 竖屏评论弹层：列表 + 点赞/点踩/回复/楼中楼 + 底部输入，数据与详情页评论共用
        // 注意渲染在楼中楼弹层之前，保证从评论弹层点进楼中楼时楼中楼盖在上层
        if (portrait && commentSheetProgress > 0.001f) {
            PortraitCommentSheet(
                progress = commentSheetProgress,
                heightFraction = portraitSheetFraction,
                comments = state.comments,
                commentEnd = state.commentEnd,
                commentFailed = state.commentFailed,
                isLogin = AppGraph.session.isLogin(),
                input = reply,
                replyTargetName = state.replyTarget?.userName,
                onInputChange = { reply = it },
                onClose = { portraitComments = false },
                onLoadMore = viewModel::loadMoreComments,
                onSend = { content, imageUris, syncToDynamic ->
                    if (!AppGraph.session.isLogin()) {
                        onOpenLogin()
                    } else {
                        viewModel.sendReply(content, imageUris, syncToDynamic)
                    }
                    reply = ""
                },
                emotePackages = state.emotePackages,
                emoteLoading = state.emoteLoading,
                emoteFailed = state.emoteFailed,
                sending = state.sendingReply,
                onRequestEmotes = viewModel::loadEmotes,
                onSearchMentions = viewModel::searchMentions,
                currentPositionMs = { player.currentPosition },
                onLike = viewModel::likeReply,
                onHate = viewModel::hateReply,
                onReply = { viewModel.setReplyTarget(it) },
                onShowReplies = { viewModel.openSubReplies(it) },
                onAvatarClick = { onOpenSpace(it.mid, it.userName, it.avatar) },
                onReport = viewModel::reportReply,
                onDelete = viewModel::deleteReply,
                onOpenLogin = onOpenLogin,
                commentSort = state.commentSort,
                onSelectSort = viewModel::setCommentSort,
            )
        }
        state.subRoot?.let { root ->
            SubReplySheet(
                root = root,
                replies = state.subReplies,
                isEnd = state.subEnd,
                loading = state.subLoading,
                onClose = viewModel::closeSubReplies,
                onLoadMore = viewModel::loadSubReplies,
                onLike = viewModel::likeReply,
                onHate = viewModel::hateReply,
                onReply = { viewModel.setReplyTarget(it, root.rpid) },
                onAvatarClick = { onOpenSpace(it.mid, it.userName, it.avatar) },
                onReport = viewModel::reportReply,
                onDelete = viewModel::deleteReply,
            )
        }
        // 竖屏投币弹窗（与详情页共用同一套状态）
        CoinDialog(
            visible = state.coinDialog,
            currentCoinCount = state.coinCount,
            balance = state.coinBalance,
            onDismiss = viewModel::dismissCoinDialog,
            onConfirm = viewModel::doCoin,
        )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .imePadding(),
    ) {
        // 顶部状态栏区域固定为黑色（浅色/深色模式一致），与播放器连成一片
        Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding())

        if (state.video == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator()
                else Text(state.error ?: "加载失败", color = colors.t3)
            }
            return@Column
        }

        val video = state.video!!
        // 简介/评论双页：点击页签或左右滑动切换，页签下方小蓝条跟随
        val detailPager = rememberPagerState(initialPage = startTab, pageCount = { 2 })
        LaunchedEffect(state.tab) {
            if (detailPager.currentPage != state.tab) detailPager.animateScrollToPage(state.tab)
        }
        LaunchedEffect(detailPager.currentPage) {
            if (state.tab != detailPager.currentPage) viewModel.selectTab(detailPager.currentPage)
        }
        VideoPlayerPane(
            player = player,
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            fullscreen = false,
            onToggleFullscreen = enterAutoFullscreen,
            onPictureInPicture = enterPip,
            onBack = onBack,
            onHome = onHome,
            onTogglePortrait = { portrait = true },
        )
        DetailTabs(
            selected = state.tab,
            commentCount = state.video?.replies ?: 0,
            onSelect = viewModel::selectTab,
            danmakuEnabled = state.danmakuSettings.enabled,
            onToggleDanmaku = {
                viewModel.setDanmakuSettings(
                    state.danmakuSettings.copy(enabled = !state.danmakuSettings.enabled),
                )
            },
            onSendDanmaku = { danmakuMode = !danmakuMode },
            danmakuMode = danmakuMode,
            indicatorProgress = (detailPager.currentPage + detailPager.currentPageOffsetFraction).coerceIn(0f, 1f),
        )
        HorizontalPager(state = detailPager, modifier = Modifier.weight(1f)) { page ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (page == 0) {
                // 简介页：UP主 / 标题（含简介正文、播放信息） / 互动
                item {
                    UpSection(
                        video = video,
                        following = state.following,
                        upFans = state.upFans,
                        onFollow = viewModel::toggleFollow,
                        onAvatarClick = { onOpenSpace(video.upMid, video.upName, video.upFace) },
                    )
                }
                item { VideoTitleSection(video) }
                item {
                    TripleActions(
                        video = video,
                        liked = state.liked,
                        coined = state.coinCount > 0,
                        faved = state.faved,
                        onLike = viewModel::toggleLike,
                        onCoin = viewModel::openCoinDialog,
                        onFav = viewModel::toggleFav,
                        onShare = { shareVideo(context, video) },
                        onDownload = viewModel::download,
                    )
                }
                if (state.episodes.isNotEmpty()) {
                    item {
                        SeasonEpisodesSection(
                            seasonTitle = state.seasonTitle,
                            episodes = state.episodes,
                            currentBvid = bvid,
                            onOpenEpisode = onOpenVideo,
                        )
                    }
                }
                if (state.collectionEpisodes.isNotEmpty()) {
                    item {
                        SeasonEpisodesSection(
                            label = "合集",
                            countUnit = "个视频",
                            seasonTitle = state.collectionTitle,
                            episodes = state.collectionEpisodes,
                            currentBvid = bvid,
                            onOpenEpisode = onOpenVideo,
                        )
                    }
                }
                if (state.notes.isNotEmpty()) {
                    item {
                        NotesSection(
                            notes = state.notes,
                            onOpen = { note ->
                                val url = note.webUrl.ifBlank { "https://www.bilibili.com/" }
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                        )
                    }
                }
                if (state.related.isNotEmpty()) {
                    item { RelatedHeader() }
                    itemsIndexed(state.related, key = { _, video -> video.bvid }) { index, video ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .height(0.5.dp)
                                    .background(colors.line),
                            )
                        }
                        RankRow(
                            video = video,
                            onClick = { onOpenVideo(video.bvid) },
                            trailingActions = listOf(
                                "稍后再看" to { viewModel.watchLater(video) },
                                "不感兴趣" to { viewModel.notInterested(video) },
                            ),
                        )
                    }
                    if (!state.relatedEnd) {
                        // 信息流：滚到底自动加载下一页相关视频
                        item {
                            LaunchedEffect(state.related.size) {
                                viewModel.loadMoreRelated()
                            }
                            Text(
                                text = "正在加载相关视频…",
                                fontSize = 13.sp,
                                color = colors.t3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                            )
                        }
                    }
                }
            } else {
                item {
                    CommentSortHeader(sort = state.commentSort, onSelect = viewModel::setCommentSort)
                }
                items(state.comments, key = { it.rpid }) { comment ->
                    CommentRow(
                        comment = comment,
                        onLike = { viewModel.likeReply(comment) },
                        onHate = { viewModel.hateReply(comment) },
                        onReply = { viewModel.setReplyTarget(comment) },
                        onShowReplies = { viewModel.openSubReplies(comment) },
                        onAvatarClick = { onOpenSpace(comment.mid, comment.userName, comment.avatar) },
                        onReport = { reason -> viewModel.reportReply(comment, reason) },
                        onDelete = if (comment.isMine) ({ viewModel.deleteReply(comment) }) else null,
                        onOpenBvid = { onOpenVideo(it) },
                        onOpenUser = { mid, name -> onOpenSpace(mid, name, "") },
                        onOpenTopic = onOpenTopic,
                    )
                }
                if (!state.commentEnd && state.comments.isNotEmpty()) {
                    // 滚到底自动加载下一页评论；失败时给出重试入口，避免卡在「正在加载评论…」
                    item {
                        if (state.commentFailed) {
                            Text(
                                text = "加载失败，点击重试",
                                fontSize = 13.sp,
                                color = colors.acc,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.loadMoreComments() }
                                    .padding(8.dp),
                            )
                        } else {
                            LaunchedEffect(state.comments.size) {
                                viewModel.loadMoreComments()
                            }
                            Text(
                                text = "正在加载评论…",
                                fontSize = 13.sp,
                                color = colors.t3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                            )
                        }
                    }
                }
                if (state.comments.isEmpty()) {
                    item {
                        if (state.commentFailed) {
                            Text(
                                text = "评论加载失败，点击重试",
                                fontSize = 13.sp,
                                color = colors.acc,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.loadMoreComments() }
                                    .padding(24.dp),
                            )
                        } else {
                            Text(
                                text = "暂无评论",
                                fontSize = 13.sp,
                                color = colors.t3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                }
                // 未登录时接口只返回 3 条评论，在底部说明并给出登录入口
                if (!AppGraph.session.isLogin()) {
                    item {
                        Text(
                            text = if (state.comments.isEmpty()) {
                                "登录后可查看评论"
                            } else {
                                "未登录仅显示 ${state.comments.size} 条评论 · 点击登录查看全部"
                            },
                            fontSize = 12.5.sp,
                            color = colors.acc,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenLogin() }
                                .padding(vertical = 16.dp),
                        )
                    }
                }
            }
            }
        }

        // 底部输入框：简介页不显示，只在评论页或点了「发弹幕」后出现
        if (state.tab == 1 || danmakuMode) {
            ReplyBar(
                value = reply,
                onValueChange = { reply = it },
                danmakuMode = danmakuMode,
                replyTargetName = state.replyTarget?.userName,
                emotePackages = state.emotePackages,
                emoteLoading = state.emoteLoading,
                emoteFailed = state.emoteFailed,
                isLogin = AppGraph.session.isLogin(),
                onOpenLogin = onOpenLogin,
                sending = state.sendingReply,
                onRequestEmotes = viewModel::loadEmotes,
                onSearchMentions = viewModel::searchMentions,
                onSend = { content, imageUris, syncToDynamic ->
                    if (!AppGraph.session.isLogin()) {
                        onOpenLogin()
                    } else if (danmakuMode) {
                        viewModel.sendDanmaku(content, player.currentPosition)
                    } else {
                        viewModel.sendReply(content, imageUris, syncToDynamic)
                    }
                    reply = ""
                    danmakuMode = false
                },
                currentPositionMs = { player.currentPosition },
            )
        }
    }

    // 楼中楼回复弹层
    state.subRoot?.let { root ->
        SubReplySheet(
            root = root,
            replies = state.subReplies,
            isEnd = state.subEnd,
            loading = state.subLoading,
            onClose = viewModel::closeSubReplies,
            onLoadMore = viewModel::loadSubReplies,
            onLike = viewModel::likeReply,
            onHate = viewModel::hateReply,
            onReply = { viewModel.setReplyTarget(it, root.rpid) },
            onAvatarClick = { onOpenSpace(it.mid, it.userName, it.avatar) },
            onReport = viewModel::reportReply,
            onDelete = viewModel::deleteReply,
        )
    }
    // 投币弹窗：选择投 1/2 枚 + 是否同时点赞，显示硬币余额
    CoinDialog(
        visible = state.coinDialog,
        currentCoinCount = state.coinCount,
        balance = state.coinBalance,
        onDismiss = viewModel::dismissCoinDialog,
        onConfirm = viewModel::doCoin,
    )
    }
}

@Composable
private fun VideoTitleSection(video: Video) {
    val colors = biko
    // 简介里 B 站常以「-」占位，去掉开头多余的「-」与空白
    val desc = video.desc.trim().removePrefix("-").trim()
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "descArrow",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = video.title,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                lineHeight = 24.sp,
                modifier = Modifier.weight(1f),
            )
            if (desc.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(24.dp)
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = colors.t3,
                        modifier = Modifier.size(20.dp).rotate(arrowRotation),
                    )
                }
            }
        }
        if (desc.isNotBlank()) {
            Text(
                text = desc,
                fontSize = 13.5.sp,
                color = colors.t2,
                lineHeight = 22.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
        Text(
            text = buildString {
                append(formatCount(video.views)).append("播放 · ")
                append(formatCount(video.danmaku)).append("弹幕 · ")
                append(formatTimeAgo(video.pubdate))
            },
            fontSize = 12.sp,
            color = colors.t3,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

/** 相关推荐标题（列表本体作为信息流项渲染在简介页底部）。 */
@Composable
private fun RelatedHeader() {
    val colors = biko
    Text(
        text = "相关推荐",
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Bold,
        color = colors.text,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** 视频笔记区块：标题 + 笔记卡片列表（点击在浏览器中打开对应笔记网页）。 */
@Composable
private fun NotesSection(notes: List<VideoNote>, onOpen: (VideoNote) -> Unit) {
    val colors = biko
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "视频笔记",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
            Text(
                text = "共 ${notes.size} 篇",
                fontSize = 11.5.sp,
                color = colors.t3,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        notes.forEach { note ->
            NoteCard(note = note, onClick = { onOpen(note) })
        }
    }
}

/** 单条笔记卡片：标题 + 摘要 + 作者 / 时间 / 点赞数。 */
@Composable
private fun NoteCard(note: VideoNote, onClick: () -> Unit) {
    val colors = biko
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = note.title.ifBlank { "无标题笔记" },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (note.summary.isNotBlank()) {
            Text(
                text = note.summary,
                fontSize = 12.5.sp,
                color = colors.t2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (note.authorFace.isNotBlank()) {
                NetworkImage(
                    url = note.authorFace,
                    modifier = Modifier.size(18.dp).clip(CircleShape),
                )
            }
            Text(
                text = note.authorName,
                fontSize = 11.5.sp,
                color = colors.t2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            Text(
                text = note.pubtime,
                fontSize = 11.sp,
                color = colors.t3,
            )
            Text(
                text = " · ${formatCount(note.likes)}赞",
                fontSize = 11.sp,
                color = colors.t3,
            )
        }
    }
}

/**
 * 选集 / 合集区块：位于点赞行之下、相关推荐之上。
 * 横向卡片列表，正在播放的一集加遮罩与播放图标高亮。
 * 番剧走默认的「选集 / 共 N 集」，UP 主合集由调用方改传「合集 / 共 N 个视频」。
 */
@Composable
private fun SeasonEpisodesSection(
    seasonTitle: String,
    episodes: List<Video>,
    currentBvid: String,
    onOpenEpisode: (String) -> Unit,
    label: String = "选集",
    countUnit: String = "集",
    episodeBadge: (Int) -> String = { "第${it + 1}集" },
) {
    val colors = biko
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
            Text(
                text = seasonTitle,
                fontSize = 12.sp,
                color = colors.t3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Text(
                text = "共 ${episodes.size} $countUnit",
                fontSize = 11.5.sp,
                color = colors.t3,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = episodes,
                key = { _, episode -> episode.bvid },
                // 统一下发 contentType：LazyRow 复用同一套 item 组合，滑动时减少重组开销
                contentType = { _, _ -> "episode" },
            ) { index, episode ->
                SeasonEpisodeCard(
                    badge = episodeBadge(index),
                    episode = episode,
                    selected = episode.bvid == currentBvid,
                    onClick = { onOpenEpisode(episode.bvid) },
                )
            }
        }
    }
}

/** 单张分集卡片：16:9 封面 + 集数/时长角标 + 集名。 */
@Composable
private fun SeasonEpisodeCard(
    badge: String,
    episode: Video,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = biko
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.card2),
        ) {
            NetworkImage(url = episode.pic, modifier = Modifier.fillMaxSize())
            Text(
                text = badge,
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
            if (episode.duration > 0) {
                Text(
                    text = formatDuration(episode.duration),
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = episode.title,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.acc else colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun UpSection(
    video: Video,
    following: Boolean,
    upFans: Long,
    onFollow: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    val colors = biko
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkImage(
            url = video.upFace,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(2.dp, colors.acc, CircleShape)
                .clickable(onClick = onAvatarClick),
        )
        // 名称 + 粉丝看成一个整体，垂直居中于头像腰线
        Column(Modifier.weight(1f).padding(start = 10.dp).offset(y = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = video.upName,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                )
                Text(
                    text = "UP主",
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    color = Color.White,
                    // 去掉字体上下留白，色块更扁
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.acc)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                text = if (upFans >= 0) "${formatCount(upFans.toInt())}粉丝" else "",
                fontSize = 11.5.sp,
                color = colors.t3,
                modifier = Modifier.offset(y = (-4).dp),
            )
        }
        Text(
            text = if (following) "已关注" else "关注",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (following) colors.t3 else Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (following) colors.card2 else colors.acc)
                .clickable(onClick = onFollow)
                .padding(horizontal = 14.dp, vertical = 3.dp),
        )
    }
}

/** 投币弹窗：显示硬币余额，选择投 1/2 枚并可同时点赞（对齐 BiliPai 的 CoinDialog）。 */
@Composable
private fun CoinDialog(
    visible: Boolean,
    currentCoinCount: Int,
    balance: Double?,
    onDismiss: () -> Unit,
    onConfirm: (count: Int, alsoLike: Boolean) -> Unit,
) {
    if (!visible) return
    val colors = biko
    var count by remember { mutableIntStateOf(1) }
    var alsoLike by remember { mutableStateOf(true) }
    // 还能投几枚：原创 2、已投过则相应减少
    val remaining = (COIN_LIMIT - currentCoinCount).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = {
            Column {
                Text("投币", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text)
                Text(
                    text = when {
                        balance == null -> "余额加载中…"
                        balance == COIN_BALANCE_NO_LOGIN -> "未登录"
                        balance < 0 -> "余额加载失败"
                        else -> "硬币余额：${balance.toInt()}"
                    },
                    fontSize = 12.sp,
                    color = if (balance != null && balance < 0) colors.t3 else colors.t2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Column {
                Text("选择投币数量", fontSize = 13.sp, color = colors.t2)
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CoinCountChip("1 硬币", selected = count == 1, enabled = remaining >= 1) { count = 1 }
                    CoinCountChip("2 硬币", selected = count == 2, enabled = remaining >= 2) { count = 2 }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .clickable { alsoLike = !alsoLike },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = alsoLike, onCheckedChange = { alsoLike = it })
                    Text("同时点赞", fontSize = 13.sp, color = colors.text)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(count.coerceIn(1, COIN_LIMIT), alsoLike) },
                enabled = remaining > 0,
            ) {
                Text("投币", color = colors.acc)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = colors.t3) }
        },
    )
}

/** 投币枚数选项：选中蓝底白字，不可选时置灰。 */
@Composable
private fun CoinCountChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = biko
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.acc else colors.card2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = when {
                !enabled -> colors.t3
                selected -> Color.White
                else -> colors.text
            },
        )
    }
}

@Composable
private fun TripleActions(
    video: Video,
    liked: Boolean,
    coined: Boolean,
    faved: Boolean,
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFav: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    // 缓存状态：跟随下载任务实时变化（未缓存 / 缓存中 xx% / 已缓存 / 失败）
    val downloadTask by remember(video.bvid) { DownloadManager.observe(video.bvid) }
        .collectAsState(initial = DownloadManager.taskOf(video.bvid))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionItem(
            iconRes = R.drawable.ic_like,
            label = formatCount(video.likes),
            active = liked,
            onClick = onLike,
            modifier = Modifier.weight(1f),
        )
        ActionItem(
            iconRes = R.drawable.ic_coin,
            label = formatCount(video.coins),
            active = coined,
            onClick = onCoin,
            modifier = Modifier.weight(1f),
        )
        ActionItem(
            iconRes = R.drawable.ic_star,
            label = formatCount(video.favorites),
            active = faved,
            onClick = onFav,
            modifier = Modifier.weight(1f),
        )
        ActionItem(
            iconRes = R.drawable.ic_share,
            label = "转发",
            onClick = onShare,
            modifier = Modifier.weight(1f),
        )
        ActionItem(
            iconRes = R.drawable.ic_download,
            label = downloadLabel(downloadTask),
            active = downloadTask?.isDone == true,
            onClick = onDownload,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 缓存按钮文案：按下载任务状态展示。 */
private fun downloadLabel(task: DownloadTask?): String = when {
    task == null -> "缓存"
    task.status == DownloadTask.STATUS_DONE -> "已缓存"
    task.status == DownloadTask.STATUS_RUNNING -> "${(task.progress * 100).toInt()}%"
    task.status == DownloadTask.STATUS_WAITING -> "等待中"
    task.status == DownloadTask.STATUS_PAUSED -> "继续缓存"
    task.status == DownloadTask.STATUS_FAILED -> "重试"
    else -> "缓存"
}

/** 竖屏播放时叠在底部控制区上方的信息区：左侧 UP 主行 + 发弹幕入口，右侧竖排点赞/投币/收藏/转发。 */
@Composable
private fun PortraitOverlay(
    video: Video,
    following: Boolean,
    upFans: Long,
    liked: Boolean,
    coined: Boolean,
    faved: Boolean,
    onFollow: () -> Unit,
    onAvatarClick: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onCoin: () -> Unit,
    onFav: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = biko
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // UP 主行：头像 + 昵称 + UP主标志 + 粉丝数 + 关注
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                // 头像与详情页同为 40dp
                NetworkImage(
                    url = video.upFace,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick),
                )
                // 昵称 + UP主标识 + 粉丝数视作一整块，交给 Row 的 CenterVertically 与头像同中线
                Column(
                    Modifier.weight(1f, fill = false).padding(start = 10.dp).offset(y = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = video.upName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "UP主",
                            fontSize = 9.5.sp,
                            lineHeight = 11.sp,
                            color = Color.White,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.acc)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    Text(
                        text = if (upFans >= 0) "${formatCount(upFans.toInt())}粉丝" else "",
                        fontSize = 11.5.sp,
                        color = Color(0xFF9AA0A6),
                        // 与横屏详情页同一套对齐法：整块下移 2dp、粉丝再上移 4dp，
                        // 间距收紧一半，且视觉重心仍落在头像腰线上
                        modifier = Modifier.offset(y = (-4).dp),
                    )
                }
                // 关注按钮：脱离名称行，与头像同一水平中线对齐
                Text(
                    text = if (following) "已关注" else "关注",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (following) Color(0xFF9AA0A6) else Color.White,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (following) Color(0x33FFFFFF) else colors.acc)
                        .clickable(onClick = onFollow)
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                )
        }
        // 右侧竖排：点赞 / 投币 / 收藏 / 转发
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 6.dp, end = 8.dp),
        ) {
            PortraitAction(painterResource(R.drawable.ic_like), formatCount(video.likes), liked, onLike)
            PortraitAction(
                rememberVectorPainter(Icons.AutoMirrored.Outlined.Comment),
                if (video.replies > 0) formatCount(video.replies) else "评论",
                false,
                onComment,
            )
            PortraitAction(painterResource(R.drawable.ic_coin), formatCount(video.coins), coined, onCoin)
            PortraitAction(painterResource(R.drawable.ic_star), formatCount(video.favorites), faved, onFav)
            PortraitAction(painterResource(R.drawable.ic_share), "转发", false, onShare)
        }
    }
}

/**
 * 发弹幕的「发送」纸飞机：实心折纸飞机，机头朝右上，
 * 从机头到折角挖出一条细缝作为折痕，把机身分成上下两片，与素材图一致。
 */
internal val DmSendPlane: ImageVector by lazy {
    ImageVector.Builder(
        name = "DmSendPlane",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.EvenOdd,
    ) {
        // 机身外轮廓：机头在右上，左下为机翼，下凹的折角形成纸飞机造型
        moveTo(21.8f, 2.4f)
        lineTo(14.6f, 21.2f)
        lineTo(10.9f, 12.9f)
        lineTo(2.2f, 9.1f)
        close()
        // 折痕：机头到折角的细缝（EvenOdd 挖空），切出上下两片机翼
        moveTo(20.96f, 3.83f)
        lineTo(11.64f, 12.81f)
        lineTo(11.02f, 12.16f)
        lineTo(20.34f, 3.19f)
        close()
    }.build()
}

/**
 * 弹幕开关图标：外框为屏幕（描边切角矩形），内部两条横线代表滚动弹幕。
 * 与 [DmSendPlane] 一样用 EvenOdd 挖空，纯几何图形，不依赖字体。
 */
internal val DmToggleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "DmToggleIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.EvenOdd,
    ) {
        // 屏幕外框
        moveTo(4.2f, 4.6f)
        lineTo(19.8f, 4.6f)
        lineTo(21.4f, 6.2f)
        lineTo(21.4f, 17.8f)
        lineTo(19.8f, 19.4f)
        lineTo(4.2f, 19.4f)
        lineTo(2.6f, 17.8f)
        lineTo(2.6f, 6.2f)
        close()
        // 内框挖空，只留描边
        moveTo(5.4f, 6.1f)
        lineTo(18.6f, 6.1f)
        lineTo(19.9f, 7.4f)
        lineTo(19.9f, 16.6f)
        lineTo(18.6f, 17.9f)
        lineTo(5.4f, 17.9f)
        lineTo(4.1f, 16.6f)
        lineTo(4.1f, 7.4f)
        close()
    }.path(fill = SolidColor(Color.White)) {
        // 两条弹幕横线
        moveTo(6.6f, 9.0f)
        lineTo(15.8f, 9.0f)
        lineTo(15.8f, 10.5f)
        lineTo(6.6f, 10.5f)
        close()
        moveTo(8.4f, 13.1f)
        lineTo(17.6f, 13.1f)
        lineTo(17.6f, 14.6f)
        lineTo(8.4f, 14.6f)
        close()
    }.build()
}

/**
 * 竖屏播放最底部「发弹幕」长药丸：右侧内嵌弹幕开关。
 * 点药丸其余区域不新开输入框，而是让这条药丸本身就地变成输入态，
 * 由播放器把它连同键盘一起往上抬。
 */
@Composable
private fun PortraitDanmakuEntry(
    inputActive: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    onActivate: () -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
) {
    val colors = biko
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 键盘收起时退出输入态，恢复成药丸
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // 以 inputActive 为键重置：药丸与输入条复用同一节点，若沿用同一个 remember，
    // 首次用过后 imeWasVisible 会残留 true，下次激活会被立刻判定为「键盘已收起」而秒退，
    // 表现为输入框只能用一次、之后打不开。
    var imeWasVisible by remember(inputActive) { mutableStateOf(false) }
    if (inputActive) {
        LaunchedEffect(imeVisible) {
            if (imeVisible) {
                imeWasVisible = true
            } else if (imeWasVisible) {
                onDismiss()
            }
        }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(80)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                    text = "发个弹幕吧",
                    fontSize = 13.sp,
                    color = Color(0xFF9AA0A6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onActivate),
                )
            } else {
                if (value.isEmpty()) {
                    Text("发个弹幕吧", fontSize = 13.sp, color = Color(0xFF9AA0A6))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
        }
        // 右侧同一个位置原地换形：药丸态是「弹」（弹幕开关），
        // 激活输入后换成蓝色纸飞机「发送」图标（不再是蓝色实心药丸）。
        val sendIconAlpha by animateFloatAsState(
            targetValue = if (inputActive) 1f else 0f,
            animationSpec = tween(durationMillis = 180),
            label = "dmSendIconAlpha",
        )
        val toggleAlpha by animateFloatAsState(
            targetValue = if (inputActive) 0f else 1f,
            animationSpec = tween(durationMillis = 180),
            label = "dmToggleAlpha",
        )
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .size(30.dp)
                .clip(CircleShape)
                .clickable { if (inputActive) onSend() else onToggleDanmaku() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = DmSendPlane,
                contentDescription = "发送",
                tint = colors.acc,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { alpha = sendIconAlpha },
            )
            Icon(
                imageVector = DmToggleIcon,
                contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                tint = if (danmakuEnabled) colors.acc else Color(0xFF9AA0A6),
                modifier = Modifier.size(19.dp).graphicsLayer { alpha = toggleAlpha },
            )
        }
    }
}

/** 竖屏播放右侧动作按钮的「雾透」白：纯白带一点点透明，仅这一处使用。 */
private val PortraitActionWhite = Color.White.copy(alpha = 0.85f)

/** 竖屏播放右侧竖排动作：图标在上、文字在下，白色本身带一点点透明（不加底色）。 */
@Composable
private fun PortraitAction(painter: Painter, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = biko
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = label,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                if (active) colors.acc else PortraitActionWhite,
            ),
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) colors.acc else PortraitActionWhite,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * 评论排序切换（热门 / 最新），对齐 B 站评论区的排序入口。
 * 详情页与竖屏评论弹层共用同一实现，保证两处位置/样式一致。
 */
@Composable
private fun CommentSortHeader(
    sort: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = biko
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "评论", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.text)
        Spacer(Modifier.weight(1f))
        listOf("热门" to COMMENT_SORT_HOT, "最新" to COMMENT_SORT_NEWEST).forEach { (label, mode) ->
            val active = sort == mode
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (active) colors.acc else colors.t3,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun DetailTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    onSendDanmaku: () -> Unit,
    /** 是否处于发弹幕模式：药丸文案在「发弹幕」「收起」之间切换。 */
    danmakuMode: Boolean = false,
    /** 评论总数（详情页接口的 stat.reply），显示在「评论」页签上。 */
    commentCount: Int = 0,
    /** 0f..1f，滑动时连续变化，用于小蓝条过渡动画。 */
    indicatorProgress: Float = selected.toFloat(),
) {
    val colors = biko
    // 两个页签的宽度与行高（px）：第二页签位置 = 页签0宽 + 16dp 间距，蓝条据此插值
    var tabW0 by remember { mutableStateOf(0) }
    var tabW1 by remember { mutableStateOf(0) }
    var rowH by remember { mutableStateOf(0) }
    // 蓝条跟随进度用弹簧：跟手时略滞后、松手/点按时有回弹，观感更灵动
    val progress by animateFloatAsState(
        targetValue = indicatorProgress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 900f),
        label = "tab_indicator",
    )
    // 行内边距 6 + 文字内边距 8 = 14，与下方标题/UP 主区的 14dp 左边距对齐
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().onSizeChanged { rowH = it.height },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val labels = listOf(
                "简介",
                if (commentCount > 0) "评论 ${formatCount(commentCount)}" else "评论",
            )
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                if (index == 1) Spacer(Modifier.width(16.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .onSizeChanged { if (index == 0) tabW0 = it.width else tabW1 = it.width }
                        .clickable { onSelect(index) },
                ) {
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colors.acc else colors.t3,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    // 底部给蓝条预留高度，行高不抖动
                    Spacer(Modifier.height(5.dp))
                }
            }
            Spacer(Modifier.weight(1f))
        // 发弹幕入口：灰底药丸，点一下底栏切成弹幕模式，再点收起
        Text(
            text = if (danmakuMode) "收起" else "发弹幕",
            fontSize = 13.sp,
            color = colors.acc,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.card2)
                .clickable(onClick = onSendDanmaku)
                .padding(horizontal = 12.dp, vertical = 2.5.dp),
        )
        // 弹幕开关：图标无框，蓝=开，灰=关
        Icon(
            imageVector = DmToggleIcon,
            contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
            tint = if (danmakuEnabled) colors.acc else colors.t3,
            modifier = Modifier
                .padding(end = 6.dp, start = 2.dp)
                .size(19.dp)
                .clickable(onClick = onToggleDanmaku),
        )
        }
        // 小蓝条：位置在两页签文字区间滑动，宽度按各自文字宽度插值，切到一半时横向拉伸
        if (tabW0 > 0 && tabW1 > 0) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val padPx = with(density) { 8.dp.toPx() }
            val gapPx = with(density) { 16.dp.toPx() }
            val w0 = (tabW0 - 2 * padPx).coerceAtLeast(0f)
            val w1 = (tabW1 - 2 * padPx).coerceAtLeast(0f)
            // 1 在中间、0 在两端：用来做「切换途中拉伸一下」的弹性
            val mid = 1f - kotlin.math.abs(progress * 2f - 1f)
            // 以两个页签文字的中心做插值，宽度按各自文字宽度插值并中途拉伸，视觉上更跟手
            val center0 = tabW0 / 2f
            val center1 = tabW0 + gapPx + tabW1 / 2f
            val width = (w0 + (w1 - w0) * progress).coerceAtLeast(0f) * (1f + 0.22f * mid)
            val left = center0 + (center1 - center0) * progress - width / 2f
            Box(
                Modifier
                    .offset { IntOffset(left.toInt(), rowH - 5.dp.roundToPx()) }
                    .width(with(density) { width.toDp() })
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.acc),
            )
        }
    }
}

@Composable
private fun VideoPlayerPane(
    player: ExoPlayer,
    state: VideoDetailState,
    viewModel: VideoDetailViewModel,
    modifier: Modifier,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onPictureInPicture: () -> Unit,
    /** 顶栏返回：退出大屏 / 竖屏 / 上一层。 */
    onBack: () -> Unit = {},
    /** 顶栏房子按钮：跳到首页（与返回是两个不同功能）。 */
    onHome: () -> Unit = {},
    portrait: Boolean = false,
    onTogglePortrait: (() -> Unit)? = null,
    portraitExtra: (@Composable () -> Unit)? = null,
    portraitBottom: (@Composable () -> Unit)? = null,
    onEnterLandscape: (() -> Unit)? = null,
    portraitInputActive: Boolean = false,
    portraitSheetProgress: Float = 0f,
    onPanelOpenChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    BikoPlayer(
        player = player,
        modifier = modifier,
        playInfo = state.playInfo,
        cookie = AppGraph.session.cookie(),
        title = state.video?.title.orEmpty(),
        quality = state.qn,
        onSelectQuality = viewModel::setQuality,
        audioIndex = state.audioIndex,
        onSelectAudio = viewModel::setAudio,
        speed = state.speed,
        onSelectSpeed = viewModel::setSpeed,
        aspect = state.aspect,
        onSelectAspect = viewModel::setAspect,
        danmaku = state.danmaku,
        danmakuSettings = state.danmakuSettings,
        onDanmakuSettingsChange = viewModel::setDanmakuSettings,
        specialDanmaku = state.specialDanmaku,
        resumePositionMs = state.resumeMs,
        onProgress = viewModel::saveProgress,
        fullscreen = fullscreen,
        onToggleFullscreen = onToggleFullscreen,
        onPictureInPicture = onPictureInPicture,
        onPlaybackError = viewModel::onPlaybackError,
        portrait = portrait,
        onTogglePortrait = onTogglePortrait,
        portraitExtra = portraitExtra,
        portraitBottom = portraitBottom,
        onEnterLandscape = onEnterLandscape,
        portraitInputActive = portraitInputActive,
        portraitSheetProgress = portraitSheetProgress,
        onHome = onHome,
        onShare = { state.video?.let { shareVideo(context, it) } },
        onBack = onBack,
        backgroundAudio = state.backgroundAudio,
        onSetBackgroundAudio = viewModel::setBackgroundAudio,
        audioOnly = state.audioOnly,
        coverUrl = state.video?.pic,
        onSetAudioOnly = viewModel::setAudioOnly,
        onPanelOpenChange = onPanelOpenChange,
    )
}

@Composable
private fun ReplyBar(
    value: String,
    onValueChange: (String) -> Unit,
    danmakuMode: Boolean,
    replyTargetName: String?,
    emotePackages: List<EmotePackage>,
    emoteLoading: Boolean,
    emoteFailed: Boolean,
    isLogin: Boolean,
    onOpenLogin: () -> Unit,
    sending: Boolean,
    onRequestEmotes: () -> Unit,
    onSearchMentions: suspend (String) -> List<MentionUser>,
    onSend: (content: String, imageUris: List<Uri>, syncToDynamic: Boolean) -> Unit,
    currentPositionMs: () -> Long,
) {
    val colors = biko
    // 软键盘是否弹出：这一排工具只在打字时出现，未弹键盘（也就是跟着键盘一起）收起
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // 输入框内部用 TextFieldValue 记录光标位置：表情 / @ / 时间戳都要插到光标处
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var syncToDynamic by remember { mutableStateOf(false) }
    var showEmotes by remember { mutableStateOf(false) }
    var showMentions by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 点「发弹幕」或点评论「回复」后，自动聚焦输入框并弹出键盘
    LaunchedEffect(danmakuMode, replyTargetName) {
        if (danmakuMode || replyTargetName != null) {
            kotlinx.coroutines.delay(80)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    // 发送成功后由外部清空 value，这里同步重置内部状态
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }

    val insertAtCursor: (String) -> Unit = { insert ->
        val text = field.text
        val start = field.selection.start.coerceIn(0, text.length)
        val end = field.selection.end.coerceIn(0, text.length)
        val merged = text.substring(0, start) + insert + text.substring(end)
        val cursor = start + insert.length
        field = TextFieldValue(merged, TextRange(cursor))
        onValueChange(merged)
    }

    val canSend = if (danmakuMode) {
        field.text.isNotBlank()
    } else {
        field.text.isNotBlank() || imageUris.isNotEmpty()
    }

    val publish: () -> Unit = {
        if (!sending) {
            onSend(field.text, imageUris, syncToDynamic && !danmakuMode)
            field = TextFieldValue("")
            imageUris = emptyList()
            syncToDynamic = false
            showEmotes = false
            showMentions = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { picked ->
        if (picked.isNotEmpty()) imageUris = (imageUris + picked).take(9)
    }

    LaunchedEffect(showEmotes) { if (showEmotes) onRequestEmotes() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        if (!danmakuMode && showEmotes) {
            EmotePanel(
                packages = emotePackages,
                loading = emoteLoading || (!emoteFailed && emotePackages.isEmpty()),
                failed = emoteFailed,
                onRetry = onRequestEmotes,
                onPick = { insertAtCursor(it) },
                onClose = { showEmotes = false },
            )
        } else if (!danmakuMode && showMentions) {
            MentionPanel(
                onSearch = onSearchMentions,
                onPick = { insertAtCursor("@${it.name} ") },
                onClose = { showMentions = false },
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            // 已选配图预览条，点右上角 × 移除
            if (!danmakuMode && imageUris.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    items(imageUris) { uri ->
                        Box(Modifier.size(64.dp)) {
                            NetworkImage(
                                url = uri.toString(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .background(Color(0x99000000), CircleShape)
                                    .clickable { imageUris = imageUris - uri },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .background(colors.card2, RoundedCornerShape(20.dp))
                    .padding(start = 13.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (field.text.isEmpty()) {
                            // 弹幕输入框只在点了「发弹幕」后才出现，平时这里是评论输入
                            Text(
                                when {
                                    danmakuMode -> "发个弹幕吧"
                                    replyTargetName != null -> "回复 @$replyTargetName"
                                    else -> "发条评论再说再见"
                                },
                                fontSize = 13.sp,
                                color = colors.t3,
                            )
                        }
                        BasicTextField(
                            value = field,
                            onValueChange = { nv ->
                                field = nv
                                onValueChange(nv.text)
                            },
                            maxLines = 5,
                            textStyle = TextStyle(fontSize = 13.sp, color = colors.text),
                            cursorBrush = SolidColor(colors.acc),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { publish() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                    // 发送：蓝色纸飞机，内嵌在药丸右侧
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canSend) { publish() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.acc,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (canSend) colors.acc else colors.t3,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
            // 工具排跟着键盘走：键盘收起时这一排也收起，只在打字时出现。
            // 弹幕模式下没有这些工具（发送已在药丸里），直接不显示这一排。
            if (keyboardVisible && !danmakuMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 转发到动态：圆圈勾选 + 文案
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { syncToDynamic = !syncToDynamic }
                            .padding(vertical = 2.dp),
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .border(1.5.dp, if (syncToDynamic) colors.acc else colors.t3, CircleShape)
                                .background(
                                    if (syncToDynamic) colors.acc else Color.Transparent,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (syncToDynamic) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Text(
                            "转发到动态",
                            fontSize = 12.5.sp,
                            color = if (syncToDynamic) colors.text else colors.t2,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    // 表情 / @提及 / 插入时间 / 图片：全部靠右排列
                    Spacer(Modifier.weight(1f))
                    ReplyToolIcon(Icons.Outlined.EmojiEmotions, "表情", showEmotes) {
                        if (!isLogin) {
                            onOpenLogin()
                        } else {
                            showEmotes = !showEmotes
                            showMentions = false
                        }
                    }
                    ReplyToolIcon(Icons.Outlined.AlternateEmail, "@提及", showMentions) {
                        if (!isLogin) {
                            onOpenLogin()
                        } else {
                            showMentions = !showMentions
                            showEmotes = false
                        }
                    }
                    ReplyToolIcon(Icons.Outlined.AccessTime, "插入时间", false) {
                        val total = (currentPositionMs() / 1000).toInt().coerceAtLeast(0)
                        insertAtCursor(" ${total / 60}:${(total % 60).toString().padStart(2, '0')} ")
                    }
                    ReplyToolIcon(Icons.Outlined.Image, "图片", false) {
                        imagePicker.launch("image/*")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = biko
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) colors.acc else colors.t2,
            modifier = Modifier.size(21.dp),
        )
    }
}

/** 评论表情面板：顶部表情包切换，下面网格展示表情，点击插入 token。 */
@Composable
private fun EmotePanel(
    packages: List<EmotePackage>,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = biko
    var selected by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(colors.card),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("表情", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.Close,
                contentDescription = "收起",
                tint = colors.t3,
                modifier = Modifier.size(18.dp).clickable(onClick = onClose),
            )
        }
        if (packages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(
                        color = colors.acc,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("表情加载失败", fontSize = 12.5.sp, color = colors.t3)
                        Text(
                            "点击重试",
                            fontSize = 12.5.sp,
                            color = colors.acc,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    else -> Text("暂无表情", fontSize = 12.5.sp, color = colors.t3)
                }
            }
        } else {
            val safeSelected = selected.coerceIn(0, packages.lastIndex)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(packages.size) { index ->
                    val active = index == safeSelected
                    Text(
                        packages[index].text.ifBlank { "表情${index + 1}" },
                        fontSize = 13.sp,
                        color = if (active) colors.acc else colors.t3,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { selected = index }
                            .padding(vertical = 6.dp),
                    )
                }
            }
            val emotes = packages[safeSelected].emote
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(count = emotes.size) { i ->
                    val emote = emotes[i]
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPick(emote.text) },
                        contentAlignment = Alignment.Center,
                    ) {
                        NetworkImage(emote.url, Modifier.fillMaxSize(), ContentScale.Fit)
                    }
                }
            }
        }
    }
}

/** @ 提及用户搜索面板：输入关键词搜索，点击插入「@昵称 」。 */
@Composable
private fun MentionPanel(
    onSearch: suspend (String) -> List<MentionUser>,
    onPick: (MentionUser) -> Unit,
    onClose: () -> Unit,
) {
    val colors = biko
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MentionUser>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        loading = true
        delay(250)
        results = onSearch(query)
        loading = false
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(colors.card),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(colors.card2, RoundedCornerShape(17.dp))
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text("搜索用户", fontSize = 12.5.sp, color = colors.t3)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.5.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.acc),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Icon(
                Icons.Filled.Close,
                contentDescription = "收起",
                tint = colors.t3,
                modifier = Modifier.padding(start = 10.dp).size(18.dp).clickable(onClick = onClose),
            )
        }
        if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (loading) "搜索中…" else "没有找到相关用户",
                    fontSize = 12.5.sp,
                    color = colors.t3,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(results) { user ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(user) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NetworkImage(
                            user.face,
                            Modifier.size(32.dp).clip(CircleShape),
                            ContentScale.Crop,
                        )
                        Text(
                            user.name,
                            fontSize = 13.sp,
                            color = colors.text,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 楼中楼回复弹层：底部弹出，楼主评论置顶 + 分页回复列表，滚到底自动加载。 */
@Composable
private fun SubReplySheet(
    root: Comment,
    replies: List<Comment>,
    isEnd: Boolean,
    loading: Boolean,
    onClose: () -> Unit,
    onLoadMore: () -> Unit,
    onLike: (Comment) -> Unit,
    onHate: (Comment) -> Unit,
    onReply: (Comment) -> Unit,
    onAvatarClick: (Comment) -> Unit,
    onReport: (Comment, Int) -> Unit,
    onDelete: (Comment) -> Unit,
) {
    val colors = biko
    Box(Modifier.fillMaxSize()) {
        // 半透明遮罩，点击收起
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(onClick = onClose),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(colors.bg)
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "共${root.rcount}条回复",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "关闭",
                    fontSize = 13.sp,
                    color = colors.t3,
                    modifier = Modifier.clickable(onClick = onClose).padding(6.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                // 楼主评论置顶
                item {
                    CommentRow(
                        comment = root,
                        onLike = { onLike(root) },
                        onHate = { onHate(root) },
                        onReply = { onReply(root) },
                        onAvatarClick = { onAvatarClick(root) },
                        onReport = { reason -> onReport(root, reason) },
                        onDelete = if (root.isMine) ({ onDelete(root) }) else null,
                    )
                }
                items(replies) { c ->
                    CommentRow(
                        comment = c,
                        onLike = { onLike(c) },
                        onHate = { onHate(c) },
                        onReply = { onReply(c) },
                        onAvatarClick = { onAvatarClick(c) },
                        onReport = { reason -> onReport(c, reason) },
                        onDelete = if (c.isMine) ({ onDelete(c) }) else null,
                    )
                }
                if (replies.isEmpty() && !loading) {
                    item {
                        Text(
                            text = "暂无回复",
                            fontSize = 13.sp,
                            color = colors.t3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
                if (!isEnd && replies.isNotEmpty()) {
                    item {
                        // 滚到底自动加载下一页
                        LaunchedEffect(replies.size) { onLoadMore() }
                        Text(
                            text = if (loading) "正在加载…" else "",
                            fontSize = 13.sp,
                            color = colors.t3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 竖屏播放页评论弹层：底部弹出，评论列表 + 底部输入框，与详情页评论数据/操作完全共用。 */
@Composable
private fun PortraitCommentSheet(
    /** 展开进度：0=完全收起（贴屏幕底部），1=完全展开；驱动滑入滑出与遮罩透明度。 */
    progress: Float,
    /** 弹层高度占比：由画面的实际高度决定（评论区给视频让位）。 */
    heightFraction: Float = PORTRAIT_SHEET_FRACTION,
    comments: List<Comment>,
    commentEnd: Boolean,
    commentFailed: Boolean,
    isLogin: Boolean,
    input: String,
    replyTargetName: String?,
    onInputChange: (String) -> Unit,
    onClose: () -> Unit,
    onLoadMore: () -> Unit,
    onSend: (content: String, imageUris: List<Uri>, syncToDynamic: Boolean) -> Unit,
    emotePackages: List<EmotePackage>,
    emoteLoading: Boolean,
    emoteFailed: Boolean,
    sending: Boolean,
    onRequestEmotes: () -> Unit,
    onSearchMentions: suspend (String) -> List<MentionUser>,
    currentPositionMs: () -> Long,
    onLike: (Comment) -> Unit,
    onHate: (Comment) -> Unit,
    onReply: (Comment) -> Unit,
    onShowReplies: (Comment) -> Unit,
    onAvatarClick: (Comment) -> Unit,
    onReport: (Comment, Int) -> Unit,
    onDelete: (Comment) -> Unit,
    onOpenLogin: () -> Unit,
    /** 评论排序：3=热门，2=最新。 */
    commentSort: Int,
    onSelectSort: (Int) -> Unit,
) {
    val colors = biko
    val p = progress.coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()) {
        // 只负责点击收起：B 站的评论半屏不压暗画面，上方视频要保持正常亮度
        Box(
            Modifier
                .fillMaxSize()
                .clickable(onClick = onClose),
        )
        // 窗口底色是白的，白色弹层单靠自己看不出圆角，垫一层同尺寸的直角黑矩形，
        // 白色圆角被裁掉后，两个 R 角外侧的三角区就露出黑色（与上方播放器黑边同色）
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction.coerceIn(0.1f, 0.9f))
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = (1f - p) * size.height }
                .background(Color.Black),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction.coerceIn(0.1f, 0.9f))
                .align(Alignment.BottomCenter)
                // 滑入滑出：收起时整体沉到屏幕下方
                .graphicsLayer { translationY = (1f - p) * size.height }
                // 白色弹层顶部圆角（比之前收小一圈，避免 R 角过大）
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(colors.bg)
                // 消费弹层自身的空白区点击，避免穿透到下方的全屏关闭层导致误关评论
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding(),
        ) {
            // 排序控件与详情页评论区共用同一个组件，位置/样式完全一致；
            CommentSortHeader(
                sort = commentSort,
                onSelect = onSelectSort,
            )
            // 滚到底自动加载下一页：用可见项位置触发，避免列表变长时反复重启导致抖动
            val listState = rememberLazyListState()
            val currentSize by rememberUpdatedState(comments.size)
            val currentEnd by rememberUpdatedState(commentEnd)
            val currentFailed by rememberUpdatedState(commentFailed)
            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    .distinctUntilChanged()
                    .collect { last ->
                        if (!currentEnd && !currentFailed && currentSize > 0 && last >= currentSize - 2) onLoadMore()
                    }
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(comments, key = { it.rpid }) { c ->
                    CommentRow(
                        comment = c,
                        onLike = { onLike(c) },
                        onHate = { onHate(c) },
                        onReply = { onReply(c) },
                        onShowReplies = { onShowReplies(c) },
                        onAvatarClick = { onAvatarClick(c) },
                        onReport = { reason -> onReport(c, reason) },
                        onDelete = if (c.isMine) ({ onDelete(c) }) else null,
                    )
                }
                if (!commentEnd && comments.isNotEmpty()) {
                    item {
                        if (commentFailed) {
                            Text(
                                text = "加载失败，点击重试",
                                fontSize = 13.sp,
                                color = colors.acc,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onLoadMore)
                                    .padding(8.dp),
                            )
                        } else {
                            Text(
                                text = "正在加载评论…",
                                fontSize = 13.sp,
                                color = colors.t3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                            )
                        }
                    }
                }
                if (comments.isEmpty()) {
                    item {
                        if (commentFailed) {
                            Text(
                                text = "评论加载失败，点击重试",
                                fontSize = 13.sp,
                                color = colors.acc,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onLoadMore)
                                    .padding(24.dp),
                            )
                        } else {
                            Text(
                                text = "暂无评论",
                                fontSize = 13.sp,
                                color = colors.t3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                }
                // 未登录时接口只返回 3 条评论，在底部说明并给出登录入口
                if (!isLogin) {
                    item {
                        Text(
                            text = if (comments.isEmpty()) {
                                "登录后可查看评论"
                            } else {
                                "未登录仅显示 ${comments.size} 条评论 · 点击登录查看全部"
                            },
                            fontSize = 12.5.sp,
                            color = colors.acc,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenLogin)
                                .padding(vertical = 16.dp),
                        )
                    }
                }
            }
            // 底部评论输入框，贴软键盘上沿
            Box(Modifier.imePadding()) {
                ReplyBar(
                    value = input,
                    onValueChange = onInputChange,
                    danmakuMode = false,
                    replyTargetName = replyTargetName,
                    emotePackages = emotePackages,
                    emoteLoading = emoteLoading,
                    emoteFailed = emoteFailed,
                    isLogin = isLogin,
                    onOpenLogin = onOpenLogin,
                    sending = sending,
                    onRequestEmotes = onRequestEmotes,
                    onSearchMentions = onSearchMentions,
                    onSend = onSend,
                    currentPositionMs = currentPositionMs,
                )
            }
        }
    }
}

/** 分享视频：打开系统分享面板（详情页与竖屏模式共用）。 */
private fun shareVideo(context: Context, video: Video) {
    val text = "${video.title}\nhttps://www.bilibili.com/video/${video.bvid}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "转发到"))
}
