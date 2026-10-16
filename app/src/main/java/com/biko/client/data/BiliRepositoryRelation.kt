package com.biko.client.data

import com.biko.client.data.model.BangumiSeason
import com.biko.client.data.model.FollowedUp
import com.biko.client.data.model.RelationUser
import com.biko.client.data.model.SpaceProfile
import com.biko.client.data.model.Video
import com.biko.client.data.remote.fixUrl

// ---------------------------------------------------------------------------
// 关注 / 空间（UP 主资料、关系、投稿）
// ---------------------------------------------------------------------------

suspend fun BiliRepository.follow(mid: Long, follow: Boolean) {
    api.follow(
        mapOf(
            "fid" to mid.toString(),
            "act" to if (follow) "1" else "2",
            "re_src" to "11",
            "csrf" to csrf(),
        )
    ).checkOk("关注")
}

suspend fun BiliRepository.isFollowing(mid: Long): Boolean {
    val attribute = api.relation(mid).requireOk("查询关注").attribute
    return attribute == 2 || attribute == 6
}

/** 是否已把该用户加入黑名单（x/relation 的 attribute=128 表示拉黑）。 */
suspend fun BiliRepository.isBlocked(mid: Long): Boolean =
    api.relation(mid).requireOk("查询关系").attribute == 128

/** 粉丝数（x/relation/stat，比 card 接口更实时）。 */
suspend fun BiliRepository.upFans(mid: Long): Long =
    api.relationStat(mid).requireOk("查询粉丝").follower

/** 获赞数（x/space/upstat）。 */
suspend fun BiliRepository.upLikes(mid: Long): Long =
    api.upStat(mid).requireOk("查询获赞").likes

suspend fun BiliRepository.followings(pn: Int): List<Video> =
    api.followings(
        "x/relation/followings?" + wbi.sign(
            mapOf(
                "vmid" to session.mid(),
                "pn" to pn.toString(),
                "ps" to "20",
                "order" to "desc",
            )
        )
    ).requireOk("获取关注").list.map { it.toVideo() }

/** 订阅（关注）列表，返回 总数 to 当前页 UP 主；[orderType]: attention 最近订阅 / frequent 最常访问。 */
suspend fun BiliRepository.followList(pn: Int, orderType: String = "attention"): Pair<Int, List<FollowedUp>> {
    val data = api.followings(
        "x/relation/followings?" + wbi.sign(
            mapOf(
                "vmid" to session.mid(),
                "pn" to pn.toString(),
                "ps" to "50",
                "order" to "desc",
                "order_type" to orderType,
            )
        )
    ).requireOk("获取订阅")
    return data.total to data.list.map {
        FollowedUp(mid = it.mid, name = it.uname, face = it.face.fixUrl(), sign = it.sign)
    }
}

/**
 * UP 主空间资料。
 * `x/web-interface/card?photo=true` 一次就能拿到昵称/头像/签名/等级/认证/大会员/
 * 空间头图/IP 属地/关注/粉丝/获赞/投稿数，比 acc/info + relation/stat + upstat 三个接口省事
 * （而且不需要 WBI）。
 */
suspend fun BiliRepository.spaceProfile(mid: Long): SpaceProfile {
    val data = api.userCard(mid = mid.toString(), photo = true).requireOk("获取UP主资料")
    val card = data.card
    return SpaceProfile(
        name = card?.name.orEmpty(),
        face = card?.face.orEmpty().fixUrl(),
        sign = card?.sign.orEmpty(),
        sex = card?.sex.orEmpty(),
        level = card?.levelInfo?.currentLevel ?: 0,
        officialDesc = card?.officialVerify?.desc.orEmpty(),
        officialType = card?.officialVerify?.type ?: -1,
        vipLabel = card?.vip?.label?.text.orEmpty(),
        vipStatus = card?.vip?.status ?: 0,
        banner = data.space?.lImg.orEmpty().fixUrl(),
        ipLocation = data.ipLocation,
        // 关注数在 card.attention；顶层 following 是布尔值
        following = card?.attention ?: 0,
        fans = data.follower,
        likes = data.likeNum,
        totalVideos = data.archiveCount,
    )
}

