package com.biko.client.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.SpaceSelection
import com.biko.client.data.SpaceUser
import com.biko.client.data.clearHistory
import com.biko.client.data.localHistory
import com.biko.client.data.model.MyStats
import com.biko.client.data.model.UserProfile
import com.biko.client.data.model.Video
import com.biko.client.data.myStats
import com.biko.client.data.syncHistoryFromServer
import com.biko.client.data.updateProfile
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko
import com.biko.client.util.formatCount
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- 「我的」页配色（取自 B 站官方设计）----
private val HeroText = Color(0xFF1A1A1A)
private val LvOrange = Color(0xFFF29A2B)
private val EditNavy = Color(0xFF3C5A80)

data class MeState(
    val profile: UserProfile = UserProfile(),
    val stats: MyStats = MyStats(),
    val history: List<Video> = emptyList(),
    /** 当前登录用户的 uid。 */
    val mid: String = "",
)

class MeViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(MeState())
    val state = _state.asStateFlow()

    /** 一次性提示（改资料结果 / 错误），用后由 UI 消费置空。 */
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var lastLogin: Boolean? = null

    init {
        viewModelScope.launch {
            session.profile.collect { profile ->
                _state.value = _state.value.copy(profile = profile)
                // 登录状态一变（扫码成功 / 退出）就同步一次云端历史与统计
                if (profile.isLogin && lastLogin != true) refresh()
                lastLogin = profile.isLogin
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(mid = session.mid(), history = repo.localHistory())
            if (!session.isLogin()) {
                _state.value = _state.value.copy(stats = MyStats())
                return@launch
            }
            runCatching { repo.myStats() }.onSuccess { stats ->
                _state.value = _state.value.copy(stats = stats)
            }
            runCatching { repo.syncHistoryFromServer() }
            _state.value = _state.value.copy(history = repo.localHistory())
        }
    }

    fun logout() {
        session.logout()
        viewModelScope.launch {
            _state.value = _state.value.copy(mid = "", history = repo.localHistory())
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repo.clearHistory()
            _state.value = _state.value.copy(history = emptyList())
        }
    }

    /** 保存昵称 / 签名（真实调用 B 站改资料接口）。 */
    fun saveProfile(uname: String, sign: String) {
        viewModelScope.launch {
            runCatching { repo.updateProfile(uname, sign) }
                .onSuccess {
                    _message.value = "资料已提交，审核通过后生效"
                    refresh()
                }
                .onFailure { _message.value = it.message ?: "修改失败，请稍后重试" }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

@Composable
fun MeScreen(
    onOpenVideo: (String) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenWatchLater: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenMySpace: (Long, Int) -> Unit = { _, _ -> },
    /** 打开关注 / 粉丝列表：0=关注 1=粉丝。 */
    onOpenRelation: (Int) -> Unit = {},
    /** 打开我的追番 / 追剧列表。 */
    onOpenBangumiFollow: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: MeViewModel = viewModel(),
) {
    val colors = biko
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val night by AppGraph.night.enabled.collectAsState()
    var showAbout by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    // 未登录时，除「观看历史」外的入口都先跳登录页
    val gate: (() -> Unit) -> () -> Unit = { action ->
        { if (state.profile.isLogin) action() else onOpenLogin() }
    }

    // 打开自己的空间页（0 动态 / 1 投稿）
    val openMySpace: (Int) -> Unit = { tab ->
        val myMid = state.mid.toLongOrNull() ?: 0L
        if (myMid > 0) {
            SpaceSelection.current = SpaceUser(myMid, state.profile.uname, state.profile.face)
            onOpenMySpace(myMid, tab)
        }
    }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        MeTopBar(
            night = night,
            isLogin = state.profile.isLogin,
            onBack = onBack,
            onToggleNight = { AppGraph.night.toggle() },
            onOpenDownloads = gate(onOpenDownloads),
            onClearCache = {
                scope.launch {
                    clearAppCache(context)
                    Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                }
            },
            onLogout = { viewModel.logout() },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 36.dp, bottom = 16.dp),
        ) {
            item {
                ProfileHeader(
                    profile = state.profile,
                    stats = state.stats,
                    mid = state.mid,
                    onOpenLogin = onOpenLogin,
                    onOpenRelation = { tab -> if (state.profile.isLogin) onOpenRelation(tab) else onOpenLogin() },
                    onEditProfile = { if (state.profile.isLogin) showEditProfile = true else onOpenLogin() },
                )
            }

            item {
                Column(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.card),
                ) {
                    SettingRow(icon = Icons.Outlined.History, text = "观看历史", onClick = onOpenHistory)
                    SettingGap()
                    SettingRow(icon = Icons.Outlined.Schedule, text = "稍后再看", onClick = gate(onOpenWatchLater))
                }
            }

            item {
                Column(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.card),
                ) {
                    SettingRow(icon = Icons.Outlined.VideoLibrary, text = "我的投稿", onClick = gate { openMySpace(1) })
                    SettingGap()
                    SettingRow(icon = Icons.Outlined.DynamicFeed, text = "我的动态", onClick = gate { openMySpace(0) })
                    SettingGap()
                    SettingRow(icon = Icons.Outlined.Subscriptions, text = "我的追番", onClick = gate(onOpenBangumiFollow))
                }
            }

            // 「关于彼可」固定在列表最下方
            item {
                Column(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.card),
                ) {
                    SettingRow(icon = Icons.Outlined.Info, text = "关于彼可", onClick = { showAbout = true })
                }
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (showEditProfile) {
        EditProfileDialog(
            initialUname = state.profile.uname,
            initialSign = state.stats.sign,
            onDismiss = { showEditProfile = false },
            onSave = { uname, sign ->
                showEditProfile = false
                viewModel.saveProfile(uname, sign)
            },
        )
    }
}

/** 编辑资料弹窗：昵称 + 签名，保存后调用 B 站真实接口。 */
@Composable
private fun EditProfileDialog(
    initialUname: String,
    initialSign: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var uname by remember { mutableStateOf(initialUname) }
    var sign by remember { mutableStateOf(initialSign) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑资料", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = uname,
                    onValueChange = { uname = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = sign,
                    onValueChange = { sign = it },
                    label = { Text("签名") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(uname.trim(), sign.trim()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 「我的」页顶栏：左上角返回主页箭头，右侧深浅色切换图标与三点菜单。 */
@Composable
private fun MeTopBar(
    night: Boolean,
    isLogin: Boolean,
    onBack: () -> Unit,
    onToggleNight: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClearCache: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = biko
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_back),
            contentDescription = "返回主页",
            colorFilter = ColorFilter.tint(colors.text),
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onBack)
                .padding(8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (night) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = "深浅色模式",
                tint = colors.text,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onToggleNight),
            )
            Box {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    tint = colors.text,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(26.dp)
                        .clickable { menu = true },
                )
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    modifier = Modifier.width(96.dp),
                    containerColor = colors.card,
                ) {
                    DropdownMenuItem(
                        text = { Text("离线缓存", color = colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        onClick = { menu = false; onOpenDownloads() },
                    )
                    DropdownMenuItem(
                        text = { Text("清除缓存", color = colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        onClick = { menu = false; onClearCache() },
                    )
                    if (isLogin) {
                        DropdownMenuItem(
                            text = { Text("退出登录", color = colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            onClick = { menu = false; onLogout() },
                        )
                    }
                }
            }
        }
    }
}

/** 「关于彼可」弹窗。 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val colors = biko
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = { Text("关于彼可", color = colors.text) },
        text = {
            Text(
                text = "彼可（BiKo）是一个第三方哔哩哔哩客户端。\n\n" +
                    "技术栈\n" +
                    "· 界面：Jetpack Compose\n" +
                    "· 图片加载：Coil\n" +
                    "· 视频播放：ExoPlayer (Media3)\n" +
                    "· 二维码：ZXing\n\n" +
                    "免责声明\n" +
                    "· 本应用为非官方第三方客户端，与哔哩哔哩及其关联公司无任何关系。\n" +
                    "· 应用内所有内容均来自哔哩哔哩公开接口，版权归原作者及哔哩哔哩所有。\n" +
                    "· 本应用仅供个人学习、技术交流使用，请勿用于任何商业用途。\n" +
                    "· 因使用本应用产生的一切后果，由使用者自行承担。",
                color = colors.text,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = colors.acc)
            }
        },
    )
}

/** 清空应用缓存（图片磁盘/内存缓存 + cacheDir 下的文件）。 */
@OptIn(ExperimentalCoilApi::class)
private suspend fun clearAppCache(context: Context) {
    withContext(Dispatchers.IO) {
        runCatching {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
            context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
            context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}

/** 设置分组里的一行（Google 风格：左侧图标 + 文字）。 */
@Composable
private fun SettingRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.t2,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            fontSize = 15.sp,
            color = colors.text,
            // 去掉字体上下留白，让文字视觉居中，分隔线与上下两行等距
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** 设置分组内的「断开」间隙：用页面底色把同一张卡片切成几段。 */
@Composable
private fun SettingGap() {
    Box(Modifier.fillMaxWidth().height(1.3.dp).background(biko.bg))
}

/**
 * 资料区：大头像 + 三列统计 + 「编辑资料」按钮 + 昵称/徽章 + uid + 签名。
 * 未登录时退化为头像 + 登录按钮。
 */
@Composable
private fun ProfileHeader(
    profile: UserProfile,
    stats: MyStats,
    mid: String,
    onOpenLogin: () -> Unit,
    onOpenRelation: (Int) -> Unit,
    onEditProfile: () -> Unit,
) {
    val colors = biko
    val loggedIn = profile.isLogin

    if (!loggedIn) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(face = profile.face, badge = "", size = 96, onClick = onOpenLogin)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                LoginButton(onClick = onOpenLogin)
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(face = profile.face, badge = stats.avatarBadge, size = 96, onClick = null)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                // 三列统计：关注 / 粉丝 / 获赞
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    // 三列等宽平分，中心间距一致（比居中聚拢更舒展）
                    MeStatColumn("关注", stats.following, Modifier.weight(1f), onClick = { onOpenRelation(0) })
                    MeStatColumn("粉丝", stats.follower, Modifier.weight(1f), onClick = { onOpenRelation(1) })
                    MeStatColumn("获赞", stats.likes, Modifier.weight(1f))
                }
                // 编辑资料
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(EditNavy)
                        .clickable(onClick = onEditProfile),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "编辑资料",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }

        // 昵称 + LV 徽章 + 大会员标签
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (profile.uname.isNotEmpty()) profile.uname else "未登录",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = parseHexColor(stats.nicknameColor) ?: HeroText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (profile.level > 0) {
                LevelChip(profile.level)
            }
            if (stats.vipLabel.isNotEmpty()) {
                VipChip(stats.vipLabel)
            }
        }

        // uid 胶囊
        if (mid.isNotEmpty()) {
            Text(
                text = "uid: $mid",
                fontSize = 13.sp,
                color = colors.t2,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.card2)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        // 签名
        Text(
            text = stats.sign.ifEmpty { "该用户还没有签名" },
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colors.text,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

/** 大头像（可选右下角大会员角标）。 */
@Composable
private fun Avatar(face: String, badge: String, size: Int, onClick: (() -> Unit)?) {
    Box(modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        val avatarModifier = Modifier.size(size.dp).clip(CircleShape)
        if (face.isEmpty()) {
            Image(
                painter = painterResource(R.drawable.ic_default_avatar),
                contentDescription = null,
                modifier = avatarModifier,
            )
        } else {
            NetworkImage(url = face, modifier = avatarModifier)
        }
        if (badge.isNotEmpty()) {
            NetworkImage(
                url = badge,
                modifier = Modifier.align(Alignment.BottomEnd).size((size / 4).dp),
            )
        }
    }
}

@Composable
private fun MeStatColumn(label: String, value: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = biko
    Column(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (value < 0) "--" else formatCount(value.toInt()),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text,
        )
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = colors.t3,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun VipChip(label: String) {
    val colors = biko
    Text(
        text = label,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.acc)
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
    )
}

@Composable
private fun LoginButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = "登录 / 注册",
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(biko.acc)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** 把 "#FB7299" 这类大会员昵称色解析成 Color，失败返回 null。 */
private fun parseHexColor(hex: String): Color? {
    if (!hex.startsWith("#")) return null
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
}

@Composable
private fun LevelChip(level: Int) {
    Text(
        text = "Lv$level",
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        // 去掉字体上下留白，色块更扁
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(LvOrange)
            .padding(horizontal = 5.dp, vertical = 2.5.dp),
    )
}
