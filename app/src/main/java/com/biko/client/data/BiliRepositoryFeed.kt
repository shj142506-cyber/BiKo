package com.biko.client.data

import com.biko.client.data.model.Video
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// ---------------------------------------------------------------------------
// 首页 / 排行 / 推荐
// ---------------------------------------------------------------------------

/** 补充分区视频统计时的最大并发（newlist 不下发 stat，需要逐条查 view）。 */
private const val STAT_FETCH_CONCURRENCY = 6

/** 排行榜接口路径（v2）。2025 起未带 WBI 签名会被风控拦截返回 -352，签名后匿名即可查看。 */
private suspend fun BiliRepository.rankingUrl(rid: Int): String =
    "x/web-interface/ranking/v2?" + wbi.sign(mapOf("rid" to rid.toString(), "type" to "all"))

suspend fun BiliRepository.ranking(rid: Int): List<Video> =
    api.ranking(rankingUrl(rid)).requireOk("获取排行").list.map { it.toVideo() }

/**
 * 一级分区 tid → 排行榜 v2 的 rid（v2 用的是 100x 编号）。
 * 分区不能用 newlist：那个接口只下发「刚发布、0 播放」的视频（stat 全为 0），
 * 卡片上除了标题什么都显示不出来；排行榜带真实播放 / 弹幕 / 发布时间。
 */
private val REGION_RANKING_RID = mapOf(
    1 to 1005,   // 动画
    3 to 1003,   // 音乐
    4 to 1008,   // 游戏
    5 to 1002,   // 娱乐
    36 to 1010,  // 知识
    119 to 1007, // 鬼畜
    129 to 1004, // 舞蹈
    155 to 1014, // 时尚
    160 to 1015, // 生活
    181 to 1001, // 影视
    188 to 1012, // 科技
    202 to 1009, // 资讯
    211 to 1020, // 美食
    217 to 1024, // 动物圈
    223 to 1013, // 汽车
    234 to 1018, // 运动
)

/**
 * 分区视频流，支持翻页（[pn] 从 1 开始）。
 * 主接口是 newlist（真正的视频流，可一页页往下刷），并用 view 逐条补齐统计；
 * 只有第一页为空（如动物圈，newlist 无新投稿）才回退分区排行榜。
 */
suspend fun BiliRepository.regionFeed(rid: Int, pn: Int): List<Video> {
    // newlist 需要设备指纹，缺失时会返回空列表（风控）
    ensureBuvid()
    val list = runCatching { api.regionNew(rid = rid, pn = pn).requireOk("获取分区").archives }
        .getOrElse { e ->
            // 番剧等 PGC 分区没有 newlist 数据（-400），按无内容处理
            if ((e as? BiliException)?.code == -400) emptyList() else throw e
        }
    if (list.isNotEmpty()) return enrichStats(list.map { it.toVideo() })
    // 第一页为空才回退排行榜；后续页码返回空表示已经到底
    if (pn > 1) return emptyList()
    REGION_RANKING_RID[rid]?.let { rankingRid ->
        return runCatching { api.ranking(rankingUrl(rankingRid)).requireOk("获取分区").list }
            .getOrElse { emptyList() }
            .map { it.toVideo() }
    }
    return emptyList()
}

/** 已补齐过统计的视频（按 bvid 缓存，避免反复刷同一页时重复请求）。 */
private val statCache = ConcurrentHashMap<String, Video>()

/**
 * newlist 的 stat 全为 0，用 view 接口逐条补齐播放 / 弹幕 / 评论数、时长与发布时间，
 * 让分区卡片和「推荐」一样有完整信息。并发受限，取不到时保留原样。
 */
private suspend fun BiliRepository.enrichStats(videos: List<Video>): List<Video> = coroutineScope {
    val gate = Semaphore(STAT_FETCH_CONCURRENCY)
    videos.map { video ->
        async {
            if (video.bvid.isEmpty() || video.views > 0) return@async video
            val detail = statCache[video.bvid] ?: gate.withPermit {
                runCatching { api.videoDetail(video.bvid).requireOk("获取视频").toVideo() }
                    .getOrNull()
                    ?.also { statCache[video.bvid] = it }
            }
            if (detail == null) {
                video
            } else {
                video.copy(
                    duration = video.duration.takeIf { it > 0 } ?: detail.duration,
                    pubdate = video.pubdate.takeIf { it > 0 } ?: detail.pubdate,
                    upFace = video.upFace.ifEmpty { detail.upFace },
                    views = detail.views,
                    danmaku = detail.danmaku,
                    replies = detail.replies,
                    likes = detail.likes,
                    coins = detail.coins,
                    favorites = detail.favorites,
                )
            }
        }
    }.awaitAll()
}

/** 首页推荐流（匿名可用），[freshIdx] 递增即为翻页；只保留可播放的视频条目。 */
suspend fun BiliRepository.feedRecommend(freshIdx: Int): List<Video> =
    api.feedRcmd(freshIdx = freshIdx).requireOk("获取推荐").item
        .filter { it.isVideo }
        .map { it.toVideo() }