package com.biko.client.data

import com.biko.client.data.model.SpaceCollection
import com.biko.client.data.model.Video
import com.biko.client.data.remote.HistoryCursor
import com.biko.client.data.remote.fixUrl

// ---------------------------------------------------------------------------
// 收藏 / 历史 / 稍后再看
// ---------------------------------------------------------------------------

suspend fun BiliRepository.favourite(aid: Long, fav: Boolean) {
    val folder = defaultFavFolder()
    api.favourite(
        mapOf(
            "rid" to aid.toString(),
            "type" to "2",
            if (fav) "add_media_ids" to folder else "del_media_ids" to folder,
            "csrf" to csrf(),
        )
    ).checkOk("收藏")
}

private suspend fun BiliRepository.defaultFavFolder(): String {
    val list = api.favFolders(session.mid()).requireOk("获取收藏夹").list
    return list.firstOrNull()?.id?.toString() ?: ""
}

/** 当前用户是否已收藏该视频（未登录直接 false）。 */
suspend fun BiliRepository.isFavoured(aid: Long): Boolean {
    if (!session.isLogin()) return false
    return runCatching { api.favoured(aid).requireOk("查询收藏状态").favoured }
        .getOrDefault(false)
}

suspend fun BiliRepository.favourites(pn: Int): List<Video> {
    val folder = defaultFavFolder()
    if (folder.isEmpty()) return emptyList()
    return api.favResources(mediaId = folder, pn = pn)
        .requireOk("获取收藏").medias.map { it.toVideo() }
}

suspend fun BiliRepository.localHistory(): List<Video> = history.load()

suspend fun BiliRepository.addHistory(video: Video) = history.add(video)

/**
 * 拉取服务器观看历史（游标分页，参照 BiliPai `HistoryRepository.getHistoryList`）。
 *
 * @param max / viewAt / business 来自上一页响应 [HistoryCursor]，首屏传 0/null。
 * @param type all/archive/live/article；null 表示不传（全部）。番剧(PGC)服务端不支持 type，
 *             需传 null 取全部后在调用方按 business 本地过滤。
 * @return 解析后的视频列表 + 下一页游标（null 表示没有更多）。
 */
suspend fun BiliRepository.historyList(
    ps: Int = 30,
    max: Long = 0,
    viewAt: Long = 0,
    business: String? = null,
    type: String? = null,
): Pair<List<Video>, HistoryCursor?> {
    val data = api.history(
        ps = ps,
        max = max.takeIf { it > 0 },
        viewAt = viewAt.takeIf { it > 0 },
        business = business?.takeIf { it.isNotBlank() },
        type = type?.takeIf { it.isNotBlank() },
    ).requireOk("获取历史")
    // 番剧历史服务器通常不下发 bvid，用 seasonId + epid 反查分集 bvid 以便播放。
    val list = resolveBangumiBvids(data.list.mapNotNull { it.toVideo() })
    return list to data.cursor
}

/** 删除单条历史（本地 + 服务器）。 */
suspend fun BiliRepository.removeHistory(video: Video) {
    history.remove(video.bvid)
    if (!session.isLogin()) return
    // 服务器删除 kid 按内容类型区分：archive_ 稿件 / pgc_ 季 / live_ 直播间。
    val kid = when {
        video.type == "live" && video.roomId > 0 -> "live_${video.roomId}"
        video.type == "bangumi" && video.seasonId > 0 -> "pgc_${video.seasonId}"
        video.aid > 0 -> "archive_${video.aid}"
        else -> null
    } ?: return
    runCatching { api.historyDelete(mapOf("kid" to kid, "csrf" to csrf())) }
}

suspend fun BiliRepository.clearHistory() {
    history.clear()
    if (session.isLogin()) runCatching { api.historyClear(mapOf("csrf" to csrf())).checkOk("清空历史") }
}

suspend fun BiliRepository.reportHistory(aid: Long, cid: Long, progress: Int) {
    if (!session.isLogin()) return
    runCatching {
        api.historyReport(
            mapOf(
                "aid" to aid.toString(),
                "cid" to cid.toString(),
                "progress" to progress.toString(),
                "platform" to "html5",
                "csrf" to csrf(),
            )
        ).checkOk("上报历史")
    }
}

suspend fun BiliRepository.syncHistoryFromServer() {
    if (!session.isLogin()) return
    runCatching {
        // 服务器条目：默认拉取全部业务类型（archive / pgc / live / article）。
        // 番剧历史服务器可能不下发 bvid，用 seasonId + epid 反查分集 bvid，保证可播放。
        val server = resolveBangumiBvids(
            api.history().requireOk("同步历史").list.mapNotNull { it.toVideo() },
        )
        if (server.isEmpty()) return@runCatching
        // 合并式同步：本地记录在前，追加服务器上本地没有的条目（按 bvid 去重）。
        val local = history.load()
        val localBvids = local.map { it.bvid }.toHashSet()
        val merged = (local + server.filterNot { it.bvid.isNotEmpty() && it.bvid in localBvids })
            .take(50)
        history.save(merged)
    }
}

