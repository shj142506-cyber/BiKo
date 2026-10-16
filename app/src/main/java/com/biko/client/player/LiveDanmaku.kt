package com.biko.client.player

import android.os.SystemClock
import com.biko.client.data.model.DanmakuItem
import com.biko.client.data.model.LiveChatItem
import com.biko.client.data.model.LiveRoomStats
import com.biko.client.data.model.LiveSuperChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import kotlin.math.min
import kotlin.math.pow

private const val HEADER_SIZE = 16
private const val OP_HEARTBEAT = 2
private const val OP_HEARTBEAT_REPLY = 3
private const val OP_MESSAGE = 5
private const val OP_AUTH = 7
private const val OP_AUTH_REPLY = 8
private const val HEARTBEAT_INTERVAL = 30_000L
private const val MAX_RETRY_DELAY = 10_000L
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * 直播弹幕：B 站直播间的弹幕走 WebSocket（broadcastlv）。
 * 只处理 DANMU_MSG（聊天弹幕），并声明 protover=2（zlib），避免引入 brotli 解压库。
 * 连接失败不影响直播播放，只是没有弹幕。
 *
 * [host]/[token] 由仓库层通过 WBI 签名的 getDanmuInfo 拉取后注入
 * （2025-06 起该接口要求 buvid3 + WBI 签名，不能再直连）。
 *
 * 与 BiliPai 对齐的关键点：
 * - 握手需带 User-Agent / Origin 请求头，否则容易被风控断开；
 * - 只有在收到 op=8 且 code==0 的认证回执后才启动心跳；
 * - 心跳包 body 为 "[object Object]"；
 * - 断开后按指数退避自动重连。
 */
