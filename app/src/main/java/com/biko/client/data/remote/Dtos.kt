package com.biko.client.data.remote

import com.biko.client.data.model.ArticleSummary
import com.biko.client.data.model.BangumiSeason
import com.biko.client.data.model.Comment
import com.biko.client.data.model.CommentPicture
import com.biko.client.data.model.LiveArea
import com.biko.client.data.model.LiveRoom
import com.biko.client.data.model.RelationUser
import com.biko.client.data.model.SearchUser
import com.biko.client.data.model.Video
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** B 站统一响应包装。 */
@Serializable
data class BiliResponse<T>(
    val code: Int = -1,
    val message: String = "",
    val data: T? = null,
)

// ---------------------------------------------------------------------------
// 通用卡片结构（排行 / 分区 / 详情共用）
// ---------------------------------------------------------------------------

@Serializable
data class OwnerDto(val mid: Long = 0, val name: String = "", val face: String = "")

@Serializable
data class StatDto(
    val view: Int = 0,
    val danmaku: Int = 0,
    val reply: Int = 0,
    val like: Int = 0,
    val coin: Int = 0,
    val favorite: Int = 0,
)

@Serializable
data class VideoCardDto(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val tname: String = "",
    val desc: String = "",
    val owner: OwnerDto? = null,
    val stat: StatDto? = null,
) {
    fun toVideo() = Video(
        aid = aid, bvid = bvid, cid = cid, title = title, pic = pic.fixUrl(),
        duration = duration, pubdate = pubdate, category = tname, desc = desc,
        upName = owner?.name.orEmpty(), upMid = owner?.mid ?: 0, upFace = owner?.face.orEmpty().fixUrl(),
        views = stat?.view ?: 0, danmaku = stat?.danmaku ?: 0,
        replies = stat?.reply ?: 0,
        likes = stat?.like ?: 0, coins = stat?.coin ?: 0, favorites = stat?.favorite ?: 0,
    )
}

@Serializable
data class VideoCardListData(val list: List<VideoCardDto> = emptyList())

// ---------------------------------------------------------------------------
// 首页推荐流（index/top/feed/rcmd）：未登录也能用，ranking 接口未登录会 -352
// ---------------------------------------------------------------------------

@Serializable
data class FeedRcmdItemDto(
    /** 该接口用 id 表示 aid。 */
    val id: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    /** 条目类型：av=视频，另有 live / ogv / ad 等，这些没有封面，需要过滤掉。 */
    val goto: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: OwnerDto? = null,
    val stat: StatDto? = null,
) {
    /**
     * 是否是可播放的普通视频条目。
     * 推荐流会掺入广告（goto=ad）以及缺封面 / 标题 / 分区号的占位条目，
     * 不过滤掉的话它们会渲染成空白卡片。
     */
    val isVideo: Boolean
        get() = goto != "ad" &&
            bvid.isNotEmpty() &&
            cid > 0 &&
            pic.isNotEmpty() &&
            title.isNotEmpty()

    fun toVideo() = Video(
        aid = id, bvid = bvid, cid = cid, title = title.replace(Regex("<[^>]+>"), ""),
        pic = pic.fixUrl(), duration = duration, pubdate = pubdate,
        upName = owner?.name.orEmpty(), upMid = owner?.mid ?: 0, upFace = owner?.face.orEmpty().fixUrl(),
        views = stat?.view ?: 0, danmaku = stat?.danmaku ?: 0, likes = stat?.like ?: 0,
    )
}

@Serializable
data class FeedRcmdData(val item: List<FeedRcmdItemDto> = emptyList())

// 视频详情（view 接口）
@Serializable
data class VideoDetailData(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val tname: String = "",
    val desc: String = "",
    val owner: OwnerDto? = null,
    val stat: StatDto? = null,
    /** 番剧分集会带该字段（形如 .../bangumi/play/ep752432），普通稿件为空。 */
    @SerialName("redirect_url") val redirectUrl: String = "",
    /** UP 主视频合集 / 系列（仅合集内的稿件下发）。 */
    @SerialName("ugc_season") val ugcSeason: UgcSeasonDto? = null,
) {
    fun toVideo() = Video(
        aid = aid, bvid = bvid, cid = cid, title = title, pic = pic.fixUrl(),
        duration = duration, pubdate = pubdate, category = tname, desc = desc,
        upName = owner?.name.orEmpty(), upMid = owner?.mid ?: 0, upFace = owner?.face.orEmpty().fixUrl(),
        views = stat?.view ?: 0, danmaku = stat?.danmaku ?: 0,
        replies = stat?.reply ?: 0,
        likes = stat?.like ?: 0, coins = stat?.coin ?: 0, favorites = stat?.favorite ?: 0,
    )

    /**
     * 提取 UP 主视频合集（合集名 to 合集内全部稿件）。
     * 一个合集可能被切成多段（sections），这里拉平为一条顺序列表；无合集时返回 null。
     */
    fun toUgcSeason(): Pair<String, List<Video>>? {
        val season = ugcSeason ?: return null
        val episodes = season.sections.flatMap { it.episodes }.mapNotNull { ep ->
            val epBvid = ep.bvid.ifEmpty { return@mapNotNull null }
            Video(
                aid = ep.arc?.aid ?: ep.aid,
                bvid = epBvid,
                cid = ep.cid,
                title = ep.title.ifEmpty { ep.arc?.title.orEmpty() },
                pic = ep.arc?.pic.orEmpty().fixUrl(),
                duration = ep.arc?.duration ?: 0,
                pubdate = ep.arc?.pubdate ?: 0,
                views = ep.arc?.stat?.view ?: 0,
            )
        }
        if (episodes.isEmpty()) return null
        return season.title to episodes
    }
}

// UP 主视频合集 / 系列（view 接口的 ugc_season）
@Serializable
data class UgcSeasonDto(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "",
    val sections: List<UgcSectionDto> = emptyList(),
)

@Serializable
data class UgcSectionDto(
    val title: String = "",
    val episodes: List<UgcEpisodeDto> = emptyList(),
)

@Serializable
data class UgcEpisodeDto(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    /** 稿件元数据（封面 / 时长 / 播放量等）。 */
    val arc: UgcEpisodeArcDto? = null,
)

@Serializable
data class UgcEpisodeArcDto(
    val aid: Long = 0,
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val stat: StatDto? = null,
)

// 分区最新（newlist）
@Serializable
data class NewListData(val archives: List<VideoCardDto> = emptyList())

// ---------------------------------------------------------------------------
// 搜索
// ---------------------------------------------------------------------------

@Serializable
data class SearchVideoDto(
    /** 条目类型：普通视频是 "video"，课程是 "ketang"、番剧是 "media_bangumi" 等。 */
    val type: String = "",
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: String = "",
    val play: Int = 0,
    @SerialName("video_review") val videoReview: Int = 0,
    /** 弹幕数（与 video_review 同义，接口两个字段都会下发）。 */
    val danmaku: Int = 0,
    /** 评论数。 */
    val review: Int = 0,
    val author: String = "",
    val mid: Long = 0,
    val upic: String = "",
    val pubdate: Long = 0,
    val typename: String = "",
) {
    /**
     * 是否是可用普通播放链路打开的视频。
     *
     * 搜索结果会混入课程（type=ketang）、番剧/影视条目等：它们没有时长、
     * 没有发布时间、没有弹幕，点开也播不了（课程要走 PGC/cheese 链路），
     * 在卡片上只会显示成一张没有封面和时长的空白卡。这里统一过滤掉。
     */
    val isPlayableVideo: Boolean
        get() = type == "video" &&
            bvid.isNotEmpty() &&
            pic.isNotEmpty() &&
            duration.isNotBlank() &&
            pubdate > 0

    fun toVideo() = Video(
        aid = aid, bvid = bvid, title = title.replace(Regex("<[^>]+>"), ""), pic = pic.fixUrl(),
        duration = duration.parseDuration(), views = play,
        danmaku = videoReview.takeIf { it > 0 } ?: danmaku, replies = review,
        pubdate = pubdate,
        upName = author, upMid = mid, upFace = upic.fixUrl(), category = typename,
    )
}

@Serializable
data class SearchData(val result: List<SearchVideoDto> = emptyList())

// ---- 搜索用户（UP 主 bili_user / 主播 live_user 共用同一响应结构）----

@Serializable
data class SearchUserDto(
    val mid: Long = 0,
    val uname: String = "",
    val upic: String = "",
    val usign: String = "",
    val fans: Int = 0,
    val videos: Int = 0,
    val level: Int = 0,
    /** 是否正在直播：1 表示直播中。 */
    @SerialName("is_live") val isLive: Int = 0,
    /** 直播间号（主播结果里的字段）。 */
    @SerialName("roomid") val roomId: Long = 0,
    /** 直播间号（UP 主结果里的字段，与 roomid 二选一）。 */
    @SerialName("live_roomid") val liveRoomId: Long = 0,
    /** 直播状态：1 表示直播中。 */
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("official_verify") val officialVerify: OfficialVerifyDto? = null,
) {
    /** [defaultLive] 为 true 时（主播 Tab），即使接口未标记也按可直播处理。 */
    fun toSearchUser(defaultLive: Boolean) = SearchUser(
        mid = mid,
        name = uname.replace(Regex("<[^>]+>"), ""),
        face = upic.fixUrl(),
        sign = usign,
        fans = fans,
        videos = videos,
        level = level,
        isLive = isLive == 1 || liveStatus == 1,
        liveRoomId = liveRoomId.takeIf { it > 0 } ?: roomId,
        verifyDesc = officialVerify?.takeIf { it.type >= 0 }?.desc.orEmpty(),
    )
}

@Serializable
data class SearchUserData(val result: List<SearchUserDto> = emptyList())

// ---- 搜索直播间（live_room）----

