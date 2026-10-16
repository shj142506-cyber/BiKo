package com.biko.client.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.articleDetail
import com.biko.client.data.articleInfo
import com.biko.client.data.coinArticle
import com.biko.client.data.comments
import com.biko.client.data.favArticle
import com.biko.client.data.follow
import com.biko.client.data.likeArticle
import com.biko.client.data.likeReply
import com.biko.client.data.model.ArticleBlock
import com.biko.client.data.model.ArticleDetail
import com.biko.client.data.model.ArticleInteraction
import com.biko.client.data.model.ArticleSpan
import com.biko.client.data.model.Comment
import com.biko.client.data.replyAdd
import com.biko.client.ui.components.CommentRow
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import com.biko.client.util.formatCount
import com.biko.client.util.formatTimeAgo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 专栏评论区类型（x/v2/reply 系列接口的 type 字段）。 */
private const val ARTICLE_COMMENT_TYPE = 12

data class ArticleDetailState(
    val loading: Boolean = true,
    val article: ArticleDetail? = null,
    val error: String? = null,
    val interaction: ArticleInteraction = ArticleInteraction(),
    val coinDialog: Boolean = false,
    val comments: List<Comment> = emptyList(),
    val commentSort: Int = COMMENT_SORT_HOT,
    val commentLoading: Boolean = false,
    val commentEnd: Boolean = true,
    val message: String? = null,
)

class ArticleDetailViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(ArticleDetailState())
    val state = _state.asStateFlow()
    private var loadedId = 0L
    private var commentNext = 0L
    private var commentLoading = false

    fun load(cvId: Long) {
        if (loadedId == cvId) return
        loadedId = cvId
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.articleDetail(cvId) }
                .onSuccess { detail ->
                    _state.update { it.copy(loading = false, article = detail) }
                    loadInfo(cvId)
                    loadComments(cvId, 0)
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
                }
        }
    }

    /** 拉取点赞 / 投币 / 收藏状态与统计。未登录时接口仍会返回统计。 */
    private fun loadInfo(cvId: Long) {
        viewModelScope.launch {
            runCatching { repo.articleInfo(cvId) }
                .onSuccess { info -> _state.update { it.copy(interaction = info) } }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun requireLogin(): Boolean {
        if (session.isLogin()) return true
        _state.update { it.copy(message = "请先登录") }
        return false
    }

    fun toggleLike() {
        if (!requireLogin()) return
        val liked = !_state.value.interaction.liked
        _state.update {
            it.copy(
                interaction = it.interaction.copy(
                    liked = liked,
                    likeCount = (it.interaction.likeCount + if (liked) 1 else -1).coerceAtLeast(0),
                )
            )
        }
        viewModelScope.launch {
            runCatching { repo.likeArticle(loadedId, liked) }
                .onFailure { e -> _rollbackLike(liked, e.message ?: "点赞失败") }
        }
    }

    private fun _rollbackLike(liked: Boolean, message: String) {
        _state.update {
            it.copy(
                interaction = it.interaction.copy(
                    liked = !liked,
                    likeCount = (it.interaction.likeCount + if (liked) -1 else 1).coerceAtLeast(0),
                ),
                message = message,
            )
        }
    }

    fun openCoinDialog() {
        if (!requireLogin()) return
        if (_state.value.interaction.coined >= 2) {
            _state.update { it.copy(message = "已投满 2 个硬币") }
            return
        }
        _state.update { it.copy(coinDialog = true) }
    }

    fun dismissCoinDialog() = _state.update { it.copy(coinDialog = false) }

    fun coin(count: Int) {
        val authorMid = _state.value.article?.authorMid ?: 0L
        _state.update { it.copy(coinDialog = false) }
        viewModelScope.launch {
            runCatching { repo.coinArticle(loadedId, authorMid, count) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            interaction = it.interaction.copy(
                                coined = (it.interaction.coined + count).coerceAtMost(2),
                                coinCount = it.interaction.coinCount + count,
                            ),
                            message = "投币成功",
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "投币失败") } }
        }
    }

    fun toggleFav() {
        if (!requireLogin()) return
        val fav = !_state.value.interaction.favorited
        _state.update {
            it.copy(
                interaction = it.interaction.copy(
                    favorited = fav,
                    favCount = (it.interaction.favCount + if (fav) 1 else -1).coerceAtLeast(0),
                )
            )
        }
        viewModelScope.launch {
            runCatching { repo.favArticle(loadedId, fav) }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            interaction = it.interaction.copy(
                                favorited = !fav,
                                favCount = (it.interaction.favCount + if (fav) -1 else 1).coerceAtLeast(0),
                            ),
                            message = e.message ?: "操作失败",
                        )
                    }
                }
        }
    }

    fun toggleFollow() {
        val mid = _state.value.article?.authorMid ?: 0L
        if (mid <= 0) return
        if (!requireLogin()) return
        val follow = !_state.value.interaction.attention
        _state.update { it.copy(interaction = it.interaction.copy(attention = follow)) }
        viewModelScope.launch {
            runCatching { repo.follow(mid, follow) }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            interaction = it.interaction.copy(attention = !follow),
                            message = e.message ?: "操作失败",
                        )
                    }
                }
        }
    }

    // ---- 评论 ----

    private fun loadComments(cvId: Long, next: Long, force: Boolean = false) {
        if (commentLoading && !force) return
        commentLoading = true
        _state.update { it.copy(commentLoading = true) }
        val upMid = _state.value.article?.authorMid ?: 0L
        val sort = _state.value.commentSort
        viewModelScope.launch {
            runCatching { repo.comments(cvId, next, sort, upMid, ARTICLE_COMMENT_TYPE) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            comments = if (next == 0L) page.comments else it.comments + page.comments,
                            commentEnd = page.isEnd,
                            commentLoading = false,
                        )
                    }
                    commentNext = page.next
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(commentLoading = false, commentEnd = true, message = e.message)
                    }
                }
            commentLoading = false
        }
    }

    fun loadMoreComments() {
        if (_state.value.commentEnd) return
        loadComments(loadedId, commentNext)
    }

    fun setCommentSort(sort: Int) {
        if (_state.value.commentSort == sort) return
        _state.update { it.copy(commentSort = sort, comments = emptyList(), commentEnd = false) }
        commentNext = 0
        loadComments(loadedId, 0)
    }

    fun likeComment(comment: Comment) {
        if (!requireLogin()) return
        val target = !comment.liked
        _state.update { st ->
            st.copy(comments = st.comments.map {
                if (it.rpid != comment.rpid) it
                else it.copy(liked = target, likes = (it.likes + if (target) 1 else -1).coerceAtLeast(0))
            })
        }
        viewModelScope.launch {
            runCatching { repo.likeReply(loadedId, comment.rpid, target, ARTICLE_COMMENT_TYPE) }
                .onFailure { e ->
                    _state.update { st ->
                        st.copy(
                            comments = st.comments.map {
                                if (it.rpid != comment.rpid) it
                                else it.copy(
                                    liked = !target,
                                    likes = (it.likes + if (target) -1 else 1).coerceAtLeast(0),
                                )
                            },
                            message = e.message,
                        )
                    }
                }
        }
    }

    /** 发表专栏评论（type = 12）。返回是否已发起请求，成功后强制刷新评论列表。 */
    fun sendComment(message: String): Boolean {
        val text = message.trim()
        if (text.isEmpty()) return false
        if (!requireLogin()) return false
        viewModelScope.launch {
            runCatching { repo.replyAdd(loadedId, text, type = ARTICLE_COMMENT_TYPE) }
                .onSuccess {
                    _state.update { it.copy(message = "评论已发布") }
                    commentNext = 0
                    // 强制刷新：即使列表正在加载中也要重新拉取，确保刚发布的评论能显示出来
                    loadComments(loadedId, 0, force = true)
                }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "发送失败") } }
        }
        return true
    }
}

