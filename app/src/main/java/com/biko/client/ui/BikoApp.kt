package com.biko.client.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biko.client.R
import com.biko.client.data.AppGraph
import com.biko.client.data.ChatSelection
import com.biko.client.data.CollectionSelection
import com.biko.client.data.CollectionSelectionData
import com.biko.client.data.LikeDetailSelection
import com.biko.client.data.LiveSelection
import com.biko.client.data.SpaceSelection
import com.biko.client.data.SpaceUser
import com.biko.client.data.TopicSelection
import com.biko.client.data.TopicSelectionData
import com.biko.client.data.model.LiveRoom
import com.biko.client.data.model.PrivateSession
import com.biko.client.data.model.SpaceCollection
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.components.bouncyClickable
import com.biko.client.ui.screens.BangumiFollowScreen
import com.biko.client.ui.screens.ArticleDetailScreen
import com.biko.client.ui.screens.ChatScreen
import com.biko.client.ui.screens.CollectionDetailScreen
import com.biko.client.ui.screens.DynamicScreen
import com.biko.client.ui.screens.DownloadScreen
import com.biko.client.ui.screens.FavoritesScreen
import com.biko.client.ui.screens.HistoryScreen
import com.biko.client.ui.screens.HomeScreen
import com.biko.client.ui.screens.LikeDetailScreen
import com.biko.client.ui.screens.LiveDetailScreen
import com.biko.client.ui.screens.LoginScreen
import com.biko.client.ui.screens.MeScreen
import com.biko.client.ui.screens.MessageScreen
import com.biko.client.ui.screens.MyFavoritesScreen
import com.biko.client.ui.screens.PublishDynamicScreen
import com.biko.client.ui.screens.RankScreen
import com.biko.client.ui.screens.RelationListScreen
import com.biko.client.ui.screens.SearchScreen
import com.biko.client.ui.screens.ShortVideoEntry
import com.biko.client.ui.screens.SpaceScreen
import com.biko.client.ui.screens.SpaceSearchScreen
import com.biko.client.ui.screens.TopicDetailScreen
import com.biko.client.ui.screens.VideoDetailScreen
import com.biko.client.ui.screens.WatchLaterScreen
import com.biko.client.ui.theme.biko

private const val ROUTE_HOME = "home"
private const val ROUTE_DYNAMIC = "dynamic"
private const val ROUTE_PUBLISH_DYNAMIC = "publish_dynamic"
private const val ROUTE_MESSAGE = "message"
private const val ROUTE_LIKE_DETAIL = "msg_like_detail"
private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_MY_FAV = "my_favorites"
private const val ROUTE_WATCH_LATER = "watch_later"
private const val ROUTE_SHORT = "short_video"
private const val ROUTE_RANK = "rank"
private const val ROUTE_ME = "me"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_VIDEO = "video/{bvid}?portrait={portrait}&type={type}&tab={tab}"
private const val ROUTE_LIVE = "live/{roomId}"
private const val ROUTE_SPACE = "space/{mid}?tab={tab}"
private const val ROUTE_SPACE_SEARCH = "space_search/{mid}"
private const val ROUTE_CHAT = "chat/{talkerId}/{sessionType}"
private const val ROUTE_RELATION = "relation_list?tab={tab}"
private const val ROUTE_BANGUMI_FOLLOW = "bangumi_follow"
private const val ROUTE_ARTICLE = "article/{cvId}"
private const val ROUTE_COLLECTION = "collection/{mid}/{id}/{isSeason}"
private const val ROUTE_TOPIC = "topic/{topicId}"

private val bottomRoutes = setOf(ROUTE_HOME, ROUTE_FAVORITES, ROUTE_MY_FAV, ROUTE_RANK, ROUTE_ME)

/** 底栏整体下移的量（约 2.5mm）。 */
private val BOTTOM_LIFT = 16.dp

/** 底栏图标与文字之间的间距（去掉字体留白后会显得更紧）。 */
private val ICON_LABEL_GAP = 3.dp

/** 图标上方相对底部预留量额外多留的高度：避免图标顶着底栏上沿。 */
private val NAV_TOP_EXTRA = 6.dp

private data class Tab(val route: String, val label: String, val icon: Int)

private val tabs = listOf(
    Tab(ROUTE_HOME, "首页", R.drawable.ic_nav_home),
    Tab(ROUTE_RANK, "排行", R.drawable.ic_nav_rank),
    Tab(ROUTE_FAVORITES, "订阅", R.drawable.ic_sub),
    Tab(ROUTE_MY_FAV, "收藏", R.drawable.ic_star),
    Tab(ROUTE_ME, "我的", R.drawable.ic_nav_user),
)

