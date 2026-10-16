package com.biko.client.data

import com.biko.client.data.model.LiveArea
import com.biko.client.data.model.LiveChatItem
import com.biko.client.data.model.LiveDanmakuInfo
import com.biko.client.data.model.LivePlayInfo
import com.biko.client.data.model.LiveRoom
import com.biko.client.data.remote.LiveRoomPlayInfoData
import com.biko.client.data.remote.fixUrl
import com.biko.client.data.remote.watchedTextOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ---------------------------------------------------------------------------
// 直播
// ---------------------------------------------------------------------------

/** 直播默认清晰度：原画。 */
private const val LIVE_DEFAULT_QN = 10000

/**
 * 直播分区树（一级分类 + 其下二级分区），最前面固定插入「全部」（id=0）。
 * 接口不可用时只返回「全部」，保证直播页仍能正常展示。
 */
suspend fun BiliRepository.liveAreas(): List<LiveArea> {
    val areas = runCatching {
        api.liveAreaList("${LIVE_BASE}room/v1/area/getList?parent_area_id=0")
            .requireOk("获取直播分区")
    }.getOrDefault(emptyList())
        .mapNotNull { dto -> dto.name.takeIf { it.isNotBlank() }?.let { dto.toArea() } }
    return listOf(LiveArea(0, "全部")) + areas
}

/**
 * 直播房间流，支持翻页（[pn] 从 1 开始）。
 * 优先走 xlive/web-interface/v1/second/getList（Web 直播页同款接口，WBI 签名）：
 * 与 B 站直播页一致，返回每个直播间的累计「看过」watched_show；
 * 该接口需要 WBI 签名（匿名会返回 -352），失败时回退 room/v3/area/getRoomList
 * （匿名可用，但只下发实时人气 online，无 watched_show）。
 *
 * [parentAreaId] 为一级分区 id（0 表示全部）；[areaId] 为二级分区 id（0 表示不限）。
 * [sortType] 支持 online（人气）等排序。
 */
suspend fun BiliRepository.liveFeed(
    parentAreaId: Int,
    areaId: Int = 0,
    pn: Int,
    sortType: String = "online",
): List<LiveRoom> {
    runCatching {
        ensureBuvid()
        val query = wbi.sign(
            mapOf(
                "platform" to "web",
                "parent_area_id" to parentAreaId.toString(),
                "area_id" to areaId.toString(),
                "sort_type" to sortType,
                "page" to pn.toString(),
                "web_location" to "444.8",
            ),
        )
        api.liveList("${LIVE_BASE}xlive/web-interface/v1/second/getList?$query")
            .requireOk("获取直播").toLiveRooms()
    }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
    return api.liveList(
        "${LIVE_BASE}room/v3/area/getRoomList" +
            "?parent_area_id=$parentAreaId&area_id=$areaId&page=$pn" +
            "&page_size=30&sort_type=$sortType&platform=web",
    ).requireOk("获取直播").toLiveRooms()
}

/**
 * 直播播放地址与可用清晰度。
 * 优先走 xlive/web-room/v2/index/getRoomPlayInfo（WBI 签名）：返回完整的
 * stream → format → codec → urlInfo 线路树与真实 accept_qn 档位；
 * 失败时回退到老的 room/v1/Room/playUrl（h5，仅单条 durl）。
 */
suspend fun BiliRepository.livePlayInfo(roomId: Long, qn: Int = LIVE_DEFAULT_QN): LivePlayInfo {
    runCatching { livePlayInfoTree(roomId, qn) }
        .getOrNull()
        ?.takeIf { !it.url.isNullOrEmpty() }
        ?.let { return it }
    return livePlayInfoLegacy(roomId, qn)
}

private suspend fun BiliRepository.livePlayInfoTree(roomId: Long, qn: Int): LivePlayInfo {
    // 该接口要 WBI 签名 + buvid3，缺 buvid 会被风控挡掉（-352）从而白白多走一次回退请求
    ensureBuvid()
    val query = wbi.sign(
        mapOf(
            "room_id" to roomId.toString(),
            // protocol 0=FLV(http_stream) 1=HLS(http_hls)，两个都要
            "protocol" to "0,1",
            "format" to "0,1,2",
            "codec" to "0,1,2",
            "qn" to qn.toString(),
            "platform" to "web",
            "ptype" to "8",
            "dolby" to "5",
            "panorama" to "1",
            "web_location" to "444.8",
        ),
    )
    val data = api.livePlayInfo("${LIVE_BASE}xlive/web-room/v2/index/getRoomPlayInfo?$query")
        .requireOk("获取直播流")
    val acceptQn = linkedSetOf<Int>()
    data.playurlInfo?.playurl?.stream.orEmpty().forEach { stream ->
        stream.format.forEach { format -> format.codec.forEach { codec -> acceptQn.addAll(codec.acceptQn) } }
    }
    // 优先 HLS（与旧接口行为一致、兼容性最好），其次 FLV；编码优先 avc
    val pick = pickLiveCodec(data, preferHls = true)
    return LivePlayInfo(
        url = pick?.url,
        quality = pick?.qn?.takeIf { it > 0 } ?: qn,
        acceptQuality = acceptQn.toList().sortedDescending(),
        protocolName = pick?.protocolName.orEmpty(),
        formatName = pick?.formatName.orEmpty(),
        codecName = pick?.codecName.orEmpty(),
        liveStatus = data.liveStatus,
        liveTime = data.liveTime,
        uid = data.uid,
    )
}

