package com.biko.client.data.model

import kotlinx.serialization.Serializable

/** 已订阅（关注）的 UP 主，用于订阅页列表。 */
data class FollowedUp(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
)

/** UP 主的合集 / 系列（空间「合集和列表」）。 */
data class SpaceCollection(
    /** 合集 id（season_id）或系列 id（series_id）。 */
    val id: Long = 0,
    /** true = 合集（season），false = 系列（series）。 */
    val isSeason: Boolean = true,
    val title: String = "",
    val cover: String = "",
    /** 内含稿件数。 */
    val total: Int = 0,
)

/** 视频（同时用于卡片、详情与历史记录持久化）。 */
@Serializable
data class Video(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val views: Int = 0,
    val danmaku: Int = 0,
    /** 评论数（详情页接口 stat.reply）。 */
    val replies: Int = 0,
    val likes: Int = 0,
    val coins: Int = 0,
    val favorites: Int = 0,
    val pubdate: Long = 0,
    val desc: String = "",
    val category: String = "",
    val upName: String = "",
    val upMid: Long = 0,
    val upFace: String = "",
    val progress: Int = 0,
    /** 记录类型："video"（默认）/ "live"（直播）/ "bangumi"（番剧、影视），用于历史记录分类。 */
    val type: String = "video",
    /** 直播历史对应的直播间号（type=="live" 时有效）。 */
    val roomId: Long = 0,
    /** 番剧 / 影视历史对应的季度 id（type=="bangumi" 时有效）。 */
    val seasonId: Long = 0,
    /** 番剧 / 影视历史对应的分集 id（type=="bangumi" 时有效）。 */
    val epid: Long = 0,
)

/** 一条弹幕。 */
data class DanmakuItem(
    /** 出现时间（毫秒）。 */
    val timeMs: Long,
    /** 类型：1/2/3 滚动，4 底部，5 顶部。 */
    val mode: Int,
    val fontSize: Int,
    val color: Int,
    val text: String,
)

/** 单条音视频码流（DASH 分片或整段 MP4）。 */
data class StreamTrack(
    /** B 站 qn 值（视频清晰度）或音频档位 id。 */
    val id: Int = 0,
    val url: String = "",
    val backupUrls: List<String> = emptyList(),
    val bandwidth: Long = 0,
    val codecs: String = "",
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: String = "",
    /** SegmentBase 的初始化区间与索引区间，形如 "0-1023"。 */
    val initRange: String = "",
    val indexRange: String = "",
)

/** 一次播放地址请求的结果：优先 DASH，缺失时回退 durl 整段 MP4。 */
data class PlayInfo(
    val quality: Int = 0,
    val acceptQuality: List<Int> = emptyList(),
    val videoTracks: List<StreamTrack> = emptyList(),
    val audioTracks: List<StreamTrack> = emptyList(),
    val durationMs: Long = 0,
    val durl: String? = null,
) {
    val isDash: Boolean get() = videoTracks.isNotEmpty() && audioTracks.isNotEmpty()
}

/** 直播播放信息：播放地址 + 该直播间可用的清晰度档位。 */
data class LivePlayInfo(
    val url: String? = null,
    val quality: Int = 0,
    val acceptQuality: List<Int> = emptyList(),
    /** 流协议名：http_stream（FLV）/ http_hls（HLS）。 */
    val protocolName: String = "",
    /** 容器格式名：flv / ts / fmp4。 */
    val formatName: String = "",
    /** 编码名：avc / hevc / av1。 */
    val codecName: String = "",
    /** 是否仍在直播（0 未开播，1 直播中，2 轮播）。 */
    val liveStatus: Int = 0,
    /** 开播时间戳（秒，getRoomPlayInfo 一并下发），未开播为 0。 */
    val liveTime: Long = 0,
    /** 主播 uid（getRoomPlayInfo 一并下发，用于点赞上报）。 */
    val uid: Long = 0,
)

