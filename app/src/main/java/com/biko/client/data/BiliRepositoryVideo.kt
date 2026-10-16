package com.biko.client.data

import com.biko.client.data.model.DanmakuItem
import com.biko.client.data.model.PlayInfo
import com.biko.client.data.model.StreamTrack
import com.biko.client.data.model.Video
import com.biko.client.data.remote.DashTrackDto
import com.biko.client.data.remote.DanmakuSegParser
import com.android.purebilibili.danmaku.parser.AdvancedDanmakuData
import com.android.purebilibili.danmaku.parser.DanmakuParser
import com.android.purebilibili.danmaku.parser.DanmakuProto
import com.android.purebilibili.danmaku.parser.bas.BasDanmaku

// ---------------------------------------------------------------------------
// 视频 / 播放 / 弹幕
// ---------------------------------------------------------------------------

/** 拿不到所选清晰度时降级用的最低清晰度（360P）。 */
private const val MIN_QN = 16

/** 弹幕分段最多取多少段（每段 6 分钟，20 段 = 2 小时，足够覆盖绝大多数视频）。 */
private const val MAX_DANMAKU_SEGMENTS = 20

suspend fun BiliRepository.videoDetail(bvid: String): Video =
    api.videoDetail(bvid).requireOk("加载视频").toVideo()

/**
 * 视频详情 + UP 主视频合集。
 * 合集的 ugc_season 与详情在同一次 view 响应里下发，合并成一个请求返回，避免重复拉取。
 * 第二个值无合集时为 null。
 */
suspend fun BiliRepository.videoDetailWithCollection(bvid: String): Pair<Video, Pair<String, List<Video>>?> {
    val data = api.videoDetail(bvid).requireOk("加载视频")
    return data.toVideo() to data.toUgcSeason()
}

/** 相关推荐视频（视频详情页底部）。 */
suspend fun BiliRepository.related(bvid: String): List<Video> =
    runCatching {
        api.related(bvid).requireOk("获取相关推荐").map { it.toVideo() }
            .filter { it.bvid.isNotEmpty() && it.bvid != bvid }
    }.getOrDefault(emptyList())

/**
 * 取播放地址：优先 Web 端 DASH（多清晰度 + 独立音频轨），
 * 依次尝试「所选清晰度 → 最低清晰度」，都拿不到时才退化为 html5 的整段 MP4。
 */
suspend fun BiliRepository.playInfo(aid: Long, cid: Long, qn: Int): PlayInfo {
    runCatching { buildDashPlayInfo(aid, cid, qn) }.getOrNull()?.let { return it }
    if (qn > MIN_QN) {
        runCatching { buildDashPlayInfo(aid, cid, MIN_QN) }.getOrNull()?.let { return it }
    }
    // 没有 DASH：退化为 html5 的整段 MP4（清晰度受限，但至少能播）
    val durl = runCatching {
        api.playUrl(aid = aid, cid = cid, qn = MIN_QN, fnval = 1, platform = "html5")
            .requireOk("获取播放地址").durl.firstOrNull()?.url
    }.getOrNull()?.takeIf { it.isNotEmpty() }
    if (!durl.isNullOrEmpty()) {
        return PlayInfo(quality = MIN_QN, durl = durl, acceptQuality = listOf(MIN_QN))
    }
    throw BiliException(-1, "该视频暂无可用播放地址", "获取播放地址")
}

/** Web 端 DASH 播放信息；缺少可用轨道时返回 null，交由上层降级重试。 */
private suspend fun BiliRepository.buildDashPlayInfo(aid: Long, cid: Long, qn: Int): PlayInfo? {
    val response = api.playUrl(aid = aid, cid = cid, qn = qn)
    if (response.code != 0) return null
    val data = response.data ?: return null
    val dash = data.dash

    // 缺少分片区间或地址的轨道直接丢掉，否则生成的 MPD 无法播放
    fun DashTrackDto.toStreamTrack(defaultMime: String) = StreamTrack(
        id = id,
        url = url,
        backupUrls = backups,
        bandwidth = bandwidth,
        codecs = codecs,
        mimeType = mime.ifEmpty { defaultMime },
        width = width,
        height = height,
        frameRate = fps,
        initRange = segment?.initRange.orEmpty(),
        indexRange = segment?.index.orEmpty(),
    ).takeIf { it.url.isNotEmpty() && it.initRange.isNotEmpty() && it.indexRange.isNotEmpty() }

    val videoTracks = dash?.video.orEmpty().mapNotNull { it.toStreamTrack("video/mp4") }
    val audioTracks = dash?.audio.orEmpty().mapNotNull { it.toStreamTrack("audio/mp4") }
    if (videoTracks.isEmpty() || audioTracks.isEmpty()) return null

    return PlayInfo(
        quality = data.quality,
        acceptQuality = data.acceptQuality,
        videoTracks = videoTracks,
        audioTracks = audioTracks,
        durationMs = (dash?.duration ?: 0) * 1000L,
        durl = null,
    )
}

