package com.biko.client.data

import com.biko.client.data.model.TopicFeedPage
import com.biko.client.data.model.TopicInfo
import com.biko.client.data.model.TopicSortOption
import com.biko.client.data.remote.TopicDetailTopData
import com.biko.client.data.remote.fixUrl

/** 话题详情头部接口（跨域，走绝对地址）。 */
private const val TOPIC_DETAIL_BASE = "https://app.bilibili.com/x/topic/web/details/top"

/**
 * 话题详情头部；失败返回 null（页面仍可展示动态流）。
 */
suspend fun BiliRepository.topicInfo(topicId: Long): TopicInfo? {
    if (topicId <= 0L) return null
    val url = "$TOPIC_DETAIL_BASE?topic_id=$topicId&source=Web&web_location=333.1036"
    val data = runCatching { api.topicDetailTop(url).requireOk("获取话题信息") }.getOrNull() ?: return null
    return data.toTopicInfo()
}

/**
 * 话题动态流分页。[offset] 为上一页游标（首页传空串）。
 */
suspend fun BiliRepository.topicFeed(
    topicId: Long,
    offset: String = "",
    sortBy: Int = 0,
): TopicFeedPage {
    val data = api.topicFeed(topicId = topicId, sortBy = sortBy, offset = offset)
        .requireOk("获取话题动态")
    val card = data.topicCardList
        ?: return TopicFeedPage(offset = offset, selectedSortBy = sortBy)
    val posts = card.items
        .mapNotNull { it.dynamicCardItem }
        .filter { it.visible }
        .mapNotNull { it.toDynamicPost() }
    val conf = card.topicSortByConf
    return TopicFeedPage(
        posts = posts,
        offset = card.offset,
        hasMore = card.hasMore,
        sortOptions = conf?.allSortBy.orEmpty()
            .filter { it.sortName.isNotEmpty() }
            .distinctBy { it.sortBy }
            .map { TopicSortOption(it.sortBy, it.sortName) },
        selectedSortBy = conf?.showSortBy ?: sortBy,
    )
}

/** 话题名 → 话题 id：评论里的「#话题#」只有名字，需要先搜出 id 再进话题页。 */
suspend fun BiliRepository.resolveTopicId(name: String): Long {
    val keyword = name.trim().removeSurrounding("#").trim()
    if (keyword.isEmpty()) return 0L
    val data = runCatching { api.searchTopics(keywords = keyword).requireOk("搜索话题") }
        .getOrNull() ?: return 0L
    val items = data.topicItems.orEmpty()
    return items.firstOrNull { it.name.equals(keyword, ignoreCase = true) }?.id
        ?: items.firstOrNull()?.id
        ?: 0L
}

private fun TopicDetailTopData.toTopicInfo(): TopicInfo? {
    val item = topDetails?.topicItem ?: return null
    val creator = topDetails.topicCreator
    return TopicInfo(
        id = item.id,
        name = item.name,
        description = item.description,
        sharePic = item.sharePic.fixUrl(),
        view = item.view,
        discuss = item.discuss,
        dynamics = item.dynamics,
        creatorUid = creator?.uid ?: 0L,
        creatorName = creator?.name.orEmpty(),
        creatorFace = (creator?.face ?: "").fixUrl(),
    )
}