private data class LiveCodecPick(
    val url: String,
    val qn: Int,
    val protocolName: String,
    val formatName: String,
    val codecName: String,
)

/** 按「协议 → 容器格式 → 编码」优先级挑一条可用线路，拼出可直接播放的地址。 */
private fun pickLiveCodec(data: LiveRoomPlayInfoData, preferHls: Boolean): LiveCodecPick? {
    val protocolOrder = if (preferHls) listOf("http_hls", "http_stream") else listOf("http_stream", "http_hls")
    val formatOrder = listOf("flv", "ts", "fmp4")
    val codecOrder = listOf("avc", "hevc", "av1")
    val streams = data.playurlInfo?.playurl?.stream.orEmpty()
        .sortedBy { it.protocolName.rankIn(protocolOrder) }
    for (stream in streams) {
        for (format in stream.format.sortedBy { it.formatName.rankIn(formatOrder) }) {
            for (codec in format.codec.sortedBy { it.codecName.rankIn(codecOrder) }) {
                val info = codec.urlInfo.firstOrNull() ?: continue
                val url = info.host + codec.baseUrl + info.extra
                if (url.isBlank()) continue
                return LiveCodecPick(url, codec.currentQn, stream.protocolName, format.formatName, codec.codecName)
            }
        }
    }
    return null
}

private fun String.rankIn(order: List<String>): Int =
    order.indexOf(this).let { if (it < 0) order.size else it }

private suspend fun BiliRepository.livePlayInfoLegacy(roomId: Long, qn: Int): LivePlayInfo {
    val data = api.livePlayUrl("${LIVE_BASE}room/v1/Room/playUrl?cid=$roomId&qn=$qn&platform=h5")
        .requireOk("获取直播流")
    return LivePlayInfo(
        url = data.durl.firstOrNull()?.url,
        quality = data.currentQn.takeIf { it > 0 } ?: qn,
        acceptQuality = data.acceptQn,
    )
}

/**
 * 直播间信息。优先 getH5InfoByRoom（第三方客户端同款轻量接口，无需签名 / buvid，
 * 出首屏最快，且能拿到服务端排版的「多少人看过」text_large）；
 * 失败回退 getInfoByRoom，再退 room/v1/Room/get_info。
 */
suspend fun BiliRepository.liveRoomInfo(roomId: Long): LiveRoom {
    runCatching { liveRoomInfoH5(roomId) }
        .getOrNull()
        ?.takeIf { it.title.isNotEmpty() || it.liveStatus > 0 || it.watchedText.isNotEmpty() }
        ?.let { return it }
    runCatching { liveRoomInfoInit(roomId) }
        .getOrNull()
        ?.takeIf { it.uid > 0 || it.title.isNotEmpty() }
        ?.let { return it }
    val d = api.liveRoomInfo("${LIVE_BASE}room/v1/Room/get_info?room_id=$roomId")
        .requireOk("获取直播间")
    return LiveRoom(
        roomId = roomId,
        title = d.title,
        area = d.area,
        online = d.online,
        watchedShow = d.watchedCount(),
        watchedText = d.watchedText(),
    )
}

/** getH5InfoByRoom：一次拿到标题 / 看过 / 主播 / 开播状态，无签名依赖。 */
private suspend fun BiliRepository.liveRoomInfoH5(roomId: Long): LiveRoom {
    val data = api.liveRoomInfoH5("${LIVE_BASE}xlive/web-room/v1/index/getH5InfoByRoom?room_id=$roomId")
        .requireOk("获取直播间")
    val room = data.roomInfo
    val anchor = data.anchorInfo?.baseInfo
    return LiveRoom(
        roomId = room?.roomId?.takeIf { it > 0 } ?: roomId,
        uid = room?.uid ?: 0,
        title = room?.title.orEmpty(),
        uname = anchor?.uname.orEmpty(),
        face = anchor?.face.orEmpty().fixUrl(),
        cover = room?.cover.orEmpty().fixUrl(),
        area = room?.areaName.orEmpty().ifEmpty { room?.parentAreaName.orEmpty() },
        online = room?.online ?: 0,
        watchedShow = watchedNum(data.watchedShow),
        watchedText = watchedTextOf(data.watchedShow),
        liveStatus = room?.liveStatus ?: 0,
    )
}

