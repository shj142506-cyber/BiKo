package com.biko.client.data

import com.biko.client.data.model.ChatMessage
import com.biko.client.data.model.ChatPage
import com.biko.client.data.model.PrivateSession
import com.biko.client.data.remote.PrivateMessageItemDto
import com.biko.client.data.remote.fixUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// 私信（api.vc.bilibili.com）
// ---------------------------------------------------------------------------

private const val VC_BASE = "https://api.vc.bilibili.com/"
private const val VC_SESSIONS = VC_BASE + "session_svr/v1/session_svr/get_sessions"
private const val VC_MESSAGES = VC_BASE + "svr_sync/v1/svr_sync/fetch_session_msgs"
private const val VC_SEND = VC_BASE + "web_im/v1/web_im/send_msg"
private const val VC_ACK = VC_BASE + "session_svr/v1/session_svr/update_ack"
private const val VC_SET_TOP = VC_BASE + "session_svr/v1/session_svr/set_top"
private const val VC_REMOVE_SESSION = VC_BASE + "session_svr/v1/session_svr/remove_session"
private const val VC_SET_DND = VC_BASE + "link_setting/v1/link_setting/set_msg_dnd"
private const val VC_UPDATE_INTERCEPT = VC_BASE + "session_svr/v1/session_svr/update_intercept"
private const val VC_BATCH_DUSTBIN_ACK = VC_BASE + "session_svr/v1/session_svr/batch_update_dustbin_ack"
private const val VC_BATCH_RM_DUSTBIN = VC_BASE + "session_svr/v1/session_svr/batch_rm_dustbin"

private val privateMsgJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** 会话用户信息缓存（mid -> 昵称/头像），跨刷新复用，避免每次都补拉。 */
private val privateUserCache = ConcurrentHashMap<Long, Pair<String, String>>()

/** 设备标识，进程内固定即可（BiliPai 同样缓存一个随机 UUID）。 */
private val privateDeviceId: String by lazy { UUID.randomUUID().toString() }

/** 会话列表。session_type：4=全部（默认）。 */
suspend fun BiliRepository.getPrivateSessions(
    sessionType: Int = 4,
    size: Int = 50,
    endTs: Long = 0,
): List<PrivateSession> {
    val data = api.privateSessions(
        VC_SESSIONS,
        sessionType = sessionType,
        size = size,
        endTs = endTs,
    ).requireOk("获取私信会话")
    val rawSessions = data.sessionList.orEmpty().map { s ->
        val last = s.lastMsg
        PrivateSession(
            talkerId = s.talkerId,
            sessionType = s.sessionType,
            name = s.accountInfo?.name.orEmpty().ifEmpty { s.groupName },
            avatar = s.accountInfo?.avatarUrl.orEmpty().ifEmpty { s.groupCover }.fixUrl(),
            unread = s.unreadCount,
            preview = last?.let { privateSessionPreview(it.content, it.msgType) }.orEmpty(),
            time = last?.timestamp ?: s.sessionTs,
            topTs = s.topTs,
            isDnd = s.isDnd == 1,
            isIntercept = s.isIntercept == 1,
        )
    }
    // 先用缓存兜一层（同一 mid 反复刷新时无需再次请求）。
    val sessions = rawSessions.map { s ->
        val cached = s.talkerId.takeIf { it > 0 }?.let { privateUserCache[it] } ?: return@map s
        s.copy(name = s.name.ifEmpty { cached.first }, avatar = s.avatar.ifEmpty { cached.second })
    }
    // 接口对普通用户会话不下发 account_info（BAC 文档：仅系统会话下发），
    // 昵称 / 头像任一为空都要按 mid 补拉用户卡片（与 BiliPai 判定一致）。
    val missing = sessions.filter {
        it.sessionType == 1 && it.talkerId > 0 && (it.avatar.isEmpty() || it.name.isEmpty())
    }
    if (missing.isEmpty()) return sessions
    val mids = missing.map { it.talkerId }.distinct()
    val info = mutableMapOf<Long, Pair<String, String>>()
    // 逐个补拉用户卡片；限并发（3）+ 间隔，避免突发请求被风控静默拒绝。
    mids.chunked(3).forEach { chunk ->
        coroutineScope {
            chunk.map { mid -> async { mid to fetchUserBasic(mid) } }
                .map { it.await() }
                .forEach { (mid, v) ->
                    if (v.first.isNotEmpty() || v.second.isNotEmpty()) info[mid] = v
                }
        }
        delay(80)
    }
    info.forEach { (mid, v) -> privateUserCache[mid] = v }
    return sessions.map { s ->
        val (name, avatar) = info[s.talkerId] ?: return@map s
        s.copy(name = s.name.ifEmpty { name }, avatar = s.avatar.ifEmpty { avatar })
    }
}