/** html5 平台的整段 MP4 地址：DASH 分片不可用时的兜底线路。 */
suspend fun BiliRepository.progressivePlayUrl(aid: Long, cid: Long, qn: Int = MIN_QN): String? =
    runCatching {
        api.playUrl(aid = aid, cid = cid, qn = qn, fnval = 1, platform = "html5")
            .requireOk("获取播放地址").durl.firstOrNull()?.url
    }.getOrNull()?.takeIf { it.isNotEmpty() }

/**
 * 拉取弹幕。
 *
 * 走 `x/v2/dm/web/seg.so`（protobuf 分段），**不是**老的 `x/v1/dm/list.so` ——
 * 老接口现在对普通请求一律返回 412 风控，会导致全 App 弹幕为空。
 * 每个分段覆盖 6 分钟，按视频时长取够分段；某段返回空（超出时长）就停止。
 */
suspend fun BiliRepository.danmaku(cid: Long, durationSec: Int): List<DanmakuItem> {
    if (cid <= 0) return emptyList()
    val segments = ((durationSec.coerceAtLeast(0) + DanmakuSegParser.SEGMENT_SECONDS - 1) /
        DanmakuSegParser.SEGMENT_SECONDS).coerceIn(1, MAX_DANMAKU_SEGMENTS)
    val all = ArrayList<DanmakuItem>(segments * 300)
    for (index in 1..segments) {
        val bytes = runCatching {
            api.danmakuXml(
                "https://api.bilibili.com/x/v2/dm/web/seg.so" +
                    "?type=1&oid=$cid&segment_index=$index",
            ).bytes()
        }.getOrNull() ?: break
        val items = runCatching { DanmakuSegParser.parse(bytes) }.getOrDefault(emptyList())
        if (items.isEmpty()) break
        all += items
    }
    all.sortBy { it.timeMs }
    return all
}

/**
 * 特殊弹幕：Mode 7（高级定位弹幕）+ Mode 9（BAS 代码弹幕）。
 *
 * 先请求 `x/v2/dm/web/view` 拿 special_dms 文件地址列表，再逐个全量下载解析。
 * 这类文件一般很小（几十 KB 量级），直接整包下载即可，无需分段索引。
 */
data class SpecialDanmaku(
    val advanced: List<AdvancedDanmakuData> = emptyList(),
    val bas: List<BasDanmaku> = emptyList(),
) {
    val isEmpty: Boolean get() = advanced.isEmpty() && bas.isEmpty()
}

suspend fun BiliRepository.specialDanmaku(cid: Long, aid: Long): SpecialDanmaku {
    if (cid <= 0L) return SpecialDanmaku()
    val reply = runCatching {
        DanmakuProto.parseWebViewReply(api.danmakuView(oid = cid, pid = aid).bytes())
    }.getOrNull() ?: return SpecialDanmaku()
    if (reply.specialDms.isEmpty()) return SpecialDanmaku()

    val advanced = ArrayList<AdvancedDanmakuData>()
    val bas = ArrayList<BasDanmaku>()
    reply.specialDms.forEach { raw ->
        val url = if (raw.startsWith("//")) "https:$raw" else raw
        val bytes = runCatching { api.specialDanmakuFile(url).bytes() }.getOrNull() ?: return@forEach
        val parsed = runCatching { DanmakuParser.parseProtobuf(listOf(bytes)) }.getOrNull() ?: return@forEach
        advanced += parsed.advancedList
        bas += parsed.basList
    }
    return SpecialDanmaku(
        advanced = advanced.sortedBy { it.startTimeMs },
        bas = bas.sortedBy { it.startTimeMs },
    )
}