@Serializable
data class SearchLiveRoomDto(
    @SerialName("roomid") val roomId: Long = 0,
    val title: String = "",
    val uname: String = "",
    val uface: String = "",
    val cover: String = "",
    @SerialName("area_v2_name") val areaV2Name: String = "",
    @SerialName("cate_name") val cateName: String = "",
    val online: JsonElement? = null,
) {
    fun toLiveRoom(): LiveRoom {
        val onlineText = (online as? JsonPrimitive)?.content.orEmpty()
        return LiveRoom(
            roomId = roomId,
            title = title.replace(Regex("<[^>]+>"), ""),
            uname = uname,
            face = uface.fixUrl(),
            cover = cover.fixUrl(),
            area = areaV2Name.ifEmpty { cateName },
            online = onlineText.filter(Char::isDigit).toIntOrNull() ?: 0,
        )
    }
}

@Serializable
data class SearchLiveRoomData(val result: List<SearchLiveRoomDto> = emptyList())

// ---- 搜索番剧 / 影视（media_bangumi / media_ft）----

@Serializable
data class MediaScoreDto(
    /** 评分，接口可能下发字符串或数字。 */
    val score: JsonElement? = null,
    @SerialName("user_count") val userCount: Int = 0,
)

@Serializable
data class SearchMediaDto(
    /** 番剧索引 id（等于 PGC season_id），用于拉取分集。 */
    @SerialName("season_id") val seasonId: Long = 0,
    /** 备用字段：部分结果用 media_id 标识。 */
    @SerialName("media_id") val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    /** 更新进度，如「更新至第12话」「全8话」。 */
    @SerialName("index_show") val indexShow: String = "",
    /** 集数。 */
    @SerialName("ep_size") val epSize: Int = 0,
    @SerialName("media_score") val mediaScore: MediaScoreDto? = null,
    /** 角标文案：如「大会员」「独家」。 */
    val badge: String = "",
    /** 角标附加信息，部分接口只在 badge_info.text 里下发文案。 */
    @SerialName("badge_info") val badgeInfo: BadgeInfoDto? = null,
) {
    fun toSeason(): BangumiSeason {
        val scoreText = (mediaScore?.score as? JsonPrimitive)?.contentOrNull.orEmpty()
        val subtitle = listOfNotNull(
            indexShow.takeIf { it.isNotBlank() },
            scoreText.takeIf { it.isNotBlank() && it != "0" }?.let { "$it 分" },
        ).joinToString(" · ")
        return BangumiSeason(
            seasonId = seasonId.takeIf { it > 0 } ?: mediaId,
            title = title.replace(Regex("<[^>]+>"), ""),
            cover = cover.fixUrl(),
            subtitle = subtitle,
            badge = badge.ifBlank { badgeInfo?.text.orEmpty() },
        )
    }
}

@Serializable
data class SearchMediaData(val result: List<SearchMediaDto> = emptyList())

// ---------------------------------------------------------------------------
// 专栏 / 文章（x/article/view、搜索专栏、x/space/wbi/article）
// ---------------------------------------------------------------------------

