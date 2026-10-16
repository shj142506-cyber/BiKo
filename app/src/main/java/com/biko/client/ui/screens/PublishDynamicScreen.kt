package com.biko.client.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.DynamicPublishDraft
import com.biko.client.data.DynamicTopic
import com.biko.client.data.createDynamicVote
import com.biko.client.data.emotePackages
import com.biko.client.data.model.EmotePackage
import com.biko.client.data.model.MentionUser
import com.biko.client.data.publishDynamic
import com.biko.client.data.searchMentionUsers
import com.biko.client.data.searchPublishTopics
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 发布动态页最大配图数。 */
private const val MAX_DYNAMIC_IMAGES = 18

data class PublishDynamicState(
    val title: String = "",
    val text: String = "",
    val imageUris: List<Uri> = emptyList(),
    val isPrivate: Boolean = false,
    val mentions: List<MentionUser> = emptyList(),
    val emotes: List<String> = emptyList(),
    val topic: DynamicTopic? = null,
    val voteId: Long = 0L,
    val voteTitle: String = "",
    val publishing: Boolean = false,
)

/** 发布动态页状态。 */
class PublishDynamicViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val _state = MutableStateFlow(PublishDynamicState())
    val state = _state.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    private val _emotePackages = MutableStateFlow<List<EmotePackage>>(emptyList())
    val emotePackages = _emotePackages.asStateFlow()

    fun consumeToast() {
        _toast.value = null
    }

    /** 首次展开表情面板时加载表情包。 */
    fun ensureEmotes() {
        if (_emotePackages.value.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { repo.emotePackages() }.onSuccess { _emotePackages.value = it }
        }
    }

    suspend fun searchMentions(keyword: String): List<MentionUser> =
        runCatching { repo.searchMentionUsers(keyword) }.getOrDefault(emptyList())

    suspend fun searchTopics(keyword: String): List<DynamicTopic> =
        runCatching { repo.searchPublishTopics(keyword) }.getOrDefault(emptyList())

    fun setTitle(value: String) = _state.update { it.copy(title = value.take(20)) }

    fun setText(value: String) = _state.update { it.copy(text = value) }

    fun addImages(uris: List<Uri>) =
        _state.update { it.copy(imageUris = (it.imageUris + uris).take(MAX_DYNAMIC_IMAGES)) }

    fun removeImage(uri: Uri) = _state.update { it.copy(imageUris = it.imageUris - uri) }

    fun togglePrivate() = _state.update { it.copy(isPrivate = !it.isPrivate) }

    fun addMention(user: MentionUser) = _state.update { it.copy(mentions = it.mentions + user) }

    fun addEmote(token: String) = _state.update { it.copy(emotes = it.emotes + token) }

    fun setTopic(topic: DynamicTopic?) = _state.update { it.copy(topic = topic) }

    fun createVote(
        title: String,
        options: List<String>,
        choiceCount: Int,
        durationSeconds: Int,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repo.createDynamicVote(
                    title = title,
                    options = options,
                    choiceCount = choiceCount,
                    durationSeconds = durationSeconds,
                )
            }
                .onSuccess { (voteId, voteTitle) ->
                    _state.update { it.copy(voteId = voteId, voteTitle = voteTitle) }
                    onDone(true)
                }
                .onFailure { e ->
                    _toast.value = e.message ?: "创建投票失败"
                    onDone(false)
                }
        }
    }

    fun publish(onPublished: () -> Unit) {
        if (_state.value.publishing) return
        viewModelScope.launch {
            _state.update { it.copy(publishing = true) }
            val s = _state.value
            runCatching {
                repo.publishDynamic(
                    DynamicPublishDraft(
                        text = s.text,
                        title = s.title,
                        imageUris = s.imageUris,
                        isPrivate = s.isPrivate,
                        mentions = s.mentions,
                        emotes = s.emotes,
                        topic = s.topic,
                        voteId = s.voteId,
                        voteTitle = s.voteTitle,
                    ),
                )
            }
                .onSuccess {
                    _state.update { it.copy(publishing = false) }
                    _toast.value = "发布成功"
                    onPublished()
                }
                .onFailure { e ->
                    _state.update { it.copy(publishing = false) }
                    _toast.value = e.message ?: "发布失败"
                }
        }
    }
}

