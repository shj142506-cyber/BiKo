package com.biko.client.data

import com.biko.client.data.model.VideoNote
import com.biko.client.data.remote.PublicVideoNoteItemDto
import com.biko.client.data.remote.fixUrl

// ---------------------------------------------------------------------------
// 视频笔记（x/note/publish/list/archive）
// ---------------------------------------------------------------------------

/**
 * 视频的公开笔记列表。[oid] 传稿件的 aid。
 * 未开放笔记的稿件服务端会返回非 0 code 或 show_public_note=false，此处统一返回空列表。
 */
suspend fun BiliRepository.videoNotes(oid: Long): List<VideoNote> {
    val data = runCatching { api.publicVideoNotes(oid = oid).requireOk("获取视频笔记") }
        .getOrNull() ?: return emptyList()
    if (!data.showPublicNote) return emptyList()
    return data.list.map { it.toVideoNote() }
}

private fun PublicVideoNoteItemDto.toVideoNote() = VideoNote(
    cvid = cvid,
    title = title,
    summary = summary,
    pubtime = pubtime,
    webUrl = webUrl,
    likes = likes,
    hasLike = hasLike,
    authorMid = author?.mid ?: 0,
    authorName = author?.name.orEmpty(),
    authorFace = (author?.face ?: "").fixUrl(),
    authorLevel = author?.level ?: 0,
)
