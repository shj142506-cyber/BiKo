package com.biko.client.ui.components

import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.biko.client.R
import com.biko.client.data.BILI_PLAY_USER_AGENT
import com.biko.client.data.BILI_REFERER
import com.biko.client.data.SpecialDanmaku
import com.biko.client.data.model.DanmakuItem
import com.biko.client.data.model.PlayInfo
import com.biko.client.data.model.StreamTrack
import com.biko.client.player.buildLocalMpd
import com.biko.client.player.createDashMediaSource
import com.biko.client.ui.theme.Accent
import com.biko.client.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// ---------------------------------------------------------------------------
// 常量与文案
// ---------------------------------------------------------------------------

private val White = Color.White
private val Dim = Color(0xFF9AA0A6)
private val PanelBg = Color(0xF21C1C1E)
/** 选项选中态（品牌蓝）。 */
private val SelectedBlue = Accent
/** 未激活态：半透明白，与选中态区分。 */
private val InactiveWhite = Color(0xB3FFFFFF)

// 图标尺寸（对齐 BiliPai 手机档）：字形统一再压小一档，点按热区仍保持 30dp。
// 暂停三角字形只占画布 14/24，需放大到 22；全屏四角约 18/24，用 18 即可。
private val ICON_SLOT = 30.dp
private val CONTROL_ICON_PLAY = 22.dp
private val CONTROL_ICON_FULLSCREEN = 18.dp
private val CONTROL_ICON_SETTINGS = 16.dp
/** 顶栏图标（返回 / 主页 / 听视频 / 投屏 / 更多 / 分享）统一字号。 */
private val TOP_ICON = 19.dp
/** 顶栏图标之间的固定间距，保证左右两侧视觉间距一致。 */
private val TOP_ICON_GAP = 2.dp
/**
 * 顶栏左上角「返回 / 主页」单独放大：这两个图标原本太小、且与右侧图标不成比例。
 * 返回是窄字形（画布留白多）给更大尺寸，主页字形饱满给小一点，视觉上等大。
 */
private val TOP_ICON_BACK = 24.dp
private val TOP_ICON_HOME = 22.dp

/** 倍速候选（与 BiliPai 一致，覆盖到 3x）。 */
val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)

/** 长按临时倍速的默认档位。 */
private const val LONG_PRESS_SPEED = 2f

/** 未登录时能拿到的最低清晰度兜底。 */
private val DEFAULT_QUALITIES = listOf(80, 64, 32, 16)

/** 清晰度文案（B 站 qn → 显示名）。 */
fun qnLabel(qn: Int): String = when {
    qn >= 127 -> "8K"
    qn >= 126 -> "杜比视界"
    qn >= 125 -> "HDR"
    qn >= 120 -> "4K"
    qn >= 116 -> "1080P60"
    qn >= 112 -> "1080P+"
    qn >= 80 -> "1080P"
    qn >= 74 -> "720P60"
    qn >= 64 -> "720P"
    qn >= 32 -> "480P"
    else -> "360P"
}

/** 直播清晰度文案（B 站直播 qn → 显示名）。 */
fun liveQnLabel(qn: Int): String = when (qn) {
    30000 -> "杜比"
    20000 -> "4K"
    10000 -> "原画"
    400 -> "蓝光"
    250 -> "超清"
    150 -> "高清"
    else -> "原画"
}

/** 音频档位文案（B 站音频 id → 显示名）。 */
fun audioLabel(id: Int): String = when {
    id >= 30251 -> "Hi-Res"
    id >= 30250 -> "杜比全景声"
    id >= 30280 -> "192K"
    id >= 30232 -> "132K"
    else -> "64K"
}

/** 倍速按钮文案（对齐 BiliPai）：1x 显示「倍速」，其余显示具体倍率。 */
fun speedLabel(speed: Float): String =
    if (speed == 1f) "倍速" else formatSpeed(speed)

/** 倍速选项文案（对齐 BiliPai）：1x 显示「正常」。 */
fun speedLabelFull(speed: Float): String =
    if (speed == 1f) "正常" else formatSpeed(speed)

/** 倍率文案：整数去掉小数位（2x / 1.5x / 1.25x）。 */
private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

/** 画面比例。 */
enum class VideoAspect(val label: String) {
    FIT("适应屏幕"),
    FILL("填充屏幕"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    STRETCH("拉伸"),
}

/** 弹幕设置。 */
data class DanmakuSettings(
    val enabled: Boolean = true,
    val fontScale: Float = 1f,
    val alpha: Float = 1f,
    val speedFactor: Float = 1f,
    val displayArea: Float = 0.75f,
    val blockWords: String = "",
)

private const val PAGE_NONE = 0
private const val PAGE_MAIN = 1
private const val PAGE_SPEED = 2
private const val PAGE_AUDIO = 3
private const val PAGE_QUALITY = 4
private const val PAGE_ASPECT = 5
private const val PAGE_DANMAKU = 6
private const val PAGE_TIMER = 7
private const val PAGE_BACKGROUND = 8
private const val PAGE_AUDIO_MODE = 9

/**
 * 「小窗播放」图标：外框是主画面（圆角矩形描边），右下角浮出一个实心小窗，
 * 直观表达「主画面里弹出一个小窗口」。视口 24x24，光学尺寸与顶栏相邻图标接近。
 */
private val PipRectIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PipWindow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 主画面描边：外矩形 + 内矩形用 EvenOdd 挖空成描边
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            roundedRect(1.2f, 4f, 22.8f, 20f, 2.2f)
            roundedRect(3.2f, 6f, 20.8f, 18f, 1.2f)
        }
        // 右下角实心小窗（落在挖空区域内，单独填充覆盖上去）
        path(fill = SolidColor(Color.Black)) {
            roundedRect(13.4f, 12.6f, 19.8f, 16.9f, 0.9f)
        }
    }.build()
}

/** 在 vector DSL 中追加一个圆角矩形子路径（顺时针，四角用二次贝塞尔）。 */
private fun PathBuilder.roundedRect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float) {
    moveTo(x0 + r, y0)
    horizontalLineTo(x1 - r)
    quadTo(x1, y0, x1, y0 + r)
    verticalLineTo(y1 - r)
    quadTo(x1, y1, x1 - r, y1)
    horizontalLineTo(x0 + r)
    quadTo(x0, y1, x0, y1 - r)
    verticalLineTo(y0 + r)
    quadTo(x0, y0, x0 + r, y0)
    close()
}

/**
 * 「隐藏/显示图标层」图标（对齐 B 站同款）：两块竖放的圆角卡片并排，
 * 前面一块是白卡（亮起、完整），后面一块是灰卡（被前卡遮住一条边，呈半截描边）；
 * 白卡左下角两根短横线代表画面上的图标层。
 * 未隐藏时白卡在左（带横线），隐藏后白卡移到右侧且不带横线，灰卡留在左侧仍带横线。
 */
private val OverlayVisibleIcon: ImageVector by lazy { buildOverlayIcon(hidden = false) }
private val OverlayHiddenIcon: ImageVector by lazy { buildOverlayIcon(hidden = true) }

