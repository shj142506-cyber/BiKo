package com.biko.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biko.client.data.model.DanmakuItem
import kotlin.math.floor

/** 滚动弹幕单条飞行时长（毫秒），越大越慢。 */
private const val SCROLL_BASE_MS = 8000f

/** 顶部 / 底部弹幕停留时长。 */
private const val FIXED_MS = 4000f

/** 排版缓存上限，超出后整体清空（弹幕多时避免无限增长）。 */
private const val LAYOUT_CACHE_LIMIT = 3000

/** 播放位置单帧跳变阈值（毫秒）：超过即视为 seek / 续播 / 卡顿补帧。 */
private const val POSITION_JUMP_MS = 400L

/** 滚动弹幕轨道数上限（显示区域再大也不超过这个行数，避免满屏糊脸）。 */
private const val MAX_SCROLL_LANES = 12

/** 顶部 / 底部弹幕轨道数上限。 */
private const val MAX_FIXED_LANES = 4

private const val POOL_SCROLL = 0
private const val POOL_TOP = 1
private const val POOL_BOTTOM = 2

private class LaidDanmaku(
    val item: DanmakuItem,
    val textSizeSp: Float,
    val pool: Int,
    val lane: Int,
)

/** 轨道分配结果 + 时间轴（时间轴用于二分定位可视窗口）+ 同屏数量上限。 */
private class LaidDanmakuSet(
    val items: List<LaidDanmaku>,
    val times: LongArray,
    val maxOnScreen: Int,
)

/**
 * 弹幕渲染层：滚动（mode 1/2/3 从右向左飞行）、顶部（5）、底部（4）。
 *
 * 与 BiliPai（dss886 弹幕引擎）一致的防重叠策略：
 * - 同一条轨道里，上一条弹幕的「尾部完全进屏 + 间距」之后才允许放下一条；
 * - 新弹幕比上一条快时，按剩余飞行时间推算是否追上，追得上就不放；
 * - 所有轨道都不满足就丢弃该条，而不是堆到同一条轨道上；
 * - 同屏数量按轨道数动态封顶（每条约 2~3 条），不会出现整屏弹幕。
 *
 * 性能要点：
 * - 位置由 [positionProvider] 提供，层内按屏幕刷新率（帧回调）取值；
 * - 每帧只遍历「时间窗」内的弹幕（二分定位）。
 */
