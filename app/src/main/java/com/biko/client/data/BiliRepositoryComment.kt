package com.biko.client.data

import com.biko.client.data.model.Comment
import com.biko.client.data.model.CommentEmote
import com.biko.client.data.model.CommentPage
import com.biko.client.data.model.CommentPicture
import com.biko.client.data.model.EmotePackage
import com.biko.client.data.model.MentionUser
import com.biko.client.data.remote.fixUrl
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

// ---------------------------------------------------------------------------
// 评论 / 弹幕
// ---------------------------------------------------------------------------

/**
 * 一级评论分页。[mode] 与 B 站一致：2=按时间（最新），3=按热度（热门）。
 * [upMid] 用于标记「UP 主的评论」，接口本身不下发该判断。
 * [type] 评论区类型，1=视频、12=专栏（oid 为对应稿件/专栏 id）。
 */
suspend fun BiliRepository.comments(
    oid: Long,
    next: Long,
    mode: Int,
    upMid: Long,
    type: Int = 1,
): CommentPage {
    val data = api.comments(
        "x/v2/reply/wbi/main?" + wbi.sign(
            mapOf(
                "oid" to oid.toString(),
                "type" to type.toString(),
                "mode" to mode.toString(),
                "next" to next.toString(),
                "ps" to "20",
            )
        )
    ).requireOk("获取评论")
    val myMid = session.mid().toLongOrNull() ?: 0L
    // 第一页额外带「置顶评论」，接口不会把它放进 replies，需单独合并，否则会少一条
    val list = data.replies?.map { it.toComment(upMid, myMid) }.orEmpty()
    val merged = if (next == 0L) {
        (data.topReplies?.map { it.toComment(upMid, myMid) }.orEmpty() + list)
            .distinctBy { it.rpid }
    } else {
        list
    }
    return CommentPage(
        comments = merged,
        next = data.cursor?.next ?: 0,
        isEnd = data.cursor?.isEnd ?: true,
    )
}

/** 举报评论，失败抛出可读错误。[reason] 为 B 站举报原因码。 */
suspend fun BiliRepository.reportReply(oid: Long, rpid: Long, reason: Int) {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "举报评论")
    val resp = api.replyReport(
        mapOf(
            "oid" to oid.toString(),
            "rpid" to rpid.toString(),
            "type" to "1",
            "reason" to reason.toString(),
            "csrf" to token,
        )
    )
    if (resp.code != 0) {
        val text = when (resp.code) {
            -101 -> "请先登录"
            -110 -> "已举报过该评论"
            else -> resp.message.ifBlank { "举报失败" }
        }
        throw BiliException(resp.code, text, "举报评论")
    }
}

/** 删除自己发的评论。 */
suspend fun BiliRepository.deleteReply(oid: Long, rpid: Long) {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "删除评论")
    val resp = api.replyDelete(
        mapOf(
            "oid" to oid.toString(),
            "rpid" to rpid.toString(),
            "type" to "1",
            "csrf" to token,
        )
    )
    if (resp.code != 0) {
        val text = when (resp.code) {
            -101 -> "请先登录"
            -403 -> "没有权限删除该评论"
            else -> resp.message.ifBlank { "删除失败" }
        }
        throw BiliException(resp.code, text, "删除评论")
    }
}

/**
 * 发表评论或回复评论，返回新评论的 rpid（0 表示接口没回传）。
 * [root]/[parent] 非 0 时为回复评论：root 是楼主的 rpid，parent 是直接被回复者的 rpid。
 * [type] 为评论区类型（1 视频、12 专栏、17 动态等）。
 * 参数与 BiliPai 对齐（不带 web_location，避免触发风控）；常见错误码翻成人话。
 */
suspend fun BiliRepository.replyAdd(
    oid: Long,
    message: String,
    root: Long = 0,
    parent: Long = 0,
    pictures: List<CommentPicture> = emptyList(),
    syncToDynamic: Boolean = false,
    type: Int = 1,
): Long {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "发表评论")
    val body = mutableMapOf(
        "oid" to oid.toString(),
        "type" to type.toString(),
        "message" to message,
        "plat" to "1",
        "csrf" to token,
    )
    if (root > 0) {
        body["root"] = root.toString()
        body["parent"] = parent.toString()
    }
    if (pictures.isNotEmpty()) {
        body["pictures"] = buildJsonArray {
            pictures.forEach { p ->
                add(
                    buildJsonObject {
                        put("img_src", p.imgSrc)
                        put("img_width", p.imgWidth)
                        put("img_height", p.imgHeight)
                        put("img_size", p.imgSize)
                    }
                )
            }
        }.toString()
    }
    if (syncToDynamic) body["sync_to_dynamic"] = "1"
    val resp = api.replyAdd(body)
    if (resp.code != 0) {
        val friendly = when (resp.code) {
            -101 -> "请先登录"
            -102 -> "账号被封禁"
            -509 -> "请求过于频繁"
            12002 -> "评论区已关闭"
            12015 -> "需要评论验证码"
            12016 -> "评论内容包含敏感信息"
            12025 -> "评论字数过多"
            12035 -> "你已被UP主拉黑"
            12051 -> "重复评论，请勿刷屏"
            else -> resp.message.ifEmpty { "发送失败" }
        }
        throw BiliException(resp.code, friendly, "发表评论")
    }
    return (resp.data as? JsonObject)
        ?.get("reply")?.jsonObject
        ?.get("rpid")?.jsonPrimitive?.longOrNull ?: 0L
}

