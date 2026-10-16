package com.biko.client.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.biko.client.data.BILI_REFERER
import com.biko.client.data.BILI_USER_AGENT
import com.biko.client.data.model.BangumiSeason
import com.biko.client.data.model.LiveRoom
import com.biko.client.data.model.Video
import com.biko.client.ui.theme.Accent
import com.biko.client.ui.theme.biko
import com.biko.client.ui.theme.rankColor
import com.biko.client.util.formatCount
import com.biko.client.util.formatDuration
import com.biko.client.util.formatTimeAgo

/** 带 Referer 的网络图片（B 站图床需要）。 */
@Composable
fun NetworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val colors = biko
    val context = LocalContext.current
    // 请求对象按 url 缓存：列表滚动时会频繁重组，避免每次都新建 ImageRequest
    val model = remember(url, context) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .addHeader("Referer", BILI_REFERER)
            .addHeader("User-Agent", BILI_USER_AGENT)
            .build()
    }
    if (url.isNullOrEmpty()) {
        Box(modifier.background(colors.card2))
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

/** 读取 assets 中的品牌图（biko_logo / biko_moon / biko_sun 等）。 */
@Composable
fun AssetImage(assetName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data("file:///android_asset/$assetName").build(),
        contentDescription = null,
        modifier = modifier,
    )
}