@Composable
fun DanmakuOverlay(
    items: List<DanmakuItem>,
    positionProvider: () -> Long,
    enabled: Boolean,
    fontScale: Float,
    alpha: Float,
    speedFactor: Float,
    displayArea: Float,
    keywordBlock: String = "",
    modifier: Modifier = Modifier,
) {
    if (!enabled || items.isEmpty()) return
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        // 行高比字号略大，减少上下贴太近的观感
        val laneHeightPx = with(density) { (16f * 1.5f).dp.toPx() } * fontScale
        val spToPx = with(density) { 1.sp.toPx() }
        // 同轨道相邻弹幕的最小间距（引擎默认 24px，这里用 12dp 折算）
        val marginPx = with(density) { 12.dp.toPx() }
        val scrollMs = (SCROLL_BASE_MS / speedFactor).coerceAtLeast(1200f)

        val laid = remember(items, widthPx, heightPx, fontScale, displayArea, keywordBlock, scrollMs) {
            assignLanes(
                items = items,
                widthPx = widthPx,
                heightPx = heightPx,
                laneHeightPx = laneHeightPx,
                fontScale = fontScale,
                displayArea = displayArea,
                keywordBlock = keywordBlock,
                scrollMs = scrollMs,
                spToPx = spToPx,
                marginPx = marginPx,
            )
        }
        val layoutCache = remember { HashMap<DanmakuItem, TextLayoutResult>() }
        val maxLifetime = if (scrollMs > FIXED_MS) scrollMs else FIXED_MS

        // 帧级位置：只驱动本层重绘，不牵动播放器其它 UI
        val provider by rememberUpdatedState(positionProvider)
        var drawPos by remember { mutableStateOf(provider()) }
        // 弹幕数据到达时记录当时的播放位置：早于该位置的弹幕不再「半途冒出」，
        // 只显示此后正常从右侧进入的弹幕。数据在播放前就绪（常见情况）时该值约为 0，不影响观感。
        var visibleFrom by remember(items) { mutableStateOf(provider()) }
        LaunchedEffect(Unit) {
            var prev = drawPos
            while (true) {
                withFrameNanos { }
                val next = provider()
                if (next != prev) {
                    // seek / 续播 / 卡顿时位置会突跳，重置入场基准，避免整批弹幕半途冒出
                    if (next < prev || next - prev > POSITION_JUMP_MS) visibleFrom = next
                    prev = next
                    drawPos = next
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val now = drawPos
            val list = laid.items
            val times = laid.times
            // 从最新一条往回遍历：超量时舍弃的是已飞到最左、即将离场的旧弹幕，
            // 而不是刚在屏幕右侧入场的新弹幕（否则新弹幕会「从中间冒出」）。
            val from = lowerBound(times, now - maxLifetime.toLong())
            var index = lowerBound(times, now + 1L) - 1
            var drawn = 0
            while (index >= from && drawn < laid.maxOnScreen) {
                val d = list[index]
                val time = times[index]
                index--
                // 数据晚到/续播时，已经过时的弹幕直接跳过，避免在屏幕中间整片冒出
                if (time < visibleFrom) continue
                val lifetime = if (d.pool == POOL_SCROLL) scrollMs else FIXED_MS
                val elapsed = now - time
                if (elapsed > lifetime) continue

                if (layoutCache.size > LAYOUT_CACHE_LIMIT) layoutCache.clear()
                val layout = layoutCache.getOrPut(d.item) {
                    measurer.measure(
                        text = d.item.text,
                        style = TextStyle(
                            fontSize = d.textSizeSp.sp,
                            color = Color(0xFF000000L or d.item.color.toLong()),
                            // 深色描边阴影，白字压在亮画面上也能看清
                            shadow = Shadow(
                                color = Color(0x99000000),
                                offset = Offset(1f, 1f),
                                blurRadius = 2f,
                            ),
                        ),
                        constraints = Constraints(),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                val w = layout.size.width.toFloat()
                val x = when (d.pool) {
                    POOL_SCROLL -> size.width - (size.width + w) * (elapsed / lifetime)
                    else -> (size.width - w) / 2f
                }
                val y = when (d.pool) {
                    POOL_BOTTOM -> size.height - (d.lane + 1) * laneHeightPx
                    else -> d.lane * laneHeightPx
                }
                drawText(layout, topLeft = Offset(x, y), alpha = alpha)
                drawn++
            }
        }
    }
}

/** 找到第一个 `>= value` 的位置（[times] 已按时间升序）。 */
private fun lowerBound(times: LongArray, value: Long): Int {
    var lo = 0
    var hi = times.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (times[mid] < value) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * 轨道分配（离线预排，items 已按时间升序）：
 * 每条滚动轨道记录上一条弹幕的出场时间 / 估算宽度 / 速度，
 * 新弹幕进轨道前做「尾部进屏 + 防追上」两项检查，任一不满足就换下一条轨道；
 * 所有轨道都排不下就丢弃。宽度按字符类型估算（汉字 1em，ASCII 约 0.58em），
 * 并换算成像素，保证不同屏幕密度下避让关系一致。
 */
private fun assignLanes(
    items: List<DanmakuItem>,
    widthPx: Float,
    heightPx: Float,
    laneHeightPx: Float,
    fontScale: Float,
    displayArea: Float,
    keywordBlock: String,
    scrollMs: Float,
    spToPx: Float,
    marginPx: Float,
): LaidDanmakuSet {
    if (widthPx <= 0f || laneHeightPx <= 0f) return LaidDanmakuSet(emptyList(), LongArray(0), 0)
    val blockWords = keywordBlock
        .split(' ', '\n', ',', '，', '|')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val scrollLanes = floor(heightPx * displayArea / laneHeightPx)
        .toInt().coerceIn(1, MAX_SCROLL_LANES)
    val fixedLanes = floor(heightPx * 0.35f / laneHeightPx)
        .toInt().coerceIn(1, MAX_FIXED_LANES)

    // 滚动轨道状态：上一条弹幕的出场时间 / 估算宽度 / 速度（px/ms）
    val lastStart = LongArray(scrollLanes) { Long.MIN_VALUE }
    val lastWidth = FloatArray(scrollLanes)
    val lastSpeed = FloatArray(scrollLanes)
    // 固定弹幕轨道只记下一条可用时间（不移动，互不追上）
    val topFree = LongArray(fixedLanes)
    val bottomFree = LongArray(fixedLanes)

    val result = ArrayList<LaidDanmaku>(512)
    val times = ArrayList<Long>(512)

    items.forEach { item ->
        // Mode 7/8/9 是特殊弹幕（高级/BAS/丢弃），由 SpecialDanmakuOverlay 单独渲染，这里跳过
        if (item.mode == 7 || item.mode == 8 || item.mode == 9) return@forEach
        if (blockWords.any { item.text.contains(it, ignoreCase = true) }) return@forEach
        val pool = when (item.mode) {
            4 -> POOL_BOTTOM
            5 -> POOL_TOP
            else -> POOL_SCROLL
        }
        val textSizeSp = (15f * (item.fontSize / 25f) * fontScale).coerceIn(8f, 40f)

        val lane = when (pool) {
            POOL_SCROLL -> {
                val fontPx = textSizeSp * spToPx
                var units = 0f
                val text = item.text
                for (i in text.indices) units += if (text[i].code > 0x2E7F) 1f else 0.58f
                val estimated = units * fontPx + marginPx
                val speed = (widthPx + estimated) / scrollMs
                val t = item.timeMs

                var best = -1
                for (laneIdx in 0 until scrollLanes) {
                    val start = lastStart[laneIdx]
                    if (start == Long.MIN_VALUE) {
                        best = laneIdx
                        break
                    }
                    val prevSpeed = lastSpeed[laneIdx]
                    val prevWidth = lastWidth[laneIdx]
                    val dt = (t - start).toFloat()
                    // 上一条尾部距右缘的距离（<0 表示尾部还没完全进屏）
                    val gap = prevSpeed * dt - prevWidth
                    // 条件一：尾部完全进屏并留出间距
                    if (gap < marginPx) continue
                    // 条件二：新弹幕更快时，全程都不能追上上一条
                    if (speed > prevSpeed && gap - (speed - prevSpeed) * scrollMs < marginPx) continue
                    best = laneIdx
                    break
                }
                if (best < 0) return@forEach
                lastStart[best] = t
                lastWidth[best] = estimated
                lastSpeed[best] = speed
                best
            }
            POOL_TOP -> {
                var best = -1
                for (i in topFree.indices) {
                    if (topFree[i] <= item.timeMs) {
                        best = i
                        break
                    }
                }
                if (best < 0) return@forEach
                topFree[best] = item.timeMs + FIXED_MS.toLong()
                best
            }
            else -> {
                var best = -1
                for (i in bottomFree.indices) {
                    if (bottomFree[i] <= item.timeMs) {
                        best = i
                        break
                    }
                }
                if (best < 0) return@forEach
                bottomFree[best] = item.timeMs + FIXED_MS.toLong()
                best
            }
        }
        result += LaidDanmaku(item, textSizeSp, pool, lane)
        times += item.timeMs
    }

    // 同屏上限：每条滚动轨道约 2~3 条 + 固定弹幕轨道各占 1~2 条，总量受限就不会「过多」
    val maxOnScreen = (scrollLanes * 3 + fixedLanes * 2).coerceAtLeast(8)
    val timeArray = LongArray(times.size) { times[it] }
    return LaidDanmakuSet(result, timeArray, maxOnScreen)
}
