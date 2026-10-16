package com.biko.client.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.biko.client.data.model.Comment
import com.biko.client.data.model.CommentPicture
import com.biko.client.ui.theme.Level2
import com.biko.client.ui.theme.Level3
import com.biko.client.ui.theme.Level4
import com.biko.client.ui.theme.Level5
import com.biko.client.ui.theme.Level6
import com.biko.client.ui.theme.biko
import com.biko.client.util.formatCount
import com.biko.client.util.formatTimeAgo

/** B 站评论举报原因码（与 BiliPai 的 reportComment 注释一致）。 */
private val COMMENT_REPORT_REASONS = listOf(
    "垃圾广告" to 1,
    "色情低俗" to 2,
    "刷屏" to 3,
    "引战" to 4,
    "剧透" to 5,
    "政治敏感" to 6,
    "人身攻击" to 7,
    "其他" to 0,
)

/** 评论正文里的可点内容（与 BiliPai 的富文本规则一致）。 */
private val COMMENT_URL_PATTERN =
    Regex("""(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])""")
private val COMMENT_BVID_PATTERN =
    Regex("""(?<![A-Za-z0-9])BV[a-zA-Z0-9]{10}(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
private val COMMENT_TOPIC_PATTERN = Regex("""#([^#\s]{1,30})#""")
private val COMMENT_MENTION_PATTERN = Regex("""@([^\s@]{1,20})""")

private val CommentLinkColor = Color(0xFF00AEEC)

/** 评论正文里的表情 token，形如「[doge]」。 */
private val COMMENT_EMOTE_PATTERN = Regex("""\[(.*?)\]""")

/**
 * 把评论正文解析成富文本：链接 / bvid / #话题# / @用户 都可点，
 * 表情 token（如 [doge]）替换为内联表情图。
 */
private fun buildRichComment(comment: Comment): AnnotatedString = buildAnnotatedString {
    val msg = comment.message
    val emotes = comment.emotes
    // 原始字符下标 → 构建后下标（表情 token 被压缩成 1 个占位符）
    val rawToOut = IntArray(msg.length + 1)
    var outLen = 0
    var i = 0
    while (i < msg.length) {
        val m = if (emotes.isNotEmpty()) COMMENT_EMOTE_PATTERN.find(msg, i) else null
        if (m != null && m.range.first == i && emotes.containsKey(m.value)) {
            rawToOut[i] = outLen
            appendInlineContent(m.value, m.value)
            for (k in i + 1..m.range.last) rawToOut[k] = outLen
            outLen += 1
            rawToOut[m.range.last + 1] = outLen
            i = m.range.last + 1
        } else {
            rawToOut[i] = outLen
            append(msg[i])
            outLen += 1
            i += 1
        }
    }
    rawToOut[msg.length] = outLen

    fun link(range: IntRange, tag: String, value: String) {
        val s = rawToOut.getOrElse(range.first) { outLen }
        val e = rawToOut.getOrElse(range.last + 1) { outLen }
        if (s < e) {
            addStyle(SpanStyle(color = CommentLinkColor), s, e)
            addStringAnnotation(tag, value, s, e)
        }
    }
    COMMENT_URL_PATTERN.findAll(msg).forEach { link(it.range, "url", it.value) }
    COMMENT_BVID_PATTERN.findAll(msg).forEach { link(it.range, "bvid", it.value) }
    COMMENT_TOPIC_PATTERN.findAll(msg).forEach { link(it.range, "topic", it.groupValues[1]) }
    if (comment.atUserMids.isNotEmpty()) {
        COMMENT_MENTION_PATTERN.findAll(msg).forEach { match ->
            val name = match.groupValues[1]
            val mid = comment.atUserMids[name] ?: return@forEach
            link(match.range, "user", "$mid#$name")
        }
    }
}

/**
 * 评论正文：可点跳转的富文本 + 长按弹菜单。
 *
 * 这里用普通 Text 自己接手势，而不是 ClickableText —— ClickableText 会消费长按事件，
 * 导致长按正文弹不出操作菜单（只有非文本区域能弹）。
 */
@Composable
private fun RichCommentText(
    comment: Comment,
    color: Color,
    onOpenBvid: ((String) -> Unit)?,
    onOpenUser: ((Long, String) -> Unit)?,
    onOpenTopic: ((String) -> Unit)?,
    onLongPress: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val annotated = remember(comment.message, comment.atUserMids, comment.emotes) { buildRichComment(comment) }
    val inlineContent = remember(comment.emotes) {
        comment.emotes.mapValues { (token, url) ->
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
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = TextStyle(fontSize = 14.sp, color = color, lineHeight = 20.sp),
        inlineContent = inlineContent,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(comment.rpid) {
            detectTapGestures(
                onLongPress = { offset -> onLongPress(offset) },
                onTap = { position ->
                    val result = layout ?: return@detectTapGestures
                    val offset = result.getOffsetForPosition(position)
                    annotated.getStringAnnotations("bvid", offset, offset).firstOrNull()?.let {
                        onOpenBvid?.invoke(it.item)
                        return@detectTapGestures
                    }
                    annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                        }
                        return@detectTapGestures
                    }
                    annotated.getStringAnnotations("topic", offset, offset).firstOrNull()?.let {
                        if (onOpenTopic != null) {
                            onOpenTopic.invoke(it.item)
                        } else {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://search.bilibili.com/all?keyword=" + Uri.encode(it.item),
                                        ),
                                    ),
                                )
                            }
                        }
                        return@detectTapGestures
                    }
                    annotated.getStringAnnotations("user", offset, offset).firstOrNull()?.let {
                        val mid = it.item.substringBefore('#').toLongOrNull() ?: return@detectTapGestures
                        onOpenUser?.invoke(mid, it.item.substringAfter('#'))
                    }
                },
            )
        },
    )
}