/** 表情面板：拉取评论可用表情包（`all_packages` 优先，含「热门」）。 */
suspend fun BiliRepository.emotePackages(): List<EmotePackage> {
    val data = api.emotePanel(mapOf("business" to "reply")).requireOk("获取表情")
    val source = data.allPackages ?: data.packages.orEmpty()
    return source.map { pkg ->
        EmotePackage(
            id = pkg.id,
            text = pkg.text,
            url = pkg.url,
            emote = pkg.emote.orEmpty().map { CommentEmote(it.id, it.text, it.url.fixUrl()) },
        )
    }
}

/** 上传一张评论配图，返回可在评论负载中引用的图片信息。 */
suspend fun BiliRepository.uploadCommentImage(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): CommentPicture {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "上传图片")
    val part = MultipartBody.Part.createFormData(
        "file_up",
        fileName,
        bytes.toRequestBody(mimeType.toMediaType()),
    )
    val text = "text/plain".toMediaType()
    val data = api.uploadCommentImage(
        fileUp = part,
        category = "daily".toRequestBody(text),
        biz = "new_dyn".toRequestBody(text),
        csrf = token.toRequestBody(text),
    ).requireOk("上传图片")
    if (data.imageUrl.isBlank()) throw BiliException(-1, "图片上传失败", "上传图片")
    return CommentPicture(
        imgSrc = data.imageUrl.fixUrl(),
        imgWidth = data.imageWidth,
        imgHeight = data.imageHeight,
        imgSize = data.imgSize,
    )
}

/** @ 提及用户搜索。 */
suspend fun BiliRepository.searchMentionUsers(keyword: String): List<MentionUser> {
    val data = api.mentionSearch(keyword).requireOk("搜索用户")
    return data.groups.orEmpty()
        .flatMap { it.items.orEmpty() }
        .map { MentionUser(it.uid, it.name, it.face.fixUrl()) }
}

/** 发送一条滚动弹幕（白字、25 号字体）。 */
suspend fun BiliRepository.sendDanmaku(oid: Long, msg: String, progressMs: Long) {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "发送弹幕")
    val resp = api.danmakuPost(
        mapOf(
            "type" to "1",
            "oid" to oid.toString(),
            "msg" to msg,
            "progress" to progressMs.toString(),
            "color" to "16777215",
            "fontsize" to "25",
            "pool" to "0",
            "mode" to "1",
            "rnd" to (System.currentTimeMillis() / 1000).toString(),
            "csrf" to token,
        )
    )
    if (resp.code != 0) {
        val friendly = when (resp.code) {
            -101 -> "请先登录"
            36701 -> "弹幕包含被禁止的内容"
            36703, 36702 -> "发送频率过快，稍后再试"
            36714 -> "弹幕长度超过限制"
            else -> resp.message.ifEmpty { "发送失败" }
        }
        throw BiliException(resp.code, friendly, "发送弹幕")
    }
}

/** 点赞 / 取消点赞评论（[type] 1=视频、12=专栏）。 */
suspend fun BiliRepository.likeReply(oid: Long, rpid: Long, liked: Boolean, type: Int = 1) {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "点赞评论")
    api.replyAction(
        mapOf(
            "oid" to oid.toString(),
            "rpid" to rpid.toString(),
            "type" to type.toString(),
            "action" to if (liked) "1" else "0",
            "csrf" to token,
        )
    ).checkOk("点赞评论")
}

/** 点踩 / 取消点踩评论（x/v2/reply/hate）。 */
suspend fun BiliRepository.hateReply(oid: Long, rpid: Long, hated: Boolean) {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "点踩")
    api.replyHate(
        mapOf(
            "oid" to oid.toString(),
            "rpid" to rpid.toString(),
            "type" to "1",
            "action" to if (hated) "1" else "0",
            "csrf" to token,
        )
    ).checkOk("点踩")
}

/** 楼中楼评论：返回 (回复列表, 是否到底)。 */
suspend fun BiliRepository.replyDetail(oid: Long, root: Long, pn: Int, upMid: Long): Pair<List<Comment>, Boolean> {
    val data = api.replyDetail(oid = oid, root = root, pn = pn).requireOk("获取回复")
    val myMid = session.mid().toLongOrNull() ?: 0L
    val list = data.replies.orEmpty().map { it.toComment(upMid, myMid) }
    val page = data.page
    val isEnd = if (page != null && page.count > 0) {
        pn * page.ps >= page.count
    } else {
        list.size < 20
    }
    return list to isEnd
}