private val OverlayCardWhite = Color(0xFFFFFFFF)
private val OverlayCardGray = Color(0x8AFFFFFF)

private fun buildOverlayIcon(hidden: Boolean): ImageVector =
    ImageVector.Builder(
        name = if (hidden) "OverlayHidden" else "OverlayVisible",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val strokeW = 1.7f
        // 灰卡上的两根短横线：始终落在左侧卡片内
        val bars: ImageVector.Builder.() -> Unit = {
            path(fill = SolidColor(if (hidden) OverlayCardGray else OverlayCardWhite)) {
                roundedRect(5.4f, 13.5f, 8.6f, 15.1f, 0.8f)
                roundedRect(5.4f, 16.3f, 9.8f, 17.9f, 0.8f)
            }
        }
        if (hidden) {
            // 左：灰卡（右侧开口，只画上边 + 左侧圆角 + 下边）
            path(stroke = SolidColor(OverlayCardGray), strokeLineWidth = strokeW) {
                moveTo(12.0f, 4.8f)
                horizontalLineTo(5.9f)
                quadTo(3.6f, 4.8f, 3.6f, 7.1f)
                verticalLineTo(16.9f)
                quadTo(3.6f, 19.2f, 5.9f, 19.2f)
                horizontalLineTo(12.0f)
            }
            bars()
            // 右：白卡（完整圆角矩形描边）
            path(stroke = SolidColor(OverlayCardWhite), strokeLineWidth = strokeW) {
                roundedRect(12.0f, 3.6f, 20.4f, 20.4f, 2.3f)
            }
        } else {
            // 左：白卡（完整圆角矩形描边）
            path(stroke = SolidColor(OverlayCardWhite), strokeLineWidth = strokeW) {
                roundedRect(3.6f, 3.6f, 12.0f, 20.4f, 2.3f)
            }
            bars()
            // 右：灰卡（左侧开口，只画上边 + 右侧圆角 + 下边）
            path(stroke = SolidColor(OverlayCardGray), strokeLineWidth = strokeW) {
                moveTo(12.0f, 4.8f)
                horizontalLineTo(18.1f)
                quadTo(20.4f, 4.8f, 20.4f, 7.1f)
                verticalLineTo(16.9f)
                quadTo(20.4f, 19.2f, 18.1f, 19.2f)
                horizontalLineTo(12.0f)
            }
        }
    }.build()

private const val GESTURE_NONE = 0
private const val GESTURE_BRIGHTNESS = 1
private const val GESTURE_VOLUME = 2
private const val GESTURE_SEEK = 3

/**
 * 视频播放器（对齐 BiliPai 的播放区能力）：
 * - DASH 自适应播放，可切清晰度 / 音频轨（缺失时回退整段 MP4）；
 * - 手势：左侧亮度、右侧音量、横滑定位、双击快进快退、长按临时倍速；
 * - 控制条：播放/暂停、时间、弹幕开关、全屏、设置；
 * - 设置面板：速度 / 音频 / 清晰度 / 画面比例 / 弹幕 / 定时关闭 / 画中画。
 */