/** 直播弹幕服务器与鉴权 token（getDanmuInfo 结果）。 */
data class LiveDanmakuInfo(
    /** 形如 "host:port"，用于拼接 wss://host:port/sub。 */
    val host: String = "",
    val token: String = "",
)

/** 直播间。 */
@Serializable
data class LiveRoom(
    val roomId: Long = 0,
    /** 主播 uid（getInfoByRoom 才有，卡片流不带）。 */
    val uid: Long = 0,
    val title: String = "",
    val uname: String = "",
    val face: String = "",
    val cover: String = "",
    val area: String = "",
    val online: Int = 0,
    /** 真实「多少人看过」（累计观看人次），来自直播间的 watched_show。 */
    val watchedShow: Int = 0,
    /**
     * 服务端已排版的「多少人看过」文案（watched_show.text_large，如「2081人看过」）。
     * 与 B 站 / 第三方客户端展示完全一致，非空时优先于 [watchedShow] 展示。
     */
    val watchedText: String = "",
    /** 直播状态：0 未开播，1 直播中，2 轮播。 */
    val liveStatus: Int = 0,
)

/** 直播分区（一级分区带二级子分区）。 */
data class LiveArea(
    val id: Int = 0,
    val name: String = "",
    /** 二级分区（一级分区下的子分区）。 */
    val children: List<LiveArea> = emptyList(),
)

/** 直播间聊天条目（来自 DANMU_MSG 等 CMD，用于聊天列表展示）。 */
data class LiveChatItem(
    val uid: Long = 0,
    val uname: String = "",
    val text: String = "",
    /** 弹幕颜色（0xRRGGBB）。 */
    val color: Int = 0xFFFFFF,
    /** 弹幕 mode：1/2/3 滚动。 */
    val mode: Int = 1,
    /** 是否自己发送的。 */
    val isSelf: Boolean = false,
    /** 粉丝牌名称（未佩戴为空）。 */
    val medalName: String = "",
    val medalLevel: Int = 0,
    /** 粉丝牌颜色（0xRRGGBB）。 */
    val medalColor: Int = 0,
    /** 用户等级（UL）。 */
    val userLevel: Int = 0,
    /** 大航海等级：0 无，1 总督，2 提督，3 舰长。 */
    val guardLevel: Int = 0,
    /** 是否房管。 */
    val isAdmin: Boolean = false,
    /** 进房提示（INTERACT_WORD，「xxx 进入直播间」）。 */
    val enter: Boolean = false,
    /** 整条弹幕就是一个表情图（DANMU_MSG 的 emots 字段），非空时正文渲染为表情图。 */
    val emoteUrl: String? = null,
)

/** 直播醒目留言（SC）。 */
data class LiveSuperChat(
    val id: Long = 0,
    val uid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val text: String = "",
    /** SC 背景色（0xRRGGBB）。 */
    val bgColor: Int = 0,
    /** 停留时长（秒）。 */
    val duration: Int = 0,
    /** 价格（元）。 */
    val price: Int = 0,
    /** 发送时间戳（秒）。 */
    val time: Long = 0,
)

/** 直播间实时状态（随 WS 推送更新）。 */
data class LiveRoomStats(
    /** 当前在线人气（HEARTBEAT_REPLY）。 */
    val online: Int? = null,
    /** 高能观众数（ONLINE_RANK_COUNT 的 count）。 */
    val onlineRank: Int? = null,
    /** 累计看过（WATCHED_CHANGE）。 */
    val watched: Int? = null,
    /** 累计看过文案（WATCHED_CHANGE 的 text_large，如「2081人看过」）。 */
    val watchedText: String? = null,
    /** 标题（ROOM_CHANGE 时更新）。 */
    val title: String? = null,
    /** 分区名（ROOM_CHANGE 时更新）。 */
    val area: String? = null,
)

/** 搜索到的用户（UP 主，或主播）。 */
data class SearchUser(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    /** 个性签名。 */
    val sign: String = "",
    val fans: Int = 0,
    /** 投稿数。 */
    val videos: Int = 0,
    val level: Int = 0,
    /** 是否正在直播。 */
    val isLive: Boolean = false,
    /** 正在直播时的直播间号（无则为 0）。 */
    val liveRoomId: Long = 0,
    /** 认证说明（如「bilibili 知名UP主」）；未认证时为空。 */
    val verifyDesc: String = "",
)

