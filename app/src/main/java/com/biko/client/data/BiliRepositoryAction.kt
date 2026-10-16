package com.biko.client.data

// ---------------------------------------------------------------------------
// 互动（点赞 / 投币）
// ---------------------------------------------------------------------------

suspend fun BiliRepository.hasLiked(aid: Long): Boolean =
    api.hasLiked(aid).requireOk("查询点赞") == 1

/** 当前登录用户对该稿件已投的硬币数（0/1/2）。 */
suspend fun BiliRepository.coinCount(aid: Long): Int =
    api.coins(aid).requireOk("查询投币").multiply

/** 当前登录用户的硬币余额（nav 的 money 是浮点）。 */
suspend fun BiliRepository.coinBalance(): Double = api.nav().requireOk("查询硬币余额").money

suspend fun BiliRepository.like(aid: Long, liked: Boolean) {
    api.like(
        mapOf(
            "aid" to aid.toString(),
            "like" to if (liked) "1" else "2",
            "csrf" to csrf(),
        )
    ).checkOk("点赞")
}

/**
 * 投币：[count] 1~2 枚，[alsoLike] 同时点赞。
 * 只发 aid / multiply / select_like / csrf（BiliPai 也不带 cid），错误码翻译成人话。
 */
suspend fun BiliRepository.coin(aid: Long, count: Int, alsoLike: Boolean) {
    val resp = api.coin(
        mapOf(
            "aid" to aid.toString(),
            "multiply" to count.coerceIn(1, 2).toString(),
            "select_like" to if (alsoLike) "1" else "0",
            "csrf" to csrf(),
        )
    )
    if (resp.code != 0) {
        val text = when (resp.code) {
            34004 -> "操作太频繁，请稍后重试"
            34005 -> "已投满 2 个硬币"
            -104 -> "硬币余额不足"
            -101 -> "请先登录"
            else -> resp.message.ifBlank { "投币失败" }
        }
        throw BiliException(resp.code, text, "投币")
    }
}