private fun copyToClipboard(context: Context, text: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("评论", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

/**
 * 评论配图：单图按原比例（clamp 0.5~2）宽最多 220dp；多图九宫格（4 张 2 列，其余 3 列）。
 * 超过 9 张时只显示前 9 张，最后一张叠加「+N」。
 */
@Composable
private fun CommentPictures(
    pictures: List<CommentPicture>,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pictures.isEmpty()) return
    if (pictures.size == 1) {
        val p = pictures[0]
        val ratio = if (p.imgWidth > 0 && p.imgHeight > 0) {
            (p.imgWidth.toFloat() / p.imgHeight).coerceIn(0.5f, 2f)
        } else {
            4f / 3f
        }
        NetworkImage(
            url = p.imgSrc,
            modifier = modifier
                .padding(top = 8.dp)
                .widthIn(max = 220.dp)
                .heightIn(max = 300.dp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onImageClick(0) },
        )
        return
    }
    val columns = if (pictures.size == 4) 2 else 3
    val cell = if (columns == 2) 116.dp else 90.dp
    val overflow = pictures.size - 9
    val shown = if (pictures.size > 9) pictures.take(9) else pictures
    Column(
        modifier = modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        shown.chunked(columns).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEachIndexed { colIndex, p ->
                    val index = rowIndex * columns + colIndex
                    val isLastWithOverflow = overflow > 0 && index == shown.lastIndex
                    Box(
                        modifier = Modifier
                            .size(cell)
                            .clip(RoundedCornerShape(6.dp))
                            .background(biko.card2)
                            .clickable { onImageClick(index) },
                    ) {
                        NetworkImage(url = p.imgSrc, modifier = Modifier.fillMaxSize())
                        if (isLastWithOverflow) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+$overflow",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 评论配图全屏查看：左右滑动切图，右上角关闭。 */
@Composable
private fun CommentImageViewer(
    pictures: List<CommentPicture>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (pictures.size - 1).coerceAtLeast(0)),
    ) { pictures.size }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f)),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    NetworkImage(
                        url = pictures[page].imgSrc,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(24.dp)
                    .clickable(onClick = onClose),
            )
            if (pictures.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1}/${pictures.size}",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(20.dp),
                )
            }
        }
    }
}