/** 专栏 / 文章阅读页。 */
@Composable
fun ArticleDetailScreen(
    cvId: Long,
    onBack: () -> Unit,
    onOpenSpace: (Long, String, String) -> Unit = { _, _, _ -> },
    viewModel: ArticleDetailViewModel = viewModel(),
) {
    val colors = biko
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var commentInput by remember { mutableStateOf("") }

    LaunchedEffect(cvId) { viewModel.load(cvId) }
    LaunchedEffect(state.message) {
        val msg = state.message
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val article = state.article
    // 键盘弹起时由根容器承担 ime padding，输入框紧贴键盘；底栏在键盘弹起时隐藏
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().imePadding()) {
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
                text = "专栏",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            if (article != null) {
                var menuOpen by remember { mutableStateOf(false) }
                val clipboard = LocalClipboardManager.current
                val articleLink = "https://www.bilibili.com/read/cv${article.id}"
                Box {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = colors.text,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { menuOpen = true }
                            .padding(7.dp),
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.width(132.dp),
                        containerColor = colors.card,
                    ) {
                        DropdownMenuItem(
                            text = { Text("分享链接", fontSize = 14.sp) },
                            onClick = {
                                menuOpen = false
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${article.title}\n$articleLink")
                                }
                                context.startActivity(Intent.createChooser(intent, "分享到"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("复制链接", fontSize = 14.sp) },
                            onClick = {
                                menuOpen = false
                                clipboard.setText(AnnotatedString(articleLink))
                                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("浏览器打开", fontSize = 14.sp) },
                            onClick = {
                                menuOpen = false
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(articleLink)))
                                }.onFailure {
                                    Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                article == null -> Text(
                    text = state.error ?: "加载失败",
                    fontSize = 13.sp,
                    color = colors.t3,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> ArticleBody(
                    article = article,
                    state = state,
                    onOpenSpace = onOpenSpace,
                    onOpenVideo = null,
                    onCommentLike = viewModel::likeComment,
                    onCommentSort = viewModel::setCommentSort,
                    onLoadMoreComments = viewModel::loadMoreComments,
                    onFollow = viewModel::toggleFollow,
                )
            }
        }

        // 评论输入栏：专栏同样可以发评论（发送为蓝色纸飞机，内嵌在药丸右侧）
        if (article != null) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val canSend = commentInput.isNotBlank()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.card2)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (commentInput.isEmpty()) {
                            Text("发个评论吧", fontSize = 13.sp, color = colors.t3)
                        }
                        BasicTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = colors.text),
                            cursorBrush = SolidColor(colors.acc),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (viewModel.sendComment(commentInput)) commentInput = ""
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (canSend) colors.acc else colors.t3,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canSend) {
                                if (viewModel.sendComment(commentInput)) commentInput = ""
                            },
                    )
                }
            }
        }

        if (article != null && !imeVisible) {
            ArticleActionBar(
                interaction = state.interaction,
                onLike = viewModel::toggleLike,
                onCoin = viewModel::openCoinDialog,
                onFav = viewModel::toggleFav,
                onShare = {
                    val text = "${article.title}\nhttps://www.bilibili.com/read/cv${article.id}"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享到"))
                },
            )
        }
    }

    if (state.coinDialog) {
        CoinChoiceDialog(
            coined = state.interaction.coined,
            onDismiss = viewModel::dismissCoinDialog,
            onPick = viewModel::coin,
        )
    }
}

