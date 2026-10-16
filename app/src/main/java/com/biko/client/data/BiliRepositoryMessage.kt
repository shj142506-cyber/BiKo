package com.biko.client.data

import com.biko.client.data.model.LikeDetailCard
import com.biko.client.data.model.LikeDetailPage
import com.biko.client.data.model.LikeDetailUser
import com.biko.client.data.model.MessageItem
import com.biko.client.data.model.MessageKind
import com.biko.client.data.model.MessagePage
import com.biko.client.data.model.MsgUnread
import com.biko.client.data.remote.fixUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------------------
// 消息中心
// ---------------------------------------------------------------------------

/** 系统通知接口的绝对地址（独立域名，Biko 的 baseUrl 是 api.bilibili.com）。 */
private const val SYS_MSG_BASE = "https://message.bilibili.com/x/sys-msg/query_notify_list"
private const val SYS_MSG_UPDATE_CURSOR = "https://message.bilibili.com/x/sys-msg/update_cursor"
private const val SYS_MSG_DEL = "https://message.bilibili.com/x/sys-msg/del_notify_list"

/** 删除消息接口（x/msgfeed/del）需要的类型：0=赞、1=回复、2=@。 */
const val MSGFEED_TP_LIKE = 0
const val MSGFEED_TP_REPLY = 1
const val MSGFEED_TP_AT = 2

private val msgJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** 各类消息未读数（用于主页 / 消息页角标）。 */
suspend fun BiliRepository.msgUnread(): MsgUnread {
    val data = api.msgUnread().requireOk("获取消息未读")
    return MsgUnread(reply = data.reply, at = data.at, like = data.like, sysMsg = data.sysMsg)
}

/** 回复我的。 */
suspend fun BiliRepository.msgReplies(cursorId: Long? = null, cursorTime: Long? = null): MessagePage {
    val data = api.msgReply(id = cursorId, replyTime = cursorTime).requireOk("获取回复")
    val items = data.items.map { d ->
        val content = d.item
        MessageItem(
            id = d.id,
            kind = MessageKind.REPLY,
            userName = d.user?.nickname.orEmpty(),
            avatar = d.user?.avatar.orEmpty().fixUrl(),
            title = content?.title.orEmpty(),
            content = content?.sourceContent.orEmpty(),
            quotedContent = content?.targetReplyContent.orEmpty()
                .ifEmpty { content?.rootReplyContent.orEmpty() },
            time = d.replyTime,
            uri = content?.uri.orEmpty().ifEmpty { content?.nativeUri.orEmpty() },
            cover = content?.image.orEmpty().fixUrl(),
            count = d.counts,
            extraAvatars = emptyList(),
        )
    }
    return MessagePage(
        items = items,
        cursorId = data.cursor?.id ?: 0,
        cursorTime = data.cursor?.time ?: 0,
        isEnd = data.cursor?.isEnd ?: true,
    )
}

/** @我的。 */
suspend fun BiliRepository.msgAts(cursorId: Long? = null, cursorTime: Long? = null): MessagePage {
    val data = api.msgAt(id = cursorId, atTime = cursorTime).requireOk("获取@我")
    val items = data.items.map { d ->
        val content = d.item
        MessageItem(
            id = d.id,
            kind = MessageKind.AT,
            userName = d.user?.nickname.orEmpty(),
            avatar = d.user?.avatar.orEmpty().fixUrl(),
            title = content?.title.orEmpty(),
            content = content?.sourceContent.orEmpty(),
            time = d.atTime,
            uri = content?.uri.orEmpty().ifEmpty { content?.nativeUri.orEmpty() },
            cover = content?.image.orEmpty().fixUrl(),
        )
    }
    return MessagePage(
        items = items,
        cursorId = data.cursor?.id ?: 0,
        cursorTime = data.cursor?.time ?: 0,
        isEnd = data.cursor?.isEnd ?: true,
    )
}

/**
 * 收到的赞。latest 是「最新一批」（只有首页才有），total 是「全部」（带翻页游标）。
 * 两者会重叠，需合并去重后展示，游标以 total 为准（与 BiliPai 一致）。
 */
suspend fun BiliRepository.msgLikes(cursorId: Long? = null, cursorTime: Long? = null): MessagePage {
    val data = api.msgLike(id = cursorId, likeTime = cursorTime).requireOk("获取点赞")
    val merged = (data.latest?.items.orEmpty() + data.total?.items.orEmpty()).distinctBy { it.id }
    val items = merged.map { d ->
        val content = d.item
        MessageItem(
            id = d.id,
            kind = MessageKind.LIKE,
            userName = d.users.firstOrNull()?.nickname.orEmpty(),
            avatar = d.users.firstOrNull()?.avatar.orEmpty().fixUrl(),
            title = content?.title.orEmpty(),
            content = "",
            time = d.likeTime,
            uri = content?.uri.orEmpty().ifEmpty { content?.nativeUri.orEmpty() },
            cover = content?.image.orEmpty().fixUrl(),
            count = d.counts,
            extraAvatars = d.users.drop(1).map { it.avatar.fixUrl() },
            noticeState = d.noticeState,
        )
    }
    return MessagePage(
        items = items,
        cursorId = data.total?.cursor?.id ?: 0,
        cursorTime = data.total?.cursor?.time ?: 0,
        isEnd = data.total?.cursor?.isEnd ?: true,
    )
}