/** 发布动态页（长文 + 话题 + 投票 + 多图）。 */
@Composable
fun PublishDynamicScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
    viewModel: PublishDynamicViewModel = viewModel(),
) {
    val colors = biko
    val state by viewModel.state.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val emotePkgs by viewModel.emotePackages.collectAsState()
    val context = LocalContext.current

    var bodyField by remember { mutableStateOf(TextFieldValue("")) }
    var showEmotes by remember { mutableStateOf(false) }
    var showMentions by remember { mutableStateOf(false) }
    var showTopic by remember { mutableStateOf(false) }
    var showVote by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var voteCreating by remember { mutableStateOf(false) }

    LaunchedEffect(toast) {
        val msg = toast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
    }

    LaunchedEffect(showEmotes) { if (showEmotes) viewModel.ensureEmotes() }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { picked ->
        if (picked.isNotEmpty()) viewModel.addImages(picked)
    }

    val insertAtCursor: (String) -> Unit = { insert ->
        val text = bodyField.text
        val start = bodyField.selection.start.coerceIn(0, text.length)
        val end = bodyField.selection.end.coerceIn(0, text.length)
        val merged = text.substring(0, start) + insert + text.substring(end)
        bodyField = TextFieldValue(merged, TextRange(start + insert.length))
        viewModel.setText(merged)
    }

    val canPublish = !state.publishing &&
        (state.text.isNotBlank() || state.imageUris.isNotEmpty() || state.voteId > 0L)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // 顶栏：返回 + 标题 + 发布
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "发布动态",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (canPublish) colors.acc else colors.card2)
                    .then(
                        if (canPublish) Modifier.clickable { viewModel.publish(onPublished) } else Modifier,
                    )
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    text = if (state.publishing) "发布中" else "发布",
                    fontSize = 14.sp,
                    color = if (canPublish) Color.White else colors.t3,
                )
            }
        }

        Spacer(Modifier.height(1.dp).fillMaxWidth().background(colors.line))

        // 正文区
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            // 话题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTopic = true }
                    .padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.acc)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = state.topic?.let { "#${it.name}" } ?: "选择话题",
                    fontSize = 15.sp,
                    color = colors.acc,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.topic != null) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清除话题",
                        tint = colors.t3,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { viewModel.setTopic(null) },
                    )
                }
            }

            // 标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.title.isEmpty()) {
                    Text("标题，选填 20 字", fontSize = 15.sp, color = colors.t3)
                }
                BasicTextField(
                    value = state.title,
                    onValueChange = { viewModel.setTitle(it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.acc),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            // 正文
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            ) {
                if (bodyField.text.isEmpty()) {
                    Text("说点什么吧", fontSize = 15.sp, color = colors.t3)
                }
                BasicTextField(
                    value = bodyField,
                    onValueChange = {
                        bodyField = it
                        viewModel.setText(it.text)
                    },
                    textStyle = TextStyle(fontSize = 15.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.acc),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(14.dp))

            // 可见性
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.togglePrivate() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (state.isPrivate) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = colors.acc,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (state.isPrivate) "仅自己可见" else "所有人可见",
                    fontSize = 15.sp,
                    color = colors.acc,
                )
            }

            Spacer(Modifier.height(12.dp))

            // 图片宫格（4 列）
            run {
                val cellCount = state.imageUris.size +
                    if (state.imageUris.size < MAX_DYNAMIC_IMAGES) 1 else 0
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until cellCount).chunked(4).forEach { rowIndexes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowIndexes.forEach { index ->
                                if (index < state.imageUris.size) {
                                    val uri = state.imageUris[index]
                                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                                        NetworkImage(
                                            url = uri.toString(),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.55f))
                                                .clickable { viewModel.removeImage(uri) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "删除图片",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.card2)
                                            .clickable { imagePicker.launch("image/*") },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "添加图片",
                                            tint = colors.t2,
                                            modifier = Modifier.size(30.dp),
                                        )
                                    }
                                }
                            }
                            repeat(4 - rowIndexes.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // 表情 / @ 面板
        if (showEmotes) {
            PublishEmotePanel(
                packages = emotePkgs,
                onPick = { token ->
                    viewModel.addEmote(token)
                    insertAtCursor(token)
                },
                onClose = { showEmotes = false },
            )
        }
        if (showMentions) {
            PublishMentionPanel(
                onSearch = { viewModel.searchMentions(it) },
                onPick = { user ->
                    viewModel.addMention(user)
                    insertAtCursor("@${user.name} ")
                },
                onClose = { showMentions = false },
            )
        }

        Spacer(Modifier.height(1.dp).fillMaxWidth().background(colors.line))

        // 底部工具行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolItem(Icons.Outlined.EmojiEmotions, "表情", showEmotes) {
                showEmotes = !showEmotes
                if (showEmotes) showMentions = false
            }
            ToolItem(Icons.Outlined.AlternateEmail, "@用户", showMentions) {
                showMentions = !showMentions
                if (showMentions) showEmotes = false
            }
            ToolItem(
                Icons.Outlined.Image,
                "图片 ${state.imageUris.size}/$MAX_DYNAMIC_IMAGES",
                false,
            ) { imagePicker.launch("image/*") }
            ToolItem(Icons.Outlined.Poll, "投票", state.voteId > 0L) { showVote = true }
            ToolItem(Icons.Outlined.RemoveRedEye, "预览", false) { showPreview = true }
        }
    }

    if (showTopic) {
        TopicSearchDialog(
            onSearch = { viewModel.searchTopics(it) },
            onPick = {
                viewModel.setTopic(it)
                showTopic = false
            },
            onDismiss = { showTopic = false },
        )
    }

    if (showVote) {
        VoteCreateDialog(
            creating = voteCreating,
            onCreate = { title, options, choiceCount, duration ->
                voteCreating = true
                viewModel.createVote(title, options, choiceCount, duration) { ok ->
                    voteCreating = false
                    if (ok) showVote = false
                }
            },
            onDismiss = { if (!voteCreating) showVote = false },
        )
    }

    if (showPreview) {
        PreviewDialog(state = state, onDismiss = { showPreview = false })
    }
}