/** getInfoByRoom：信息更全（含开播时长等），作为 H5 接口的补充与回退。 */
private suspend fun BiliRepository.liveRoomInfoInit(roomId: Long): LiveRoom {
    val init = api.liveRoomInit("${LIVE_BASE}xlive/web-room/v1/index/getInfoByRoom?room_id=$roomId")
        .requireOk("获取直播间")
    val room = init.roomInfo
    val anchor = init.anchorInfo?.baseInfo
    return LiveRoom(
        roomId = room?.roomId?.takeIf { it > 0 } ?: roomId,
        uid = room?.uid ?: 0,
        title = room?.title.orEmpty(),
        uname = anchor?.uname.orEmpty(),
        face = anchor?.face.orEmpty().fixUrl(),
        cover = room?.cover.orEmpty().fixUrl(),
        area = room?.areaName.orEmpty().ifEmpty { room?.parentAreaName.orEmpty() },
        online = room?.online ?: 0,
        watchedShow = watchedNum(room?.watchedShow),
        watchedText = watchedTextOf(room?.watchedShow),
        liveStatus = room?.liveStatus ?: 0,
    )
}

/** 从 watched_show 容错解析累计观看人数（对象 {num} 或纯数字）。 */
private fun watchedNum(el: JsonElement?): Int {
    val content = when (el) {
        is JsonPrimitive -> el.content
        is JsonObject -> (el["num"] as? JsonPrimitive)?.content
        else -> null
    }
    return content?.filter(Char::isDigit)?.toIntOrNull() ?: 0
}

/**
 * 直播间最近的历史弹幕（进房先垫几条，避免聊天区一开始是空的）。
 * 失败返回空列表，不影响播放。
 */
suspend fun BiliRepository.liveHistoryDanmaku(roomId: Long): List<LiveChatItem> {
    ensureBuvid()
    val data = api.liveHistoryDanmaku("${LIVE_BASE}xlive/web-room/v1/dM/gethistory?roomid=$roomId&type=0")
        .requireOk("获取历史弹幕")
    return data.room.mapNotNull { d ->
        if (d.text.isBlank()) null
        else LiveChatItem(uid = d.uid, uname = d.nickname, text = d.text)
    }
}

/**
 * 直播弹幕服务器地址与鉴权 token。
 * getDanmuInfo 从 2025-06 起要求 buvid3 + WBI 签名，否则返回 -352 风控失败。
 */
suspend fun BiliRepository.liveDanmakuInfo(roomId: Long): LiveDanmakuInfo {
    ensureBuvid()
    val query = wbi.sign(
        mapOf(
            "id" to roomId.toString(),
            "type" to "0",
            "web_location" to "444.8",
        ),
    )
    val data = api.liveDanmuInfo("${LIVE_BASE}xlive/web-room/v1/index/getDanmuInfo?$query")
        .requireOk("获取弹幕服务器")
    val host = data.hostList.firstOrNull()
        ?: throw BiliException(-1, "无可用弹幕服务器", "获取弹幕服务器")
    return LiveDanmakuInfo(host = "${host.host}:${host.wssPort}", token = data.token)
}

/** 发送直播弹幕，成功时返回服务端提示（一般为「发送成功」）。 */
suspend fun BiliRepository.sendLiveDanmaku(roomId: Long, msg: String): String {
    val text = msg.trim()
    if (text.isEmpty()) throw BiliException(-1, "弹幕不能为空", "发送弹幕")
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "发送弹幕")
    val resp = api.liveSendDanmaku(
        "${LIVE_BASE}msg/send",
        mapOf(
            "bubble" to "0",
            "msg" to text,
            "color" to "16777215",
            "mode" to "1",
            "fontsize" to "25",
            "rnd" to (System.currentTimeMillis() / 1000).toString(),
            "roomid" to roomId.toString(),
            "csrf" to csrf(),
            "csrf_token" to csrf(),
        ),
    )
    if (resp.code != 0) throw BiliException(resp.code, resp.message.ifBlank { "发送失败" }, "发送弹幕")
    return resp.message.ifBlank { "发送成功" }
}

/**
 * 直播间点赞（连点累积后一次性上报）。
 * 与第三方客户端一致走 likeReportV3（WBI 签名）。
 */
suspend fun BiliRepository.sendLiveLike(roomId: Long, uid: Long, anchorId: Long, clickTime: Int): String {
    if (!session.isLogin()) throw BiliException(-101, "请先登录", "点赞")
    ensureBuvid()
    val params = mutableMapOf(
        "click_time" to clickTime.toString(),
        "room_id" to roomId.toString(),
        "uid" to uid.toString(),
        "web_location" to "444.8",
        "csrf" to csrf(),
    )
    if (anchorId > 0) params["anchor_id"] = anchorId.toString()
    val query = wbi.sign(params)
    val resp = api.liveLikeReport(
        "${LIVE_BASE}xlive/app-ucenter/v1/like_info_v3/like/likeReportV3?$query",
        params + mapOf("w_rid" to query.substringAfter("w_rid=").substringBefore('&')),
    )
    if (resp.code != 0) throw BiliException(resp.code, resp.message.ifBlank { "点赞失败" }, "点赞")
    return resp.message.ifBlank { "点赞成功" }
}