@Serializable
data class ArticleAuthorDto(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

/** 文章新版正文（opus，段落化结构）。 */
@Serializable
data class ArticleOpusDto(
    val title: String = "",
    val content: JsonObject? = null,
) {
    /** 取出 content.paragraphs 数组，逐项解析为 JsonObject。 */
    fun paragraphs(): List<JsonObject> {
        val arr = content?.get("paragraphs")?.let { it as? JsonArray } ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }
}

/** 文章详情（x/article/view）。 */
@Serializable
data class ArticleViewData(
    val id: Long = 0,
    val title: String = "",
    val summary: String = "",
    @SerialName("banner_url") val bannerUrl: String = "",
    val author: ArticleAuthorDto? = null,
    @SerialName("publish_time") val publishTime: Long = 0,
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    @SerialName("origin_image_urls") val originImageUrls: List<String> = emptyList(),
    @SerialName("dyn_id_str") val dynamicId: String = "",
    val type: Int = 0,
    val content: String = "",
    val opus: ArticleOpusDto? = null,
    val ops: List<JsonObject> = emptyList(),
)

/** 搜索专栏结果项（x/web-interface/search/type，search_type=article）。 */
@Serializable
data class ArticleSearchItemDto(
    val type: String = "",
    val id: Long = 0,
    val mid: Long = 0,
    val title: String = "",
    val desc: String = "",
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    val view: Int = 0,
    val like: Int = 0,
    val reply: Int = 0,
    @SerialName("pub_time") val pubTime: Long = 0,
    @SerialName("category_name") val categoryName: String = "",
    val author: String = "",
    @SerialName("upic") val upFace: String = "",
    @SerialName("covers") val covers: List<String> = emptyList(),
) {
    fun toArticle(): ArticleSummary {
        val cover = (imageUrls.firstOrNull() ?: covers.firstOrNull()).orEmpty().fixUrl()
        return ArticleSummary(
            id = id,
            title = title.replace(Regex("<[^>]+>"), "").decodeHtml(),
            summary = desc.replace(Regex("<[^>]+>"), "").decodeHtml(),
            cover = cover,
            views = view,
            likes = like,
            replies = reply,
            pubTime = pubTime,
            category = categoryName,
            authorName = author,
            authorFace = upFace.fixUrl(),
            authorMid = mid,
        )
    }
}

@Serializable
data class ArticleSearchData(val result: List<ArticleSearchItemDto> = emptyList())

/** 文章统计（空间专栏列表用）。 */
@Serializable
data class ArticleStatsDto(
    val view: Int = 0,
    val like: Int = 0,
    val reply: Int = 0,
    val favorite: Int = 0,
    val coin: Int = 0,
)

@Serializable
data class ArticleCategoryDto(val id: Long = 0, val name: String = "")

/** 专栏统计详情（x/article/viewinfo.stats）。 */
@Serializable
data class ArticleStatsDetailDto(
    val view: Int = 0,
    val favorite: Int = 0,
    val like: Int = 0,
    val dislike: Int = 0,
    val reply: Int = 0,
    val share: Int = 0,
    val coin: Int = 0,
    val dynamic: Int = 0,
)

/** 专栏互动信息（x/article/viewinfo）。 */
@Serializable
data class ArticleViewInfoData(
    /** 0=未点赞 1=已点赞。 */
    val like: Int = 0,
    /** 是否已关注作者。 */
    val attention: Boolean = false,
    /** 是否已收藏。 */
    val favorite: Boolean = false,
    /** 当前登录用户已投的硬币数（0/1/2）。 */
    val coin: Int = 0,
    val stats: ArticleStatsDetailDto? = null,
    val title: String = "",
    val mid: Long = 0,
    @SerialName("author_name") val authorName: String = "",
    val type: Int = 0,
)

/** 空间专栏列表项（x/space/wbi/article）。 */
@Serializable
data class SpaceArticleItemDto(
    val id: Long = 0,
    val title: String = "",
    val desc: String = "",
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    val stats: ArticleStatsDto? = null,
    @SerialName("pub_time") val pubTime: Long = 0,
    val category: ArticleCategoryDto? = null,
) {
    fun toArticle(authorMid: Long, authorName: String, authorFace: String): ArticleSummary =
        ArticleSummary(
            id = id,
            title = title,
            summary = desc,
            cover = imageUrls.firstOrNull().orEmpty().fixUrl(),
            views = stats?.view ?: 0,
            likes = stats?.like ?: 0,
            replies = stats?.reply ?: 0,
            pubTime = pubTime,
            category = category?.name.orEmpty(),
            authorName = authorName,
            authorFace = authorFace.fixUrl(),
            authorMid = authorMid,
        )
}

@Serializable
data class SpaceArticleData(
    val articles: List<SpaceArticleItemDto> = emptyList(),
    val count: Int = 0,
)

// ---------------------------------------------------------------------------
// 空间「合集和列表」（合集 / 系列）
// ---------------------------------------------------------------------------

/** x/polymer/web-space/seasons_series_list 的响应体。 */
@Serializable
data class SeasonsSeriesData(
    @SerialName("items_lists") val itemsLists: SeasonsSeriesItems? = null,
)

@Serializable
data class SeasonsSeriesItems(
    /** 合集列表。 */
    @SerialName("seasons_list") val seasonsList: List<SeasonItemDto> = emptyList(),
    /** 系列列表。 */
    @SerialName("series_list") val seriesList: List<SeriesItemDto> = emptyList(),
)

@Serializable
data class SeasonItemDto(
    val meta: SeasonMetaDto? = null,
    val archives: List<SeasonArchiveItemDto> = emptyList(),
)

@Serializable
data class SeasonMetaDto(
    @SerialName("season_id") val seasonId: Long = 0,
    val name: String = "",
    val cover: String = "",
    val total: Int = 0,
    val description: String = "",
    val mid: Long = 0,
)

@Serializable
data class SeriesItemDto(
    val meta: SeriesMetaDto? = null,
    val archives: List<SeasonArchiveItemDto> = emptyList(),
)

@Serializable
data class SeriesMetaDto(
    @SerialName("series_id") val seriesId: Long = 0,
    val name: String = "",
    val cover: String = "",
    val total: Int = 0,
    val description: String = "",
    val mid: Long = 0,
)

/** 合集 / 系列内的稿件（两个接口共用同一结构）。 */
@Serializable
data class SeasonArchiveItemDto(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val stat: StatDto? = null,
) {
    fun toVideo() = Video(
        aid = aid, bvid = bvid, title = title, pic = pic.fixUrl(),
        duration = duration, pubdate = pubdate,
        views = stat?.view ?: 0, danmaku = stat?.danmaku ?: 0, replies = stat?.reply ?: 0,
    )
}

/** 合集内稿件列表（x/polymer/web-space/seasons_archives_list）。 */
@Serializable
data class SeasonArchivesData(
    val archives: List<SeasonArchiveItemDto> = emptyList(),
    val meta: SeasonMetaDto? = null,
    val page: PageDto? = null,
)

/** 系列内稿件列表（x/series/archives）。 */
@Serializable
data class SeriesArchivesData(
    val archives: List<SeasonArchiveItemDto> = emptyList(),
    val page: PageDto? = null,
)

@Serializable
data class PageDto(
    @SerialName("page_num") val pageNum: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    val total: Int = 0,
)

// ---------------------------------------------------------------------------
// 热搜
// ---------------------------------------------------------------------------

@Serializable
data class HotKeywordDto(val keyword: String = "")

@Serializable
data class TrendingDto(val list: List<HotKeywordDto> = emptyList())

@Serializable
data class SearchSquareData(val trending: TrendingDto? = null)

// ---------------------------------------------------------------------------
// 搜索联想词（s.search.bilibili.com/main/suggest）
// ---------------------------------------------------------------------------

@Serializable
data class SuggestTagDto(
    /** 纯文本联想词。 */
    val value: String = "",
    /** 部分场景返回的联想词（可能与 value 相同）。 */
    val term: String = "",
    /** 带高亮标记的展示名，例如 <em class="suggest_high_light">xx</em>。 */
    val name: String = "",
)

@Serializable
data class SuggestResultDto(val tag: List<SuggestTagDto> = emptyList())

/** 联想接口的响应包装：顶层字段是 result 而不是 data，故单独定义。 */
@Serializable
data class SuggestResponse(
    val code: Int = -1,
    val message: String = "",
    val result: SuggestResultDto? = null,
)

// ---------------------------------------------------------------------------
// 播放地址（DASH + 兜底 durl）
// ---------------------------------------------------------------------------

@Serializable
data class DurlDto(val url: String = "")

/** DASH 分片的字节区间（SegmentBase）。 */
@Serializable
data class SegmentBaseDto(
    val initialization: String = "",
    @SerialName("Initialization") val initializationCaps: String = "",
    val indexRange: String = "",
    @SerialName("index_range") val indexRangeSnake: String = "",
) {
    /** 接口两套键名（小写 segment_base / 大写 SegmentBase）都接收后合并。 */
    val initRange: String get() = initializationCaps.ifEmpty { initialization }
    val index: String get() = indexRange.ifEmpty { indexRangeSnake }
}

/**
 * 视频 / 音频码流。接口对同一字段会同时下发 snake_case 与 camelCase 两套键名，
 * 这里都声明，取值时做兜底，避免只解析到一半字段。
 */
@Serializable
data class DashTrackDto(
    val id: Int = 0,
    @SerialName("base_url") val baseUrlSnake: String = "",
    @SerialName("baseUrl") val baseUrlCamel: String = "",
    @SerialName("backup_url") val backupUrlSnake: List<String>? = null,
    @SerialName("backupUrl") val backupUrlCamel: List<String>? = null,
    val bandwidth: Long = 0,
    @SerialName("mime_type") val mimeTypeSnake: String = "",
    @SerialName("mimeType") val mimeTypeCamel: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("frame_rate") val frameRateSnake: String = "",
    @SerialName("frameRate") val frameRateCamel: String = "",
    @SerialName("segment_base") val segmentBaseSnake: SegmentBaseDto? = null,
    @SerialName("SegmentBase") val segmentBaseCaps: SegmentBaseDto? = null,
) {
    val url: String get() = baseUrlCamel.ifEmpty { baseUrlSnake }
    val backups: List<String> get() = backupUrlCamel ?: backupUrlSnake.orEmpty()
    val mime: String get() = mimeTypeCamel.ifEmpty { mimeTypeSnake }
    val fps: String get() = frameRateCamel.ifEmpty { frameRateSnake }
    val segment: SegmentBaseDto? get() = segmentBaseCaps ?: segmentBaseSnake
}

@Serializable
data class DashDto(
    val duration: Int = 0,
    val video: List<DashTrackDto> = emptyList(),
    val audio: List<DashTrackDto> = emptyList(),
)

/** 清晰度元信息（support_formats）。 */
@Serializable
data class SupportFormatDto(
    val quality: Int = 0,
    @SerialName("new_description") val newDescription: String = "",
    @SerialName("display_desc") val displayDesc: String = "",
    val codecs: List<String>? = null,
)

@Serializable
data class PlayUrlData(
    val quality: Int = 0,
    @SerialName("accept_quality") val acceptQuality: List<Int> = emptyList(),
    @SerialName("accept_description") val acceptDescription: List<String> = emptyList(),
    @SerialName("support_formats") val supportFormats: List<SupportFormatDto> = emptyList(),
    /** 直播接口的清晰度档位（原画 / 蓝光 / 超清 / 高清）。 */
    @SerialName("accept_qn") val acceptQn: List<Int> = emptyList(),
    @SerialName("current_qn") val currentQn: Int = 0,
    val durl: List<DurlDto> = emptyList(),
    val dash: DashDto? = null,
)

// ---------------------------------------------------------------------------
// 评论
// ---------------------------------------------------------------------------

@Serializable
data class MemberDto(
    val mid: Long = 0,
    val uname: String = "",
    val avatar: String = "",
    @SerialName("level_info") val levelInfo: LevelInfoDto? = null,
    /** 粉丝牌（勋章）。 */
    @SerialName("fans_detail") val fansDetail: FansDetailDto? = null,
)

@Serializable
data class LevelInfoDto(
    @SerialName("current_level") val currentLevel: Int = 0,
    @SerialName("current_exp") val currentExp: Int = 0,
    @SerialName("next_exp") val nextExp: Int = 0,
)

@Serializable
data class ContentDto(
    val message: String = "",
    val location: String = "",
    /**
     * @ 到的用户名 → mid，用于把正文里的「@名字」变成可点跳转。
     * 接口各版本结构不完全一致，这里按 JsonElement 收，解析时再兜底。
     */
    @SerialName("at_name_to_mid") val atNameToMid: JsonElement? = null,
    /** 正文表情：token（如 `[doge]`）→ 表情图信息。 */
    val emote: Map<String, EmoteDto>? = null,
    /** 评论配图。 */
    val pictures: List<CommentPictureDto>? = null,
) {
    /** 用户名 → mid；结构不是对象时返回空 map。 */
    fun atUserMids(): Map<String, Long> {
        val obj = atNameToMid as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (name, value) ->
            val mid = (value as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            name to mid
        }.toMap()
    }
}

/** 粉丝牌（勋章）。 */
@Serializable
data class FansDetailDto(
    @SerialName("medal_name") val medalName: String = "",
    val level: Int = 0,
)

/**
 * 评论附加控制信息。
 * IP 属地在新版评论接口里下发在 `reply_control.location`（形如「IP属地：上海」），
 * `content.location` 是老接口字段，多数情况下为空。
 */
@Serializable
data class ReplyControlDto(
    val location: String = "",
    /** 是否 UP 主置顶：接口有的返回 1/0，有的返回布尔。 */
    @SerialName("is_up_top") val isUpTop: JsonElement? = null,
)

/** UP 主对评论的操作状态（`up_action`）。 */
@Serializable
data class UpActionDto(val like: Boolean = false, val reply: Boolean = false)

/** 宽松读取接口里可能下发 1/0 或 true/false 的布尔字段。 */
private fun JsonElement?.asTruthy(): Boolean {
    val primitive = this as? JsonPrimitive ?: return false
    return primitive.booleanOrNull ?: (primitive.contentOrNull == "1")
}

/** 评论正文里的表情条目。 */
@Serializable
data class EmoteDto(
    val id: Long = 0,
    val text: String = "",
    val url: String = "",
)

/** 评论配图。 */
@Serializable
data class CommentPictureDto(
    @SerialName("img_src") val imgSrc: String = "",
    @SerialName("img_width") val imgWidth: Int = 0,
    @SerialName("img_height") val imgHeight: Int = 0,
    @SerialName("img_size") val imgSize: Double = 0.0,
)

/** 表情面板（`x/emote/user/panel/web`）。 */
@Serializable
data class EmotePanelData(
    val packages: List<EmotePackageDto>? = null,
    @SerialName("all_packages") val allPackages: List<EmotePackageDto>? = null,
)

@Serializable
data class EmotePackageDto(
    val id: Long = 0,
    val text: String = "",
    val url: String = "",
    val emote: List<EmoteDto>? = null,
)

/** 图片上传返回（`x/dynamic/feed/draw/upload_bfs`）。 */
@Serializable
data class UploadImageData(
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("image_width") val imageWidth: Int = 0,
    @SerialName("image_height") val imageHeight: Int = 0,
    @SerialName("img_size") val imgSize: Double = 0.0,
)

// ---------------------------------------------------------------------------
// 发布动态（x/dynamic/feed/create/dyn）
// ---------------------------------------------------------------------------

/** 富文本段：type 1=纯文本 2=@某人 4=投票 9=表情。 */
@Serializable
data class DynamicCreateContentDto(
    @SerialName("raw_text") val rawText: String,
    val type: Int,
    @SerialName("biz_id") val bizId: String = "",
)

@Serializable
data class DynamicCreateFeedContentDto(
    val contents: List<DynamicCreateContentDto>,
    val title: String? = null,
)

@Serializable
data class DynamicCreatePicDto(
    @SerialName("img_src") val imgSrc: String,
    @SerialName("img_width") val imgWidth: Int = 0,
    @SerialName("img_height") val imgHeight: Int = 0,
    @SerialName("img_size") val imgSize: Float = 0f,
)

@Serializable
data class DynamicCreateOptionDto(
    @SerialName("private_pub") val privatePub: Int? = null,
)

@Serializable
data class DynamicCreateTopicDto(
    val id: Long,
    val name: String,
    @SerialName("from_source") val fromSource: String = "dyn.web.list",
    @SerialName("from_topic_id") val fromTopicId: Long = 0L,
)

@Serializable
data class DynamicCreateAppMetaDto(
    val from: String = "create.dynamic.web",
    @SerialName("mobi_app") val mobiApp: String = "web",
)

@Serializable
data class DynamicCreateMetaDto(
    @SerialName("app_meta") val appMeta: DynamicCreateAppMetaDto = DynamicCreateAppMetaDto(),
)

@Serializable
data class DynamicCreateFeedReqDto(
    val content: DynamicCreateFeedContentDto,
    val scene: Int = 1,
    val pics: List<DynamicCreatePicDto>? = null,
    @SerialName("attach_card") val attachCard: JsonElement? = null,
    val option: DynamicCreateOptionDto? = null,
    val topic: DynamicCreateTopicDto? = null,
    @SerialName("upload_id") val uploadId: String,
    val meta: DynamicCreateMetaDto = DynamicCreateMetaDto(),
)

@Serializable
data class DynamicCreateFeedRequest(
    @SerialName("dyn_req") val dynReq: DynamicCreateFeedReqDto,
)

@Serializable
data class DynamicCreateFeedData(
    @SerialName("dyn_id") val dynId: Long = 0,
    @SerialName("dyn_id_str") val dynIdStr: String = "",
    @SerialName("dynamic_id_str") val dynamicIdStr: String = "",
)

/** 发布话题搜索（x/topic/pub/search）。 */
@Serializable
data class TopicSearchData(
    @SerialName("topic_items") val topicItems: List<TopicSearchItemDto>? = null,
)

@Serializable
data class TopicSearchItemDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("stat_desc") val statDesc: String = "",
    val description: String = "",
)