@Composable
fun BikoPlayer(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    /** 直播等简单场景直接给地址；与 [playInfo] 二选一。 */
    url: String? = null,
    referer: String = BILI_REFERER,
    /** 播放请求也带上会话 Cookie：部分清晰度/大会员线路会校验登录态。 */
    cookie: String = "",
    playInfo: PlayInfo? = null,
    title: String = "",
    quality: Int = 80,
    onSelectQuality: (Int) -> Unit = {},
    audioIndex: Int = 0,
    onSelectAudio: (Int) -> Unit = {},
    speed: Float = 1f,
    onSelectSpeed: (Float) -> Unit = {},
    aspect: VideoAspect = VideoAspect.FIT,
    onSelectAspect: (VideoAspect) -> Unit = {},
    /** 直播等场景可直接指定清晰度档位；留空时取自 [playInfo] 的 accept_quality。 */
    qualityOptions: List<Int> = emptyList(),
    /** 清晰度文案（直播用「原画 / 蓝光 / 超清 / 高清」）。 */
    qualityLabel: (Int) -> String = { qnLabel(it) },
    danmaku: List<DanmakuItem> = emptyList(),
    danmakuSettings: DanmakuSettings = DanmakuSettings(),
    onDanmakuSettingsChange: (DanmakuSettings) -> Unit = {},
    /** 特殊弹幕（Mode 7 高级 / Mode 9 BAS），与普通弹幕叠加渲染。 */
    specialDanmaku: SpecialDanmaku = SpecialDanmaku(),
    /** 续播起点（毫秒），仅在首次就绪时生效。 */
    resumePositionMs: Long = 0L,
    /** 周期性上报进度，用于写入播放记忆。 */
    onProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    /** 播放出错时回调（用于页面层面降级换线路）。 */
    onPlaybackError: (String) -> Unit = {},
    live: Boolean = false,
    /** 传 null 表示不支持画中画（如直播）。 */
    onPictureInPicture: (() -> Unit)? = null,
    /** 是否处于竖屏播放（铺满屏幕、保持竖屏）。 */
    portrait: Boolean = false,
    /** 传 null 表示不显示「竖屏播放」按钮（如直播）。 */
    onTogglePortrait: (() -> Unit)? = null,
    /** 竖屏播放时叠加在底部控制区上方的内容（UP 主信息、三连等），由调用方提供。 */
    portraitExtra: (@Composable () -> Unit)? = null,
    /** 竖屏播放时渲染在进度条之下的内容（发弹幕入口等）。 */
    portraitBottom: (@Composable () -> Unit)? = null,
    /** 竖屏模式下从设置面板进入横屏全屏；传 null 则不显示该入口。 */
    onEnterLandscape: (() -> Unit)? = null,
    /** 竖屏发弹幕输入激活时（键盘弹起到收起的全过程），隐藏顶栏与底部 UI。 */
    portraitInputActive: Boolean = false,
    /** 竖屏半屏弹层（评论）展开进度：0=收起，1=完全展开；画面下移让开顶栏，底部 UI 同时隐藏。 */
    portraitSheetProgress: Float = 0f,
    /** 顶栏主页按钮：为 null 时不显示。 */
    onHome: (() -> Unit)? = null,
    /** 顶栏分享按钮：为 null 时不显示。 */
    onShare: (() -> Unit)? = null,
    /** 顶栏返回按钮：详情页内嵌（非全屏/非竖屏）时返回上一层；为 null 时回退到退出全屏。 */
    onBack: (() -> Unit)? = null,
    /** 后台听视频开关状态：设置面板里显示当前取值。 */
    backgroundAudio: Boolean = false,
    /** 设置面板「后台听视频」子页的开关回调。 */
    onSetBackgroundAudio: ((Boolean) -> Unit)? = null,
    /** 纯音频模式：只下载/播放音频轨，画面上显示封面（省流量、省电）。 */
    audioOnly: Boolean = false,
    /** 纯音频模式显示的封面图。 */
    coverUrl: String? = null,
    /** 切换纯音频模式；为 null 时不显示该设置项。 */
    onSetAudioOnly: ((Boolean) -> Unit)? = null,
    /** 设置面板展开状态变化：竖屏下借此禁止 pager 与面板争抢上下滑手势。 */
    onPanelOpenChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 软键盘是否弹出（竖屏发弹幕时隐藏底部 UI）
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    var showControls by remember { mutableStateOf(true) }
    // 竖屏：是否隐藏画面上的「图标」层（顶栏 + 右侧三连竖排），由底部按钮控制
    var hideIcons by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(PAGE_NONE) }
    // 面板展开时把状态抛给外部，让竖屏 pager 先停手：在面板上上下滑只滚面板，不带动视频
    LaunchedEffect(page) { onPanelOpenChange?.invoke(page != PAGE_NONE) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var locked by remember { mutableStateOf(false) }
    var tempSpeedActive by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableStateOf(0) }
    var sleepLeftSec by remember { mutableStateOf(0) }

    // 手势状态
    var gestureMode by remember { mutableStateOf(GESTURE_NONE) }
    var gestureText by remember { mutableStateOf("") }
    var seekPreviewMs by remember { mutableStateOf(0L) }
    var brightness by remember { mutableStateOf(currentBrightness(activity)) }
    var volume by remember { mutableStateOf(currentVolume(context)) }

    // 长按快进/双击提示
    var flashText by remember { mutableStateOf<String?>(null) }

    // 视频实际像素尺寸，用于把弹幕限制在画面显示区域内（竖屏时画面上下会留黑边）
    var videoW by remember { mutableStateOf(0) }
    var videoH by remember { mutableStateOf(0) }

    val upstreamFactory = remember(referer, cookie) {
        val headers = mutableMapOf("Referer" to referer)
        if (cookie.isNotEmpty()) headers["Cookie"] = cookie
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BILI_PLAY_USER_AGENT)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        // 包一层 DefaultDataSource，让 file:// 的本地离线缓存也能走同一个数据源
        DefaultDataSource.Factory(context, httpFactory)
    }

    // ---- 媒体源 ----
    var resumedApplied by remember(playInfo) { mutableStateOf(false) }
    LaunchedEffect(playInfo, quality, audioIndex, url, audioOnly) {
        if (url != null && playInfo == null) {
            player.setMediaSource(
                DefaultMediaSourceFactory(upstreamFactory).createMediaSource(MediaItem.fromUri(url)),
            )
            player.prepare()
            player.playWhenReady = true
            return@LaunchedEffect
        }
        val info = playInfo ?: return@LaunchedEffect
        val keep = player.currentPosition.coerceAtLeast(0L)
        val dashSource = if (info.isDash) {
            val video = pickVideoTrack(info.videoTracks, quality)
            val audio = info.audioTracks.getOrNull(audioIndex)
                ?: info.audioTracks.maxByOrNull { it.bandwidth }
            if (audio != null && (audioOnly || video != null)) {
                createDashMediaSource(
                    buildLocalMpd(video, audio, info.durationMs, audioOnly).toByteArray(),
                    upstreamFactory,
                )
            } else null
        } else null

        val source = dashSource
            ?: info.durl?.takeIf { it.isNotEmpty() }?.let {
                DefaultMediaSourceFactory(upstreamFactory).createMediaSource(MediaItem.fromUri(it))
            }
        if (source == null) {
            flashText = "暂无可用播放地址"
            return@LaunchedEffect
        }

        player.setMediaSource(source)
        player.prepare()
        if (keep > 0) player.seekTo(keep)
        player.playWhenReady = true
    }

    // ---- 状态监听 ----
    DisposableEffect(player, live) {
        // 普通视频播完自动重播；直播是无限流，不能循环
        player.repeatMode = if (live) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                val d = player.duration
                if (d > 0) duration = d
                // 仅在真正从头播放时续播（全屏切换会重建组件，此时进度已非 0）
                if (state == Player.STATE_READY && !resumedApplied &&
                    resumePositionMs >= 5_000L && player.currentPosition < 3_000L
                ) {
                    resumedApplied = true
                    player.seekTo(resumePositionMs)
                    flashText = "已续播 ${clock(resumePositionMs)}"
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoW = videoSize.width
                videoH = videoSize.height
            }

            override fun onPlayerError(error: PlaybackException) {
                flashText = "播放失败：${error.errorCodeName}"
                onPlaybackError(error.errorCodeName)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // ---- 进度轮询 + 播放记忆上报 ----
    LaunchedEffect(player) {
        var tick = 0
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            if (d > 0) duration = d
            tick++
            if (tick % 10 == 0) onProgress(position, duration) // 约每 3 秒
            delay(300)
        }
    }

    LaunchedEffect(speed, tempSpeedActive) {
        player.setPlaybackSpeed(if (tempSpeedActive) LONG_PRESS_SPEED else speed)
    }

    // 播放中 4 秒后自动隐藏控制条（竖屏播放页不自动隐藏，只响应单点隐藏/恢复）
    LaunchedEffect(showControls, isPlaying, page) {
        if (showControls && isPlaying && page == PAGE_NONE && !locked && !(portrait && !fullscreen)) {
            delay(4000)
            showControls = false
        }
    }

    // 手势提示 1.2 秒后消失
    LaunchedEffect(flashText) {
        if (flashText != null) {
            delay(1200)
            flashText = null
        }
    }

    // 定时关闭
    LaunchedEffect(sleepMinutes) {
        sleepLeftSec = sleepMinutes * 60
        while (sleepMinutes > 0 && sleepLeftSec > 0) {
            delay(1000)
            sleepLeftSec--
        }
        if (sleepMinutes > 0 && sleepLeftSec <= 0) {
            player.pause()
            showControls = true
            flashText = "定时关闭已生效"
            sleepMinutes = 0
        }
    }

    val qualities = remember(playInfo, qualityOptions) {
        if (qualityOptions.isNotEmpty()) qualityOptions
        else (playInfo?.acceptQuality?.takeIf { it.isNotEmpty() } ?: DEFAULT_QUALITIES)
            .distinct().sortedDescending()
    }
    val audioTracks = playInfo?.audioTracks.orEmpty()

    BoxWithConstraints(modifier.background(Color.Black)) {
        // 弹幕区域横竖屏分开处理：
        // - 竖屏（容器比宽更高）：弹幕铺在上方黑边，且避开状态栏 + 标题栏（约 44dp），不压标题也不压画面
        // - 横屏：铺满整个容器（保持原始行为）
        val portraitBox = maxHeight > maxWidth
        val danmakuBoxModifier = when {
            // 竖屏强制按比例显示（画面留黑边），弹幕必须走下面的 portraitBox 分支铺在黑边上，
            // 不能因为选了「填充/拉伸」就铺满整屏，否则弹幕会压到画面外。
            !portraitBox && (aspect == VideoAspect.FILL || aspect == VideoAspect.STRETCH) ->
                Modifier.fillMaxSize()
            portraitBox -> {
                val videoRatio =
                    if (videoW > 0 && videoH > 0) videoW.toFloat() / videoH else 16f / 9f
                val barH = (maxHeight - maxWidth / videoRatio) / 2
                val base = Modifier.statusBarsPadding().padding(top = 44.dp)
                // 顶栏（状态栏 + 标题栏）占掉的高度，弹幕不能用
                val density = LocalDensity.current
                val topReserve =
                    with(density) { WindowInsets.statusBars.getTop(density).toDp() } + 44.dp
                if (barH > topReserve) {
                    // 非竖版比例视频：上方黑边足够，弹幕只铺在黑边内，
                    // 底边正好止于画面顶端，不再压到视频上。
                    base.fillMaxWidth().height(barH - topReserve).padding(top = 12.dp)
                        .align(Alignment.TopCenter)
                } else {
                    // 竖版视频占满全屏（或黑边过窄）：弹幕压画面，但同样避开标题栏
                    base.fillMaxSize()
                }
            }
            else -> Modifier.fillMaxSize()
        }

        // 竖屏评论半屏打开时：画面区整体下移，让出顶部状态栏与顶栏的位置，
        // 播放器容器由调用方收进弹层上方，这里只负责不压到上面的 UI。
        val sheetTopPadding = if (portrait && !fullscreen && portraitSheetProgress > 0.001f) {
            val density = LocalDensity.current
            val topUiPx = WindowInsets.statusBars.getTop(density) + with(density) { 48.dp.toPx() }
            Modifier.padding(top = with(density) { (topUiPx * portraitSheetProgress.coerceIn(0f, 1f)).toDp() })
        } else {
            Modifier
        }

        if (audioOnly) {
            // 纯音频模式：不渲染视频画面，用封面 + 标识占位（省流量、省电）
            Box(
                modifier = Modifier.fillMaxSize().then(sheetTopPadding),
                contentAlignment = Alignment.Center,
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    NetworkImage(
                        url = coverUrl,
                        modifier = Modifier.fillMaxSize().alpha(0.35f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "音频模式",
                        color = Color.White.copy(alpha = 0.9f),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                }
            }
        } else {
            AndroidView(
                // 用布局里的 TextureView 版 PlayerView：SurfaceView 不跟随 Compose 图层变换
                factory = { ctx ->
                    (android.view.LayoutInflater.from(ctx).inflate(R.layout.view_biko_player, null)
                        as PlayerView).apply {
                        this.player = player
                    }
                },
                update = {
                    it.player = player
                    it.resizeMode = when {
                        // 竖屏是整屏竖版容器：任何「填满」都会把画面拽变形。这里强制保持原始比例居中
                        // 显示（恢复竖屏原本的样子），不受「填充屏幕/拉伸」设置影响，杜绝蠢蠢的拉伸。
                        portrait -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        aspect == VideoAspect.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        // 只有用户显式选择「拉伸」时才真正拉伸（保留原有语义）。
                        aspect == VideoAspect.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        // 其余（FIT / 16:9 / 4:3）：保持比例、留黑边。
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize().then(sheetTopPadding),
            )
        }

        // 弹幕层（在控制层之下）：自己按帧取时间轴，避免跟随 300ms 轮询出现卡顿。
        // 直播没有播放时间轴，用「设备运行时刻」当时间基准（弹幕的 timeMs 即到达时刻）。
        DanmakuOverlay(
            items = danmaku,
            positionProvider = {
                if (live) SystemClock.elapsedRealtime() else player.currentPosition.coerceAtLeast(0L)
            },
            enabled = danmakuSettings.enabled && !audioOnly,
            fontScale = danmakuSettings.fontScale,
            alpha = danmakuSettings.alpha,
            speedFactor = danmakuSettings.speedFactor,
            // 竖屏弹幕只铺在上方黑边，区域内不做显示比例限制，整条黑边都能用
            displayArea = if (portrait && !fullscreen) 1f else danmakuSettings.displayArea,
            keywordBlock = danmakuSettings.blockWords,
            modifier = danmakuBoxModifier.then(sheetTopPadding),
        )

        // 特殊弹幕层（叠加在普通弹幕之上）：Mode 7 高级弹幕 + Mode 9 BAS 代码弹幕。
        SpecialDanmakuOverlay(
            advancedDanmaku = specialDanmaku.advanced,
            basDanmaku = specialDanmaku.bas,
            player = player,
            enabled = danmakuSettings.enabled && !live && !audioOnly,
            fontScale = danmakuSettings.fontScale,
            alpha = danmakuSettings.alpha,
            modifier = danmakuBoxModifier.then(sheetTopPadding),
        )

        // 手势层
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked, page) {
                    // 面板展开时仍要能点视频空白处收起面板，只是不再响应长按/双击快进
                    if (locked) return@pointerInput
                    detectTapGestures(
                        onPress = { _ ->
                            if (page == PAGE_NONE) {
                                val longPressed =
                                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        tryAwaitRelease()
                                    } == null
                                if (longPressed) {
                                    tempSpeedActive = true
                                    flashText = "长按加速 ${LONG_PRESS_SPEED}x"
                                    tryAwaitRelease()
                                    tempSpeedActive = false
                                }
                            }
                        },
                        onTap = {
                            when {
                                page != PAGE_NONE -> page = PAGE_NONE
                                // 竖屏：去掉单点切换控制层，改由底部「隐藏图标」按钮控制
                                portrait && !fullscreen -> Unit
                                else -> showControls = !showControls
                            }
                        },
                        onDoubleTap = {
                            when {
                                page != PAGE_NONE -> page = PAGE_NONE
                                // 双击任意位置 = 暂停/播放（不再用双击快进 / 快退）
                                else -> if (player.isPlaying) player.pause() else player.play()
                            }
                        },
                    )
                }
                .pointerInput(locked, page) {
                    // 竖屏模式：不消费视频区的拖动，让整屏的上下滑交给外层 pager 切视频
                    if (locked || page != PAGE_NONE || (portrait && !fullscreen)) return@pointerInput
                    var startX = 0f
                    var accX = 0f
                    var accY = 0f
                    var dragBase = 0L
                    detectDragGestures(
                        onDragStart = { offset ->
                            startX = offset.x
                            accX = 0f
                            accY = 0f
                            gestureMode = GESTURE_NONE
                            dragBase = player.currentPosition
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            accX += drag.x
                            accY += drag.y
                            if (gestureMode == GESTURE_NONE && (abs(accX) > 14f || abs(accY) > 14f)) {
                                gestureMode = when {
                                    // 直播不能拖动进度；竖屏播放只支持拖进度条，视频区横滑不 seek
                                    abs(accX) > abs(accY) -> if (live || portrait) GESTURE_NONE else GESTURE_SEEK
                                    // 竖屏播放：取消滑动调节亮度/音量
                                    portrait -> GESTURE_NONE
                                    startX < size.width / 3f -> GESTURE_BRIGHTNESS
                                    else -> GESTURE_VOLUME
                                }
                            }
                            when (gestureMode) {
                                GESTURE_BRIGHTNESS -> {
                                    brightness = (brightness - accY / size.height).coerceIn(0.01f, 1f)
                                    accY = 0f
                                    applyBrightness(activity, brightness)
                                    gestureText = "亮度 ${(brightness * 100).toInt()}%"
                                }
                                GESTURE_VOLUME -> {
                                    volume = (volume - accY / size.height).coerceIn(0f, 1f)
                                    accY = 0f
                                    applyVolume(context, volume)
                                    gestureText = "音量 ${(volume * 100).toInt()}%"
                                }
                                GESTURE_SEEK -> {
                                    val total = if (duration > 0) duration else 1L
                                    val delta = (accX / size.width * 120_000f).toLong()
                                    seekPreviewMs = (dragBase + delta).coerceIn(0L, total)
                                    gestureText =
                                        "${clock(seekPreviewMs)} / ${clock(total)}"
                                }
                            }
                        },
                        onDragEnd = {
                            if (gestureMode == GESTURE_SEEK) {
                                // 立即同步本地位置，避免 seek 缓冲期轮询回报旧进度导致进度条乱跳
                                position = seekPreviewMs
                                player.seekTo(seekPreviewMs)
                            }
                            gestureMode = GESTURE_NONE
                            gestureText = ""
                        },
                        onDragCancel = {
                            gestureMode = GESTURE_NONE
                            gestureText = ""
                        },
                    )
                },
        )

        if (showControls && !locked) {
            // 顶栏（对齐 BiliPai）：左「返回 + 主页」，右「听视频 + 投屏 + 更多 + 分享」。
            // 详情页内嵌、竖屏、横屏全屏三种模式统一显示；发弹幕输入态不再隐藏顶栏，
            // 上半部分 UI 保持可见。
            // 隐藏图标时顶栏一并收起，竖屏底部「隐藏/显示图标」按钮可切回
            if (!hideIcons) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        // 只有铺满屏幕（全屏/竖屏）时才避让状态栏；
                        // 详情页内嵌播放器位于状态栏占位之下，无需额外内边距。
                        .then(
                            if (fullscreen || portrait) Modifier.statusBarsPadding() else Modifier,
                        )
                        // 横屏全屏时屏幕两侧是 R 角，最边缘图标会被圆角裁到：
                        // 整体再向中间挪 10dp（竖屏与详情页内嵌不受影响）。
                        .padding(
                            horizontal = 8.dp + (if (fullscreen) 10.dp else 0.dp),
                            vertical = 4.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左侧：返回 + 主页（统一热区尺寸与间距）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TOP_ICON_GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 返回：全屏/竖屏退出大屏；详情页内嵌返回上一层
                        PlayerIconButton(
                            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            description = "返回",
                            size = TOP_ICON_BACK,
                            onClick = if (fullscreen || portrait) {
                                onToggleFullscreen
                            } else {
                                onBack ?: onToggleFullscreen
                            },
                        )
                        if (onHome != null) {
                            PlayerIconButton(
                                icon = Icons.Outlined.Home,
                                description = "主页",
                                size = TOP_ICON_HOME,
                                onClick = onHome,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // 右侧：听视频 + 小窗 + 更多 + 分享（与左侧同一套热区与间距）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TOP_ICON_GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 耳机：弹出「后台听视频」子菜单（总设置里也有一项入口）
                        if (!live && onSetBackgroundAudio != null) {
                            PlayerIconButton(
                                icon = if (backgroundAudio) Icons.Filled.Headphones
                                else Icons.Outlined.Headphones,
                                description = "后台听视频",
                                size = TOP_ICON,
                                tint = if (backgroundAudio) Accent else White,
                                onClick = { page = PAGE_BACKGROUND },
                            )
                        }
                        // 耳机、小窗两个图标字形都较宽，在 30dp 热区里的透明留白小，
                        // 视觉间距比右侧「更多 / 分享」（窄字形）窄，这里补一段间距拉齐。
                        if (!live && onPictureInPicture != null) {
                            Spacer(Modifier.width(4.dp))
                        }
                        if (onPictureInPicture != null) {
                            PlayerIconButton(
                                // 纯长方形：去掉电视图标下方的底座，避免被当成投屏
                                icon = PipRectIcon,
                                description = "小窗播放",
                                size = TOP_ICON,
                                onClick = onPictureInPicture,
                            )
                        }
                        // 更多：打开设置主面板
                        PlayerIconButton(
                            icon = Icons.Filled.MoreVert,
                            description = "更多",
                            size = TOP_ICON,
                            onClick = { page = PAGE_MAIN },
                        )
                        if (onShare != null) {
                            PlayerIconButton(
                                icon = Icons.Filled.Share,
                                description = "分享",
                                size = TOP_ICON,
                                onClick = onShare,
                            )
                        }
                    }
                }
            }

            // 控制层容器铺满整个播放器区域：设置面板贴右上角，底部控制行仍贴底居中。
            // 铺满后中间/左侧区域没有可滚动控件，面板打开时上下滑动仍能交给外层切成视频。
            Box(Modifier.fillMaxSize()) {
                if (page != PAGE_NONE) {
                    // 面板展开时隐藏进度条与「弹幕 / 全屏 / 齿轮」图标，只留设置面板
                    SettingsPanel(
                        page = page,
                        qualities = qualities,
                        quality = quality,
                        audioTracks = audioTracks,
                        audioIndex = audioIndex,
                        speed = speed,
                        aspect = aspect,
                        qualityLabel = qualityLabel,
                        danmakuSettings = danmakuSettings,
                        sleepMinutes = sleepMinutes,
                        sleepLeftSec = sleepLeftSec,
                        live = live,
                        pipAvailable = onPictureInPicture != null,
                        backgroundAudio = backgroundAudio,
                        onSetBackgroundAudio = { onSetBackgroundAudio?.invoke(it) },
                        audioOnly = audioOnly,
                        onSetAudioOnly = { onSetAudioOnly?.invoke(it) },
                        audioModeAvailable = onSetAudioOnly != null,
                        onPage = { page = it },
                        onSelectQuality = { onSelectQuality(it); page = PAGE_NONE },
                        onSelectAudio = { onSelectAudio(it); page = PAGE_NONE },
                        onSelectSpeed = { onSelectSpeed(it); page = PAGE_NONE },
                        onSelectAspect = { onSelectAspect(it); page = PAGE_NONE },
                        onDanmakuChange = onDanmakuSettingsChange,
                        onSelectTimer = { sleepMinutes = it; page = PAGE_NONE },
                        onPictureInPicture = {
                            page = PAGE_NONE
                            onPictureInPicture?.invoke()
                        },
                        portrait = portrait,
                        onEnterLandscape = onEnterLandscape?.let { f -> { page = PAGE_NONE; f() } },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            // 铺满屏幕时避让状态栏，再下移 44dp 让开顶栏图标行，贴右上角显示
                            .then(
                                if (fullscreen || portrait) Modifier.statusBarsPadding() else Modifier,
                            )
                            .padding(top = 44.dp, end = 8.dp),
                    )
                } else {
                    // 竖屏发弹幕输入态复用同一套底部 UI：只隐藏标题/进度/附加信息与右侧图标，
                    // 让发弹幕条在同一位置原地拉长、并随键盘上抬，避免切换瞬间先往下掉。
                    // 软键盘弹出（如评论输入）或弹层展开时整体隐藏，收起后恢复。
                    val dmInputMode = portrait && !fullscreen && portraitInputActive
                    val bottomHidden = !dmInputMode && portrait && !fullscreen &&
                        (imeVisible || portraitSheetProgress > 0.001f)
                    if (!bottomHidden) {
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                // 铺满屏幕时才避让系统栏：竖屏取「键盘 / 导航栏」中较大者，
                                // 键盘未弹起时即等于导航栏高度，保证输入条不会先往下掉；
                                // 详情页内嵌播放器让控制行与进度条紧贴视频底边。
                                .then(
                                    when {
                                        portrait && !fullscreen ->
                                            Modifier
                                                .windowInsetsPadding(
                                                    WindowInsets.ime.union(WindowInsets.navigationBars),
                                                )
                                                .padding(bottom = 16.dp)
                                        fullscreen -> Modifier.navigationBarsPadding()
                                        else -> Modifier
                                    },
                                ),
                        ) {
                        // 竖屏播放：UP 主信息 / 三连等附加内容叠在标题上方（隐藏图标时一起收起）
                        if (!dmInputMode && portrait && !fullscreen && portraitExtra != null && !hideIcons) {
                            portraitExtra()
                        }
                        // 竖屏播放：标题放到进度条上方
                        if (!dmInputMode && portrait && !fullscreen && title.isNotBlank()) {
                            Text(
                                text = title,
                                color = White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp)
                                    .padding(bottom = 4.dp),
                            )
                        }
                        if (portrait && !fullscreen) {
                            // 竖屏播放：进度条在标题与发弹幕入口之间。上方留 1dp、下方留 7dp
                            // （总高不变，标题不动），使进度条到标题与到发弹幕条的视觉间距相等。
                            if (!live && !dmInputMode) {
                                Box(Modifier.padding(top = 1.dp, bottom = 7.dp)) {
                                    PlayerProgress(
                                        // 拖动定位时进度条保持可见并实时预览目标位置
                                        position = if (gestureMode == GESTURE_SEEK) seekPreviewMs else position,
                                        duration = duration,
                                        onSeek = {
                                            // 立即同步本地位置，避免 seek 缓冲期进度条回跳
                                            position = it
                                            player.seekTo(it)
                                        },
                                        onDragPreview = { ms ->
                                            if (ms != null) {
                                                gestureMode = GESTURE_SEEK
                                                seekPreviewMs = ms
                                                gestureText =
                                                    "${clock(ms)} / ${clock(if (duration > 0) duration else 1L)}"
                                            } else {
                                                gestureMode = GESTURE_NONE
                                                gestureText = ""
                                            }
                                        },
                                    )
                                }
                            }
                            // 竖屏播放：发弹幕长药丸 + 右侧「隐藏/显示图标」开关（屏幕最底部）
                            // 进入输入态时右侧图标原地淡出收窄，药丸随之在原地拉长、左右对称居中
                            val dmRowEndPad by animateDpAsState(
                                targetValue = if (dmInputMode) 14.dp else 6.dp,
                                animationSpec = tween(durationMillis = 220),
                                label = "dmRowEndPad",
                            )
                            val dmIconSlot by animateDpAsState(
                                targetValue = if (dmInputMode) 0.dp else ICON_SLOT,
                                animationSpec = tween(durationMillis = 220),
                                label = "dmIconSlot",
                            )
                            val dmIconAlpha by animateFloatAsState(
                                targetValue = if (dmInputMode) 0f else 1f,
                                animationSpec = tween(durationMillis = 160),
                                label = "dmIconAlpha",
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = dmRowEndPad, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (portraitBottom != null) {
                                    Box(Modifier.weight(1f)) { portraitBottom() }
                                }
                                Box(Modifier.width(dmIconSlot).alpha(dmIconAlpha)) {
                                    PlayerIconButton(
                                        icon = if (hideIcons) OverlayHiddenIcon else OverlayVisibleIcon,
                                        description = if (hideIcons) "显示图标" else "隐藏图标",
                                        size = 23.dp,
                                        // 图标内含白卡 + 灰卡两种颜色，不能整体染色，保留矢量自带颜色
                                        tint = Color.Unspecified,
                                        onClick = { hideIcons = !hideIcons },
                                    )
                                }
                            }
                        } else {
                            // 详情页内嵌 / 大屏横屏：进度条在上、控制行在下
                            if (!live) {
                                Box(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
                                    PlayerProgress(
                                        position = if (gestureMode == GESTURE_SEEK) seekPreviewMs else position,
                                        duration = duration,
                                        onSeek = {
                                            position = it
                                            player.seekTo(it)
                                        },
                                        onDragPreview = { ms ->
                                            if (ms != null) {
                                                gestureMode = GESTURE_SEEK
                                                seekPreviewMs = ms
                                                gestureText =
                                                    "${clock(ms)} / ${clock(if (duration > 0) duration else 1L)}"
                                            } else {
                                                gestureMode = GESTURE_NONE
                                                gestureText = ""
                                            }
                                        },
                                    )
                                }
                            }
                            ControlRow(
                                isPlaying = isPlaying,
                                position = position,
                                duration = duration,
                                live = live,
                                fullscreen = fullscreen,
                                qualityText = qualityLabel(quality),
                                audioText = if (live) null else audioLabel(audioTracks.getOrNull(audioIndex)?.id ?: 0),
                                speedText = speedLabel(speed),
                                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                                onOpenAudio = { page = PAGE_AUDIO },
                                onOpenQuality = { page = PAGE_QUALITY },
                                onOpenSpeed = { page = PAGE_SPEED },
                                onToggleFullscreen = onToggleFullscreen,
                                onTogglePortrait = onTogglePortrait,
                                // 齿轮已移除（对齐 BiliPai）：设置入口统一走顶栏「更多」
                                showSettings = false,
                                onOpenSettings = { page = PAGE_MAIN },
                            )
                        }
                    }
                }
            }
        }
    }

        // 锁定态下的解锁入口：和锁定键同一位置（顶栏右上角）、同一风格
        if (locked) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    // 与顶栏一致：横屏全屏时 R 角会裁到图标，往中间挪 10dp
                    .padding(
                        horizontal = 8.dp + (if (fullscreen) 10.dp else 0.dp),
                        vertical = 4.dp,
                    ),
                horizontalArrangement = Arrangement.End,
            ) {
                PlayerIconButton(
                    icon = Icons.Filled.LockOpen,
                    description = "解锁",
                    size = TOP_ICON,
                    onClick = { locked = false },
                )
            }
        }

        // 手势 / 长按 / 续播提示（竖屏拖进度也在屏幕中央显示时间）
        val hint = gestureText.ifEmpty { flashText ?: "" }
        if (hint.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(hint, color = White, fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 手势辅助
// ---------------------------------------------------------------------------

private fun currentBrightness(activity: android.app.Activity?): Float {
    val value = activity?.window?.attributes?.screenBrightness ?: -1f
    return if (value < 0f) 0.5f else value
}

private fun applyBrightness(activity: android.app.Activity?, value: Float) {
    val window = activity?.window ?: return
    val params = window.attributes
    params.screenBrightness = value
    window.attributes = params
}

private fun currentVolume(context: android.content.Context): Float {
    val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager ?: return 0.5f
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun applyVolume(context: android.content.Context, value: Float) {
    val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager ?: return
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    am.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).toInt().coerceIn(0, max), 0)
}

// ---------------------------------------------------------------------------
// 进度条
// ---------------------------------------------------------------------------

/** 细进度条：蓝色轨道 + 蓝色圆点。按下不跟手，只按横向位移前后拖动定位。高度压到最低，减少对画面的遮挡。 */
@Composable
private fun PlayerProgress(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    /** 拖动过程中实时回调预览位置（毫秒），松手后回调 null。 */
    onDragPreview: (Long?) -> Unit = {},
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) position.toFloat() / duration else 0f
    // 手势闭包只随 duration 重建，进度值要实时读，避免按到旧值
    val latestPosition by rememberUpdatedState(position)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(duration) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 不跟手：按下时不把圆点跳到手指位置，而是记住按下点和当前进度，
                    // 之后按手指的横向位移相对增减，只做「前后拖动」。
                    val startX = down.position.x
                    val base = if (duration > 0) latestPosition.toFloat() / duration else 0f
                    dragging = base
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val f = (base + (change.position.x - startX) / size.width).coerceIn(0f, 1f)
                        dragging = f
                        if (duration > 0) onDragPreview((f * duration).toLong())
                        change.consume()
                    }
                    dragging?.let { f -> if (duration > 0) onSeek((f * duration).toLong()) }
                    dragging = null
                    onDragPreview(null)
                }
            },
    ) {
        val cy = size.height / 2f
        drawLine(SelectedBlue.copy(alpha = 0.35f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2.dp.toPx())
        if (fraction > 0f) {
            drawLine(SelectedBlue, Offset(0f, cy), Offset(size.width * fraction, cy), strokeWidth = 2.dp.toPx())
        }
        drawCircle(SelectedBlue, radius = 4.5.dp.toPx(), center = Offset(size.width * fraction, cy))
    }
}