/** 用户卡片补拉：(昵称, 头像)，失败返回空对。 */
private suspend fun BiliRepository.fetchUserBasic(mid: Long): Pair<String, String> = runCatching {
    val card = api.userCard(mid.toString(), photo = true).data?.card
    card?.name.orEmpty() to card?.face.orEmpty().fixUrl()
}.getOrDefault("" to "")

/** 拉取聊天记录。[endSeqno] 传上一页的 minSeqno，返回按时间正序（旧→新）。 */
suspend fun BiliRepository.getPrivateMessages(
    talkerId: Long,
    sessionType: Int = 1,
    size: Int = 30,
    endSeqno: Long = 0,
): ChatPage {
    val data = api.privateMessages(
        VC_MESSAGES,
        talkerId = talkerId,
        sessionType = sessionType,
        size = size,
        endSeqno = endSeqno,
    ).requireOk("获取聊天记录")
    val messages = data.messages.orEmpty().map { it.toChatMessage() }.sortedBy { it.seqno }
    return ChatPage(
        messages = messages,
        hasMore = data.hasMore == 1,
        minSeqno = data.minSeqno,
        maxSeqno = data.maxSeqno,
    )
}

/** 发送文本私信。 */
suspend fun BiliRepository.sendPrivateText(
    receiverId: Long,
    content: String,
    receiverType: Int = 1,
) {
    val csrf = csrf()
    val body = mapOf(
        "msg[sender_uid]" to session.mid(),
        "msg[receiver_id]" to receiverId.toString(),
        "msg[receiver_type]" to receiverType.toString(),
        "msg[msg_type]" to "1",
        "msg[content]" to buildTextContent(content),
        "msg[timestamp]" to (System.currentTimeMillis() / 1000).toString(),
        "msg[dev_id]" to privateDeviceId,
        "msg[new_face_version]" to "1",
        "csrf" to csrf,
        "csrf_token" to csrf,
    )
    val resp = api.sendPrivateMsg(VC_SEND, body)
    if (resp.code != 0) throw sendError(resp.code, resp.message)
}

/** 上报已读（进入会话 / 收到新消息后调用）。 */
suspend fun BiliRepository.markPrivateRead(
    talkerId: Long,
    sessionType: Int,
    ackSeqno: Long,
) {
    val csrf = csrf()
    val body = mapOf(
        "talker_id" to talkerId.toString(),
        "session_type" to sessionType.toString(),
        "ack_seqno" to ackSeqno.toString(),
        "csrf" to csrf,
        "csrf_token" to csrf,
    )
    api.updatePrivateAck(VC_ACK, body).checkOk("上报已读")
}

/** 置顶 / 取消置顶会话。 */
suspend fun BiliRepository.setPrivateSessionTop(
    talkerId: Long,
    sessionType: Int,
    top: Boolean,
) {
    val csrf = csrf()
    val body = mapOf(
        "talker_id" to talkerId.toString(),
        "session_type" to sessionType.toString(),
        "op_type" to if (top) "0" else "1",
        "csrf" to csrf,
        "csrf_token" to csrf,
    )
    api.privateSetTop(VC_SET_TOP, body).checkOk("置顶会话")
}