/** 时长角标（#99000000 底、白色 10.5sp）。 */
@Composable
fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        color = Color.White,
        modifier = modifier
            .background(Color(0x99000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * 卡片信息区文案（时长之外的统计与发布时间）。
 *
 * 组合规则只在这里维护一份：首页网格卡片、搜索/历史/排行行等所有卡片都消费同一实现，
 * 避免「同一个视频在不同页面显示的信息不一样」。规则对齐 BiliPai 的
 * resolveVideoCardStatsTexts：次统计优先评论数，其次弹幕数。
 */
data class VideoCardInfo(
    /** 「播放量 · 弹幕数」，为 0 的项省略（都没有时降级为观看进度，仍没有则为空串）。 */
    val stats: String,
    /** 相对发布时间（无发布时间时为空串）。 */
    val publish: String,
)

fun videoCardInfo(video: Video): VideoCardInfo {
    val stats = buildList {
        if (video.views > 0) add("${formatCount(video.views)}播放")
        val secondary = video.replies.takeIf { it > 0 } ?: video.danmaku
        if (secondary > 0) add("${formatCount(secondary)}弹幕")
        // 接口不给播放/弹幕数据时（如观看历史）退化成观看进度，避免信息区空着
        if (isEmpty() && video.progress > 0 && video.duration > 0) {
            add("已看 ${formatDuration(video.progress)}/${formatDuration(video.duration)}")
        }
    }.joinToString(" · ")
    return VideoCardInfo(stats = stats, publish = formatTimeAgo(video.pubdate))
}

/** 视频卡片（对应 item_video_card.xml）。长按可弹出「稍后再看 / 不感兴趣」。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onWatchLater: (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    /** 卡片底色。 */
    containerColor: Color = biko.card,
) {
    val colors = biko
    val info = videoCardInfo(video)
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = onWatchLater != null || onNotInterested != null

    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(containerColor)
                .bouncyCombinedClickable(
                    onClick = onClick,
                ),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                NetworkImage(url = video.pic, modifier = Modifier.fillMaxSize())
                if (video.duration > 0) {
                    DurationBadge(
                        text = formatDuration(video.duration),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    )
                }
            }
            Text(
                text = video.title,
                fontSize = 13.5.sp,
                color = colors.text,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 7.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = video.upName,
                    fontSize = 11.5.sp,
                    color = colors.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (info.publish.isNotEmpty()) {
                    Text(
                        text = info.publish,
                        fontSize = 11.sp,
                        color = colors.t3,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 3.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = info.stats,
                    fontSize = 11.5.sp,
                    color = colors.t3,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (hasMenu) {
                    Box {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = colors.t3,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable { menuOpen = true },
                        )
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            modifier = Modifier.width(88.dp),
                            containerColor = colors.card,
                        ) {
                            if (onWatchLater != null) {
                                DropdownMenuItem(
                                    text = { Text("稍后再看", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                    onClick = {
                                        menuOpen = false
                                        onWatchLater()
                                    },
                                )
                            }
                            if (onNotInterested != null) {
                                DropdownMenuItem(
                                    text = { Text("不感兴趣", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                    onClick = {
                                        menuOpen = false
                                        onNotInterested()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 排行榜 / 历史行（对应 item_rank_row.xml）。传 [onLongClick] 时开启长按菜单。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RankRow(
    video: Video,
    rank: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 非 null 时长按弹出菜单（如稍后再看的「移出」）。 */
    onLongClick: (() -> Unit)? = null,
    /** 长按菜单项文案。 */
    longClickLabel: String = "",
    /** 观看进度（秒），>0 时在封面底部显示进度条。 */
    progress: Int = 0,
    /** 是否在封面右下角显示时长角标。 */
    showDuration: Boolean = true,
    /** 非空时在行右下角显示三点图标，展开菜单项为「文案 to 动作」。 */
    trailingActions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val colors = biko
    val info = videoCardInfo(video)
    var menuOpen by remember { mutableStateOf(false) }
    var trailingOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyCombinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick?.let { { menuOpen = true } },
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(142.dp).aspectRatio(16f / 10f)) {
                NetworkImage(url = video.pic, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)))
                if (rank != null) {
                    // 名次角标：固定最小宽度并居中，保证 1 位数与 2/3 位数宽度一致、数字对齐不歪。
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(rankColor(rank))
                            .defaultMinSize(minWidth = 20.dp)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (rank + 1).toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (showDuration && video.duration > 0) {
                    DurationBadge(
                        text = formatDuration(video.duration),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    )
                }
                if (progress in 1 until video.duration) {
                    val fraction = progress.toFloat() / video.duration
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color.Black.copy(alpha = 0.35f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .height(2.5.dp)
                                .background(colors.acc),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Text(
                    text = video.upName,
                    fontSize = 12.sp,
                    color = colors.t2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
                Text(
                    text = listOf(info.stats, info.publish).filter { it.isNotEmpty() }.joinToString(" · "),
                    fontSize = 11.5.sp,
                    color = colors.t3,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            if (trailingActions.isNotEmpty()) {
                Box(Modifier.align(Alignment.Bottom)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = colors.t2,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(22.dp)
                            .clickable { trailingOpen = true },
                    )
                    DropdownMenu(
                        expanded = trailingOpen,
                        onDismissRequest = { trailingOpen = false },
                        modifier = Modifier.width(88.dp),
                        containerColor = colors.card,
                    ) {
                        trailingActions.forEach { (label, action) ->
                            DropdownMenuItem(
                                text = {
                                    Text(text = label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                },
                                onClick = {
                                    trailingOpen = false
                                    action()
                                },
                            )
                        }
                    }
                }
            }
        }

        if (onLongClick != null) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.width(88.dp),
                containerColor = colors.card,
            ) {
                DropdownMenuItem(
                    text = { Text(longClickLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    onClick = {
                        menuOpen = false
                        onLongClick()
                    },
                )
            }
        }
    }
}

/** 直播卡片（对应 item_live_card.xml：封面固定 100dp、左上「直播中」、右下人数）。 */
@Composable
fun LiveCard(
    room: LiveRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = biko.card,
) {
    val colors = biko
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(containerColor)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(100.dp)) {
            NetworkImage(url = room.cover, modifier = Modifier.fillMaxSize())
            Text(
                text = "直播中",
                fontSize = 9.5.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .background(colors.acc, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            val watchedLabel = when {
                // 优先用服务端排版的 text_large（如「2081人看过」），与 B 站 / 第三方客户端完全一致
                room.watchedText.isNotBlank() -> room.watchedText
                room.watchedShow > 0 -> "${formatCount(room.watchedShow)}人看过"
                // 拿不到「看过」时不再用实时人气冒充，直接不显示
                else -> ""
            }
            if (watchedLabel.isNotEmpty()) {
                Text(
                    text = watchedLabel,
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color(0x66000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            text = room.title,
            fontSize = 13.sp,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 7.dp),
        )
        Text(
            text = room.uname,
            fontSize = 11.5.sp,
            color = colors.t3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}

/** 视频详情底部互动项（图标 + 文案）。按下有弹性缩放 + 触觉反馈，计数变化时数字滚动切换。 */
@Composable
fun ActionItem(
    iconRes: Int,
    label: String,
    active: Boolean = false,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = biko
    val color = tint ?: if (active) colors.acc else colors.inactive
    // 点赞/投币/收藏激活时图标「弹一下」
    val iconPop = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            iconPop.snapTo(0.7f)
            iconPop.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                ),
            )
        }
    }
    Row(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.bouncyClickable(scaleDown = 0.9f, onClick = onClick)
            } else {
                Modifier
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = label,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = iconPop.value
                    scaleY = iconPop.value
                },
        )
        AnimatedCountText(text = label) { value ->
            Text(
                text = value,
                fontSize = 11.sp,
                color = if (active) colors.acc else colors.t3,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
    }
}

/** 频道标签栏（对应原版 Ui.buildChannelTabs：选中 acc 加粗 + 底部圆角下划线）。 */
@Composable
fun ChannelTabs(
    names: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = biko
    val density = LocalDensity.current
    // 记录每个标签的实测宽度（px），用于计算小蓝条位置与宽度
    val widths = remember(names) { mutableStateListOf<Int>().apply { repeat(names.size) { add(0) } } }
    var rowH by remember { mutableIntStateOf(0) }
    // 标签栏可视宽度（px），用于把屏幕外的选中标签滚回可见范围
    var viewport by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    // 蓝条跟随选中项用弹簧：切换时有回弹 + 中途横向拉伸，观感对齐详情页「简介/评论」标签
    val progress by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 900f),
        label = "channel_tab_indicator",
    )
    // 选中项自动滚进可视范围：滑动切到屏幕外的分区时，标签栏跟着滚，
    // 保证加粗文字和小蓝条都留在屏幕内（左右各留 24dp 余量）。
    LaunchedEffect(selected, viewport, widths.toList()) {
        if (viewport <= 0 || names.isEmpty() || widths.any { it <= 0 }) return@LaunchedEffect
        val index = selected.coerceIn(0, names.lastIndex)
        var start = 0
        for (k in 0 until index) start += widths[k]
        val end = start + widths[index]
        val margin = with(density) { 24.dp.toPx() }
        val target = when {
            start - margin < scroll.value -> start - margin
            end + margin > scroll.value + viewport -> end + margin - viewport
            else -> scroll.value.toFloat()
        }.coerceIn(0f, scroll.maxValue.toFloat())
        if (target.toInt() != scroll.value) scroll.animateScrollTo(target.toInt())
    }

    Box(modifier.fillMaxWidth().onSizeChanged { viewport = it.width }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .onSizeChanged { rowH = it.height },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            names.forEachIndexed { index, name ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .onSizeChanged { widths[index] = it.width }
                        .bouncyClickable(scaleDown = 0.96f, onClick = { onSelect(index) }),
                ) {
                    Text(
                        text = name,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) colors.acc else colors.inactive,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
                    )
                }
            }
        }
        // 小蓝条：从左到右在标签文字区间平滑滑动，宽度按各自文字宽度插值，切换途中横向拉伸
        if (rowH > 0 && names.isNotEmpty() && widths.all { it > 0 }) {
            val padPx = with(density) { 15.dp.toPx() }
            val p = progress.coerceIn(0f, (names.size - 1).toFloat())
            val i0 = kotlin.math.floor(p).toInt().coerceIn(0, names.size - 1)
            val i1 = (i0 + 1).coerceAtMost(names.size - 1)
            val f = p - i0
            fun centerOf(i: Int): Float {
                var acc = 0f
                for (k in 0 until i) acc += widths[k]
                return acc + widths[i] / 2f
            }
            val w0 = (widths[i0] - 2 * padPx).coerceAtLeast(0f)
            val w1 = (widths[i1] - 2 * padPx).coerceAtLeast(0f)
            val mid = 1f - kotlin.math.abs(f * 2f - 1f)
            val barW = (w0 + (w1 - w0) * f).coerceAtLeast(0f) * (1f + 0.22f * mid)
            val center = centerOf(i0) + (centerOf(i1) - centerOf(i0)) * f
            val left = center - barW / 2f - scroll.value
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.toInt(), rowH - 6.dp.roundToPx()) }
                    .width(with(density) { barW.toDp() })
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.acc),
            )
        }
    }
}

/** 番剧 / 影视卡片：竖版封面 + 更新进度/评分 + 标题。 */
@Composable
fun BangumiSeasonCard(
    season: BangumiSeason,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = biko.card,
) {
    val colors = biko
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(containerColor)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            NetworkImage(url = season.cover, modifier = Modifier.fillMaxSize())
            if (season.badge.isNotEmpty()) {
                Text(
                    text = season.badge,
                    fontSize = 10.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(bangumiBadgeColor(season.badge))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                )
            }
            if (season.subtitle.isNotEmpty()) {
                Text(
                    text = season.subtitle,
                    fontSize = 10.5.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = season.title,
            fontSize = 13.5.sp,
            color = colors.text,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        )
    }
}

/** 番剧角标底色：独家系用橙色、会员系用品牌蓝，其余用半透明黑。 */
private fun bangumiBadgeColor(badge: String): Color = when {
    badge.contains("独家") || badge.contains("独播") -> Color(0xFFF0772D)
    badge.contains("会员") -> Accent
    else -> Color(0xCC000000)
}