// ---------------------------------------------------------------------------
// 控制条
// ---------------------------------------------------------------------------

/** 底部控制行（对齐 BiliPai）：播放/暂停 + 时间 | 音质 清晰度 倍速 竖屏 + [齿轮] + 全屏。 */
@Composable
private fun ControlRow(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    live: Boolean,
    fullscreen: Boolean,
    qualityText: String,
    audioText: String?,
    speedText: String,
    onTogglePlay: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onTogglePortrait: (() -> Unit)?,
    /** 是否显示齿轮设置入口（内嵌模式显示，全屏模式设置收进顶栏「更多」）。 */
    showSettings: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(
        // 控制行位于进度条下方：底部留 6dp，避免整行贴到视频底边
        // 横屏全屏时左右再各内缩 10dp，避免最左播放键 / 最右图标被 R 角裁切
        modifier = Modifier.fillMaxWidth().padding(
            start = 6.dp + (if (fullscreen) 10.dp else 0.dp),
            end = 6.dp + (if (fullscreen) 10.dp else 0.dp),
            bottom = 6.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ICON_SLOT)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = White,
                modifier = Modifier.size(CONTROL_ICON_PLAY),
            )
        }

        if (!live) {
            Text(
                text = "${clockFull(position)} / ${clockFull(duration)}",
                color = White,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 2.dp),
            )
            // 时间与右侧功能（音质 / 清晰度 / 倍速…）之间保底留白：
            // 即使行内空间紧张，也不会贴到一起（对齐 BiliPai 的 afterTimeSpacing）。
            Spacer(Modifier.width(14.dp))
        }

        Spacer(Modifier.weight(1f))

        if (audioText != null) ControlText(audioText, onOpenAudio)
        ControlText(qualityText, onOpenQuality)
        ControlText(speedText, onOpenSpeed)
        if (onTogglePortrait != null) {
            ControlText("竖屏", onTogglePortrait)
        }
        if (showSettings) {
            PlayerIconButton(
                icon = Icons.Filled.Settings,
                description = "设置",
                size = CONTROL_ICON_SETTINGS,
                onClick = onOpenSettings,
            )
        }
        PlayerIconButton(
            icon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            description = if (fullscreen) "退出全屏" else "全屏",
            size = CONTROL_ICON_FULLSCREEN,
            onClick = onToggleFullscreen,
        )
    }
}

