package com.biko.client.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.biko.client.data.model.DynamicPage
import com.biko.client.data.model.DynamicPost
import com.biko.client.data.model.MentionUser
import com.biko.client.data.remote.DynamicCreateContentDto
import com.biko.client.data.remote.DynamicCreateFeedContentDto
import com.biko.client.data.remote.DynamicCreateFeedData
import com.biko.client.data.remote.DynamicCreateFeedReqDto
import com.biko.client.data.remote.DynamicCreateFeedRequest
import com.biko.client.data.remote.DynamicCreateOptionDto
import com.biko.client.data.remote.DynamicCreatePicDto
import com.biko.client.data.remote.DynamicCreateTopicDto
import com.biko.client.data.remote.DynamicCreateVoteInfoDto
import com.biko.client.data.remote.DynamicCreateVoteOptionDto
import com.biko.client.data.remote.DynamicCreateVoteRequest
import com.biko.client.data.remote.DynamicItemDto
import com.biko.client.data.remote.fixUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ---------------------------------------------------------------------------
// 关注动态
// ---------------------------------------------------------------------------

/**
 * 关注动态 Feed。[offset] 为上一页末条动态 id（首页传空串），返回下一页游标。
 * 接口会下发 opus / 视频 / 图文等多种风格，统一映射成 [DynamicPost]。
 */
suspend fun BiliRepository.dynamicFeed(offset: String = ""): DynamicPage {
    val data = api.dynamicFeed(offset = offset).requireOk("获取动态")
    val posts = data.items
        .filter { it.visible }
        .mapNotNull { it.toDynamicPost() }
    return DynamicPage(
        posts = posts,
        offset = data.offset,
        hasMore = data.hasMore,
    )
}

/**
 * 指定 UP 主的空间动态（空间页「动态」标签）。
 */
suspend fun BiliRepository.spaceDynamicFeed(mid: Long, offset: String = ""): DynamicPage {
    val data = api.spaceDynamicFeed(hostMid = mid, offset = offset).requireOk("获取TA的动态")
    val posts = data.items
        .filter { it.visible }
        .mapNotNull { it.toDynamicPost() }
    return DynamicPage(
        posts = posts,
        offset = data.offset,
        hasMore = data.hasMore,
    )
}

internal fun DynamicItemDto.toDynamicPost(): DynamicPost? {
    val author = modules?.author ?: return null
    val dynamic = modules?.dynamic
    val major = dynamic?.major
    val stat = modules?.stat

    // 正文：优先 desc.text，opus 风格正文在 opus.summary.text。
    val opusText = major?.opus?.summary?.text.orEmpty()
    val text = dynamic?.desc?.text.orEmpty().ifEmpty { opusText }

    // 配图：draw.items[].src 或 opus.pics[].url。
    val pics = buildList {
        major?.draw?.items?.forEach { add(it.src.fixUrl()) }
        if (isEmpty()) major?.opus?.pics?.forEach { add(it.url.fixUrl()) }
    }.filter { it.isNotEmpty() }

    // 视频：archive 或 pgc（番剧）。
    val archive = major?.archive ?: major?.pgc

    return DynamicPost(
        id = idStr,
        type = type,
        authorName = author.name,
        authorFace = author.face.fixUrl(),
        authorMid = author.mid,
        pubAction = author.pubAction,
        pubTime = author.pubTime,
        pubTs = author.pubTs,
        text = text,
        pics = pics,
        bvid = archive?.bvid.orEmpty(),
        videoTitle = archive?.title.orEmpty(),
        videoCover = archive?.cover.orEmpty().fixUrl(),
        videoDuration = archive?.durationText.orEmpty(),
        videoPlay = archive?.stat?.play.orEmpty(),
        videoDanmaku = archive?.stat?.danmaku.orEmpty(),
        orig = orig?.toDynamicPost(),
        likeCount = stat?.like?.count ?: 0,
        commentCount = stat?.comment?.count ?: 0,
        forwardCount = stat?.forward?.count ?: 0,
    )
}

// ---------------------------------------------------------------------------
// 发布动态（x/dynamic/feed/create/dyn）
// ---------------------------------------------------------------------------

/** 发布话题。 */
data class DynamicTopic(val id: Long, val name: String)

/** 发布动态草稿。 */
data class DynamicPublishDraft(
    val text: String,
    val title: String = "",
    val imageUris: List<Uri> = emptyList(),
    val isPrivate: Boolean = false,
    val mentions: List<MentionUser> = emptyList(),
    val emotes: List<String> = emptyList(),
    val topic: DynamicTopic? = null,
    val voteId: Long = 0L,
    val voteTitle: String = "",
)

/** 发布一条图文 / 纯文本动态，返回动态 id（失败抛 [BiliException]）。 */
suspend fun BiliRepository.publishDynamic(draft: DynamicPublishDraft): String {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "发布动态")
    val mid = session.mid()
    val pics = withContext(Dispatchers.IO) {
        draft.imageUris.map { uploadDynamicImage(it) }
    }
    val contents = buildDynamicCreateContents(
        text = draft.text,
        voteId = draft.voteId,
        voteTitle = draft.voteTitle,
        mentions = draft.mentions,
        emotes = draft.emotes,
    )
    if (contents.isEmpty() && pics.isEmpty()) {
        throw BiliException(-1, "内容不能为空", "发布动态")
    }
    val request = DynamicCreateFeedRequest(
        dynReq = DynamicCreateFeedReqDto(
            content = DynamicCreateFeedContentDto(
                contents = contents.ifEmpty {
                    listOf(DynamicCreateContentDto(rawText = " ", type = 1, bizId = ""))
                },
                title = draft.title.trim().takeIf { it.isNotEmpty() },
            ),
            scene = if (pics.isNotEmpty()) 2 else 1,
            pics = pics.takeIf { it.isNotEmpty() },
            option = if (draft.isPrivate) DynamicCreateOptionDto(privatePub = 1) else null,
            topic = draft.topic?.takeIf { it.id > 0L }?.let {
                DynamicCreateTopicDto(id = it.id, name = it.name)
            },
            uploadId = "${mid}_${System.currentTimeMillis() / 1000}_${Random.nextInt(1000, 10000)}",
        ),
    )
    val resp = api.createFeedDynamic(csrf = token, body = request)
    if (resp.code != 0) {
        throw BiliException(resp.code, resp.message.ifEmpty { "发布失败" }, "发布动态")
    }
    return resolveCreatedDynamicId(resp.data)
}