/** 删除会话（仅从列表移除，不删聊天记录）。 */
suspend fun BiliRepository.removePrivateSession(
    talkerId: Long,
    sessionType: Int,
) {
    val csrf = csrf()
    val body = mapOf(
        "talker_id" to talkerId.toString(),
        "session_type" to sessionType.toString(),
        "csrf" to csrf,
        "csrf_token" to csrf,
    )
    api.privateRemoveSession(VC_REMOVE_SESSION, body).checkOk("删除会话")
}

/** 开启 / 关闭会话免打扰。 */
suspend fun BiliRepository.setPrivateSessionDnd(
    talkerId: Long,
    sessionType: Int,
    enable: Boolean,
) {
    val csrf = csrf()
    val ownUid = session.mid()
    val body = buildMap {
        put("uid", ownUid)
        put("setting", if (enable) "1" else "0")
        if (sessionType == 2) put("dnd_group_id", talkerId.toString())
        else put("dnd_uid", talkerId.toString())
        put("csrf", csrf)
        put("csrf_token", csrf)
    }
    api.privateSetDnd(VC_SET_DND, body).checkOk("设置免打扰")
}

/** 移入 / 移出拦截列表。 */
suspend fun BiliRepository.setPrivateSessionIntercept(
    talkerId: Long,
    sessionType: Int,
    intercept: Boolean,
) {
    val csrf = csrf()
    val body = mapOf(
        "talker_id" to talkerId.toString(),
        "session_type" to sessionType.toString(),
        "status" to if (intercept) "1" else "0",
        "csrf" to csrf,
        "csrf_token" to csrf,
    )
    api.privateUpdateIntercept(VC_UPDATE_INTERCEPT, body).checkOk("拦截会话")
}

/** 拦截区全部已读。 */
suspend fun BiliRepository.markDustbinRead() {
    val csrf = csrf()
    val body = mapOf("csrf" to csrf, "csrf_token" to csrf)
    api.privateBatchDustbinAck(VC_BATCH_DUSTBIN_ACK, body).checkOk("拦截区已读")
}

/** 清空拦截列表。 */
suspend fun BiliRepository.clearDustbinSessions() {
    val csrf = csrf()
    val body = mapOf("csrf" to csrf, "csrf_token" to csrf)
    api.privateBatchRmDustbin(VC_BATCH_RM_DUSTBIN, body).checkOk("清空拦截")
}

// ---------------------------------------------------------------------------
// 解析 / 映射
// ---------------------------------------------------------------------------

/** 会话列表的最后一条消息预览：优先展示解析出的正文，无法解析时回退占位文案。 */
internal fun privateSessionPreview(content: String, msgType: Int): String =
    privateContentText(content, msgType).ifEmpty { privateContentPlaceholder(msgType) }

/** 各消息类型无法解析正文时的占位文案。 */
internal fun privateContentPlaceholder(msgType: Int): String = when (msgType) {
    2 -> "[图片]"
    5 -> "[消息已撤回]"
    6 -> "[表情]"
    10 -> "[通知]"
    11 -> "[视频]"
    12 -> "[专栏]"
    13, 14, 7 -> "[分享]"
    16 -> "[自动回复]"
    18 -> "[系统提示]"
    in 301..306 -> "[粉丝团消息]"
    else -> "[消息]"
}

/**
 * 按 msg_type 解析私信正文，尽量还原可读文本；无法解析时返回空串。
 * 结构参考 bilibili-API-collect 的 msg_type 枚举（10=通知、16=关注自动回复、
 * 18=系统提示、19=AI、301~306=粉丝团系统消息、7/11/12/13/14=各类分享卡）。
 */
internal fun privateContentText(raw: String, msgType: Int): String {
    if (msgType == 2 || msgType == 5 || msgType == 6) return ""
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    if (msgType == 1) return parsePrivateText(trimmed)
    val el = runCatching { privateMsgJson.parseToJsonElement(trimmed) }.getOrNull()
    if (el !is JsonObject && el !is JsonArray) return trimmed
    val out = mutableListOf<String>()
    collectReadable(el, 0, out)
    return out.joinToString("\n")
}