/** 控制行上的纯文字按钮（音质 / 清晰度 / 倍速 / 竖屏）。 */
@Composable
private fun ControlText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 4.dp),
    )
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    description: String,
    size: Dp = CONTROL_ICON_FULLSCREEN,
    tint: Color = White,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ICON_SLOT)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

// ---------------------------------------------------------------------------
// 设置面板
// ---------------------------------------------------------------------------

@Composable
private fun SettingsPanel(
    page: Int,
    qualities: List<Int>,
    quality: Int,
    audioTracks: List<com.biko.client.data.model.StreamTrack>,
    audioIndex: Int,
    speed: Float,
    aspect: VideoAspect,
    qualityLabel: (Int) -> String,
    danmakuSettings: DanmakuSettings,
    sleepMinutes: Int,
    sleepLeftSec: Int,
    live: Boolean,
    pipAvailable: Boolean,
    backgroundAudio: Boolean,
    onSetBackgroundAudio: (Boolean) -> Unit,
    audioOnly: Boolean,
    onSetAudioOnly: (Boolean) -> Unit,
    audioModeAvailable: Boolean,
    onPage: (Int) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectAspect: (VideoAspect) -> Unit,
    onDanmakuChange: (DanmakuSettings) -> Unit,
    onSelectTimer: (Int) -> Unit,
    onPictureInPicture: () -> Unit,
    portrait: Boolean = false,
    onEnterLandscape: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val audioText = audioTracks.getOrNull(audioIndex)?.let { audioLabel(it.id) } ?: "自动"

    // 对齐 BiliPai：展开后是一张深色圆角卡片（小标题 + 选项列表），
    // 选中项右侧带勾选、不可用项灰显并附说明；不再铺满整条边缘。
    Column(
        // 面板已贴右上角，不再需要避让底部导航栏
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        PopupCard {
            when (page) {
                PAGE_MAIN -> {
                    PopupTitle("设置")
                    PopupNavRow("速度", speedLabel(speed)) { onPage(PAGE_SPEED) }
                    if (!live) PopupNavRow("音质", audioText) { onPage(PAGE_AUDIO) }
                    PopupNavRow("画质", qualityLabel(quality)) { onPage(PAGE_QUALITY) }
                    PopupNavRow("画面比例", aspect.label) { onPage(PAGE_ASPECT) }
                    PopupNavRow("弹幕设置", if (danmakuSettings.enabled) "已开启" else "已关闭") {
                        onPage(PAGE_DANMAKU)
                    }
                    PopupNavRow("定时关闭", timerSubtitle(sleepMinutes, sleepLeftSec)) {
                        onPage(PAGE_TIMER)
                    }
                    PopupNavRow("后台听视频", if (backgroundAudio) "开启" else "关闭") {
                        onPage(PAGE_BACKGROUND)
                    }
                    if (!live && audioModeAvailable) PopupNavRow("音频模式", if (audioOnly) "开启" else "关闭") {
                        onPage(PAGE_AUDIO_MODE)
                    }
                    if (pipAvailable) PopupNavRow("画中画", "小窗播放", onPictureInPicture)
                    // 竖屏模式下从「更多」进入横屏全屏（底部控制行已移除）
                    if (portrait && onEnterLandscape != null) {
                        PopupNavRow("横屏全屏", "切换横屏播放", onEnterLandscape)
                    }
                }

                PAGE_SPEED -> {
                    PopupTitle("播放速度", onBack = { onPage(PAGE_MAIN) })
                    // 与 BiliPai 一致：从慢到快倒序展示，1x 显示「正常」
                    SPEED_OPTIONS.asReversed().forEach { value ->
                        PopupOption(speedLabelFull(value), value == speed) { onSelectSpeed(value) }
                    }
                }

                PAGE_AUDIO -> {
                    PopupTitle("音质选择", onBack = { onPage(PAGE_MAIN) })
                    if (audioTracks.isEmpty()) {
                        PopupOption("自动", true) { onSelectAudio(0) }
                    } else {
                        audioTracks.forEachIndexed { index, track ->
                            PopupOption(audioLabel(track.id), index == audioIndex) { onSelectAudio(index) }
                        }
                    }
                }

                PAGE_QUALITY -> {
                    PopupTitle("画质选择", onBack = { onPage(PAGE_MAIN) })
                    qualities.forEach { value ->
                        PopupOption(qualityLabel(value), value == quality) { onSelectQuality(value) }
                    }
                }

                PAGE_ASPECT -> {
                    PopupTitle("画面比例", onBack = { onPage(PAGE_MAIN) })
                    VideoAspect.entries.forEach { value ->
                        PopupOption(value.label, value == aspect) { onSelectAspect(value) }
                    }
                }

                PAGE_DANMAKU -> {
                    PopupTitle("弹幕设置", onBack = { onPage(PAGE_MAIN) })
                    PopupOption(if (danmakuSettings.enabled) "弹幕：开" else "弹幕：关", danmakuSettings.enabled) {
                        onDanmakuChange(danmakuSettings.copy(enabled = !danmakuSettings.enabled))
                    }
                    PopupGroup("字号")
                    listOf(0.6f, 0.8f, 1f, 1.2f, 1.5f).forEach { value ->
                        PopupOption("${(value * 100).toInt()}%", danmakuSettings.fontScale == value) {
                            onDanmakuChange(danmakuSettings.copy(fontScale = value))
                        }
                    }
                    PopupGroup("透明度")
                    listOf(0.3f, 0.5f, 0.7f, 1f).forEach { value ->
                        PopupOption("${(value * 100).toInt()}%", danmakuSettings.alpha == value) {
                            onDanmakuChange(danmakuSettings.copy(alpha = value))
                        }
                    }
                    PopupGroup("速度")
                    listOf(0.8f, 1f, 1.2f, 1.5f).forEach { value ->
                        PopupOption("${value}x", danmakuSettings.speedFactor == value) {
                            onDanmakuChange(danmakuSettings.copy(speedFactor = value))
                        }
                    }
                    // 竖屏弹幕铺满上方黑边，不提供显示区域选项
                    if (!portrait) {
                        PopupGroup("显示区域")
                        listOf("1/4" to 0.25f, "1/2" to 0.5f, "全部" to 1f).forEach { (label, value) ->
                            PopupOption(label, danmakuSettings.displayArea == value) {
                                onDanmakuChange(danmakuSettings.copy(displayArea = value))
                            }
                        }
                    }
                }

                PAGE_TIMER -> {
                    PopupTitle("定时关闭", onBack = { onPage(PAGE_MAIN) })
                    listOf(0 to "关闭", 15 to "15 分钟", 30 to "30 分钟", 60 to "60 分钟").forEach { (min, label) ->
                        PopupOption(label, sleepMinutes == min) { onSelectTimer(min) }
                    }
                }

                PAGE_BACKGROUND -> {
                    PopupTitle("后台听视频", onBack = { onPage(PAGE_MAIN) })
                    PopupOption("关闭", !backgroundAudio) { onSetBackgroundAudio(false) }
                    PopupOption("开启", backgroundAudio) { onSetBackgroundAudio(true) }
                }

                PAGE_AUDIO_MODE -> {
                    PopupTitle("音频模式", onBack = { onPage(PAGE_MAIN) })
                    PopupOption("关闭", !audioOnly) { onSetAudioOnly(false) }
                    PopupOption("开启", audioOnly) { onSetAudioOnly(true) }
                }
            }
        }
    }
}