@Composable
fun BikoApp(
    shortcutTarget: String? = null,
    onShortcutConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 桌面图标长按快捷方式：解析后跳到对应页面
    LaunchedEffect(shortcutTarget) {
        val route = when (shortcutTarget) {
            "search" -> ROUTE_SEARCH
            "watch_later" -> ROUTE_WATCH_LATER
            "favorite" -> ROUTE_MY_FAV
            "history" -> ROUTE_HISTORY
            else -> null
        }
        if (route != null) {
            navController.navigate(route) { launchSingleTop = true }
            onShortcutConsumed()
        }
    }

    val openVideo: (String) -> Unit = { bvid ->
        navController.navigate("video/$bvid")
    }

    // 消息中心点击通知项：进入原视频并直接定位到评论区
    val openVideoComment: (String) -> Unit = { bvid ->
        navController.navigate("video/$bvid?tab=1")
    }

    // 番剧 / 影视分集：同样进详情页，但历史记录归入「番剧」分类
    val openBangumi: (String) -> Unit = { bvid ->
        navController.navigate("video/$bvid?type=bangumi")
    }

    // 直播：记录选中的直播间信息用于详情页初始化
    val openLive: (LiveRoom) -> Unit = { room ->
        LiveSelection.current = room
        navController.navigate("live/${room.roomId}")
    }

    // 专栏 / 文章阅读页
    val openArticle: (Long) -> Unit = { cvId ->
        if (cvId > 0) navController.navigate("article/$cvId")
    }

    // 合集 / 系列详情页：先暂存标题，进入后按 mid + id 拉取内容
    val openCollection: (Long, SpaceCollection) -> Unit = { mid, collection ->
        CollectionSelection.current = CollectionSelectionData(
            mid = mid,
            id = collection.id,
            isSeason = collection.isSeason,
            title = collection.title,
        )
        navController.navigate("collection/$mid/${collection.id}/${collection.isSeason}")
    }

    // 话题页：评论里的 #话题# 只有名字（id=0），进入后由页面内搜索解析出 id
    val openTopic: (String) -> Unit = { name ->
        if (name.isNotBlank()) {
            TopicSelection.current = TopicSelectionData(id = 0, name = name)
            navController.navigate("topic/0")
        }
    }

    Scaffold(
        containerColor = biko.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in bottomRoutes) {
                BottomTabBar(navController, currentRoute)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(innerPadding),
            // 原版是 Activity 直切，无过渡动画；这里同样去掉，避免退出播放页时画面滞留
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
                    onOpenVideo = openVideo,
                    onOpenLive = openLive,
                    onOpenBangumi = openBangumi,
                    // 点顶栏 logo 进短视频模式（整屏竖屏、上下滑切换）
                    onOpenShortVideo = { navController.navigate(ROUTE_SHORT) },
                    onOpenDynamic = { navController.navigate(ROUTE_DYNAMIC) },
                    onOpenMessage = { navController.navigate(ROUTE_MESSAGE) },
                )
            }
            composable(ROUTE_DYNAMIC) { entry ->
                val published by entry.savedStateHandle
                    .getStateFlow("dynamic_published", false)
                    .collectAsState()
                DynamicScreen(
                    onOpenVideo = openVideo,
                    onBack = { navController.popBackStack() },
                    onOpenPublish = { navController.navigate(ROUTE_PUBLISH_DYNAMIC) },
                    refreshSignal = published,
                    onRefreshConsumed = {
                        entry.savedStateHandle["dynamic_published"] = false
                    },
                )
            }
            composable(ROUTE_PUBLISH_DYNAMIC) {
                PublishDynamicScreen(
                    onBack = { navController.popBackStack() },
                    onPublished = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("dynamic_published", true)
                        navController.popBackStack()
                    },
                )
            }
            composable(ROUTE_MESSAGE) {
                MessageScreen(
                    onOpenComment = openVideoComment,
                    onOpenChat = { s ->
                        ChatSelection.current = s
                        navController.navigate("chat/${s.talkerId}/${s.sessionType}")
                    },
                    onOpenLikeDetail = { item ->
                        LikeDetailSelection.current = item
                        navController.navigate(ROUTE_LIKE_DETAIL)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_LIKE_DETAIL) {
                val item = LikeDetailSelection.current
                if (item != null) {
                    LikeDetailScreen(
                        item = item,
                        onOpenContent = openVideoComment,
                        onOpenUser = { mid, name, face ->
                            SpaceSelection.current = SpaceUser(mid, name, face)
                            navController.navigate("space/$mid")
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(
                route = ROUTE_CHAT,
                arguments = listOf(
                    navArgument("talkerId") { type = NavType.LongType },
                    navArgument("sessionType") { type = NavType.IntType },
                ),
            ) { entry ->
                ChatScreen(
                    talkerId = entry.arguments?.getLong("talkerId") ?: 0L,
                    sessionType = entry.arguments?.getInt("sessionType") ?: 1,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_SHORT) {
                // 短视频入口：直接进详情页的竖屏模式（可上下滑切换推荐）
                ShortVideoEntry(
                    onOpenPortraitVideo = { bvid ->
                        navController.navigate("video/$bvid?portrait=true") {
                            popUpTo(ROUTE_SHORT) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_FAVORITES) {
                FavoritesScreen(
                    onOpenVideo = openVideo,
                    onOpenSpace = { mid, name, face ->
                        if (mid > 0) {
                            SpaceSelection.current = SpaceUser(mid, name, face)
                            navController.navigate("space/$mid")
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_RANK) {
                RankScreen(
                    onOpenVideo = openVideo,
                    onBack = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(ROUTE_ME) {
                MeScreen(
                    onOpenVideo = openVideo,
                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                    onOpenHistory = { navController.navigate(ROUTE_HISTORY) },
                    onOpenWatchLater = { navController.navigate(ROUTE_WATCH_LATER) },
                    onOpenDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                    onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
                    onOpenMySpace = { mid, tab -> navController.navigate("space/$mid?tab=$tab") },
                    onOpenRelation = { tab -> navController.navigate("relation_list?tab=$tab") },
                    onOpenBangumiFollow = { navController.navigate(ROUTE_BANGUMI_FOLLOW) },
                    onBack = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = ROUTE_RELATION,
                arguments = listOf(
                    navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
            ) { entry ->
                RelationListScreen(
                    initialTab = entry.arguments?.getInt("tab") ?: 0,
                    onOpenSpace = { mid, name, face ->
                        if (mid > 0) {
                            SpaceSelection.current = SpaceUser(mid, name, face)
                            navController.navigate("space/$mid")
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_BANGUMI_FOLLOW) {
                BangumiFollowScreen(
                    onOpenBangumi = openBangumi,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_WATCH_LATER) {
                WatchLaterScreen(
                    onOpenVideo = openVideo,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_MY_FAV) {
                MyFavoritesScreen(
                    onOpenVideo = openVideo,
                    onBack = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    onOpenVideo = openVideo,
                    onOpenLive = openLive,
                    onOpenBangumi = openBangumi,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_DOWNLOADS) {
                DownloadScreen(
                    onOpenVideo = openVideo,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_SEARCH) {
                SearchScreen(
                    onOpenVideo = openVideo,
                    onOpenLive = openLive,
                    onOpenBangumi = openBangumi,
                    onOpenArticle = openArticle,
                    onOpenSpace = { mid, name, face ->
                        if (mid > 0) {
                            SpaceSelection.current = SpaceUser(mid, name, face)
                            navController.navigate("space/$mid")
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_LOGIN) {
                LoginScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = ROUTE_VIDEO,
                arguments = listOf(
                    navArgument("bvid") { type = NavType.StringType },
                    navArgument("portrait") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "video"
                    },
                    navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
            ) { entry ->
                VideoDetailScreen(
                    bvid = entry.arguments?.getString("bvid").orEmpty(),
                    startPortrait = entry.arguments?.getBoolean("portrait") ?: false,
                    historyType = entry.arguments?.getString("type") ?: "video",
                    startTab = entry.arguments?.getInt("tab") ?: 0,
                    onBack = { navController.popBackStack() },
                    onHome = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                    onOpenSpace = { mid, name, face ->
                        if (mid > 0) {
                            SpaceSelection.current = SpaceUser(mid, name, face)
                            navController.navigate("space/$mid")
                        }
                    },
                    onOpenVideo = openVideo,
                    onOpenTopic = openTopic,
                )
            }
            composable(
                route = ROUTE_SPACE,
                arguments = listOf(
                    navArgument("mid") { type = NavType.LongType },
                    navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ),
            ) { entry ->
                val mid = entry.arguments?.getLong("mid") ?: 0L
                val tab = entry.arguments?.getInt("tab") ?: 0
                SpaceScreen(
                    mid = mid,
                    initialTab = tab,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = openVideo,
                    onOpenArticle = openArticle,
                    onOpenCollection = { collection -> openCollection(mid, collection) },
                    onOpenSearch = { navController.navigate("space_search/$mid") },
                    // 空间页「发消息」：直接进入与该用户的私信会话
                    onOpenChat = { talkerId, name, face ->
                        ChatSelection.current = PrivateSession(
                            talkerId = talkerId,
                            sessionType = 1,
                            name = name,
                            avatar = face,
                        )
                        navController.navigate("chat/$talkerId/1")
                    },
                )
            }
            composable(
                route = ROUTE_SPACE_SEARCH,
                arguments = listOf(navArgument("mid") { type = NavType.LongType }),
            ) { entry ->
                SpaceSearchScreen(
                    mid = entry.arguments?.getLong("mid") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = openVideo,
                )
            }
            composable(
                route = ROUTE_ARTICLE,
                arguments = listOf(navArgument("cvId") { type = NavType.LongType }),
            ) { entry ->
                ArticleDetailScreen(
                    cvId = entry.arguments?.getLong("cvId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenSpace = { mid, name, face ->
                        if (mid > 0) {
                            SpaceSelection.current = SpaceUser(mid, name, face)
                            navController.navigate("space/$mid")
                        }
                    },
                )
            }
            composable(
                route = ROUTE_COLLECTION,
                arguments = listOf(
                    navArgument("mid") { type = NavType.LongType },
                    navArgument("id") { type = NavType.LongType },
                    navArgument("isSeason") { type = NavType.BoolType },
                ),
            ) { entry ->
                CollectionDetailScreen(
                    mid = entry.arguments?.getLong("mid") ?: 0L,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    isSeason = entry.arguments?.getBoolean("isSeason") ?: true,
                    onOpenVideo = openVideo,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_TOPIC,
                arguments = listOf(navArgument("topicId") { type = NavType.LongType }),
            ) { entry ->
                TopicDetailScreen(
                    topicId = entry.arguments?.getLong("topicId") ?: 0L,
                    topicName = TopicSelection.current?.name.orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenVideo = openVideo,
                    onOpenSpace = { mid, name, face ->
                        if (mid > 0) {
                            SpaceSelection.current = SpaceUser(mid, name, face)
                            navController.navigate("space/$mid")
                        }
                    },
                )
            }
            composable(
                route = ROUTE_LIVE,
                arguments = listOf(navArgument("roomId") { type = NavType.LongType }),
            ) { entry ->
                LiveDetailScreen(
                    roomId = entry.arguments?.getLong("roomId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenSpace = { mid, name, face ->
                        if (mid > 0) {
                            SpaceSelection.current = SpaceUser(mid, name, face)
                            navController.navigate("space/$mid")
                        }
                    },
                )
            }
        }
    }
}

/** 原版 tabbar：54dp 高、card 底色、22dp 图标 + 10.5sp 文字（选中 acc）。 */
@Composable
private fun BottomTabBar(navController: NavHostController, currentRoute: String?) {
    val colors = biko
    // 登录后「我的」显示用户头像（原版行为），未登录或头像为空时仍用通用图标
    val profile by AppGraph.session.profile.collectAsState()
    val avatarUrl = profile.face.takeIf { profile.isLogin && it.isNotBlank() }

    // 原版底栏是贴着屏幕底部的；这里把系统导航栏的预留距离收窄一点，让图标整体下移。
    // 底部保留系统导航栏的空间（内容必须在其上方），顶部补上等量留白 + 少量额外高度，
    // 否则图标会顶着白色底栏的上沿、文字下方却留一大片空白。
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomReserve = (navBarBottom - BOTTOM_LIFT).coerceAtLeast(0.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(top = bottomReserve + NAV_TOP_EXTRA, bottom = bottomReserve),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val tint = if (selected) colors.acc else colors.inactive
                // 选中态图标轻微放大，切换底栏有回弹感
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "tab_icon_scale",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .bouncyClickable(scaleDown = 0.9f) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val iconModifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                    if (tab.route == ROUTE_ME && avatarUrl != null) {
                        // 头像本身有颜色，不能像矢量图标那样整体染色
                        NetworkImage(url = avatarUrl, modifier = iconModifier.clip(CircleShape))
                    } else {
                        androidx.compose.foundation.Image(
                            painter = painterResource(tab.icon),
                            contentDescription = tab.label,
                            colorFilter = ColorFilter.tint(tint),
                            modifier = iconModifier,
                        )
                    }
                    Text(
                        text = tab.label,
                        fontSize = 10.5.sp,
                        lineHeight = 11.sp,
                        color = tint,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        // 去掉字体上下留白，图标与文字的视觉间距才真正变小
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        modifier = Modifier.padding(top = ICON_LABEL_GAP),
                    )
                }
            }
        }
    }
}
