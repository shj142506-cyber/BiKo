package com.biko.client.data

import com.biko.client.data.model.MyStats
import com.biko.client.data.model.UserProfile

// ---------------------------------------------------------------------------
// 通用 / 个人资料
// ---------------------------------------------------------------------------

suspend fun BiliRepository.ensureBuvid() {
    if (session.buvid3().isNotEmpty()) return
    runCatching {
        val data = api.fingerSpi().requireOk("获取指纹") ?: return
        session.saveBuvid(data.b3, data.b4)
    }
}

suspend fun BiliRepository.refreshProfile(): UserProfile {
    val nav = runCatching { api.nav() }.getOrNull() ?: return session.userProfile()
    val data = nav.data
    if (data != null && data.isLogin) {
        // 硬币直接取 nav 的 money（coinLog 的 money 是浮点，Int 解析会丢成 0）
        session.saveProfile(
            uname = data.uname,
            face = data.face,
            level = data.levelInfo?.currentLevel ?: 0,
            coin = data.money.toInt(),
            bcoin = data.wallet?.bcoinBalance ?: 0,
            exp = data.levelInfo?.currentExp ?: 0,
            nextExp = data.levelInfo?.nextExp ?: 0,
        )
    }
    // 注意：未登录时 nav 返回 code=-101，这里不再清空本地会话，
    // 避免一次网络抖动就把刚扫码成功的登录态抹掉。
    return session.userProfile()
}

/** 「我的」页顶部统计（关注 / 粉丝 / 获赞 / 大会员标签与配色）。 */
suspend fun BiliRepository.myStats(): MyStats {
    val mid = session.mid()
    if (mid.isEmpty()) return MyStats()
    val data = api.userCard(mid).requireOk("获取用户信息")
    val vip = data.card?.vip
    return MyStats(
        // 关注数取 card.attention（顶层的 following 是布尔值）
        following = data.card?.attention ?: 0,
        follower = data.follower,
        likes = data.likeNum,
        vipLabel = vip?.label?.text.orEmpty(),
        nicknameColor = vip?.nicknameColor.orEmpty(),
        avatarBadge = vip?.avatarIcon?.iconResource?.url.orEmpty(),
        sign = data.card?.sign.orEmpty(),
    )
}

/**
 * 修改个人资料（昵称 / 签名）。
 * 先读取账号原值，把生日、性别原样回传，避免只改昵称时被清空；
 * 昵称/签名若为空串则保持原值，不覆盖。
 */
suspend fun BiliRepository.updateProfile(uname: String, sign: String) {
    val account = api.myAccount().requireOk("获取账号信息")
    api.updateProfile(
        mapOf(
            "uname" to uname.ifBlank { account.uname },
            "usersign" to sign,
            "user_birthday" to account.birthday,
            "sex" to account.sex,
            "csrf" to csrf(),
        )
    ).checkOk("修改资料")
}