/** 系统通知。 */
suspend fun BiliRepository.systemNotices(cursor: Long? = null): MessagePage {
    val url = buildString {
        append(SYS_MSG_BASE)
        append("?page_size=20&mobi_app=web&build=0")
        if (cursor != null && cursor > 0) append("&cursor=$cursor")
    }
    val list = api.sysNotices(url).requireOk("获取系统通知")
    val items = list.map { d ->
        MessageItem(
            id = d.id,
            kind = MessageKind.SYSTEM,
            title = d.title,
            content = parseSysContent(d.content),
            timeText = d.timeAt,
        )
    }
    // 首屏加载后上报已读（取最新一条的 cursor），失败不影响列表展示。
    if (cursor == null) {
        list.firstOrNull()?.cursor?.takeIf { it > 0 }?.let { markSysNoticeRead(it) }
    }
    val nextCursor = list.lastOrNull()?.cursor ?: 0
    return MessagePage(
        items = items,
        cursorId = nextCursor,
        isEnd = list.isEmpty(),
    )
}

/** 系统通知已读上报。 */
suspend fun BiliRepository.markSysNoticeRead(cursor: Long) {
    runCatching {
        api.msgSysUpdateCursor(SYS_MSG_UPDATE_CURSOR, csrf(), cursor).checkOk("系统通知已读")
    }
}

/** 删除一条「回复我的 / @我的 / 收到的赞」消息。[tp] 见 [MSGFEED_TP_*]。 */
suspend fun BiliRepository.deleteMsgfeed(tp: Int, id: Long) {
    val csrf = csrf()
    val body = mapOf(
        "tp" to tp.toString(),
        "id" to id.toString(),
        "build" to "0",
        "mobi_app" to "web",
        "csrf_token" to csrf,
        "csrf" to csrf,
    )
    api.delMsgfeed(body).checkOk("删除消息")
}

/** 删除一条系统通知。 */
suspend fun BiliRepository.deleteSysNotice(id: Long) {
    val csrf = csrf()
    val body = mapOf(
        "csrf" to csrf,
        "ids" to id.toString(),
        "type" to "4",
        "mobi_app" to "android",
    )
    api.delSysMsg(SYS_MSG_DEL, csrf = csrf, body = body).checkOk("删除系统通知")
}

/** 点赞通知开关。[noticeState]：0=接收通知、1=不再通知。 */
suspend fun BiliRepository.setLikeNotice(id: Long, noticeState: Int) {
    val csrf = csrf()
    val body = mapOf(
        "mobi_app" to "web",
        "platform" to "web",
        "tp" to "0",
        "id" to id.toString(),
        "notice_state" to noticeState.toString(),
        "build" to "0",
        "csrf_token" to csrf,
        "csrf" to csrf,
    )
    api.msgSetNotice(body).checkOk("设置点赞通知")
}

/**
 * 点赞详情分页。[counts] 是累计点赞数，取满即到底（与 BiliPai 一致）。
 * [lastMid] 传上一页末位用户的 mid。
 */
suspend fun BiliRepository.likeDetail(
    cardId: Long,
    pn: Int,
    lastMid: Long,
    counts: Int,
): LikeDetailPage {
    val data = api.msgLikeDetail(cardId = cardId, pn = pn, lastMid = lastMid).requireOk("获取点赞详情")
    val users = data.items.map { d ->
        LikeDetailUser(
            mid = d.user?.mid ?: 0,
            name = d.user?.nickname.orEmpty(),
            avatar = d.user?.avatar.orEmpty().fixUrl(),
            time = d.likeTime,
        )
    }
    val end = data.page?.isEnd == true || users.size >= counts || users.isEmpty()
    return LikeDetailPage(
        card = data.card?.let { LikeDetailCard(business = it.business, title = it.title) },
        users = users,
        isEnd = end,
        lastMid = users.lastOrNull()?.mid ?: 0,
    )
}

/** 系统通知 content 是内嵌 JSON 字符串，正文在 web 字段。 */
private fun parseSysContent(raw: String): String = runCatching {
    val element = msgJson.parseToJsonElement(raw)
    if (element is JsonObject) element["web"]?.jsonPrimitive?.contentOrNull ?: raw else raw
}.getOrDefault(raw)