/**
 * 番剧 / 影视历史补全 bvid：服务器 pgc 历史可能只给 seasonId + epid，
 * 这里按季度批量取分集（同季度只请求一次），命中 epid 后回填 bvid；
 * 仍无法确定 bvid 的条目无法播放，直接剔除，避免出现点不动的死条目。
 */
private suspend fun BiliRepository.resolveBangumiBvids(items: List<Video>): List<Video> {
    val needResolve = items.filter { it.type == "bangumi" && it.bvid.isBlank() && it.seasonId > 0 }
    if (needResolve.isEmpty()) return items
    val episodesBySeason = mutableMapOf<Long, List<Video>>()
    val resolved = items.map { item ->
        if (item.type != "bangumi" || item.bvid.isNotBlank() || item.seasonId <= 0) return@map item
        val episodes = episodesBySeason.getOrPut(item.seasonId) {
            runCatching { bangumiEpisodes(item.seasonId).second }.getOrDefault(emptyList())
        }
        val ep = episodes.firstOrNull { item.epid > 0 && it.epid == item.epid }
            ?: episodes.firstOrNull()
        if (ep == null || ep.bvid.isBlank()) item else item.copy(bvid = ep.bvid)
    }
    return resolved.filterNot { it.type == "bangumi" && it.bvid.isBlank() }
}

/** 加入稍后再看（需要登录）。 */
suspend fun BiliRepository.watchLaterAdd(aid: Long) {
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "稍后再看")
    val resp = api.watchLaterAdd(mapOf("aid" to aid.toString(), "csrf" to csrf()))
    if (resp.code != 0) {
        throw BiliException(resp.code, watchLaterError(resp.code, resp.message, "加入稍后再看失败"), "稍后再看")
    }
}

/** 从稍后再看移除。 */
suspend fun BiliRepository.watchLaterRemove(aid: Long) {
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "稍后再看")
    val resp = api.watchLaterDel(mapOf("aid" to aid.toString(), "csrf" to csrf()))
    if (resp.code != 0) {
        throw BiliException(resp.code, watchLaterError(resp.code, resp.message, "移出稍后再看失败"), "稍后再看")
    }
}

/** 稍后再看列表（接口按最新在前返回全部）。 */
suspend fun BiliRepository.watchLaterList(): List<Video> =
    api.watchLaterList().requireOk("获取稍后再看").list.map { it.toVideo() }

private fun BiliRepository.watchLaterError(code: Int, message: String, fallback: String): String = when (code) {
    -101 -> "请先登录"
    90001 -> "稍后再看列表已满"
    90003 -> "视频已被删除"
    else -> message.ifBlank { fallback }
}

// ---------------------------------------------------------------------------
// 空间「合集和列表」（合集 season / 系列 series）
// ---------------------------------------------------------------------------

/** UP 主的合集与系列列表，合集在前、系列在后（与 B 站空间页顺序一致）。 */
suspend fun BiliRepository.spaceCollections(mid: Long): List<SpaceCollection> {
    ensureBuvid()
    // page_size 超过 20 会被服务端拒绝（-400 请求错误），这里固定用 20
    val data = api.seasonsSeriesList(mid, pageNum = 1, pageSize = 20).requireOk("获取合集和列表")
    val items = data.itemsLists ?: return emptyList()
    val seasons = items.seasonsList.mapNotNull { item ->
        item.meta?.let { m ->
            SpaceCollection(
                id = m.seasonId,
                isSeason = true,
                title = m.name,
                cover = m.cover.fixUrl(),
                total = m.total,
            )
        }
    }
    val series = items.seriesList.mapNotNull { item ->
        item.meta?.let { m ->
            SpaceCollection(
                id = m.seriesId,
                isSeason = false,
                title = m.name,
                cover = m.cover.fixUrl(),
                total = m.total,
            )
        }
    }
    return seasons + series
}

/**
 * 合集 / 系列内的稿件，返回 (总数, 当前页)。
 * 合集走 seasons_archives_list（page_num），系列走 series/archives（pn）。
 */
suspend fun BiliRepository.collectionVideos(
    mid: Long,
    id: Long,
    isSeason: Boolean,
    pn: Int,
): Pair<Int, List<Video>> {
    ensureBuvid()
    return if (isSeason) {
        val data = api.seasonArchives(mid = mid, seasonId = id, pageNum = pn).requireOk("获取合集内容")
        val total = data.page?.total ?: data.meta?.total ?: data.archives.size
        total to data.archives.map { it.toVideo() }
    } else {
        val data = api.seriesArchives(mid = mid, seriesId = id, pn = pn).requireOk("获取系列内容")
        val total = data.page?.total ?: data.archives.size
        total to data.archives.map { it.toVideo() }
    }
}