/** 文本消息 content 是 {"content":"..."} JSON；非 JSON 直接返回原文。 */
private fun parsePrivateText(raw: String): String {
    if (raw.isEmpty()) return ""
    if (!raw.trimStart().startsWith("{")) return raw
    return runCatching {
        val el = privateMsgJson.parseToJsonElement(raw)
        (el as? JsonObject)?.get("content")?.jsonPrimitive?.contentOrNull ?: raw
    }.getOrDefault(raw)
}

/** 可读文本候选键（按出现顺序拼接）。 */
private val privateTextKeys = listOf(
    "title", "main_title", "text", "content", "reply_content",
    "summary", "desc", "headline", "sketch", "jump_text",
)

/** 提取时跳过的噪音键（颜色 / 图片 / 跳转 / 业务数据等）。 */
private val privateSkipKeys = setOf(
    "color_day", "color_nig", "color", "jump_url", "jump_uri",
    "jump_uri_config", "biz_content", "url", "uri", "cover",
    "thumb", "image", "image_url", "icon", "pic_url", "avatar",
)

/** 递归抽取 JSON 中的可读文本（白名单键 + 深度受限，避免卷入业务数据）。 */
private fun collectReadable(el: JsonElement, depth: Int, out: MutableList<String>) {
    if (depth > 6) return
    when (el) {
        is JsonObject -> el.forEach { (key, value) ->
            when {
                key in privateSkipKeys -> Unit
                value is JsonPrimitive && value.isString && key in privateTextKeys ->
                    pushReadable(value.content, out)
                value is JsonObject || value is JsonArray -> collectReadable(value, depth + 1, out)
                else -> Unit
            }
        }
        is JsonArray -> el.forEach { collectReadable(it, depth + 1, out) }
        else -> Unit
    }
}

/** 追加一段可读文本；若其本身是内嵌 JSON（如 msg_type=18 的 content）则二次解析。 */
private fun pushReadable(value: String, out: MutableList<String>) {
    val text = value.trim()
    if (text.isEmpty()) return
    if (text.startsWith("[") || text.startsWith("{")) {
        val inner = runCatching { privateMsgJson.parseToJsonElement(text) }.getOrNull()
        if (inner != null) {
            collectReadable(inner, 0, out)
            return
        }
    }
    if (text !in out) out.add(text)
}

/** 图片消息抽取图片地址（msg_type=2）。 */
private fun parseImageUrl(raw: String): String = runCatching {
    val el = privateMsgJson.parseToJsonElement(raw)
    val obj = el as? JsonObject ?: return@runCatching ""
    val url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url
}.getOrDefault("")

private fun PrivateMessageItemDto.toChatMessage(): ChatMessage {
    val withdrawn = msgType == 5 || sysCancel
    return ChatMessage(
        msgKey = msgKey,
        senderUid = senderUid,
        msgType = msgType,
        text = privateContentText(content, msgType),
        imageUrl = if (msgType == 2) parseImageUrl(content) else "",
        time = timestamp,
        seqno = msgSeqno,
        withdrawn = withdrawn,
    )
}

/** 文本消息 content 需要转义为 JSON 字符串（与 BiliPai PayloadFactory 一致）。 */
private fun buildTextContent(content: String): String {
    val escaped = content
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
    return "{\"content\":\"$escaped\"}"
}

/** 发送失败的友好提示（对齐 BiliPai 的错误码映射）。 */
private fun sendError(code: Int, message: String): BiliException {
    val friendly = when (code) {
        -101 -> "请先登录"
        -400 -> "请求参数错误"
        21007 -> "消息内容过长"
        21015 -> "需要绑定手机号后才能发送私信"
        21020 -> "发送频率过快，请稍后再试"
        21026 -> "不能给自己发送私信"
        21046 -> "发送过于频繁，请 24 小时后再试"
        21047 -> "对方未关注你，最多只能发送 1 条消息"
        25003 -> "对方隐私设置限制，无法发送"
        25005 -> "对方已将你拉黑"
        else -> message.ifEmpty { "发送失败" }
    }
    return BiliException(code, friendly, "发送私信")
}