/** 评论行（对应 item_comment.xml）。长按弹出操作菜单，支持复制 / 举报 / 删除。 */
@Composable
fun CommentRow(
    comment: Comment,
    onLike: () -> Unit,
    modifier: Modifier = Modifier,
    onAvatarClick: () -> Unit = {},
    onHate: () -> Unit = {},
    onReply: () -> Unit = {},
    /** 非 null 时显示「共N条回复」入口（主评论用，楼中楼里传 null）。 */
    onShowReplies: (() -> Unit)? = null,
    /** 举报评论；入参是 B 站举报原因码。 */
    onReport: ((Int) -> Unit)? = null,
    /** 删除自己的评论（仅自己的评论传非 null）。 */
    onDelete: (() -> Unit)? = null,
    /** 点正文里的 bvid 打开对应视频。 */
    onOpenBvid: ((String) -> Unit)? = null,
    /** 点正文里的 @用户 打开对方空间。 */
    onOpenUser: ((Long, String) -> Unit)? = null,
    /** 点正文里的 #话题# 打开话题页（为 null 时回退外部浏览器搜索）。 */
    onOpenTopic: ((String) -> Unit)? = null,
) {
    val colors = biko
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    // 评论配图全屏查看：-1 表示关闭
    var viewerIndex by remember { mutableStateOf(-1) }
    // 菜单锚在行内固定位置，不用手指坐标：否则会盖住正在看的评论正文
    val openMenu: () -> Unit = { menuOpen = true }

    Box(
        modifier
            .fillMaxWidth()
            // 长按评论弹出操作菜单（与 BiliPai 的条目操作面板一致）
            .pointerInput(comment.rpid) {
                detectTapGestures(onLongPress = { openMenu() })
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp)) {
            NetworkImage(
                url = comment.avatar,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick),
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.userName,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.t2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (comment.level >= 2) {
                        Text(
                            text = "Lv${comment.level}",
                            fontSize = 9.sp,
                            lineHeight = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = Color.White,
                            // 去掉字体上下留白，徽章更扁
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(levelColor(comment.level))
                                .padding(horizontal = 4.dp),
                        )
                    }
                    if (comment.isUp) {
                        CommentBadge(text = "UP主", color = colors.acc, modifier = Modifier.padding(start = 6.dp))
                    }
                    if (comment.isUpTop) {
                        CommentBadge(text = "置顶", color = colors.t3, modifier = Modifier.padding(start = 6.dp))
                    }
                    // 粉丝牌（勋章）
                    if (comment.medalName.isNotEmpty() && comment.medalLevel > 0) {
                        CommentBadge(
                            text = "${comment.medalName} ${comment.medalLevel}",
                            color = medalColor(comment.medalLevel),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                if (comment.upLiked) {
                    Text(
                        text = "UP主觉得很赞",
                        fontSize = 11.sp,
                        color = colors.acc,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                RichCommentText(
                    comment = comment,
                    color = colors.text,
                    onOpenBvid = onOpenBvid,
                    onOpenUser = onOpenUser,
                    onOpenTopic = onOpenTopic,
                    onLongPress = { openMenu() },
                    modifier = Modifier.padding(top = 7.dp),
                )
                CommentPictures(
                    pictures = comment.pictures,
                    onImageClick = { viewerIndex = it },
                )
                Row(
                    // 铺满行宽，让「日期 + IP 属地」在剩余空间里居中，左右留白相等
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.clickable(onClick = onLike),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(com.biko.client.R.drawable.ic_like),
                            contentDescription = "点赞",
                            // 已点赞高亮为主题色
                            colorFilter = if (comment.liked) {
                                androidx.compose.ui.graphics.ColorFilter.tint(colors.acc)
                            } else {
                                null
                            },
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (comment.likes > 0) formatCount(comment.likes) else "",
                            fontSize = 12.sp,
                            color = if (comment.liked) colors.acc else colors.t3,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    // 点踩：与点赞互斥，无计数
                    Icon(
                        imageVector = Icons.Filled.ThumbDown,
                        contentDescription = "点踩",
                        tint = if (comment.hated) colors.acc else colors.t3,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(14.dp)
                            .clickable(onClick = onHate),
                    )
                    Text(
                        text = "回复",
                        fontSize = 12.sp,
                        color = colors.t3,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .clickable(onClick = onReply)
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                    // 「日期 + IP 属地」整组在剩余宽度内居中：左侧（回复之后）与右侧留白相等
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 日期放在 IP 属地前面
                        Text(
                            text = formatTimeAgo(comment.ctime),
                            fontSize = 12.sp,
                            color = colors.t3,
                            maxLines = 1,
                        )
                        // IP 属地（接口不下发时整条不占位）
                        if (comment.location.isNotEmpty()) {
                            Text(
                                text = comment.location,
                                fontSize = 12.sp,
                                color = colors.t3,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                // 楼中楼入口（仅主评论显示）
                if (onShowReplies != null && comment.rcount > 0) {
                    Text(
                        text = "共${comment.rcount}条回复 >",
                        fontSize = 12.sp,
                        color = colors.acc,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable(onClick = onShowReplies)
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }

        // 评论之间的间隔线：贴行底，左端与评论正文对齐
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 58.dp, end = 14.dp)
                .height(1.dp)
                .background(colors.line),
        )

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            offset = DpOffset(46.dp, 0.dp),
        ) {
            DropdownMenuItem(
                text = { Text("复制评论", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                onClick = {
                    menuOpen = false
                    copyToClipboard(context, comment.message, "已复制评论")
                },
            )
            DropdownMenuItem(
                text = { Text("复制用户名", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                onClick = {
                    menuOpen = false
                    copyToClipboard(context, comment.userName, "已复制用户名")
                },
            )
            if (onReport != null) {
                DropdownMenuItem(
                    text = { Text("举报", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    onClick = {
                        menuOpen = false
                        reportOpen = true
                    },
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = {
                        Text("删除", color = colors.acc, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }

    if (reportOpen && onReport != null) {
        AlertDialog(
            onDismissRequest = { reportOpen = false },
            title = { Text("举报评论") },
            text = {
                Column {
                    Text("请选择举报原因", fontSize = 13.sp, color = colors.t3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    COMMENT_REPORT_REASONS.forEach { (label, code) ->
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = colors.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    reportOpen = false
                                    onReport(code)
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

    if (viewerIndex >= 0) {
        CommentImageViewer(
            pictures = comment.pictures,
            initialIndex = viewerIndex,
            onClose = { viewerIndex = -1 },
        )
    }
}

/** 粉丝牌配色：等级越高颜色越醒目（低灰 / 中紫 / 高橙红）。 */
private fun medalColor(level: Int): Color = when {
    level >= 30 -> Color(0xFFE8623C)
    level >= 20 -> Color(0xFFF0A020)
    level >= 10 -> Color(0xFFA062D8)
    else -> Color(0xFF4E9BE8)
}

/** 评论者身份徽章（UP主 / 置顶 / 粉丝牌）。 */
@Composable
private fun CommentBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        color = Color.White,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private fun levelColor(level: Int) = when (level) {
    3 -> Level3
    4 -> Level4
    5 -> Level5
    6 -> Level6
    else -> Level2
}
