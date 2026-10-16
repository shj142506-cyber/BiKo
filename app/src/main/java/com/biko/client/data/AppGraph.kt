package com.biko.client.data

import android.content.Context
import com.biko.client.data.local.DislikeStore
import com.biko.client.data.local.DownloadStore
import com.biko.client.data.local.HistoryStore
import com.biko.client.data.local.NightModeStore
import com.biko.client.data.local.PlaybackProgressStore
import com.biko.client.data.local.PlayerSettingsStore
import com.biko.client.data.local.SearchHistoryStore
import com.biko.client.data.local.SessionStore
import com.biko.client.data.remote.BiliApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

const val BILI_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
const val BILI_REFERER = "https://www.bilibili.com/"

/**
 * api.bilibili.com 的 Web 接口 UA：必须是桌面浏览器 UA。
 * B 站对写操作（投币 / 点赞 / 评论）会按浏览器做同源风控校验，
 * 移动 UA 容易被判成脚本流量；BiliPai 对 api 域名也统一用桌面 UA + Origin。
 */
const val BILI_WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/** api.bilibili.com 的 Origin：写接口缺这个头会被风控拦下。 */
const val BILI_ORIGIN = "https://www.bilibili.com"

/** 动态 / 消息接口专用 Referer（t.bilibili.com 域），可降低 -412 风控概率。 */
const val DYNAMIC_REFERER = "https://t.bilibili.com/"

/** 动态 / 消息接口专用 Origin。 */
const val DYNAMIC_ORIGIN = "https://t.bilibili.com"

/**
 * 私信接口域（api.vc.bilibili.com）专用 Referer / Origin。
 * Web IM 页面在 message.bilibili.com，其跨域调用 api.vc，因此 Origin 用 message 域。
 */
const val VC_REFERER = "https://message.bilibili.com/"
const val VC_ORIGIN = "https://message.bilibili.com"

/**
 * 播放（m4s 分片）请求专用 UA，必须是桌面 UA。
 * 实测：B 站 CDN 对带 Range 的分片请求会校验 UA ——
 * upos / mountaintoys 等节点用移动 UA 直接 403，桌面 UA 返回 206；
 * 只有 mcdn 节点两种 UA 都放行。所以播放器不能复用上面的移动 UA。
 */
const val BILI_PLAY_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** 轻量依赖容器（单例）。应用启动时初始化。 */
object AppGraph {

    lateinit var repository: BiliRepository
        private set
    /** 应用级 Context（供 ContentResolver 读取待上传图片等）。 */
    lateinit var appContext: Context
        private set
    lateinit var session: SessionStore
        private set
    lateinit var night: NightModeStore
        private set
    lateinit var playback: PlaybackProgressStore
        private set
    lateinit var playerSettings: PlayerSettingsStore
        private set
    /** 本地「不感兴趣」名单（B 站 Web 端没有可用的屏蔽接口，按本地负反馈处理）。 */
    lateinit var dislike: DislikeStore
        private set
    /** 本地搜索历史（关键词）。 */
    lateinit var searchHistory: SearchHistoryStore
        private set
    /** 离线缓存任务持久化。 */
    lateinit var downloads: DownloadStore
        private set

    /** 与页面生命周期无关的后台任务（如退出播放页后上报观看进度）。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun fireAndForget(block: suspend () -> Unit) {
        appScope.launch { runCatching { block() } }
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        this.appContext = appContext
        val sessionStore = SessionStore(appContext)
        night = NightModeStore(appContext)

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(BiliAuthInterceptor(sessionStore))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(BiliApi::class.java)
        val history = HistoryStore(appContext)

        repository = BiliRepository(api, sessionStore, history)
        session = sessionStore
        playback = PlaybackProgressStore(appContext)
        playerSettings = PlayerSettingsStore(appContext)
        dislike = DislikeStore(appContext)
        searchHistory = SearchHistoryStore(appContext)
        downloads = DownloadStore(appContext)
        DownloadManager.init(appContext, downloads)
    }
}

/** 统一注入 UA、Referer 与 Cookie，并把响应头下发的会话 Cookie 存起来。 */
private class BiliAuthInterceptor(
    private val session: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // api.bilibili.com 的 Web 接口按浏览器同源校验：桌面 UA + Origin，
        // 缺 Origin / 用移动 UA 时投币这类写操作容易被风控拒绝
        val host = original.url.host
        val path = original.url.encodedPath
        val isApiWeb = host == "api.bilibili.com" || host == "message.bilibili.com"
        // 私信接口域：UA/Origin 与 Web IM 保持一致，否则写操作易被风控。
        val isVc = host == "api.vc.bilibili.com"
        // 动态 / 消息接口的 Referer 必须是 t.bilibili.com，可降低 -412 风控概率。
        // 注意：不要整体匹配 "/x/dynamic"，避免影响图片上传 x/dynamic/feed/draw/upload_bfs。
        val isDynamicOrMsg = path.startsWith("/x/polymer/web-dynamic") ||
            path.startsWith("/x/msgfeed") ||
            path.startsWith("/x/sys-msg")
        val referer = when {
            isVc -> VC_REFERER
            isDynamicOrMsg -> DYNAMIC_REFERER
            else -> BILI_REFERER
        }
        val builder = original.newBuilder()
            .header("User-Agent", if (isApiWeb || isVc) BILI_WEB_USER_AGENT else BILI_USER_AGENT)
            .header("Referer", referer)
        if (isApiWeb) {
            builder.header("Origin", if (isDynamicOrMsg) DYNAMIC_ORIGIN else BILI_ORIGIN)
        } else if (isVc) {
            builder.header("Origin", VC_ORIGIN)
        }
        val cookie = session.cookie()
        // B 站风控要求请求携带设备指纹 buvid3；登录响应未下发时兜底生成一个，
        // 否则批量用户卡片补拉会被当成异常流量返回 -352（BiliPai 同款处理）。
        val isBiliHost = host == "bilibili.com" || host.endsWith(".bilibili.com")
        val cookieWithBuvid = if (isBiliHost && !cookie.contains("buvid3=")) {
            val b3 = "buvid3=${session.ensureBuvid3()}"
            if (cookie.isEmpty()) b3 else "$cookie; $b3"
        } else {
            cookie
        }
        if (cookieWithBuvid.isNotEmpty()) builder.header("Cookie", cookieWithBuvid)
        val response = chain.proceed(builder.build())
        session.captureCookies(response.headers("Set-Cookie"))
        return response
    }
}