private fun timerSubtitle(minutes: Int, leftSec: Int): String = when {
    minutes <= 0 -> "未开启"
    leftSec <= 0 -> "即将关闭"
    else -> "${leftSec / 60}:${(leftSec % 60).toString().padStart(2, '0')}"
}

/** 展开面板：深色圆角卡片，内容超高时卡片内部滚动（对齐 BiliPai 的 ListPopup）。 */
@Composable
private fun PopupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 151.dp, max = 252.dp)
            .heightIn(max = 380.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PanelBg)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        content = content,
    )
}

/** 卡片小标题；带返回箭头时整行都是返回热区（对齐 BiliPai 的 SmallTitle）。 */
@Composable
private fun PopupTitle(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 返回热区扩大到整行、并保证至少 44dp 高，避免只点中小箭头才生效
            .then(if (onBack != null) Modifier.clickable(onClick = onBack) else Modifier)
            .heightIn(min = if (onBack != null) 44.dp else 0.dp)
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "返回",
                tint = White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
}

/** 分组小标题（弹幕设置里的「字号 / 透明度 / …」）。 */
@Composable
private fun PopupGroup(text: String) {
    Text(
        text = text,
        color = Dim,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** 子页面入口行：标题 + 当前取值 + 右箭头。 */
@Composable
private fun PopupNavRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = Dim, fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Dim,
            modifier = Modifier.padding(start = 6.dp).size(16.dp),
        )
    }
}

/** 卡片选项行（对齐 BiliPai）：选中主色 + 右侧勾选；不可用项灰显并附说明。 */
@Composable
private fun PopupOption(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    hint: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = when {
                    !enabled -> Color(0x61FFFFFF)
                    selected -> SelectedBlue
                    else -> InactiveWhite
                },
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
            if (hint != null) {
                Text(hint, color = Color(0x61FFFFFF), fontSize = 11.sp)
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = SelectedBlue,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 选视频轨：优先同清晰度；同一清晰度下优先 AVC（兼容性最好），
 * 没有 AVC 再退回码率最高的一条（可能是 HEVC / AV1）。
 */
private fun pickVideoTrack(tracks: List<StreamTrack>, quality: Int): StreamTrack? {
    val pool = tracks.filter { it.id == quality }.ifEmpty { tracks }
    return pool.firstOrNull { it.codecs.startsWith("avc1") } ?: pool.maxByOrNull { it.bandwidth }
}

/** 毫秒 → mm:ss。 */
private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

/** 毫秒 → 时长文案：不足 1 小时为 mm:ss，超过则带小时位 hh:mm:ss（对齐 BiliPai）。 */
private fun clockFull(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
