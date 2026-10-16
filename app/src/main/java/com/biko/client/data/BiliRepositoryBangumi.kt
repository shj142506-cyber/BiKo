package com.biko.client.data

import com.biko.client.data.model.BangumiSeason
import com.biko.client.data.model.Video
import com.biko.client.data.remote.BangumiSeasonResult
import com.biko.client.data.remote.fixUrl

// ---------------------------------------------------------------------------
// 番剧（PGC）
// ---------------------------------------------------------------------------

/**
 * 番剧索引，返回 (列表, 是否还有下一页)。
 * 一级分区里的「番剧」「影视」都是 PGC，排行榜接口不提供，只能走 PGC 索引。
 * [seasonType] 1=番剧 2=电影 3=纪录片 4=国创 5=电视剧 7=综艺。
 */
suspend fun BiliRepository.bangumiIndex(pn: Int, seasonType: Int = 1): Pair<List<BangumiSeason>, Boolean> {
    val data = api.bangumiIndex(page = pn, seasonType = seasonType).requireOk("获取番剧")
    return data.list.map { it.toSeason() } to (data.hasNext == 1)
}

/**
 * 番剧 / 影视详情：返回 (名称, 分集)。分集自带 bvid，
 * 可以直接交给普通的视频详情页播放（B 站对免费分集放行普通 playurl）。
 *
 * 注意：pgc/view/web/season 的业务数据挂在顶层 result 上（不包 data），
 * 因此这里直接读取 [PgcResponse.result]，不能走 requireOk（它读的是 data）。
 */
suspend fun BiliRepository.bangumiEpisodes(seasonId: Long): Pair<String, List<Video>> {
    val resp = api.bangumiSeason(seasonId)
    if (resp.code != 0) throw BiliException(resp.code, resp.message, "获取番剧详情")
    val result = resp.result ?: return "" to emptyList()
    return result.title to result.toEpisodeList()
}

/**
 * 从一个分集的 bvid 反查它所在的季度，返回 (剧名, 整季分集)。
 * 普通稿件（非 PGC）没有 redirect_url，返回 null。
 *
 * 详情页只拿得到 bvid，view 接口不下发 season_id，
 * 但番剧稿件会带 redirect_url（.../bangumi/play/epXXXX），据此拿到 ep_id 再反查整季。
 */
suspend fun BiliRepository.bangumiEpisodesOfBvid(bvid: String): Pair<String, List<Video>>? {
    val detail = api.videoDetail(bvid).requireOk("加载视频")
    val epId = Regex("/ep(\\d+)").find(detail.redirectUrl)?.groupValues?.get(1)?.toLongOrNull() ?: return null
    val resp = api.bangumiSeasonByEp(epId)
    if (resp.code != 0) return null
    val result = resp.result ?: return null
    return result.title to result.toEpisodeList()
}

/** 把 PGC 分集 DTO 映射成可播放的 [Video]（每集用自己的封面与时长）。 */
private fun BangumiSeasonResult.toEpisodeList(): List<Video> =
    episodes
        .filter { it.bvid.isNotEmpty() }
        .map {
            Video(
                aid = it.aid,
                bvid = it.bvid,
                title = it.longTitle.ifEmpty { it.title },
                pic = it.cover.ifBlank { cover }.fixUrl(),
                // PGC 的分集时长是毫秒，Video.duration 统一用秒
                duration = it.duration / 1000,
                upName = title,
                type = "bangumi",
                seasonId = seasonId,
                epid = it.id,
            )
        }