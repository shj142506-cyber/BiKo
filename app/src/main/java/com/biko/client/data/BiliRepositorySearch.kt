package com.biko.client.data

import com.biko.client.data.model.BangumiSeason
import com.biko.client.data.model.LiveRoom
import com.biko.client.data.model.SearchUser
import com.biko.client.data.model.Video
import java.net.URLEncoder

private const val SUGGEST_BASE = "https://s.search.bilibili.com/main/suggest"

/** 热搜榜条数（对齐官方 App 的 30 条）。 */
private const val HOT_SEARCH_LIMIT = 30

/** 搜索接口路径（wbi 版）；参数需经 [com.biko.client.data.remote.WbiSigner] 签名后拼接。 */
private const val SEARCH_PATH = "x/web-interface/wbi/search/type?"

/**
 * 生成签名后的搜索 URL。
 * 2025 起搜索接口（search/type）未带 WBI 签名一律返回 412，因此所有搜索都必须签名。
 */
private suspend fun BiliRepository.signedSearchUrl(
    type: String,
    keyword: String,
    page: Int,
    order: String? = null,
    /** 内容时长：0 全部 / 1 0-10 分钟 / 2 10-30 分钟 / 3 30-60 分钟 / 4 60 分钟以上（0 不传）。 */
    duration: Int = 0,
    /** 内容分区 tid（0 为全部，不传）。 */
    tids: Int = 0,
    /** 发布时间的起止（epoch 秒，为空不传）。 */
    pubBegin: Long? = null,
    pubEnd: Long? = null,
): String {
    ensureBuvid()
    val params = mutableMapOf(
        "search_type" to type,
        "keyword" to keyword,
        "page" to page.toString(),
    )
    if (order != null) params["order"] = order
    if (duration > 0) params["duration"] = duration.toString()
    if (tids > 0) params["tids"] = tids.toString()
    if (pubBegin != null) params["pubtime_begin_s"] = pubBegin.toString()
    if (pubEnd != null) params["pubtime_end_s"] = pubEnd.toString()
    return SEARCH_PATH + wbi.sign(params)
}

// ---------------------------------------------------------------------------
// 搜索 / 热搜
// ---------------------------------------------------------------------------

/**
 * 搜索视频；分区频道的视频内容也走这里（和搜索栏搜同一个词结果一致）。
 * 只保留能用普通播放链路打开的视频，过滤掉课程 / 番剧等不可播条目。
 */
suspend fun BiliRepository.search(keyword: String, page: Int): List<Video> {
    val url = signedSearchUrl(type = "video", keyword = keyword, page = page)
    return api.search(url).requireOk("搜索").result
        .filter { it.isPlayableVideo }
        .map { it.toVideo() }
}

/** 搜索视频（可指定排序与筛选）：搜索结果页的视频 Tab 用。order 取值 totalrank / click / pubdate / dm / stow / scores。 */
suspend fun BiliRepository.searchVideos(
    keyword: String,
    page: Int,
    order: String,
    duration: Int = 0,
    tids: Int = 0,
    pubBegin: Long? = null,
    pubEnd: Long? = null,
): List<Video> {
    val url = signedSearchUrl(
        type = "video",
        keyword = keyword,
        page = page,
        order = order,
        duration = duration,
        tids = tids,
        pubBegin = pubBegin,
        pubEnd = pubEnd,
    )
    return api.search(url).requireOk("搜索视频").result
        .filter { it.isPlayableVideo }
        .map { it.toVideo() }
}

/** 搜索直播间（search_type=live_room）。 */
suspend fun BiliRepository.searchLiveRooms(keyword: String, page: Int): List<LiveRoom> {
    val url = signedSearchUrl(type = "live_room", keyword = keyword, page = page)
    return api.searchLiveRooms(url).requireOk("搜索直播间").result
        .map { it.toLiveRoom() }
        .filter { it.roomId > 0 }
}

/**
 * 搜索用户：[live] 为 true 时搜主播（live_user），否则搜 UP 主（bili_user）。
 */
suspend fun BiliRepository.searchUsers(keyword: String, page: Int, live: Boolean): List<SearchUser> {
    val type = if (live) "live_user" else "bili_user"
    val url = signedSearchUrl(type = type, keyword = keyword, page = page)
    return api.searchUsers(url).requireOk("搜索用户").result
        .map { it.toSearchUser(live) }
        .filter { it.mid > 0 }
}

/** 搜索番剧 / 影视（PGC）。[bangumi] true 搜番剧（media_bangumi），false 搜影视（media_ft）。 */
suspend fun BiliRepository.searchMedia(keyword: String, page: Int, bangumi: Boolean): List<BangumiSeason> {
    val url = signedSearchUrl(
        type = if (bangumi) "media_bangumi" else "media_ft",
        keyword = keyword,
        page = page,
    )
    return api.searchMedia(url).requireOk(if (bangumi) "搜索番剧" else "搜索影视").result
        .map { it.toSeason() }
        .filter { it.seasonId > 0 }
}

/** 获取热搜榜（对齐 B 站官方，最多 30 条）。 */
suspend fun BiliRepository.hotSearch(): List<String> =
    api.hotSearch(limit = HOT_SEARCH_LIMIT).requireOk("获取热搜").trending?.list?.map { it.keyword } ?: emptyList()

/**
 * 搜索联想词（输入框下拉提示）。
 * 走独立的 s.search.bilibili.com，输入过程中调用频繁，任何异常都吞掉返回空列表，
 * 避免网络抖动打断用户输入。
 */
suspend fun BiliRepository.searchSuggest(keyword: String): List<String> {
    val kw = keyword.trim()
    if (kw.isEmpty()) return emptyList()
    val url = "$SUGGEST_BASE?term=${URLEncoder.encode(kw, "UTF-8")}&main_ver=v1&highlight="
    return runCatching { api.searchSuggest(url).result?.tag.orEmpty() }
        .getOrDefault(emptyList())
        .map { it.value.ifBlank { it.term }.replace(Regex("<[^>]+>"), "").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}