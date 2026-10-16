package com.biko.client.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.android.purebilibili.danmaku.parser.AdvancedDanmakuData
import com.android.purebilibili.danmaku.parser.BasPathPoint
import com.android.purebilibili.danmaku.parser.BasScenePainter
import com.android.purebilibili.danmaku.parser.bas.BasDanmaku
import com.android.purebilibili.danmaku.parser.bas.BasTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

/** BAS 场景用户字号权重（与普通弹幕一致的默认值）。 */
private const val BAS_FONT_WEIGHT = 5

/**
 * 特殊弹幕渲染层：Mode 7（高级定位弹幕）+ Mode 9（BAS 代码弹幕）。
 *
 * 与普通弹幕层共存，叠加在播放器画面之上：
 * - Mode 7 用 Compose 逐帧推算位置/透明度/旋转后绘制单条文本；
 * - Mode 9 交由 [BasOverlayView]（原生 Canvas + 三轴旋转/透视）绘制，并支持点击交互。
 *
 * 弹幕作者坐标已归一化到 0~1，这里乘以实际画面尺寸还原。
 */
@Composable
fun SpecialDanmakuOverlay(
    advancedDanmaku: List<AdvancedDanmakuData>,
    basDanmaku: List<BasDanmaku>,
    player: Player,
    enabled: Boolean,
    fontScale: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
    renderingPaused: Boolean = false,
) {
    if (!enabled) return
    if (advancedDanmaku.isEmpty() && basDanmaku.isEmpty()) return

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        if (advancedDanmaku.isNotEmpty()) {
            AdvancedDanmakuLayer(
                danmakuList = advancedDanmaku,
                player = player,
                widthPx = widthPx,
                heightPx = heightPx,
                opacity = alpha,
                fontScale = fontScale,
                renderingPaused = renderingPaused,
            )
        }
        if (basDanmaku.isNotEmpty()) {
            BasDanmakuLayer(
                items = basDanmaku,
                player = player,
                widthPx = widthPx,
                heightPx = heightPx,
                opacity = alpha,
                fontScale = fontScale,
                renderingPaused = renderingPaused,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Mode 7：Compose 直接绘制
// ---------------------------------------------------------------------------

@Composable
private fun AdvancedDanmakuLayer(
    danmakuList: List<AdvancedDanmakuData>,
    player: Player,
    widthPx: Float,
    heightPx: Float,
    opacity: Float,
    fontScale: Float,
    renderingPaused: Boolean,
) {
    // 逐帧跟随播放进度；无活跃弹幕时降频，避免空转。
    val currentPosition by produceState(
        initialValue = player.currentPosition,
        key1 = player,
        key2 = danmakuList,
        key3 = renderingPaused,
    ) {
        if (renderingPaused) return@produceState
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                value = newPosition.positionMs
            }
        }
        player.addListener(listener)
        try {
            while (isActive) {
                val pos = player.currentPosition
                if (player.isPlaying) {
                    value = pos
                    val hasActive = danmakuList.any {
                        pos >= it.startTimeMs - 500 && pos <= it.startTimeMs + it.durationMs + 200
                    }
                    delay(if (hasActive) 16 else 250)
                } else {
                    value = pos
                    delay(300)
                }
            }
        } finally {
            player.removeListener(listener)
        }
    }

    val active by remember(danmakuList, currentPosition) {
        derivedStateOf {
            danmakuList.filter {
                currentPosition >= it.startTimeMs - 100 &&
                    currentPosition <= it.startTimeMs + it.durationMs + 100
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        active.forEach { danmaku ->
            key(danmaku.id) {
                RenderAdvancedDanmaku(
                    danmaku = danmaku,
                    currentPosition = currentPosition,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    opacity = opacity,
                    fontScale = fontScale,
                )
            }
        }
    }
}

@Composable
private fun RenderAdvancedDanmaku(
    danmaku: AdvancedDanmakuData,
    currentPosition: Long,
    widthPx: Float,
    heightPx: Float,
    opacity: Float,
    fontScale: Float,
) {
    // 高能弹幕（maxCount > 1）在积累阶段显示动态数量。
    val displayText = if (danmaku.maxCount > 1) {
        val elapsed = currentPosition - danmaku.startTimeMs
        if (elapsed < danmaku.accumulationDurationMs && danmaku.accumulationDurationMs > 0) {
            val currentCount =
                (1 + (danmaku.maxCount - 1) * (elapsed.toFloat() / danmaku.accumulationDurationMs)).toInt()
            "${danmaku.content} ×$currentCount"
        } else {
            "${danmaku.content} ×${danmaku.maxCount}"
        }
    } else {
        danmaku.content
    }

    val easedProgress = danmaku.easing.transform(danmaku.getTranslationProgress(currentPosition))
    val position: BasPathPoint = if (danmaku.path.isNotEmpty()) {
        danmaku.getPathPointAt(easedProgress)
    } else {
        BasPathPoint(
            x = danmaku.startX + (danmaku.endX - danmaku.startX) * easedProgress,
            y = danmaku.startY + (danmaku.endY - danmaku.startY) * easedProgress,
        )
    }

    val xPx = (position.x * widthPx).roundToInt()
    val yPx = (position.y * heightPx).roundToInt()
    val currentAlpha = danmaku.getAlphaAt(currentPosition)

    // 高能弹幕积累阶段做心跳缩放。
    val isAccumulating = danmaku.maxCount > 1 &&
        currentPosition < danmaku.startTimeMs + danmaku.accumulationDurationMs
    val scale = if (isAccumulating) {
        val pulsePhase = (currentPosition % 300) / 300f
        if (pulsePhase < 0.5f) 1.0f + 0.3f * (pulsePhase * 2) else 1.3f - 0.3f * ((pulsePhase - 0.5f) * 2)
    } else {
        1.0f
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            .alpha((currentAlpha * opacity).coerceIn(0f, 1f))
            .rotate(danmaku.rotateZ)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Text(
            text = displayText,
            color = Color(danmaku.color or 0xFF000000.toInt()),
            style = TextStyle(
                fontSize = (danmaku.fontSize * fontScale.coerceIn(0.3f, 2f)).sp,
                fontWeight = FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Mode 9：BAS 原生场景（AndroidView）
// ---------------------------------------------------------------------------

@Composable
private fun BasDanmakuLayer(
    items: List<BasDanmaku>,
    player: Player,
    widthPx: Float,
    heightPx: Float,
    opacity: Float,
    fontScale: Float,
    renderingPaused: Boolean,
) {
    val context = LocalContext.current
    val view = remember(context, player) { BasOverlayView(context, player) }

    DisposableEffect(view) {
        onDispose { view.clear() }
    }

    AndroidView(
        factory = { view },
        modifier = Modifier.fillMaxSize(),
        update = {
            it.configure(items, widthPx, heightPx, opacity, fontScale, BAS_FONT_WEIGHT)
        },
    )

    LaunchedEffect(view, items, widthPx, heightPx, renderingPaused) {
        if (renderingPaused || items.isEmpty()) return@LaunchedEffect
        while (isActive) {
            val position = player.currentPosition
            view.frame(position)
            if (view.hasActiveScene && player.isPlaying) {
                withFrameNanos { }
            } else {
                val next = items.asSequence().map { it.startTimeMs }.filter { it > position }.minOrNull()
                val wait = if (next == null) 250L else (next - position).coerceIn(1L, 250L)
                delay(wait)
            }
        }
    }
}

/** Mode 9 是保留场景的原生多对象绘制，不是 Mode 7 文本数组。 */
private class BasOverlayView(context: Context, private val player: Player) : View(context) {
    private var items: List<BasDanmaku> = emptyList()
    private val painters = java.util.IdentityHashMap<BasDanmaku, BasScenePainter>()
    private val active = ArrayList<BasScenePainter>()
    private var widthPx = 0f
    private var heightPx = 0f
    private var opacity = 1f
    private var fontScale = 1f
    private var fontWeight = BAS_FONT_WEIGHT
    private var pressed: BasTarget? = null
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val hasActiveScene: Boolean get() = active.isNotEmpty()

    init {
        setWillNotDraw(false)
    }

    fun configure(
        items: List<BasDanmaku>,
        widthPx: Float,
        heightPx: Float,
        opacity: Float,
        fontScale: Float,
        fontWeight: Int,
    ) {
        if (this.items !== items) {
            this.items = items
            val iterator = painters.keys.iterator()
            while (iterator.hasNext()) {
                val cached = iterator.next()
                if (items.none { it === cached }) iterator.remove()
            }
        }
        this.widthPx = widthPx
        this.heightPx = heightPx
        this.opacity = opacity
        this.fontScale = fontScale
        this.fontWeight = fontWeight
    }

    fun frame(positionMs: Long) {
        if (widthPx <= 0f || heightPx <= 0f) return
        active.clear()
        for (index in items.indices) {
            val item = items[index]
            val relative = positionMs - item.startTimeMs
            if (relative < 0L || relative >= item.durationMs) continue
            val painter = painters.getOrPut(item) { BasScenePainter(item) }
            painter.update(relative, widthPx, heightPx, opacity, fontScale, fontWeight)
            active.add(painter)
        }
        if (painters.size > 64) {
            val iterator = painters.values.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() !in active) iterator.remove()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (widthPx <= 0f || heightPx <= 0f) return
        val save = canvas.save()
        canvas.clipRect(0f, 0f, widthPx, heightPx)
        for (index in active.indices) active[index].draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun hit(x: Float, y: Float): BasTarget? {
        if (x < 0f || y < 0f || x > widthPx || y > heightPx) return null
        for (i in active.indices.reversed()) active[i].hit(x, y)?.let { return it }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = hit(event.x, event.y) ?: return false
                downX = event.x; downY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) pressed = null
            }
            MotionEvent.ACTION_UP -> {
                val target = pressed
                pressed = null
                if (target != null && hit(event.x, event.y) == target) {
                    performClick()
                    activate(target)
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> pressed = null
            else -> return pressed != null
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun activate(target: BasTarget) {
        if (target is BasTarget.Seek) {
            player.seekTo(target.timeMs)
            return
        }
        val url = when (target) {
            is BasTarget.Video -> {
                val video = target.bvid ?: target.aid?.let { "av$it" } ?: return
                Uri.Builder().scheme("https").authority("www.bilibili.com")
                    .appendPath("video").appendPath(video)
                    .appendQueryParameter("p", target.page.toString())
                    .appendQueryParameter("t", seconds(target.timeMs)).build()
            }
            is BasTarget.Bangumi -> {
                val episode = target.episodeId?.let { "ep$it" }
                    ?: target.seasonId?.let { "ss$it" } ?: return
                Uri.Builder().scheme("https").authority("www.bilibili.com")
                    .appendPath("bangumi").appendPath("play").appendPath(episode)
                    .appendQueryParameter("t", seconds(target.timeMs)).build()
            }
            is BasTarget.Seek -> return
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            if (it is ActivityNotFoundException) {
                Toast.makeText(context, "无法打开弹幕链接", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun seconds(timeMs: Long): String =
        java.math.BigDecimal.valueOf(timeMs, 3).stripTrailingZeros().toPlainString()

    fun clear() {
        pressed = null
        active.clear()
        painters.clear()
    }
}