/** 关注 / 粉丝列表里的用户条目。 */
data class RelationUser(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    /** 个性签名。 */
    val sign: String = "",
    /** 当前用户是否已关注该用户（attribute 2=已关注 6=互关）。 */
    val isFollowing: Boolean = false,
    /** 认证说明（如「bilibili 知名UP主」）；未认证时为空。 */
    val verifyDesc: String = "",
)

/** 关注分组（x/relation/tags）。 */
data class FollowGroup(
    val tagid: Long = 0,
    val name: String = "",
    /** 分组内 UP 数量（服务端给出的近似值）。 */
    val count: Int = 0,
)

/** 视频笔记（x/note/publish/list/archive 的公开笔记条目）。 */
data class VideoNote(
    val cvid: Long = 0,
    val title: String = "",
    val summary: String = "",
    /** 发布时间文案（服务端直接下发，如 "2024-01-01 12:00"）。 */
    val pubtime: String = "",
    /** 笔记网页地址（用于在浏览器中打开）。 */
    val webUrl: String = "",
    val likes: Int = 0,
    val hasLike: Boolean = false,
    val authorMid: Long = 0,
    val authorName: String = "",
    val authorFace: String = "",
    val authorLevel: Int = 0,
)

/** 话题（话题页头部信息）。 */
data class TopicInfo(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val sharePic: String = "",
    val view: Long = 0,
    val discuss: Long = 0,
    val dynamics: Long = 0,
    val creatorUid: Long = 0,
    val creatorName: String = "",
    val creatorFace: String = "",
)

/** 话题页排序项（如「最新」「最热」）。 */
data class TopicSortOption(
    val sortBy: Int = 0,
    val name: String = "",
)

/** 话题动态分页结果。 */
data class TopicFeedPage(
    val posts: List<DynamicPost> = emptyList(),
    val offset: String = "",
    val hasMore: Boolean = false,
    val sortOptions: List<TopicSortOption> = emptyList(),
    val selectedSortBy: Int = 0,
)

/** 搜索结果的统一封装：视频 / 直播间 / 用户，按当前分类 Tab 装不同的行。 */
sealed interface SearchRow {
    /** 分页去重用的稳定主键。 */
    val key: String

    data class VideoRow(val video: Video) : SearchRow {
        override val key: String get() = "v_${video.bvid}"
    }

    data class LiveRow(val room: LiveRoom) : SearchRow {
        override val key: String get() = "l_${room.roomId}"
    }

    data class UserRow(val user: SearchUser) : SearchRow {
        override val key: String get() = "u_${user.mid}"
    }

    /** 番剧 / 影视（PGC）结果行。 */
    data class SeasonRow(val season: BangumiSeason) : SearchRow {
        override val key: String get() = "s_${season.seasonId}"
    }

    /** 专栏 / 文章结果行。 */
    data class ArticleRow(val article: ArticleSummary) : SearchRow {
        override val key: String get() = "a_${article.id}"
    }
}

/** 番剧（PGC）条目，来自 pgc/season/index/result。 */
data class BangumiSeason(
    val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    /** 副标题：更新进度 / 评分，如「更新至第12话 · 9.7分」。 */
    val subtitle: String = "",
    /** 角标文案：如「大会员」「独家」。为空则不显示。 */
    val badge: String = "",
)

/** 专栏 / 文章条目（列表用）。 */
data class ArticleSummary(
    val id: Long = 0,
    val title: String = "",
    val summary: String = "",
    val cover: String = "",
    val views: Int = 0,
    val likes: Int = 0,
    val replies: Int = 0,
    val pubTime: Long = 0,
    /** 分类名（如「动画」「游戏」）；可能为空。 */
    val category: String = "",
    val authorName: String = "",
    val authorFace: String = "",
    val authorMid: Long = 0,
)