@Composable
private fun RowScope.ToolItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = biko
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) colors.acc else colors.t2,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) colors.acc else colors.t2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 发布页表情面板：顶部表情包切换，网格展示表情，点击插入 token。 */
@Composable
private fun PublishEmotePanel(
    packages: List<EmotePackage>,
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
                CircularProgressIndicator(
                    color = colors.acc,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                emotes.chunked(5).forEach { rowEmotes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowEmotes.forEach { emote ->
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
    }
}

/** 发布页 @ 提及面板：输入关键词搜索，点击插入「@昵称 」。 */
@Composable
private fun PublishMentionPanel(
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

/** 话题搜索对话框。 */
@Composable
private fun TopicSearchDialog(
    onSearch: suspend (String) -> List<DynamicTopic>,
    onPick: (DynamicTopic) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = biko
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DynamicTopic>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        loading = true
        delay(250)
        results = onSearch(query)
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("选择话题", color = colors.text) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(colors.card2, RoundedCornerShape(19.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text("搜索话题", fontSize = 13.sp, color = colors.t3)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = colors.text),
                        cursorBrush = SolidColor(colors.acc),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                when {
                    loading -> Text("搜索中…", fontSize = 13.sp, color = colors.t3)
                    results.isEmpty() -> Text("没有找到相关话题", fontSize = 13.sp, color = colors.t3)
                    else -> LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(results) { topic ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(topic) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("# ", color = colors.acc, fontSize = 14.sp)
                                Text(topic.name, color = colors.text, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        },
    )
}

/** 创建投票对话框。 */
@Composable
private fun VoteCreateDialog(
    creating: Boolean,
    onCreate: (String, List<String>, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = biko
    var title by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var choiceCount by remember { mutableIntStateOf(1) }
    var durationSeconds by remember { mutableIntStateOf(7 * 24 * 3600) }
    val valid = title.isNotBlank() && options.count { it.isNotBlank() } >= 2
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onCreate(title, options, choiceCount, durationSeconds) },
                enabled = !creating && valid,
            ) { Text(if (creating) "创建中" else "创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("发起投票", color = colors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .background(colors.card2, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (title.isEmpty()) {
                        Text("投票标题", fontSize = 14.sp, color = colors.t3)
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                        cursorBrush = SolidColor(colors.acc),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                options.forEachIndexed { index, option ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .background(colors.card2, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (option.isEmpty()) {
                            Text("选项 ${index + 1}", fontSize = 14.sp, color = colors.t3)
                        }
                        BasicTextField(
                            value = option,
                            onValueChange = { v ->
                                options = options.toMutableList().also { it[index] = v }
                            },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                            cursorBrush = SolidColor(colors.acc),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = { options = options + "" }) { Text("+ 添加选项") }
                Spacer(Modifier.height(6.dp))
                Text("每天可投 $choiceCount 项", fontSize = 13.sp, color = colors.t2)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { n ->
                        val active = n == choiceCount
                        Text(
                            "$n 项",
                            fontSize = 13.sp,
                            color = if (active) Color.White else colors.t2,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) colors.acc else colors.card2)
                                .clickable { choiceCount = n }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("投票时长", fontSize = 13.sp, color = colors.t2)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        24 * 3600 to "1天",
                        3 * 24 * 3600 to "3天",
                        7 * 24 * 3600 to "7天",
                        30 * 24 * 3600 to "30天",
                    ).forEach { (sec, label) ->
                        val active = sec == durationSeconds
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (active) Color.White else colors.t2,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) colors.acc else colors.card2)
                                .clickable { durationSeconds = sec }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        },
    )
}

/** 发布预览对话框。 */
@Composable
private fun PreviewDialog(
    state: PublishDynamicState,
    onDismiss: () -> Unit,
) {
    val colors = biko
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("预览", color = colors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.topic?.let {
                    Text("#${it.name}", color = colors.acc, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                }
                if (state.title.isNotBlank()) {
                    Text(
                        state.title,
                        color = colors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (state.text.isNotBlank()) {
                    Text(state.text, color = colors.text, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                if (state.voteId > 0L) {
                    Text("「${state.voteTitle}」投票", color = colors.acc, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                state.imageUris.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { uri ->
                            NetworkImage(
                                uri.toString(),
                                Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
    )
}