@Composable
private fun ArticleBody(
    article: ArticleDetail,
    state: ArticleDetailState,
    onOpenSpace: (Long, String, String) -> Unit,
    onOpenVideo: ((String) -> Unit)?,
    onCommentLike: (Comment) -> Unit,
    onCommentSort: (Int) -> Unit,
    onLoadMoreComments: () -> Unit,
    onFollow: () -> Unit,
) {
    val colors = biko
    LazyColumn(Modifier.fillMaxSize()) {
        article.banner?.takeIf { it.isNotBlank() }?.let { banner ->
            item {
                NetworkImage(
                    url = banner,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
        }
        item {
            Text(
                text = article.title,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                lineHeight = 30.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item {
            ArticleAuthorRow(
                article = article,
                following = state.interaction.attention,
                onOpenSpace = onOpenSpace,
                onFollow = onFollow,
            )
        }
        if (article.summary.isNotBlank()) {
            item {
                Text(
                    text = article.summary,
                    fontSize = 15.sp,
                    color = colors.t2,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(article.blocks) { block -> ArticleBlockView(block = block) }

        item { ArticleStatsRow(interaction = state.interaction) }

        item { ArticleCommentHeader(sort = state.commentSort, onSelect = onCommentSort) }

        items(state.comments, key = { it.rpid }) { comment ->
            CommentRow(
                comment = comment,
                onLike = { onCommentLike(comment) },
                onAvatarClick = { onOpenSpace(comment.mid, comment.userName, comment.avatar) },
                onOpenBvid = onOpenVideo,
                onOpenUser = { mid, name -> onOpenSpace(mid, name, "") },
            )
        }

        if (!state.commentEnd && state.comments.isNotEmpty()) {
            item {
                LaunchedEffect(state.comments.size) { onLoadMoreComments() }
                Text(
                    text = "正在加载评论…",
                    fontSize = 13.sp,
                    color = colors.t3,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
        }
        if (!state.commentLoading && state.comments.isEmpty()) {
            item {
                Text(
                    text = if (AppGraph.session.isLogin()) "暂无评论" else "登录后可查看评论",
                    fontSize = 13.sp,
                    color = colors.t3,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** 底部互动栏：点赞 / 投币 / 收藏 / 分享。 */
@Composable
private fun ArticleActionBar(
    interaction: ArticleInteraction,
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFav: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ArticleAction(R.drawable.ic_like, formatCount(interaction.likeCount), interaction.liked, onLike)
        ArticleAction(
            icon = R.drawable.ic_coin,
            label = if (interaction.coined > 0) "${interaction.coined}/2" else formatCount(interaction.coinCount),
            active = interaction.coined > 0,
            onClick = onCoin,
        )
        ArticleAction(R.drawable.ic_star, formatCount(interaction.favCount), interaction.favorited, onFav)
        ArticleAction(
            icon = R.drawable.ic_share,
            label = formatCount(interaction.shareCount),
            active = false,
            onClick = onShare,
        )
    }
}

@Composable
private fun ArticleAction(
    icon: Int,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = biko
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (active) colors.acc else colors.t2),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (active) colors.acc else colors.t3,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** 阅读量 / 点赞 / 评论统计行。 */
@Composable
private fun ArticleStatsRow(interaction: ArticleInteraction) {
    val colors = biko
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "阅读 ${formatCount(interaction.viewCount)}", fontSize = 12.sp, color = colors.t3)
        Text(
            text = " · 点赞 ${formatCount(interaction.likeCount)} · 评论 ${formatCount(interaction.replyCount)}",
            fontSize = 12.sp,
            color = colors.t3,
        )
    }
}

/** 评论区标题 + 排序切换。 */
@Composable
private fun ArticleCommentHeader(sort: Int, onSelect: (Int) -> Unit) {
    val colors = biko
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
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

/** 投币选择弹窗。 */
@Composable
private fun CoinChoiceDialog(coined: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val colors = biko
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(text = "投币", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text) },
        text = {
            Column {
                Text(
                    text = if (coined > 0) "已投 $coined 枚，最多可投 2 枚" else "为该专栏投币（最多 2 枚）",
                    fontSize = 13.sp,
                    color = colors.t2,
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    listOf(1, 2).forEach { n ->
                        val enabled = coined + n <= 2
                        Text(
                            text = "投 $n 币",
                            fontSize = 14.sp,
                            color = if (enabled) colors.acc else colors.inactive,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.card2)
                                .clickable(enabled = enabled) { onPick(n) }
                                .padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        if (n == 1) Spacer(Modifier.width(10.dp))
                    }
                }
            }
        },
        containerColor = colors.card,
    )
}

@Composable
private fun ArticleAuthorRow(
    article: ArticleDetail,
    following: Boolean,
    onOpenSpace: (Long, String, String) -> Unit,
    onFollow: () -> Unit,
) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = article.authorMid > 0) {
                    onOpenSpace(article.authorMid, article.authorName, article.authorFace)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetworkImage(url = article.authorFace, modifier = Modifier.size(34.dp).clip(CircleShape))
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    text = article.authorName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.acc,
                )
                val time = formatTimeAgo(article.publishTime)
                if (time.isNotEmpty()) {
                    Text(text = time, fontSize = 12.sp, color = colors.t3)
                }
            }
        }
        if (article.authorMid > 0) {
            Text(
                text = if (following) "已关注" else "关注",
                fontSize = 13.sp,
                color = if (following) colors.t3 else Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (following) colors.card2 else colors.acc)
                    .clickable(onClick = onFollow)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ArticleBlockView(block: ArticleBlock) {
    val colors = biko
    when (block) {
        is ArticleBlock.Heading -> Text(
            text = annotated(block.text, block.spans, baseSize = 19.sp, baseBold = true),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            lineHeight = 28.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )

        is ArticleBlock.Paragraph -> Text(
            text = annotated(block.text, block.spans),
            fontSize = 16.sp,
            color = colors.text,
            lineHeight = 26.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )

        is ArticleBlock.Quote -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(colors.acc, RoundedCornerShape(2.dp)),
            )
            Text(
                text = annotated(block.text, block.spans),
                fontSize = 15.sp,
                color = colors.t2,
                lineHeight = 24.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        is ArticleBlock.ListBlock -> Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            block.items.forEachIndexed { i, item ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(
                        text = if (block.ordered) "${i + 1}." else "•",
                        fontSize = 16.sp,
                        color = colors.text,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        text = item,
                        fontSize = 16.sp,
                        color = colors.text,
                        lineHeight = 25.sp,
                    )
                }
            }
        }

        is ArticleBlock.Code -> Text(
            text = block.content,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = colors.text,
            lineHeight = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.card2)
                .padding(12.dp),
        )

        is ArticleBlock.Image -> {
            val ratio = if (block.width > 0 && block.height > 0) {
                block.width.toFloat() / block.height.toFloat()
            } else {
                16f / 9f
            }
            NetworkImage(
                url = block.url,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .aspectRatio(ratio.coerceIn(0.4f, 3f)),
            )
        }
    }
}

/** 把纯文本 + span 样式合成 AnnotatedString。 */
private fun annotated(
    text: String,
    spans: List<ArticleSpan>,
    baseSize: TextUnit = TextUnit.Unspecified,
    baseBold: Boolean = false,
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (end <= start) return@forEach
            addStyle(
                style = SpanStyle(
                    fontSize = if (span.fontSizeSp > 0) span.fontSizeSp.sp else baseSize,
                    color = if (span.colorArgb != 0L) Color(span.colorArgb) else Color.Unspecified,
                    fontWeight = if (span.bold || baseBold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = if (span.strikethrough) TextDecoration.LineThrough else null,
                ),
                start = start,
                end = end,
            )
        }
    }
}
