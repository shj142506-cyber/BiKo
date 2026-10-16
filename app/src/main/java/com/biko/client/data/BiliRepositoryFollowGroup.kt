package com.biko.client.data

import com.biko.client.data.model.FollowGroup
import com.biko.client.data.model.RelationUser

// ---------------------------------------------------------------------------
// 关注分组（x/relation/tags、x/relation/tag、x/relation/tag/user、x/relation/tags/addUsers）
// ---------------------------------------------------------------------------

/**
 * 我的关注分组（x/relation/tags）。
 * 过滤掉 tagid<0 的特殊分组（-10 特别关注 / 0 默认分组），只保留用户自建分组。
 */
suspend fun BiliRepository.followGroups(): List<FollowGroup> {
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "获取分组")
    return api.relationTags().requireOk("获取关注分组")
        .filter { it.tagid > 0 }
        .map { FollowGroup(tagid = it.tagid, name = it.name, count = it.count) }
}

/** 某 UP 所属的自建分组 id 集合。 */
suspend fun BiliRepository.userFollowGroupIds(mid: Long): Set<Long> {
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "获取分组")
    val data = api.relationTagUser(mid).requireOk("获取UP分组")
    return data.keys.mapNotNull { it.toLongOrNull() }.filter { it > 0 }.toSet()
}

/** 分组内 UP 列表（服务端一次性返回，无分页总数）。 */
suspend fun BiliRepository.followGroupMembers(tagid: Long): List<RelationUser> {
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "获取分组成员")
    return api.relationTagMembers(tagId = tagid).requireOk("获取分组成员")
        .filter { it.mid > 0L }
        .map { it.toRelationUser() }
}

/**
 * 覆盖设置某 UP 的关注分组：先移入默认分组（tagids=0）再设选中，语义=完全覆盖。
 * 与 BiliPai 的 overwriteFollowGroupIds 一致。
 */
suspend fun BiliRepository.setUserFollowGroups(mid: Long, tagIds: Set<Long>) {
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "设置分组")
    val fids = mid.toString()
    api.addUsersToRelationTags(mapOf("fids" to fids, "tagids" to "0", "csrf" to csrf()))
        .checkOk("设置分组")
    val selected = tagIds.filter { it > 0 }
    if (selected.isNotEmpty()) {
        api.addUsersToRelationTags(
            mapOf("fids" to fids, "tagids" to selected.joinToString(","), "csrf" to csrf())
        ).checkOk("设置分组")
    }
}
