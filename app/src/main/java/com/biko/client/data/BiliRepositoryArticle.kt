package com.biko.client.data

import com.biko.client.data.model.ArticleDetail
import com.biko.client.data.model.ArticleInteraction
import com.biko.client.data.model.ArticleSummary
import com.biko.client.data.remote.ArticleViewData
import com.biko.client.data.remote.fixUrl

// ---------------------------------------------------------------------------
// 专栏 / 文章
// ---------------------------------------------------------------------------

/** 文章详情（x/article/view）。正文按「结构化段落 / ops / HTML」三种来源取最完整的一份。 */
suspend fun BiliRepository.articleDetail(cvId: Long): ArticleDetail {
    ensureBuvid()
    val data = api.articleView(
        "x/article/view?" + wbi.sign(
            mapOf(
                "id" to cvId.toString(),
                "gaia_source" to "main_web",
                "web_location" to "333.976",
            )
        )
    ).requireOk("获取文章")
    return data.toDetail(cvId)
}

private fun ArticleViewData.toDetail(cvId: Long): ArticleDetail {
    val blocks = parseArticleBlocks(opus?.paragraphs().orEmpty(), content, ops)
    val resolvedTitle = title.ifBlank { opus?.title.orEmpty() }
        .ifBlank { summary }
        .ifBlank { "专栏详情" }
    val banner = listOf(bannerUrl, originImageUrls.firstOrNull().orEmpty(), imageUrls.firstOrNull().orEmpty())
        .firstOrNull { it.isNotBlank() }
        ?.fixUrl()
    return ArticleDetail(
        id = id.takeIf { it > 0 } ?: cvId,
        title = resolvedTitle,
        summary = summary,
        authorName = author?.name.orEmpty(),
        authorMid = author?.mid ?: 0,
        authorFace = author?.face.orEmpty().fixUrl(),
        publishTime = publishTime,
        banner = banner,
        blocks = blocks,
    )
}

/** 搜索专栏（x/web-interface/wbi/search/type，search_type=article，需 WBI 签名）。 */
suspend fun BiliRepository.searchArticles(keyword: String, page: Int): List<ArticleSummary> {
    ensureBuvid()
    val url = "x/web-interface/wbi/search/type?" + wbi.sign(
        mapOf(
            "search_type" to "article",
            "keyword" to keyword,
            "page" to page.toString(),
        )
    )
    return api.searchArticles(url)
        .requireOk("搜索专栏")
        .result
        .map { it.toArticle() }
}

/** UP 主空间专栏列表（x/space/wbi/article），返回 总数 to 当前页。 */
suspend fun BiliRepository.spaceArticles(
    mid: Long,
    pn: Int,
    authorName: String = "",
    authorFace: String = "",
): Pair<Int, List<ArticleSummary>> {
    ensureBuvid()
    val params = mutableMapOf(
        "mid" to mid.toString(),
        "pn" to pn.toString(),
        "ps" to "20",
        "sort" to "publish_time",
    )
    params.putAll(SPACE_FINGERPRINT)
    val data = api.spaceArticles("x/space/wbi/article?" + wbi.sign(params)).requireOk("获取专栏")
    return data.count to data.articles.map { it.toArticle(mid, authorName, authorFace) }
}

// ---------------------------------------------------------------------------
// 专栏互动（点赞 / 投币 / 收藏 / 状态）
// ---------------------------------------------------------------------------

/** 专栏互动信息：点赞 / 投币 / 收藏状态 + 统计（x/article/viewinfo）。 */
suspend fun BiliRepository.articleInfo(cvId: Long): ArticleInteraction {
    ensureBuvid()
    val data = api.articleViewInfo(cvId).requireOk("获取专栏信息")
    val stats = data.stats
    return ArticleInteraction(
        liked = data.like == 1,
        favorited = data.favorite,
        attention = data.attention,
        coined = data.coin,
        viewCount = stats?.view ?: 0,
        likeCount = stats?.like ?: 0,
        replyCount = stats?.reply ?: 0,
        shareCount = stats?.share ?: 0,
        favCount = stats?.favorite ?: 0,
        coinCount = stats?.coin ?: 0,
    )
}

/** 专栏点赞 / 取消点赞（x/article/like，type 1=点赞 2=取消）。 */
suspend fun BiliRepository.likeArticle(cvId: Long, liked: Boolean) {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "点赞")
    val resp = api.articleLike(
        mapOf(
            "id" to cvId.toString(),
            "type" to if (liked) "1" else "2",
            "csrf" to token,
        )
    )
    if (resp.code != 0) {
        val text = when (resp.code) {
            -101 -> "请先登录"
            else -> resp.message.ifBlank { "点赞失败" }
        }
        throw BiliException(resp.code, text, "点赞")
    }
}

/** 专栏投币（x/web-interface/coin/add，avtype=2 表示专栏）。 */
suspend fun BiliRepository.coinArticle(cvId: Long, authorMid: Long, count: Int) {
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", "投币")
    val resp = api.coin(
        mapOf(
            "aid" to cvId.toString(),
            "upid" to authorMid.toString(),
            "multiply" to count.coerceIn(1, 2).toString(),
            "avtype" to "2",
            "csrf" to token,
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

/** 专栏收藏 / 取消收藏（x/article/favorites/add、del）。 */
suspend fun BiliRepository.favArticle(cvId: Long, fav: Boolean) {
    val action = if (fav) "收藏" else "取消收藏"
    val token = csrf()
    if (token.isEmpty()) throw BiliException(-101, "请先登录", action)
    val path = if (fav) "x/article/favorites/add" else "x/article/favorites/del"
    val resp = api.articleFav(path, mapOf("id" to cvId.toString(), "csrf" to token))
    if (resp.code != 0) {
        val text = when (resp.code) {
            -101 -> "请先登录"
            else -> resp.message.ifBlank { "${action}失败" }
        }
        throw BiliException(resp.code, text, action)
    }
}