/** 拉黑 / 解除拉黑某用户（x/relation/modify，act 5=拉黑 6=解除）。 */
suspend fun BiliRepository.blockUser(mid: Long, blocked: Boolean) {
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "屏蔽")
    val resp = api.follow(
        mapOf(
            "fid" to mid.toString(),
            "act" to if (blocked) "5" else "6",
            "re_src" to "11",
            "csrf" to csrf(),
        )
    )
    if (resp.code != 0) {
        val text = when (resp.code) {
            -101 -> "请先登录"
            -102 -> "账号被封禁"
            else -> resp.message.ifBlank { if (blocked) "屏蔽失败" else "解除屏蔽失败" }
        }
        throw BiliException(resp.code, text, "屏蔽")
    }
}

/**
 * 举报 UP 主（`x/space/report`，原因码沿用 B 站评论举报那一套：
 * 0=其他 1=垃圾广告 2=色情 3=刷屏 4=引战 5=剧透 6=政治 7=人身攻击）。
 */
suspend fun BiliRepository.reportUser(mid: Long, reason: Int) {
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "举报")
    val resp = api.reportUser(
        mapOf(
            "mid" to mid.toString(),
            "reason" to reason.toString(),
            "csrf" to csrf(),
        )
    )
    if (resp.code != 0) {
        val text = when (resp.code) {
            -101 -> "请先登录"
            -110 -> "已举报过该用户"
            else -> resp.message.ifBlank { "举报失败" }
        }
        throw BiliException(resp.code, text, "举报")
    }
}

/**
 * UP 主投稿，返回 (投稿总数, 当前页)。
 * [order] 与 B 站一致：pubdate 最新发布 / click 最多播放 / stow 最多收藏；
 * [keyword] 非空时是该 UP 内的投稿搜索。
 */
suspend fun BiliRepository.spaceVideos(
    mid: Long,
    pn: Int,
    order: String = "pubdate",
    keyword: String = "",
): Pair<Int, List<Video>> {
    ensureBuvid()
    val params = mutableMapOf(
        "mid" to mid.toString(),
        "order" to order,
        "pn" to pn.toString(),
        "ps" to "20",
    )
    params.putAll(SPACE_FINGERPRINT)
    if (keyword.isNotBlank()) params["keyword"] = keyword
    val data = api.spaceVideos("x/space/wbi/arc/search?" + wbi.sign(params)).requireOk("获取投稿")
    return (data.page?.count ?: 0) to (data.list?.vlist?.map { it.toVideo() } ?: emptyList())
}

/** 我的关注列表（x/relation/followings），返回 总数 to 当前页。 */
suspend fun BiliRepository.myFollowings(pn: Int): Pair<Int, List<RelationUser>> {
    ensureBuvid()
    val data = api.relationList(
        "x/relation/followings?" + wbi.sign(
            mapOf(
                "vmid" to session.mid(),
                "pn" to pn.toString(),
                "ps" to "20",
                "order" to "desc",
                "order_type" to "attention",
            )
        )
    ).requireOk("获取关注列表")
    return data.total to data.list.map { it.toRelationUser() }
}

/** 我的粉丝列表（x/relation/fans），返回 总数 to 当前页。 */
suspend fun BiliRepository.myFans(pn: Int): Pair<Int, List<RelationUser>> {
    ensureBuvid()
    val data = api.relationList(
        "x/relation/fans?" + wbi.sign(
            mapOf(
                "vmid" to session.mid(),
                "pn" to pn.toString(),
                "ps" to "20",
                "order" to "desc",
            )
        )
    ).requireOk("获取粉丝列表")
    return data.total to data.list.map { it.toRelationUser() }
}

/** 我的追番 / 追剧（x/space/bangumi/follow/list），返回 总数 to 当前页。[type]: 1=追番 2=追剧。 */
suspend fun BiliRepository.myBangumiFollow(type: Int, pn: Int): Pair<Int, List<BangumiSeason>> {
    val mid = session.mid().toLongOrNull() ?: 0L
    if (mid <= 0L) throw BiliException(-101, "请先登录", "获取追番")
    val data = api.myBangumiFollow(vmid = mid, type = type, pn = pn).requireOk("获取追番")
    return data.total to data.list.map { it.toSeason() }
}