/** 文章正文块。 */
sealed interface ArticleBlock {
    data class Heading(val text: String, val spans: List<ArticleSpan> = emptyList()) : ArticleBlock
    data class Paragraph(val text: String, val spans: List<ArticleSpan> = emptyList()) : ArticleBlock
    data class Quote(val text: String, val spans: List<ArticleSpan> = emptyList()) : ArticleBlock
    data class ListBlock(val ordered: Boolean, val items: List<String>) : ArticleBlock
    data class Code(val language: String, val content: String) : ArticleBlock
    data class Image(val url: String, val width: Int = 0, val height: Int = 0) : ArticleBlock
}

/** 富文本片段样式。 */
data class ArticleSpan(
    val start: Int,
    val end: Int,
    val fontSizeSp: Int = 0,
    /** ARGB 颜色（Long），0 表示不覆盖。 */
    val colorArgb: Long = 0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
)

/** 文章详情。 */
data class ArticleDetail(
    val id: Long = 0,
    val title: String = "",
    val summary: String = "",
    val authorName: String = "",
    val authorMid: Long = 0,
    val authorFace: String = "",
    val publishTime: Long = 0,
    val banner: String? = null,
    val blocks: List<ArticleBlock> = emptyList(),
)

/** 专栏互动状态与统计（x/article/viewinfo）。 */
data class ArticleInteraction(
    val liked: Boolean = false,
    val favorited: Boolean = false,
    /** 是否已关注作者。 */
    val attention: Boolean = false,
    /** 当前登录用户已投的硬币数（0/1/2）。 */
    val coined: Int = 0,
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val shareCount: Int = 0,
    val favCount: Int = 0,
    val coinCount: Int = 0,
)

/** UP 主空间资料（来自 x/web-interface/card）。 */
data class SpaceProfile(
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val sex: String = "",
    val level: Int = 0,
    /** 认证说明（如「bilibili创始人（站长）」）；未认证时为空。 */
    val officialDesc: String = "",
    /** 认证类型，-1 表示无认证。 */
    val officialType: Int = -1,
    /** 大会员标签文案。 */
    val vipLabel: String = "",
    /** 大会员状态，>0 表示有效。 */
    val vipStatus: Int = 0,
    /** 空间头图。 */
    val banner: String = "",
    /** IP 属地。 */
    val ipLocation: String = "",
    /** 关注数 / 粉丝数 / 获赞数。 */
    val following: Long = 0,
    val fans: Long = 0,
    val likes: Long = 0,
    /** 投稿总数。 */
    val totalVideos: Int = 0,
)

/** 评论。 */
data class Comment(
    val rpid: Long = 0,
    val mid: Long = 0,
    val userName: String = "",
    val avatar: String = "",
    val level: Int = 0,
    val message: String = "",
    val location: String = "",
    val likes: Int = 0,
    val ctime: Long = 0,
    val liked: Boolean = false,
    val hated: Boolean = false,
    /** 楼中楼回复数。 */
    val rcount: Int = 0,
    /** 是否 UP 主本人（与视频 UP 主 mid 相同）。 */
    val isUp: Boolean = false,
    /** 是否 UP 主置顶评论。 */
    val isUpTop: Boolean = false,
    /** UP 主是否点过赞。 */
    val upLiked: Boolean = false,
    /** 是否是当前登录用户自己发的（可删除）。 */
    val isMine: Boolean = false,
    /** 粉丝牌名称（未佩戴时为空）。 */
    val medalName: String = "",
    /** 粉丝牌等级。 */
    val medalLevel: Int = 0,
    /** 正文里 @ 到的用户名 → mid，用于点击跳转。 */
    val atUserMids: Map<String, Long> = emptyMap(),
    /** 评论配图。 */
    val pictures: List<CommentPicture> = emptyList(),
    /** 正文表情：token → 图片 URL。 */
    val emotes: Map<String, String> = emptyMap(),
)

/** 评论配图。 */
data class CommentPicture(
    val imgSrc: String = "",
    val imgWidth: Int = 0,
    val imgHeight: Int = 0,
    val imgSize: Double = 0.0,
)