/** 发布页话题搜索。 */
suspend fun BiliRepository.searchPublishTopics(keyword: String): List<DynamicTopic> {
    val data = api.searchTopics(keywords = keyword.trim().takeIf { it.isNotEmpty() })
        .requireOk("搜索话题")
    return data.topicItems.orEmpty()
        .filter { it.id > 0L && it.name.isNotEmpty() }
        .distinctBy { it.id }
        .map { DynamicTopic(it.id, it.name) }
}

/** 创建投票，返回 (投票 id, 标题)。 */
suspend fun BiliRepository.createDynamicVote(
    title: String,
    options: List<String>,
    description: String = "",
    choiceCount: Int = 1,
    durationSeconds: Int,
): Pair<Long, String> {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "创建投票")
    val cleaned = options.map { it.trim() }.filter { it.isNotEmpty() }
    if (title.isBlank() || cleaned.size < 2) {
        throw BiliException(-1, "至少填写标题和两个选项", "创建投票")
    }
    val resp = api.createVote(
        csrf = token,
        body = DynamicCreateVoteRequest(
            voteInfo = DynamicCreateVoteInfoDto(
                title = title.trim(),
                desc = description.trim(),
                choiceCnt = choiceCount.coerceIn(1, cleaned.size),
                duration = durationSeconds.coerceAtLeast(60),
                options = cleaned.map { DynamicCreateVoteOptionDto(optDesc = it) },
                votePublisher = session.mid().toLongOrNull() ?: 0L,
            ),
        ),
    )
    if (resp.code != 0) {
        throw BiliException(resp.code, resp.message.ifEmpty { "创建投票失败" }, "创建投票")
    }
    val voteId = resp.data?.voteId ?: 0L
    if (voteId <= 0L) throw BiliException(-1, "投票创建失败", "创建投票")
    return voteId to title.trim()
}

/** 读取本地图片并上传，返回发布负载中的图片信息。 */
private suspend fun BiliRepository.uploadDynamicImage(uri: Uri): DynamicCreatePicDto {
    val resolver = AppGraph.appContext.contentResolver
    val mime = resolver.getType(uri) ?: "image/jpeg"
    val name = queryDisplayName(resolver, uri) ?: "dyn_${System.currentTimeMillis()}.jpg"
    val bytes = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw BiliException(-1, "无法读取图片", "上传图片")
    }
    val pic = uploadCommentImage(bytes, name, mime)
    return DynamicCreatePicDto(
        imgSrc = pic.imgSrc,
        imgWidth = pic.imgWidth,
        imgHeight = pic.imgHeight,
        imgSize = pic.imgSize.toFloat(),
    )
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

/** 把正文按 @提及 / 表情 切分成富文本段（对齐 BiliPai 算法）。 */
private fun buildDynamicCreateContents(
    text: String,
    voteId: Long,
    voteTitle: String,
    mentions: List<MentionUser> = emptyList(),
    emotes: List<String> = emptyList(),
): List<DynamicCreateContentDto> {
    val items = mutableListOf<DynamicCreateContentDto>()
    val trimmed = text.trim()
    if (trimmed.isNotEmpty()) {
        val specialNodes = buildList {
            mentions.filter { it.uid > 0L && it.name.isNotBlank() }.forEach { mention ->
                add(Triple("@${mention.name} ", 2, mention.uid.toString()))
                add(Triple("@${mention.name}", 2, mention.uid.toString()))
            }
            emotes.filter { it.isNotBlank() }.distinct().forEach { emote ->
                add(Triple(emote, 9, ""))
            }
        }
        var cursor = 0
        while (cursor < trimmed.length) {
            val next = specialNodes
                .sortedByDescending { it.first.length }
                .mapNotNull { node ->
                    trimmed.indexOf(node.first, startIndex = cursor)
                        .takeIf { it >= 0 }
                        ?.let { index -> index to node }
                }
                .minByOrNull { it.first }
            if (next == null) {
                items += DynamicCreateContentDto(trimmed.substring(cursor), type = 1, bizId = "")
                break
            }
            val (index, node) = next
            if (index > cursor) {
                items += DynamicCreateContentDto(trimmed.substring(cursor, index), type = 1, bizId = "")
            }
            items += DynamicCreateContentDto(rawText = node.first, type = node.second, bizId = node.third)
            cursor = index + node.first.length
        }
    }
    if (voteId > 0L) {
        items += DynamicCreateContentDto(voteTitle.ifBlank { "投票" }, type = 4, bizId = voteId.toString())
        items += DynamicCreateContentDto(" ", type = 1, bizId = "")
    }
    return items
}

private fun resolveCreatedDynamicId(data: DynamicCreateFeedData?): String =
    data?.dynIdStr?.trim().orEmpty()
        .ifBlank { data?.dynamicIdStr?.trim().orEmpty() }
        .ifBlank { data?.dynId?.takeIf { it > 0L }?.toString().orEmpty() }