class LiveDanmakuClient(
    private val roomId: Long,
    private val host: String,
    private val token: String,
    private val buvid: String = "",
    /** 当前登录用户 uid，用于标记「我」发的弹幕。 */
    private val selfUid: Long = 0,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var reconnectJob: Job? = null

    @Volatile private var authenticated = false
    @Volatile private var reconnectAllowed = false
    private var retryCount = 0

    private val channel = Channel<DanmakuItem>(Channel.BUFFERED)
    val messages: Flow<DanmakuItem> = channel.receiveAsFlow()

    private val chatChannel = Channel<LiveChatItem>(Channel.BUFFERED)

    /** 聊天列表（含用户名 / 粉丝牌 / 等级等）。 */
    val chats: Flow<LiveChatItem> = chatChannel.receiveAsFlow()

    private val superChatChannel = Channel<LiveSuperChat>(Channel.BUFFERED)

    /** 醒目留言（SC）。 */
    val superChats: Flow<LiveSuperChat> = superChatChannel.receiveAsFlow()

    private val statsChannel = Channel<LiveRoomStats>(Channel.BUFFERED)

    /** 直播间实时状态：人气 / 看过 / 标题 / 分区。 */
    val stats: Flow<LiveRoomStats> = statsChannel.receiveAsFlow()

    fun start() {
        if (host.isEmpty()) return
        reconnectAllowed = true
        retryCount = 0
        connect()
    }

    fun close() {
        reconnectAllowed = false
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeat?.cancel()
        heartbeat = null
        runCatching { socket?.close(1000, null) }
        socket = null
        channel.close()
        chatChannel.close()
        superChatChannel.close()
        statsChannel.close()
        scope.cancel()
    }

    private fun connect() {
        authenticated = false
        heartbeat?.cancel()
        val request = Request.Builder()
            .url("wss://$host/sub")
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://live.bilibili.com")
            .build()
        socket = http.newWebSocket(request, Listener())
    }

    private fun scheduleReconnect() {
        if (!reconnectAllowed || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val delayMs = min(1000.0 * 2.0.pow(retryCount), MAX_RETRY_DELAY.toDouble()).toLong()
            delay(delayMs)
            if (reconnectAllowed) {
                retryCount++
                connect()
            }
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive) {
                if (!authenticated) return@launch
                runCatching {
                    webSocket.send(
                        packet(OP_HEARTBEAT, "[object Object]".toByteArray(Charsets.UTF_8)).toByteString(),
                    )
                }
                delay(HEARTBEAT_INTERVAL)
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = webSocket
            runCatching { webSocket.send(authPacket().toByteString()) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            runCatching { handle(bytes.toByteArray()) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { handleCommand(text) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            heartbeat?.cancel()
            authenticated = false
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            heartbeat?.cancel()
            authenticated = false
            scheduleReconnect()
        }
    }

    private fun authPacket(): ByteArray {
        val payload = JSONObject().apply {
            put("uid", selfUid)
            put("roomid", roomId)
            put("protover", 2)
            put("buvid", buvid)
            put("platform", "web")
            put("type", 2)
            put("key", token)
        }.toString()
        return packet(OP_AUTH, payload.toByteArray(Charsets.UTF_8))
    }

    private fun packet(op: Int, body: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + body.size)
        buffer.putInt(HEADER_SIZE + body.size)
        buffer.putShort(HEADER_SIZE.toShort())
        buffer.putShort(1.toShort())
        buffer.putInt(op)
        buffer.putInt(1)
        buffer.put(body)
        return buffer.array()
    }

    /** 一个 TCP 包内可能包含多帧，ver=2 时整包还经过 zlib 压缩，先解压再递归解析。 */
    private fun handle(raw: ByteArray) {
        var offset = 0
        while (offset + HEADER_SIZE <= raw.size) {
            val header = ByteBuffer.wrap(raw, offset, HEADER_SIZE)
            val total = header.int
            val headerLen = header.short.toInt()
            val ver = header.short.toInt()
            val op = header.int
            if (total < HEADER_SIZE || offset + total > raw.size) break
            val body = raw.copyOfRange(offset + headerLen, offset + total)
            when {
                ver == 2 -> runCatching { handle(inflate(body)) }
                op == OP_MESSAGE -> runCatching { handleCommand(String(body, Charsets.UTF_8)) }
                op == OP_AUTH_REPLY -> runCatching { handleAuthReply(String(body, Charsets.UTF_8)) }
                op == OP_HEARTBEAT_REPLY -> runCatching { handleHeartbeatReply(body) }
            }
            offset += total
        }
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        return out.toByteArray()
    }

    /** 心跳回执：body 前 4 字节为大端整数，即当前直播间的人气值。 */
    private fun handleHeartbeatReply(body: ByteArray) {
        if (body.size < 4) return
        val popular = ByteBuffer.wrap(body).int
        statsChannel.trySend(LiveRoomStats(online = popular))
    }

    /** 认证回执：code==0 才算握手成功，随后启动心跳；失败则停止重连。 */
    private fun handleAuthReply(body: String) {
        val code = runCatching { JSONObject(body).optInt("code", -1) }.getOrDefault(-1)
        if (code == 0) {
            if (!authenticated) {
                authenticated = true
                retryCount = 0
                socket?.let { startHeartbeat(it) }
            }
        } else {
            reconnectAllowed = false
            runCatching { socket?.close(4001, "Auth Failed") }
        }
    }

    /** cmd 分发：弹幕 / 醒目留言 / 看过 / 房间变更 / 进房提示。 */
    private fun handleCommand(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (val cmd = json.optString("cmd")) {
            "SUPER_CHAT_MESSAGE" -> handleSuperChat(json)
            else -> when {
                cmd.startsWith("DANMU_MSG") -> handleDanmuMsg(json)
                cmd.startsWith("WATCHED_CHANGE") -> handleWatchedChange(json)
                cmd.startsWith("ONLINE_RANK_COUNT") -> handleOnlineRankCount(json)
                cmd.startsWith("ROOM_CHANGE") -> handleRoomChange(json)
                cmd.startsWith("INTERACT_WORD") -> handleInteractWord(json)
            }
        }
    }

    private fun handleDanmuMsg(json: JSONObject) {
        val info = json.optJSONArray("info") ?: return
        val message = info.optString(1)
        if (message.isBlank()) return
        val meta = info.optJSONArray(0)
        val color = meta?.optInt(3) ?: 0xFFFFFF
        val mode = meta?.optInt(1) ?: 1
        // info[0][13] 为表情弹幕：含 url / emoticon_unique，整条弹幕应渲染成表情图
        val emoteUrl = meta?.optJSONObject(13)?.optString("url")?.takeIf { it.isNotBlank() }
        channel.trySend(
            DanmakuItem(
                // 直播没有时间轴，用「到达时刻」当做出场时间
                timeMs = SystemClock.elapsedRealtime(),
                mode = mode,
                fontSize = meta?.optInt(2) ?: 25,
                color = color,
                text = message,
            ),
        )

        // 聊天列表：解析用户名 / 粉丝牌 / 等级 / 大航海
        val user = info.optJSONArray(2)
        val medal = info.optJSONArray(3)
        val uid = user?.optLong(0) ?: 0L
        chatChannel.trySend(
            LiveChatItem(
                uid = uid,
                uname = user?.optString(1).orEmpty(),
                text = message,
                color = color,
                mode = mode,
                isSelf = selfUid > 0 && uid == selfUid,
                medalName = medal?.optString(1).orEmpty(),
                medalLevel = medal?.optInt(0) ?: 0,
                medalColor = medal?.optInt(4) ?: 0,
                userLevel = info.optJSONArray(4)?.optInt(0) ?: 0,
                guardLevel = info.optInt(7),
                isAdmin = user?.optInt(2) == 1,
                emoteUrl = emoteUrl,
            ),
        )
    }

    /** 醒目留言（SC）：data.user_info 里带昵称 / 头像，background_color 为背景色。 */
    private fun handleSuperChat(json: JSONObject) {
        val data = json.optJSONObject("data") ?: return
        val user = data.optJSONObject("user_info")
        superChatChannel.trySend(
            LiveSuperChat(
                id = data.optLong("id"),
                uid = data.optLong("uid"),
                uname = user?.optString("uname").orEmpty(),
                face = user?.optString("face").orEmpty(),
                text = data.optString("message"),
                bgColor = parseLiveColor(data.optString("background_color")),
                duration = data.optInt("time"),
                price = data.optInt("price"),
                time = data.optLong("start_time"),
            ),
        )
    }

    /** 看过人数变更：直接用服务端排版的 text_large，保证与其他客户端数值一致。 */
    private fun handleWatchedChange(json: JSONObject) {
        val data = json.optJSONObject("data") ?: return
        statsChannel.trySend(
            LiveRoomStats(
                watched = data.optInt("num").takeIf { it > 0 },
                watchedText = data.optString("text_large").takeIf { it.isNotBlank() },
            ),
        )
    }

    /** 高能观众数（「高能观众(N)」）。 */
    private fun handleOnlineRankCount(json: JSONObject) {
        val data = json.optJSONObject("data") ?: return
        statsChannel.trySend(LiveRoomStats(onlineRank = data.optInt("count")))
    }

    /** 直播间标题 / 分区变更。 */
    private fun handleRoomChange(json: JSONObject) {
        val data = json.optJSONObject("data") ?: return
        statsChannel.trySend(
            LiveRoomStats(
                title = data.optString("title").takeIf { it.isNotBlank() },
                area = data.optString("area_name").takeIf { it.isNotBlank() },
            ),
        )
    }

    /** 进房提示：msg_type==1 表示进入直播间（2 关注 / 3 分享忽略）。 */
    private fun handleInteractWord(json: JSONObject) {
        val data = json.optJSONObject("data") ?: return
        if (data.optInt("msg_type") != 1) return
        val uname = data.optString("uname")
        if (uname.isBlank()) return
        chatChannel.trySend(
            LiveChatItem(uid = data.optLong("uid"), uname = uname, text = "进入了直播间", enter = true),
        )
    }

    /** 直播颜色字段容错：支持 "#RRGGBB" / "0xRRGGBB" / 十进制或十六进制数字串。 */
    private fun parseLiveColor(raw: String): Int {
        val s = raw.trim()
        if (s.isEmpty()) return 0
        return runCatching {
            when {
                s.startsWith("#") -> s.substring(1).toInt(16)
                s.startsWith("0x", ignoreCase = true) -> s.substring(2).toInt(16)
                s.all { it.isDigit() } -> s.toInt()
                else -> s.toInt(16)
            }
        }.getOrDefault(0)
    }
}