/** 表情包。 */
data class EmotePackage(
    val id: Long = 0,
    val text: String = "",
    val url: String = "",
    val emote: List<CommentEmote> = emptyList(),
)

/** 单个表情。 */
data class CommentEmote(
    val id: Long = 0,
    val text: String = "",
    val url: String = "",
)

/** @ 提及搜索到的用户。 */
data class MentionUser(
    val uid: Long = 0,
    val name: String = "",
    val face: String = "",
)

/** 评论分页结果。 */
data class CommentPage(
    val comments: List<Comment> = emptyList(),
    val next: Long = 0,
    val isEnd: Boolean = true,
)

/** 登录用户信息。 */
data class UserProfile(
    val isLogin: Boolean = false,
    val uname: String = "",
    val face: String = "",
    val level: Int = 0,
    val coin: Int = 0,
    val bcoin: Int = 0,
    val exp: Int = 0,
    val nextExp: Int = 0,
)

/** 「我的」页顶部的统计（来自 x/web-interface/card）。 */
data class MyStats(
    val following: Long = -1,
    val follower: Long = -1,
    val likes: Long = -1,
    val vipLabel: String = "",
    /** 大会员昵称颜色，如 #FB7299。 */
    val nicknameColor: String = "",
    /** 头像右下角的大会员角标图。 */
    val avatarBadge: String = "",
    /** 个人签名。 */
    val sign: String = "",
)

/** 关注动态条目（主页「动态」页）。 */
data class DynamicPost(
    val id: String = "",
    /** DYNAMIC_TYPE_AV / DRAW / WORD / FORWARD 等。 */
    val type: String = "",
    val authorName: String = "",
    val authorFace: String = "",
    val authorMid: Long = 0,
    /** 动作描述，如「投稿了视频」。 */
    val pubAction: String = "",
    val pubTime: String = "",
    val pubTs: Long = 0,
    /** 正文（转发动态本人配文）。 */
    val text: String = "",
    val pics: List<String> = emptyList(),
    /** 视频卡片（DYNAMIC_TYPE_AV）。 */
    val bvid: String = "",
    val videoTitle: String = "",
    val videoCover: String = "",
    val videoDuration: String = "",
    val videoPlay: String = "",
    val videoDanmaku: String = "",
    /** 转发动态的原文。 */
    val orig: DynamicPost? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val forwardCount: Int = 0,
)

/** 动态分页结果。[offset] 传下一页。 */
data class DynamicPage(
    val posts: List<DynamicPost> = emptyList(),
    val offset: String = "",
    val hasMore: Boolean = false,
)

/** 消息中心未读数。 */
data class MsgUnread(
    val reply: Int = 0,
    val at: Int = 0,
    val like: Int = 0,
    val sysMsg: Int = 0,
) {
    val total: Int get() = reply + at + like + sysMsg
}

enum class MessageKind { REPLY, AT, LIKE, SYSTEM }

/** 消息条目（回复我的 / @我的 / 收到的赞 / 系统通知统一模型）。 */
data class MessageItem(
    val id: Long = 0,
    val kind: MessageKind = MessageKind.REPLY,
    val userName: String = "",
    val avatar: String = "",
    /** 消息附带的内容封面图（视频封面等）。 */
    val cover: String = "",
    /** 消息标题（如视频标题 / 系统通知标题）。 */
    val title: String = "",
    /** 正文内容（对方说的话 / 系统通知正文）。 */
    val content: String = "",
    /** 被回复的内容（即我自己的评论），用于「回复我的」列表的灰色药丸。 */
    val quotedContent: String = "",
    /** 时间戳（秒）；系统通知为 0。 */
    val time: Long = 0,
    /** 无时间戳时展示的原文日期（系统通知）。 */
    val timeText: String = "",
    /** 跳转链接（可选）。 */
    val uri: String = "",
    /** 「收到的赞」里其余点赞者头像。 */
    val extraAvatars: List<String> = emptyList(),
    /** 「收到的赞」的点赞数。 */
    val count: Int = 0,
    /** 「收到的赞」通知开关：0=接收通知、1=不再通知。 */
    val noticeState: Int = 0,
)