// ---------------------------------------------------------------------------
// 话题页（app.bilibili.com/x/topic/web/details/top 与 x/polymer/web-dynamic/v1/feed/topic）
// ---------------------------------------------------------------------------

/** 话题详情头部。 */
@Serializable
data class TopicDetailTopData(
    @SerialName("top_details") val topDetails: TopicTopDetailsDto? = null,
)

@Serializable
data class TopicTopDetailsDto(
    @SerialName("topic_creator") val topicCreator: TopicCreatorDto? = null,
    @SerialName("topic_item") val topicItem: TopicItemDto? = null,
)

@Serializable
data class TopicCreatorDto(
    val uid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class TopicItemDto(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    @SerialName("share_pic") val sharePic: String = "",
    val view: Long = 0,
    val discuss: Long = 0,
    val dynamics: Long = 0,
    val fav: Long = 0,
    val like: Long = 0,
    val share: Long = 0,
    @SerialName("share_url") val shareUrl: String = "",
    @SerialName("jump_url") val jumpUrl: String = "",
)

/** 话题动态流。 */
@Serializable
data class TopicFeedData(
    @SerialName("topic_card_list") val topicCardList: TopicCardListDto? = null,
)

@Serializable
data class TopicCardListDto(
    @SerialName("has_more") val hasMore: Boolean = false,
    val offset: String = "",
    val items: List<TopicDynamicCardItemDto> = emptyList(),
    @SerialName("topic_sort_by_conf") val topicSortByConf: TopicSortByConfDto? = null,
)

@Serializable
data class TopicSortByConfDto(
    @SerialName("all_sort_by") val allSortBy: List<TopicSortOptionDto> = emptyList(),
    @SerialName("show_sort_by") val showSortBy: Int = 0,
)

@Serializable
data class TopicSortOptionDto(
    @SerialName("sort_by") val sortBy: Int = 0,
    @SerialName("sort_name") val sortName: String = "",
)

@Serializable
data class TopicDynamicCardItemDto(
    @SerialName("dynamic_card_item") val dynamicCardItem: DynamicItemDto? = null,
    @SerialName("topic_type") val topicType: String = "",
)

/** 创建投票（x/vote/create）。 */
@Serializable
data class DynamicCreateVoteRequest(
    @SerialName("vote_info") val voteInfo: DynamicCreateVoteInfoDto,
)

@Serializable
data class DynamicCreateVoteInfoDto(
    val title: String,
    val desc: String = "",
    val type: Int = 0,
    @SerialName("choice_cnt") val choiceCnt: Int = 1,
    val duration: Int,
    val options: List<DynamicCreateVoteOptionDto>,
    @SerialName("only_fans_level") val onlyFansLevel: Int = 0,
    @SerialName("vote_publisher") val votePublisher: Long = 0,
)

@Serializable
data class DynamicCreateVoteOptionDto(
    @SerialName("opt_desc") val optDesc: String,
    @SerialName("img_url") val imgUrl: String = "",
)

@Serializable
data class DynamicCreateVoteData(
    @SerialName("vote_id") val voteId: Long = 0,
)

/** @ 提及用户搜索。 */
@Serializable
data class MentionSearchData(
    val groups: List<MentionGroupDto>? = null,
)

@Serializable
data class MentionGroupDto(
    @SerialName("group_name") val groupName: String = "",
    val items: List<MentionUserDto>? = null,
)

@Serializable
data class MentionUserDto(
    val uid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class CommentDto(
    val rpid: Long = 0,
    val member: MemberDto? = null,
    val content: ContentDto? = null,
    @SerialName("reply_control") val replyControl: ReplyControlDto? = null,
    @SerialName("up_action") val upAction: UpActionDto? = null,
    val like: Int = 0,
    val ctime: Long = 0,
    val action: Int = 0,
    val rcount: Int = 0,
) {
    /** [upMid] / [myMid] 用于标记「是否 UP 主」「是否本人」，为 0 时不做判断。 */
    fun toComment(upMid: Long = 0, myMid: Long = 0) = Comment(
        rpid = rpid,
        mid = member?.mid ?: 0,
        userName = member?.uname.orEmpty(),
        avatar = member?.avatar.orEmpty(),
        level = member?.levelInfo?.currentLevel ?: 0,
        message = content?.message.orEmpty(),
        location = (replyControl?.location ?: content?.location).orEmpty().normalizeIpLocation(),
        likes = like, ctime = ctime, liked = action == 1, hated = action == 2,
        rcount = rcount,
        isUpTop = replyControl?.isUpTop.asTruthy(),
        upLiked = upAction?.like == true,
        isUp = upMid > 0 && member?.mid == upMid,
        isMine = myMid > 0 && member?.mid == myMid,
        medalName = member?.fansDetail?.medalName.orEmpty(),
        medalLevel = member?.fansDetail?.level ?: 0,
        atUserMids = content?.atUserMids().orEmpty(),
        pictures = content?.pictures.orEmpty().map {
            CommentPicture(it.imgSrc.fixUrl(), it.imgWidth, it.imgHeight, it.imgSize)
        },
        emotes = content?.emote.orEmpty().map { (token, e) ->
            // 正文里以「[doge]」形式出现，优先用接口下发的 text（含方括号）作为 key
            e.text.ifBlank { "[$token]" } to e.url.fixUrl()
        }.toMap(),
    )
}

/**
 * 统一 IP 属地的展示文案：接口可能下发「IP属地：上海」「IP 属地:上海」，也可能只给「上海」，
 * 这里都归一成「IP属地：上海」；取不到时返回空串（调用方据此隐藏该文案）。
 */
private fun String.normalizeIpLocation(): String {
    val region = replace("IP 属地", "IP属地")
        .replace("IP属地:", "IP属地：")
        .removePrefix("IP属地：")
        .trim()
    return if (region.isBlank()) "" else "IP属地：$region"
}

@Serializable
data class CursorDto(
    @SerialName("is_end") val isEnd: Boolean = true,
    val next: Long = 0,
)

@Serializable
data class ReplyData(
    val replies: List<CommentDto>? = null,
    /** 置顶评论：只随第一页下发，不并入 replies，需单独取回。 */
    @SerialName("top_replies") val topReplies: List<CommentDto>? = null,
    val cursor: CursorDto? = null,
)

/** 楼中楼（x/v2/reply/reply）分页信息。 */
@Serializable
data class ReplyPageDto(val pn: Int = 1, val ps: Int = 20, val count: Int = 0)

/** 楼中楼（x/v2/reply/reply）返回体。 */
@Serializable
data class ReplyDetailData(
    val replies: List<CommentDto>? = null,
    val page: ReplyPageDto? = null,
)

// ---------------------------------------------------------------------------
// 关注 / 空间
// ---------------------------------------------------------------------------

@Serializable
data class FollowingDto(
    val mid: Long = 0,
    val uname: String = "",
    val sign: String = "",
    val face: String = "",
) {
    fun toVideo() = Video(
        upMid = mid, title = uname, upName = sign, upFace = face.fixUrl(), pic = face.fixUrl(),
    )
}

@Serializable
data class FollowingData(
    val list: List<FollowingDto> = emptyList(),
    val total: Int = 0,
)

/** 关注 / 粉丝列表条目（x/relation/followings、x/relation/fans 共用）。 */
@Serializable
data class RelationUserDto(
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val sign: String = "",
    /** 关注关系：0 未关注 2 已关注 6 互关 128 拉黑。 */
    val attribute: Int = 0,
    @SerialName("official_verify") val officialVerify: OfficialVerifyDto? = null,
) {
    fun toRelationUser() = RelationUser(
        mid = mid,
        name = uname,
        face = face.fixUrl(),
        sign = sign,
        isFollowing = attribute == 2 || attribute == 6,
        verifyDesc = officialVerify?.takeIf { it.type >= 0 }?.desc.orEmpty(),
    )
}

@Serializable
data class RelationUserData(
    val list: List<RelationUserDto> = emptyList(),
    val total: Int = 0,
)

// ---------------------------------------------------------------------------
// 关注分组（x/relation/tags、x/relation/tag、x/relation/tag/user）
// ---------------------------------------------------------------------------

/** 关注分组条目（x/relation/tags，data 直接是数组）。 */
@Serializable
data class RelationTagItemDto(
    val tagid: Long = 0,
    val name: String = "",
    val count: Int = 0,
    val tip: String = "",
)

/** 分组内 UP 条目（x/relation/tag，data 是 FollowingUser 数组）。 */
@Serializable
data class FollowGroupMemberDto(
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val sign: String = "",
    @SerialName("official_verify") val officialVerify: OfficialVerifyDto? = null,
) {
    fun toRelationUser() = RelationUser(
        mid = mid,
        name = uname,
        face = face.fixUrl(),
        sign = sign,
        // 分组成员按定义一定处于「已关注」状态
        isFollowing = true,
        verifyDesc = officialVerify?.takeIf { it.type >= 0 }?.desc.orEmpty(),
    )
}

// ---------------------------------------------------------------------------
// 我的追番 / 追剧（x/space/bangumi/follow/list）
// ---------------------------------------------------------------------------

@Serializable
data class MyBangumiData(
    val list: List<MyBangumiItemDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class MyBangumiItemDto(
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    /** 追番进度，如「看到第5话」。 */
    val progress: String = "",
    @SerialName("new_ep") val newEp: NewEpDto? = null,
    /** 角标文案：如「大会员」「独家」。 */
    val badge: String = "",
) {
    fun toSeason(): BangumiSeason {
        val subtitle = listOfNotNull(
            newEp?.indexShow?.takeIf { it.isNotBlank() },
            progress.takeIf { it.isNotBlank() },
        ).distinct().joinToString(" · ")
        return BangumiSeason(
            seasonId = seasonId,
            title = title,
            cover = cover.fixUrl(),
            subtitle = subtitle,
            badge = badge,
        )
    }
}

@Serializable
data class NewEpDto(
    /** 更新进度文案，如「更新至第12话」「全24话」。 */
    @SerialName("index_show") val indexShow: String = "",
)

@Serializable
data class SpaceVideoDto(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val play: Int = 0,
    @SerialName("video_review") val videoReview: Int = 0,
    val length: String = "",
    val created: Long = 0,
    val author: String = "",
    val mid: Long = 0,
    val description: String = "",
) {
    fun toVideo() = Video(
        aid = aid, bvid = bvid, title = title, pic = pic.fixUrl(), duration = length.parseDuration(),
        pubdate = created, views = play, danmaku = videoReview, upName = author, upMid = mid, desc = description,
    )
}

@Serializable
data class SpaceSearchData(
    val list: SpaceSearchListDto? = null,
    val page: SpacePageDto? = null,
)

/** 投稿分页信息（count 是投稿总数）。 */
@Serializable
data class SpacePageDto(val pn: Int = 1, val ps: Int = 20, val count: Int = 0)

@Serializable
data class SpaceSearchListDto(val vlist: List<SpaceVideoDto> = emptyList())

// ---------------------------------------------------------------------------
// 收藏
// ---------------------------------------------------------------------------

@Serializable
data class UpperDto(val name: String = "", val mid: Long = 0)

@Serializable
data class CntInfoDto(val play: Int = 0)

@Serializable
data class FavVideoDto(
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val upper: UpperDto? = null,
    @SerialName("cnt_info") val cntInfo: CntInfoDto? = null,
) {
    fun toVideo() = Video(
        aid = id, bvid = bvid, title = title, pic = cover.fixUrl(), duration = duration,
        pubdate = pubdate, upName = upper?.name.orEmpty(), upMid = upper?.mid ?: 0,
        views = cntInfo?.play ?: 0,
    )
}

@Serializable
data class FavListData(val medias: List<FavVideoDto> = emptyList())

/** 收藏夹列表：接口返回的是 { count, list } 对象，不是裸数组。 */
@Serializable
data class FavFolderListData(val count: Int = 0, val list: List<FavFolderDto> = emptyList())

@Serializable
data class FavFolderDto(val id: Long = 0, val title: String = "")

/** 当前用户是否已收藏某视频。 */
@Serializable
data class FavouredData(val favoured: Boolean = false)

// ---------------------------------------------------------------------------
// 历史
// ---------------------------------------------------------------------------

@Serializable
data class HistoryDetailDto(
    val bvid: String = "",
    val oid: Long = 0,
    val cid: Long = 0,
    /** 番剧 / 影视的分集 id（business=="pgc" 时有效）。 */
    val epid: Long = 0,
    /** 业务类型：archive=普通视频 / pgc=番剧·影视 / live=直播。 */
    val business: String = "",
)

@Serializable
data class HistoryVideoDto(
    val title: String = "",
    val pic: String = "",
    val cover: String = "",
    /** 封面兜底数组（部分条目无 pic/cover，只有 covers）。 */
    val covers: List<String> = emptyList(),
    @SerialName("author_name") val authorName: String = "",
    @SerialName("author_face") val authorFace: String = "",
    val duration: Int = 0,
    /** 观看进度（秒），位于条目顶层（不 history 内），未看过为 -1。 */
    val progress: Int = -1,
    /** 浏览时间戳（秒），游标翻页用。 */
    @SerialName("view_at") val viewAt: Long = 0,
    val history: HistoryDetailDto? = null,
) {
    /** 转换为本地历史模型；无效条目（无 bvid 且无 oid）返回 null 以便过滤。 */
    fun toVideo(): Video? {
        val rawBvid = history?.bvid.orEmpty()
        val oid = history?.oid ?: 0
        val business = history?.business.orEmpty()
        // pgc 视为番剧/影视，live 视为直播，其余按普通视频。
        val type = when {
            business.equals("pgc", ignoreCase = true) -> "bangumi"
            business.equals("live", ignoreCase = true) -> "live"
            else -> "video"
        }
        // 直播历史服务器不下发 bvid，这里合成一个（与本地记录格式一致），
        // 使其能正常参与按 bvid 去重、展示与点击播放。
        val bvid = if (type == "live" && rawBvid.isBlank() && oid > 0) "live_$oid" else rawBvid
        if (bvid.isEmpty() && oid <= 0) return null
        // 封面三级兜底：cover → pic → covers[0]（对齐 BiliPai）。
        val cover = listOf(cover, pic, covers.firstOrNull().orEmpty())
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return Video(
            title = title,
            pic = cover.fixUrl(),
            upName = authorName,
            upFace = authorFace.fixUrl(),
            duration = duration,
            // 直播的 oid 是直播间号，不放进 aid（避免误当稿件 id 上报/删除）。
            aid = if (type == "live") 0 else oid,
            bvid = bvid,
            cid = history?.cid ?: 0,
            progress = progress.coerceAtLeast(0),
            type = type,
            roomId = if (type == "live") oid else 0,
            seasonId = if (type == "bangumi") oid else 0,
            epid = if (type == "bangumi") (history?.epid ?: 0) else 0,
        )
    }
}

// ---------------------------------------------------------------------------
// 稍后再看（x/v2/history/toview）
// ---------------------------------------------------------------------------

@Serializable
data class WatchLaterItemDto(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    /** 已观看秒数，未看过时为 -1。 */
    val progress: Int = -1,
    val pubdate: Long = 0,
    val owner: OwnerDto? = null,
    val stat: StatDto? = null,
) {
    fun toVideo() = Video(
        aid = aid, bvid = bvid, cid = cid, title = title, pic = pic.fixUrl(),
        duration = duration, pubdate = pubdate, progress = progress.coerceAtLeast(0),
        upName = owner?.name.orEmpty(), upMid = owner?.mid ?: 0, upFace = owner?.face.orEmpty().fixUrl(),
        views = stat?.view ?: 0, danmaku = stat?.danmaku ?: 0, replies = stat?.reply ?: 0,
        likes = stat?.like ?: 0, coins = stat?.coin ?: 0, favorites = stat?.favorite ?: 0,
    )
}

@Serializable
data class WatchLaterData(val count: Int = 0, val list: List<WatchLaterItemDto> = emptyList())

// ---------------------------------------------------------------------------
// 番剧 / PGC（pgc/season/index/result、pgc/view/web/season）
// ---------------------------------------------------------------------------

/** 番剧索引（PGC）。[hasNext] 1 表示还有下一页。 */
@Serializable
data class BangumiIndexData(
    val list: List<BangumiItemDto> = emptyList(),
    @SerialName("has_next") val hasNext: Int = 0,
)

@Serializable
data class BangumiItemDto(
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    /** 评分，接口可能下发字符串或数字。 */
    val score: JsonElement? = null,
    /** 更新进度，如「更新至第12话」「全8话」。 */
    @SerialName("index_show") val indexShow: String = "",
    /** 角标文案：如「大会员」「独家」。 */
    val badge: String = "",
    /** 角标附加信息，部分接口只在 badge_info.text 里下发文案。 */
    @SerialName("badge_info") val badgeInfo: BadgeInfoDto? = null,
) {
    fun toSeason(): BangumiSeason {
        val scoreText = (score as? JsonPrimitive)?.contentOrNull.orEmpty()
        val subtitle = listOfNotNull(
            indexShow.takeIf { it.isNotBlank() },
            scoreText.takeIf { it.isNotBlank() && it != "0" }?.let { "$it 分" },
        ).joinToString(" · ")
        return BangumiSeason(
            seasonId = seasonId,
            title = title,
            cover = cover.fixUrl(),
            subtitle = subtitle,
            badge = badge.ifBlank { badgeInfo?.text.orEmpty() },
        )
    }
}

@Serializable
data class BadgeInfoDto(
    /** 角标文案。 */
    val text: String = "",
    /** 角标背景色（形如 #FB7299），暂未使用。 */
    @SerialName("bg_color") val bgColor: String = "",
)

@Serializable
data class BangumiEpisodeDto(
    /** 分集 id（ep_id）。 */
    val id: Long = 0,
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    @SerialName("long_title") val longTitle: String = "",
    /** 分集自身封面（不是整季封面）。 */
    val cover: String = "",
    /** 分集时长（毫秒，PGC 接口单位为 ms）。 */
    val duration: Int = 0,
)

@Serializable
data class BangumiSeasonResult(
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val episodes: List<BangumiEpisodeDto> = emptyList(),
)

/**
 * PGC 详情接口（pgc/view/web/season）的响应外壳。
 * 注意：该接口不包 data 字段，业务数据直接挂在顶层 result 上，
 * 因此不能复用 [BiliResponse]。
 */
@Serializable
data class PgcResponse<T>(
    val code: Int = -1,
    val message: String = "",
    val result: T? = null,
)

@Serializable
data class HistoryData(
    val list: List<HistoryVideoDto> = emptyList(),
    /** 下一页游标；max>0 表示还有更多。 */
    val cursor: HistoryCursor? = null,
)

@Serializable
data class HistoryCursor(
    val max: Long = 0,
    @SerialName("view_at") val viewAt: Long = 0,
    val business: String = "",
    val ps: Int = 30,
)

// ---------------------------------------------------------------------------
// 直播
// ---------------------------------------------------------------------------

@Serializable
data class LiveRoomDto(
    @SerialName("roomid") val roomId: Long = 0,
    val title: String = "",
    val uname: String = "",
    val face: String = "",
    val cover: String = "",
    @SerialName("area_v2_name") val areaV2Name: String = "",
    @SerialName("cate_name") val cateName: String = "",
    @SerialName("area_name") val areaName: String = "",
    val online: JsonElement? = null,
    /** 「多少人看过」：B 站返回对象 {num, text_small, text_large}，个别入口为纯数字，统一容错。 */
    @SerialName("watched_show") val watchedShow: JsonElement? = null,
) {
    fun toLiveRoom(): LiveRoom {
        val onlineText = (online as? JsonPrimitive)?.content.orEmpty()
        return LiveRoom(
            roomId = roomId, title = title, uname = uname, face = face.fixUrl(), cover = cover.fixUrl(),
            area = areaV2Name.ifEmpty { cateName }.ifEmpty { areaName },
            online = onlineText.filter(Char::isDigit).toIntOrNull() ?: 0,
            watchedShow = (watchedShow as? JsonObject)?.get("num")?.let { (it as? JsonPrimitive)?.content }
                ?.filter(Char::isDigit)?.toIntOrNull()
                ?: (watchedShow as? JsonPrimitive)?.content?.filter(Char::isDigit)?.toIntOrNull()
                ?: 0,
            watchedText = watchedTextOf(watchedShow),
        )
    }
}

/** 从 watched_show 取服务端排版好的展示文案 text_large（取不到返回空串）。 */
internal fun watchedTextOf(el: JsonElement?): String =
    (el as? JsonObject)?.get("text_large")?.let { (it as? JsonPrimitive)?.content }.orEmpty()

@Serializable
data class LiveRoomListDto(val list: List<LiveRoomDto> = emptyList())

/** 直播分区（room/v1/area/getList，data 为分区数组，一级分区带 list 子分区）。 */
@Serializable
data class LiveAreaDto(
    val id: Int = 0,
    val name: String = "",
    /** 二级分区（一级分区下的子分区列表）。 */
    val list: List<LiveAreaDto> = emptyList(),
) {
    fun toArea() = LiveArea(id = id, name = name, children = list.map { LiveArea(it.id, it.name) })
}

@Serializable
data class LiveListData(
    /** room/v3/area/getRoomList 的分页房间列表（扁平数组）。 */
    val list: List<LiveRoomDto> = emptyList(),
    @SerialName("recommend_room_list") val recommendRoomList: List<LiveRoomDto> = emptyList(),
    @SerialName("room_list") val roomList: List<LiveRoomListDto> = emptyList(),
) {
    fun toLiveRooms(): List<LiveRoom> {
        val result = mutableListOf<LiveRoom>()
        list.forEach { result += it.toLiveRoom() }
        recommendRoomList.forEach { result += it.toLiveRoom() }
        roomList.forEach { it.list.forEach { dto -> result += dto.toLiveRoom() } }
        return result.filter { it.roomId > 0 }
    }
}

@Serializable
data class LiveRoomInfoDto(
    @SerialName("room_id") val roomId: Long = 0,
    val title: String = "",
    val area: String = "",
    val online: Int = 0,
    /** 「多少人看过」：B 站返回对象 {num, text_small, text_large}，个别入口为纯数字，统一容错。 */
    @SerialName("watched_show") val watchedShow: JsonElement? = null,
) {
    /** 解析真实看过人数（cumulative watch count）。 */
    fun watchedCount(): Int {
        val el = watchedShow ?: return 0
        return when (el) {
            is JsonPrimitive -> el.content.filter(Char::isDigit).toIntOrNull() ?: 0
            is JsonObject -> (el["num"] as? JsonPrimitive)?.content
                ?.filter(Char::isDigit)?.toIntOrNull() ?: 0
            else -> 0
        }
    }

    /** 服务端排版好的「多少人看过」文案（text_large）。 */
    fun watchedText(): String = watchedTextOf(watchedShow)
}

/** 直播弹幕服务器信息（xlive/web-room/v1/index/getDanmuInfo）。 */
@Serializable
data class LiveDanmuInfoDto(
    val token: String = "",
    @SerialName("host_list") val hostList: List<LiveDanmuHostDto> = emptyList(),
)

@Serializable
data class LiveDanmuHostDto(
    val host: String = "",
    @SerialName("wss_port") val wssPort: Int = 443,
)

// ---- 直播播放地址（xlive/web-room/v2/index/getRoomPlayInfo，WBI 签名）----

/**
 * 直播播放信息。返回 stream → format → codec → urlInfo 四层树，
 * 每层分别描述协议（FLV/HLS）、容器格式、编码与具体线路（host + base_url + extra）。
 */
@Serializable
data class LiveRoomPlayInfoData(
    @SerialName("room_id") val roomId: Long = 0,
    val uid: Long = 0,
    /** 开播时间戳（秒），未开播为 0。 */
    @SerialName("live_time") val liveTime: Long = 0,
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("playurl_info") val playurlInfo: LivePlayurlInfoDto? = null,
)

@Serializable
data class LivePlayurlInfoDto(@SerialName("playurl") val playurl: LivePlayurlDto? = null)

@Serializable
data class LivePlayurlDto(val stream: List<LiveStreamDto> = emptyList())

@Serializable
data class LiveStreamDto(
    /** 协议：http_stream（FLV）/ http_hls（HLS）。 */
    @SerialName("protocol_name") val protocolName: String = "",
    val format: List<LiveFormatDto> = emptyList(),
)

@Serializable
data class LiveFormatDto(
    /** 容器格式：flv / ts / fmp4。 */
    @SerialName("format_name") val formatName: String = "",
    val codec: List<LiveCodecDto> = emptyList(),
)

@Serializable
data class LiveCodecDto(
    /** 编码：avc / hevc / av1。 */
    @SerialName("codec_name") val codecName: String = "",
    @SerialName("current_qn") val currentQn: Int = 0,
    /** 该直播间全部可用清晰度。 */
    @SerialName("accept_qn") val acceptQn: List<Int> = emptyList(),
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("url_info") val urlInfo: List<LiveUrlInfoDto> = emptyList(),
)

@Serializable
data class LiveUrlInfoDto(
    val host: String = "",
    /** 拼接在 base_url 之后的签名参数（?expires=...&sign=...）。 */
    val extra: String = "",
)

// ---- 直播历史弹幕（xlive/web-room/v1/dM/gethistory）----

@Serializable
data class LiveHistoryDanmakuData(val room: List<LiveHistoryDanmakuDto> = emptyList())

@Serializable
data class LiveHistoryDanmakuDto(
    val text: String = "",
    val nickname: String = "",
    val uid: Long = 0,
    /** 发送时间（秒）。 */
    val timeline: String = "",
)

// ---- 直播间初始化信息（xlive/web-room/v1/index/getInfoByRoom）----

@Serializable
data class LiveRoomInitData(
    @SerialName("room_info") val roomInfo: LiveRoomInitRoomDto? = null,
    @SerialName("anchor_info") val anchorInfo: LiveAnchorInfoDto? = null,
)

@Serializable
data class LiveRoomInitRoomDto(
    @SerialName("room_id") val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("area_name") val areaName: String = "",
    @SerialName("parent_area_name") val parentAreaName: String = "",
    val online: Int = 0,
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("watched_show") val watchedShow: JsonElement? = null,
)

@Serializable
data class LiveAnchorInfoDto(@SerialName("base_info") val baseInfo: LiveAnchorBaseDto? = null)

@Serializable
data class LiveAnchorBaseDto(val uname: String = "", val face: String = "")

// ---- 直播间 H5 信息（xlive/web-room/v1/index/getH5InfoByRoom，无需签名、轻量）----

/**
 * 第三方客户端（PiliPlus）同款轻量接口，一次拿到标题 / 看过 / 主播信息，
 * 无签名无 buvid 依赖，出首屏比 getInfoByRoom 快。
 */
@Serializable
data class LiveRoomH5Data(
    @SerialName("room_info") val roomInfo: LiveRoomH5RoomDto? = null,
    @SerialName("anchor_info") val anchorInfo: LiveAnchorInfoDto? = null,
    /** 「多少人看过」在 data 顶层。 */
    @SerialName("watched_show") val watchedShow: JsonElement? = null,
)

@Serializable
data class LiveRoomH5RoomDto(
    @SerialName("room_id") val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("area_name") val areaName: String = "",
    @SerialName("parent_area_name") val parentAreaName: String = "",
    val online: Int = 0,
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("app_background") val appBackground: String = "",
)

// ---------------------------------------------------------------------------
// 登录 / 用户 / 票据
// ---------------------------------------------------------------------------

@Serializable
data class WbiImgDto(
    @SerialName("img_url") val imgUrl: String = "",
    @SerialName("sub_url") val subUrl: String = "",
)

@Serializable
data class NavData(
    @SerialName("isLogin") val isLogin: Boolean = false,
    val uname: String = "",
    val face: String = "",
    // 硬币余额，注意是浮点（如 123.0），不能按 Int 解析
    val money: Double = 0.0,
    @SerialName("level_info") val levelInfo: LevelInfoDto? = null,
    @SerialName("wbi_img") val wbiImg: WbiImgDto? = null,
    val wallet: WalletDto? = null,
)

@Serializable
data class WalletDto(
    @SerialName("bcoin_balance") val bcoinBalance: Int = 0,
    @SerialName("coupon_balance") val couponBalance: Int = 0,
)

@Serializable
data class CoinData(val money: Int = 0)

/** 我的账号资料（x/member/web/account）：改资料前先取回原值一并回传，避免生日/性别被清空。 */
@Serializable
data class AccountData(
    val mid: Long = 0L,
    val uname: String = "",
    val userid: String = "",
    val sign: String = "",
    val birthday: String = "",
    val sex: String = "",
)

// ---- 短信登录 ----

/** 发送短信验证码的返回：成功时只有 captchaKey，触发风控时给 recaptchaUrl。 */
@Serializable
data class SmsSendData(
    @SerialName("captcha_key") val captchaKey: String = "",
    @SerialName("recaptcha_url") val recaptchaUrl: String = "",
)

@Serializable
data class LoginCookieDto(val name: String = "", val value: String = "")

@Serializable
data class LoginCookieInfo(val cookies: List<LoginCookieDto> = emptyList())

/** 短信登录返回：会话 Cookie 在 cookie_info.cookies 里。 */
@Serializable
data class SmsLoginData(@SerialName("cookie_info") val cookieInfo: LoginCookieInfo? = null)

/** 登录 RSA 公钥（密码/设备凭据加密用）。 */
@Serializable
data class WebKeyData(val hash: String = "", val key: String = "")

// ---------------------------------------------------------------------------
// 用户卡片（x/web-interface/card）
// ---------------------------------------------------------------------------

@Serializable
data class VipLabelDto(val text: String = "")

@Serializable
data class VipIconResourceDto(val url: String = "")

@Serializable
data class VipAvatarIconDto(
    @SerialName("icon_resource") val iconResource: VipIconResourceDto? = null,
)

@Serializable
data class VipDto(
    val type: Int = 0,
    val status: Int = 0,
    val label: VipLabelDto? = null,
    @SerialName("nickname_color") val nicknameColor: String = "",
    @SerialName("avatar_icon") val avatarIcon: VipAvatarIconDto? = null,
)

@Serializable
data class CardInfoDto(
    val mid: String = "",
    val name: String = "",
    val face: String = "",
    val sex: String = "",
    val sign: String = "",
    /** 关注数（card.attention）。 */
    val attention: Long = 0,
    /** 粉丝数（card.fans）。 */
    val fans: Long = 0,
    @SerialName("level_info") val levelInfo: LevelInfoDto? = null,
    /** 认证信息（type>=0 表示有认证，desc 是认证说明）。 */
    @SerialName("official_verify") val officialVerify: OfficialVerifyDto? = null,
    val vip: VipDto? = null,
)

/** 认证信息。 */
@Serializable
data class OfficialVerifyDto(val type: Int = -1, val desc: String = "")

/** 空间头图（card 接口的 space 字段）。 */
@Serializable
data class CardSpaceDto(
    @SerialName("s_img") val sImg: String = "",
    @SerialName("l_img") val lImg: String = "",
)

@Serializable
data class CardData(
    @SerialName("like_num") val likeNum: Long = 0,
    /**
     * 注意：顶层 this.following 是「当前登录用户是否关注了他」的**布尔值**，
     * 不是关注数；关注数在 card.attention。
     */
    val following: Boolean = false,
    val follower: Long = 0,
    /** 投稿数。 */
    @SerialName("archive_count") val archiveCount: Int = 0,
    /** IP 属地，如「IP属地：上海」。 */
    @SerialName("ip_location") val ipLocation: String = "",
    val card: CardInfoDto? = null,
    val space: CardSpaceDto? = null,
)

@Serializable
data class QrcodeData(val url: String = "")

@Serializable
data class QrcodePollData(val code: Int = -1, val url: String = "")

@Serializable
data class TicketData(val ticket: String = "")

@Serializable
data class FingerData(@SerialName("b_3") val b3: String = "", @SerialName("b_4") val b4: String = "")

// ---------------------------------------------------------------------------
// 关注动态
// ---------------------------------------------------------------------------

@Serializable
data class DynamicFeedData(
    val items: List<DynamicItemDto> = emptyList(),
    val offset: String = "",
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("update_baseline") val updateBaseline: String = "",
    @SerialName("update_num") val updateNum: Int = 0,
)

@Serializable
data class DynamicItemDto(
    @SerialName("id_str") val idStr: String = "",
    val type: String = "",
    val visible: Boolean = true,
    @Serializable(with = DynamicModulesSerializer::class) val modules: DynamicModulesDto? = null,
    val orig: DynamicItemDto? = null,
)

@Serializable
data class DynamicModulesDto(
    @SerialName("module_author") val author: DynamicAuthorDto? = null,
    @SerialName("module_dynamic") val dynamic: DynamicContentDto? = null,
    @SerialName("module_stat") val stat: DynamicStatDto? = null,
)

@Serializable
data class DynamicAuthorDto(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    @SerialName("pub_time") val pubTime: String = "",
    @SerialName("pub_ts") val pubTs: Long = 0,
    @SerialName("pub_action") val pubAction: String = "",
)

@Serializable
data class DynamicContentDto(
    val desc: DynamicDescDto? = null,
    val major: DynamicMajorDto? = null,
)

@Serializable
data class DynamicDescDto(val text: String = "")

@Serializable
data class DynamicMajorDto(
    val type: String = "",
    val archive: DynamicArchiveDto? = null,
    val pgc: DynamicArchiveDto? = null,
    val draw: DynamicDrawDto? = null,
    val opus: DynamicOpusDto? = null,
)

@Serializable
data class DynamicArchiveDto(
    val aid: String = "",
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    @SerialName("duration_text") val durationText: String = "",
    val stat: DynamicArchiveStatDto? = null,
)

/** archive/pgc 的 stat.play、stat.danmaku 为字符串（如「123.4万」）。 */
@Serializable
data class DynamicArchiveStatDto(val play: String = "", val danmaku: String = "")

@Serializable
data class DynamicDrawDto(val id: Long = 0, val items: List<DynamicDrawItemDto> = emptyList())

@Serializable
data class DynamicDrawItemDto(val src: String = "", val width: Int = 0, val height: Int = 0)

@Serializable
data class DynamicOpusDto(
    @SerialName("jump_url") val jumpUrl: String = "",
    val pics: List<DynamicOpusPicDto> = emptyList(),
    val summary: DynamicOpusSummaryDto? = null,
    val title: String? = null,
)

@Serializable
data class DynamicOpusPicDto(val url: String = "", val width: Int = 0, val height: Int = 0)

@Serializable
data class DynamicOpusSummaryDto(val text: String = "")

@Serializable
data class DynamicStatDto(
    val comment: DynamicStatItemDto? = null,
    val forward: DynamicStatItemDto? = null,
    val like: DynamicStatItemDto? = null,
)

@Serializable
data class DynamicStatItemDto(val count: Int = 0)

/**
 * opus 风格动态的 `modules` 是 JSON 数组（各模块平铺），其余风格是对象。
 * 此处兼容两种形态。
 */
object DynamicModulesSerializer : KSerializer<DynamicModulesDto> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("DynamicModulesDto")

    override fun deserialize(decoder: Decoder): DynamicModulesDto {
        val json = decoder as? JsonDecoder
            ?: return DynamicModulesDto()
        val element = json.decodeJsonElement()
        val jsonFmt = json.json
        fun parseObj(obj: JsonObject): DynamicModulesDto =
            jsonFmt.decodeFromJsonElement(DynamicModulesDto.serializer(), obj)

        return when (element) {
            is JsonObject -> parseObj(element)
            is JsonArray -> {
                var merged = DynamicModulesDto()
                for (node in element) {
                    if (node !is JsonObject) continue
                    val part = try {
                        parseObj(node)
                    } catch (_: Exception) {
                        continue
                    }
                    merged = DynamicModulesDto(
                        author = part.author ?: merged.author,
                        dynamic = part.dynamic ?: merged.dynamic,
                        stat = part.stat ?: merged.stat,
                    )
                }
                merged
            }
            else -> DynamicModulesDto()
        }
    }

    override fun serialize(encoder: Encoder, value: DynamicModulesDto) {
        encoder.encodeSerializableValue(DynamicModulesDto.serializer(), value)
    }
}

// ---------------------------------------------------------------------------
// 视频笔记（x/note/publish/list/archive）
// ---------------------------------------------------------------------------

@Serializable
data class VideoNoteAuthorDto(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val level: Int = 0,
    @SerialName("is_senior_member") val isSeniorMember: Int = 0,
)

@Serializable
data class VideoNotePageDto(
    val total: Int = 0,
    val size: Int = 0,
    val num: Int = 0,
)

@Serializable
data class PublicVideoNoteItemDto(
    val cvid: Long = 0,
    val title: String = "",
    val summary: String = "",
    val pubtime: String = "",
    @SerialName("web_url") val webUrl: String = "",
    val message: String = "",
    val author: VideoNoteAuthorDto? = null,
    val likes: Int = 0,
    @SerialName("has_like") val hasLike: Boolean = false,
)

@Serializable
data class PublicVideoNoteListData(
    val list: List<PublicVideoNoteItemDto> = emptyList(),
    val page: VideoNotePageDto = VideoNotePageDto(),
    @SerialName("show_public_note") val showPublicNote: Boolean = false,
    val message: String = "",
)

// ---------------------------------------------------------------------------
// 消息中心
// ---------------------------------------------------------------------------

@Serializable
data class MsgUnreadData(
    val reply: Int = 0,
    val at: Int = 0,
    val like: Int = 0,
    @SerialName("sys_msg") val sysMsg: Int = 0,
)

@Serializable
data class MsgCursorDto(
    val id: Long = 0,
    val time: Long = 0,
    @SerialName("is_end") val isEnd: Boolean = false,
)

@Serializable
data class MsgUserDto(val mid: Long = 0, val nickname: String = "", val avatar: String = "")

@Serializable
data class MsgReplyData(
    val cursor: MsgCursorDto? = null,
    val items: List<MsgReplyItemDto> = emptyList(),
)

@Serializable
data class MsgReplyItemDto(
    val id: Long = 0,
    val user: MsgUserDto? = null,
    val item: MsgReplyContentDto? = null,
    val counts: Int = 0,
    @SerialName("is_multi") val isMulti: Int = 0,
    @SerialName("reply_time") val replyTime: Long = 0,
)

@Serializable
data class MsgReplyContentDto(
    val title: String = "",
    val image: String = "",
    val uri: String = "",
    @SerialName("native_uri") val nativeUri: String = "",
    @SerialName("root_reply_content") val rootReplyContent: String = "",
    @SerialName("source_content") val sourceContent: String = "",
    @SerialName("target_reply_content") val targetReplyContent: String = "",
)

@Serializable
data class MsgAtData(
    val cursor: MsgCursorDto? = null,
    val items: List<MsgAtItemDto> = emptyList(),
)

@Serializable
data class MsgAtItemDto(
    val id: Long = 0,
    val user: MsgUserDto? = null,
    val item: MsgAtContentDto? = null,
    @SerialName("at_time") val atTime: Long = 0,
)

@Serializable
data class MsgAtContentDto(
    val title: String = "",
    val image: String = "",
    val uri: String = "",
    @SerialName("native_uri") val nativeUri: String = "",
    @SerialName("source_content") val sourceContent: String = "",
)

@Serializable
data class MsgLikeData(
    val latest: MsgLikeBucketDto? = null,
    val total: MsgLikeBucketDto? = null,
)

@Serializable
data class MsgLikeBucketDto(
    val cursor: MsgCursorDto? = null,
    val items: List<MsgLikeItemDto> = emptyList(),
)

@Serializable
data class MsgLikeItemDto(
    val id: Long = 0,
    val users: List<MsgUserDto> = emptyList(),
    val item: MsgLikeContentDto? = null,
    val counts: Int = 0,
    @SerialName("like_time") val likeTime: Long = 0,
    @SerialName("notice_state") val noticeState: Int = 0,
)

@Serializable
data class MsgLikeContentDto(
    val title: String = "",
    val image: String = "",
    val uri: String = "",
    @SerialName("native_uri") val nativeUri: String = "",
)

/** 点赞详情（x/msgfeed/like_detail）。 */
@Serializable
data class MsgLikeDetailData(
    val page: MsgLikeDetailPageDto? = null,
    val card: MsgLikeDetailCardDto? = null,
    val items: List<MsgLikeDetailItemDto> = emptyList(),
)

@Serializable
data class MsgLikeDetailPageDto(@SerialName("is_end") val isEnd: Boolean = false)

@Serializable
data class MsgLikeDetailCardDto(
    val business: String = "",
    val title: String = "",
)

@Serializable
data class MsgLikeDetailItemDto(
    val user: MsgUserDto? = null,
    @SerialName("like_time") val likeTime: Long = 0,
)

/** 系统通知（message.bilibili.com，data 直接是数组）。 */
@Serializable
data class SysNoticeItemDto(
    val id: Long = 0,
    val cursor: Long = 0,
    val title: String = "",
    /** 内嵌 JSON 字符串，正文在 web 字段；此处保留原始串，仓库层解析。 */
    val content: String = "",
    @SerialName("time_at") val timeAt: String = "",
)

// ---------------------------------------------------------------------------
// 私信（api.vc.bilibili.com，参考 BiliPai MessageResponse）
// ---------------------------------------------------------------------------

/** 会话列表响应。 */
@Serializable
data class SessionListData(
    @SerialName("session_list") val sessionList: List<SessionItemDto>? = null,
    @SerialName("has_more") val hasMore: Int = 0,
    @SerialName("group_count") val groupCount: Int = 0,
)

@Serializable
data class SessionItemDto(
    @SerialName("talker_id") val talkerId: Long = 0,
    @SerialName("session_type") val sessionType: Int = 1,
    @SerialName("at_seqno") val atSeqno: Long = 0,
    @SerialName("top_ts") val topTs: Long = 0,
    @SerialName("group_name") val groupName: String = "",
    @SerialName("group_cover") val groupCover: String = "",
    @SerialName("is_follow") val isFollow: Int = 0,
    @SerialName("is_dnd") val isDnd: Int = 0,
    @SerialName("ack_seqno") val ackSeqno: Long = 0,
    @SerialName("ack_ts") val ackTs: Long = 0,
    @SerialName("session_ts") val sessionTs: Long = 0,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("last_msg") val lastMsg: SessionMessageDto? = null,
    @SerialName("max_seqno") val maxSeqno: Long = 0,
    @SerialName("is_intercept") val isIntercept: Int = 0,
    @SerialName("account_info") val accountInfo: SessionAccountInfoDto? = null,
)

@Serializable
data class SessionMessageDto(
    @SerialName("sender_uid") val senderUid: Long = 0,
    @SerialName("receiver_type") val receiverType: Int = 1,
    @SerialName("receiver_id") val receiverId: Long = 0,
    @SerialName("msg_type") val msgType: Int = 1,
    val content: String = "",
    @SerialName("msg_seqno") val msgSeqno: Long = 0,
    val timestamp: Long = 0,
    @SerialName("at_uids") val atUids: List<Long>? = null,
    @SerialName("msg_key") val msgKey: Long = 0,
    @SerialName("msg_status") val msgStatus: Int = 0,
    @SerialName("notify_code") val notifyCode: String = "",
    @SerialName("new_face_version") val newFaceVersion: Int = 0,
    @SerialName("msg_source") val msgSource: Int = 0,
)

@Serializable
data class SessionAccountInfoDto(
    val name: String = "",
    @SerialName("pic_url") val picUrl: String = "",
    val face: String = "",
) {
    val avatarUrl: String get() = picUrl.ifEmpty { face }
}

/** 历史消息响应。 */
@Serializable
data class MessageHistoryData(
    val messages: List<PrivateMessageItemDto>? = null,
    @SerialName("has_more") val hasMore: Int = 0,
    @Serializable(with = MessageHistoryCursorSerializer::class)
    @SerialName("min_seqno") val minSeqno: Long = 0,
    @SerialName("max_seqno") val maxSeqno: Long = 0,
    @SerialName("e_infos") val eInfos: List<EmoteInfoDto>? = null,
)

@Serializable
data class PrivateMessageItemDto(
    @SerialName("sender_uid") val senderUid: Long = 0,
    @SerialName("receiver_type") val receiverType: Int = 1,
    @SerialName("receiver_id") val receiverId: Long = 0,
    @SerialName("msg_type") val msgType: Int = 1,
    val content: String = "",
    @SerialName("msg_seqno") val msgSeqno: Long = 0,
    val timestamp: Long = 0,
    @SerialName("at_uids") val atUids: List<Long>? = null,
    @SerialName("msg_key") val msgKey: Long = 0,
    @SerialName("msg_status") val msgStatus: Int = 0,
    @SerialName("sys_cancel") val sysCancel: Boolean = false,
    @SerialName("notify_code") val notifyCode: String = "",
    @SerialName("new_face_version") val newFaceVersion: Int = 0,
    @SerialName("msg_source") val msgSource: Int = 0,
)

@Serializable
data class EmoteInfoDto(
    val text: String = "",
    val url: String = "",
    val size: Int = 0,
)

@Serializable
data class SendMessageData(
    @SerialName("msg_key") val msgKey: Long = 0,
    @SerialName("msg_content") val msgContent: String = "",
    @SerialName("e_infos") val eInfos: List<EmoteInfoDto>? = null,
)

/**
 * min_seqno 有时返回 UINT64_MAX 字符串（"18446744073709551615"），
 * 直接 toLong 会溢出。这里将其归一化为 0，其余值 coerceAtLeast(0)。
 */
object MessageHistoryCursorSerializer : KSerializer<Long> {
    private const val UINT64_MAX = "18446744073709551615"

    override val descriptor: SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
            "com.biko.client.MessageHistoryCursor", kotlinx.serialization.descriptors.PrimitiveKind.LONG,
        )

    override fun deserialize(decoder: Decoder): Long {
        if (decoder is JsonDecoder) {
            val raw = decoder.decodeJsonElement().let {
                (it as? JsonPrimitive)?.contentOrNull
            }
            if (raw == null || raw == UINT64_MAX) return 0L
            return raw.toLongOrNull()?.coerceAtLeast(0L)
                ?: throw kotlinx.serialization.SerializationException("非法 min_seqno: $raw")
        }
        return decoder.decodeLong().coerceAtLeast(0L)
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

// ---------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------

fun String.fixUrl(): String = when {
    isEmpty() -> this
    startsWith("//") -> "https:$this"
    startsWith("http://") -> replaceFirst("http://", "https://")
    else -> this
}

/** 解码搜索结果里常见的 HTML 实体（&amp; &lt; &gt; &quot; &#39; 等）。 */
fun String.decodeHtml(): String {
    if (!contains('&')) return this
    return replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}

private fun String.parseDuration(): Int = try {
    val parts = split(":")
    when (parts.size) {
        // MM:SS
        2 -> parts[0].toInt() * 60 + parts[1].toInt()
        // HH:MM:SS
        3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
        else -> toInt()
    }
} catch (_: Exception) {
    0
}