/** 消息分页结果。 */
data class MessagePage(
    val items: List<MessageItem> = emptyList(),
    val cursorId: Long = 0,
    val cursorTime: Long = 0,
    val isEnd: Boolean = true,
)

/** 点赞详情页头部卡片（business: title）。 */
data class LikeDetailCard(
    val business: String = "",
    val title: String = "",
)

/** 点赞详情页的一条「谁赞了我」。 */
data class LikeDetailUser(
    val mid: Long = 0,
    val name: String = "",
    val avatar: String = "",
    /** 点赞时间（秒）。 */
    val time: Long = 0,
)

/** 点赞详情分页结果。 */
data class LikeDetailPage(
    val card: LikeDetailCard? = null,
    val users: List<LikeDetailUser> = emptyList(),
    /** 已取完（数量达到累计点赞数）。 */
    val isEnd: Boolean = false,
    /** 末位用户的 mid，作为下一页 last_mid。 */
    val lastMid: Long = 0,
)

/** 私信会话（会话列表一项）。 */
data class PrivateSession(
    val talkerId: Long = 0,
    val sessionType: Int = 1,
    val name: String = "",
    val avatar: String = "",
    val unread: Int = 0,
    /** 最后一条消息的预览文本（已按 msg_type 解析）。 */
    val preview: String = "",
    /** 最后一条消息时间（秒）。 */
    val time: Long = 0,
    val topTs: Long = 0,
    val isDnd: Boolean = false,
    val isIntercept: Boolean = false,
) {
    val isTop: Boolean get() = topTs > 0
}

/** 私信聊天消息。 */
data class ChatMessage(
    val msgKey: Long = 0,
    val senderUid: Long = 0,
    val msgType: Int = 1,
    /** 展示文本（各类消息尽力解析出的正文；无正文时为空）。 */
    val text: String = "",
    /** 图片消息的图片地址（msg_type=2 时有值）。 */
    val imageUrl: String = "",
    val time: Long = 0,
    val seqno: Long = 0,
    val withdrawn: Boolean = false,
) {
    fun isMine(selfUid: Long): Boolean = selfUid > 0 && senderUid == selfUid
}

/** 聊天记录分页结果。[minSeqno] 传下一页（更早消息）。 */
data class ChatPage(
    val messages: List<ChatMessage> = emptyList(),
    val hasMore: Boolean = false,
    val minSeqno: Long = 0,
    val maxSeqno: Long = 0,
)

/**
 * 离线缓存任务。下载 DASH 的视频 / 音频两条 m4s 到 app 私有目录，
 * 播放时用本地 file:// 地址重建 MPD（复用 [com.biko.client.player.buildLocalMpd]）。
 */
@Serializable
data class DownloadTask(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val upName: String = "",
    val durationMs: Long = 0,
    val qn: Int = 0,
    /** 0=等待 1=下载中 2=已完成 3=失败 4=已暂停。 */
    val status: Int = STATUS_WAITING,
    val downloaded: Long = 0,
    val total: Long = 0,
    /** 待下载源地址（用于启动 / 续传）。 */
    val videoUrl: String = "",
    val audioUrl: String = "",
    /** 本地文件绝对路径（下载完成后填写）。 */
    val videoFile: String = "",
    val audioFile: String = "",
    val videoMime: String = "video/mp4",
    val audioMime: String = "audio/mp4",
    val videoCodecs: String = "",
    val videoBandwidth: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: String = "",
    val videoInitRange: String = "",
    val videoIndexRange: String = "",
    val audioInitRange: String = "",
    val audioIndexRange: String = "",
    val error: String = "",
) {
    val progress: Float
        get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f

    val isDone: Boolean get() = status == STATUS_DONE

    companion object {
        const val STATUS_WAITING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_DONE = 2
        const val STATUS_FAILED = 3
        const val STATUS_PAUSED = 4
    }
}
