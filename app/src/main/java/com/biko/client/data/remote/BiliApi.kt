package com.biko.client.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Streaming
import retrofit2.http.Url

/** B 站公开接口。baseUrl 在 [com.biko.client.data.AppGraph] 中配置。 */
interface BiliApi {

    // ---- 导航 / 用户 ----
    @GET("x/web-interface/nav")
    suspend fun nav(): BiliResponse<NavData>

    @GET("x/web-interface/coin/log")
    suspend fun coinLog(@Query("jsonp") jsonp: String = "jsonp"): BiliResponse<CoinData>

    /** 我的账号资料（昵称/签名/生日/性别），改资料前取原值用。 */
    @GET("x/member/web/account")
    suspend fun myAccount(): BiliResponse<AccountData>

    /** 修改个人资料（昵称/签名），需 csrf。 */
    @FormUrlEncoded
    @POST("x/member/web/update")
    suspend fun updateProfile(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 用户卡片：昵称/头像/等级/大会员标签/关注/粉丝/获赞（无需登录）。 */
    @GET("x/web-interface/card")
    suspend fun userCard(
        @Query("mid") mid: String,
        /** 传 true 才会下发 space.l_img（空间头图）。 */
        @Query("photo") photo: Boolean = false,
    ): BiliResponse<CardData>

    // ---- 首页 / 排行 / 分区 ----
    /**
     * 排行榜（x/web-interface/ranking/v2，需 WBI 签名）。
     * 匿名即可查看，但未带签名会被风控拦截返回 -352；签名后携带完整卡片信息（时长 / 播放 / 时间）。
     */
    @GET
    suspend fun ranking(@Url url: String): BiliResponse<VideoCardListData>

    @GET("x/web-interface/newlist")
    suspend fun regionNew(
        @Query("rid") rid: Int,
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 20,
    ): BiliResponse<NewListData>

    /** 首页推荐流：匿名可用（ranking 接口未登录会返回 -352）。 */
    @GET("x/web-interface/index/top/feed/rcmd")
    suspend fun feedRcmd(
        @Query("fresh_type") freshType: Int = 3,
        @Query("ps") ps: Int = 20,
        @Query("fresh_idx") freshIdx: Int,
        @Query("fresh_idx_1h") freshIdx1h: Int = freshIdx,
        @Query("feed_version") feedVersion: String = "V8",
        @Query("web_location") webLocation: Int = 1430650,
    ): BiliResponse<FeedRcmdData>

    // ---- 搜索 / 热搜 ----
    // 搜索接口（search/type）已升级为 wbi 版，未签名一律返回 412；统一走 @Url 传入签名后的完整 query。

    /** 搜索视频：search_type=video。 */
    @GET
    suspend fun search(@Url url: String): BiliResponse<SearchData>

    /** 搜索用户：search_type=bili_user（UP 主）/ live_user（主播）。 */
    @GET
    suspend fun searchUsers(@Url url: String): BiliResponse<SearchUserData>

    /** 搜索直播间：search_type=live_room。 */
    @GET
    suspend fun searchLiveRooms(@Url url: String): BiliResponse<SearchLiveRoomData>

    /** 搜索番剧 / 影视：search_type=media_bangumi / media_ft（共用同一响应结构）。 */
    @GET
    suspend fun searchMedia(@Url url: String): BiliResponse<SearchMediaData>

    @GET("x/web-interface/search/square")
    suspend fun hotSearch(@Query("limit") limit: Int = 10): BiliResponse<SearchSquareData>

    /** 搜索联想词（独立域名 s.search.bilibili.com/main/suggest，响应体顶层是 result）。 */
    @GET
    suspend fun searchSuggest(@Url url: String): SuggestResponse

    // ---- 视频详情 / 播放 ----
    @GET("x/web-interface/view")
    suspend fun videoDetail(@Query("bvid") bvid: String): BiliResponse<VideoDetailData>

    /** 相关推荐视频（data 直接是裸数组）。 */
    @GET("x/web-interface/archive/related")
    suspend fun related(@Query("bvid") bvid: String): BiliResponse<List<VideoCardDto>>

    /** 视频公开笔记列表（x/note/publish/list/archive，oid 传稿件 aid）。 */
    @GET("x/note/publish/list/archive")
    suspend fun publicVideoNotes(
        @Query("oid") oid: Long,
        @Query("oid_type") oidType: Int = 0,
        @Query("ps") pageSize: Int = 20,
        @Query("pn") page: Int = 1,
    ): BiliResponse<PublicVideoNoteListData>

    @GET("x/player/playurl")
    suspend fun playUrl(
        @Query("avid") aid: Long,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int,
        /** fnval 位掩码：1=MP4，16=DASH，64=HDR，128=4K，256=杜比音频，512=杜比视界，1024=8K，2048=AV1。 */
        @Query("fnval") fnval: Int = 4048,
        @Query("fourk") fourk: Int = 1,
        /**
         * 不传（默认）走 Web 端，才会下发 DASH：多清晰度 + 独立音频轨；
         * 传 "html5" 只会返回整段 MP4 的 durl，清晰度也被限制在 2 档。
         */
        @Query("platform") platform: String? = null,
        @Query("high_quality") highQuality: Int = 1,
    ): BiliResponse<PlayUrlData>

    /** 弹幕 XML（list.so 返回 deflate 压缩的 XML）。 */
    @Streaming
    @GET
    suspend fun danmakuXml(@Url url: String): okhttp3.ResponseBody

    /**
     * 弹幕元信息（x/v2/dm/web/view，protobuf）。
     * 响应里带 special_dms：特殊弹幕（Mode 7/9）文件地址列表。
     */
    @Streaming
    @GET("x/v2/dm/web/view")
    suspend fun danmakuView(
        @Query("type") type: Int = 1,
        /** 稿件 cid */
        @Query("oid") oid: Long,
        /** 稿件 aid */
        @Query("pid") pid: Long,
    ): okhttp3.ResponseBody

    /** 特殊弹幕文件（与 seg.so 同格式的 protobuf，含 Mode 7/9）。 */
    @Streaming
    @GET
    suspend fun specialDanmakuFile(@Url url: String): okhttp3.ResponseBody

    // ---- 互动（点赞 / 投币 / 收藏 / 关注）----
    @GET("x/web-interface/archive/has/like")
    suspend fun hasLiked(@Query("aid") aid: Long): BiliResponse<Int>

    /** 当前登录用户对该稿件已投的硬币数（0/1/2）。 */
    @GET("x/web-interface/archive/coins")
    suspend fun coins(@Query("aid") aid: Long): BiliResponse<CoinCountData>

    @FormUrlEncoded
    @POST("x/web-interface/archive/like")
    suspend fun like(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    @FormUrlEncoded
    @POST("x/web-interface/coin/add")
    suspend fun coin(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    @FormUrlEncoded
    @POST("x/v3/fav/resource/deal")
    suspend fun favourite(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    @FormUrlEncoded
    @POST("x/relation/modify")
    suspend fun follow(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 举报 UP 主（x/space/report）。 */
    @FormUrlEncoded
    @POST("x/space/report")
    suspend fun reportUser(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    @GET("x/relation")
    suspend fun relation(@Query("fid") fid: Long): BiliResponse<RelationData>

    @GET("x/relation/stat")
    suspend fun relationStat(@Query("vmid") vmid: Long): BiliResponse<RelationStatData>

    /** UP 主获赞数（x/space/upstat）。 */
    @GET("x/space/upstat")
    suspend fun upStat(@Query("mid") mid: Long): BiliResponse<UpStatData>

    // ---- 关注列表 / UP 主投稿（WBI）----
    @GET
    suspend fun followings(@Url url: String): BiliResponse<FollowingData>

    /** 关注 / 粉丝列表（x/relation/followings、x/relation/fans，共用同一响应结构）。 */
    @GET
    suspend fun relationList(@Url url: String): BiliResponse<RelationUserData>

    @GET
    suspend fun spaceVideos(@Url url: String): BiliResponse<SpaceSearchData>

    // ---- 关注分组 ----
    /** 全部分组（data 直接是数组，含 tagid -10 特别关注 / 0 默认分组）。 */
    @GET("x/relation/tags")
    suspend fun relationTags(): BiliResponse<List<RelationTagItemDto>>

    /** 某 UP 所属的分组：data 为 tagid→? 的映射。 */
    @GET("x/relation/tag/user")
    suspend fun relationTagUser(@Query("fid") fid: Long): BiliResponse<Map<String, String>>

    /** 分组内 UP 列表。 */
    @GET("x/relation/tag")
    suspend fun relationTagMembers(
        @Query("tagid") tagId: Long,
        @Query("order_type") orderType: String = "",
        @Query("ps") pageSize: Int = 100,
        @Query("pn") page: Int = 1,
    ): BiliResponse<List<FollowGroupMemberDto>>

    /** 批量把用户加入分组（先移出即传 tagids=0）。 */
    @FormUrlEncoded
    @POST("x/relation/tags/addUsers")
    suspend fun addUsersToRelationTags(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    // ---- 专栏 / 文章 ----
    /** 文章详情（x/article/view，需 WBI 签名）。 */
    @GET
    suspend fun articleView(@Url url: String): BiliResponse<ArticleViewData>

    /** 搜索专栏（search_type=article，需 WBI 签名）。 */
    @GET
    suspend fun searchArticles(@Url url: String): BiliResponse<ArticleSearchData>

    /** UP 主空间专栏列表（x/space/wbi/article，需 WBI 签名）。 */
    @GET
    suspend fun spaceArticles(@Url url: String): BiliResponse<SpaceArticleData>

    /** 专栏互动信息（x/article/viewinfo）：点赞 / 投币 / 收藏状态 + 统计。 */
    @GET("x/article/viewinfo")
    suspend fun articleViewInfo(@Query("id") id: Long): BiliResponse<ArticleViewInfoData>

    /** 专栏点赞（x/article/like）：type 1 点赞 / 2 取消。 */
    @FormUrlEncoded
    @POST("x/article/like")
    suspend fun articleLike(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 专栏收藏（x/article/favorites/add、del）。 */
    @FormUrlEncoded
    @POST
    suspend fun articleFav(@Url url: String, @FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    // ---- 空间合集 / 系列 ----
    /** UP 主的合集和系列列表（x/polymer/web-space/seasons_series_list）。 */
    @GET("x/polymer/web-space/seasons_series_list")
    suspend fun seasonsSeriesList(
        @Query("mid") mid: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 20,
    ): BiliResponse<SeasonsSeriesData>

    /** 合集内稿件列表（x/polymer/web-space/seasons_archives_list）。 */
    @GET("x/polymer/web-space/seasons_archives_list")
    suspend fun seasonArchives(
        @Query("mid") mid: Long,
        @Query("season_id") seasonId: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("sort_reverse") sortReverse: Boolean = false,
    ): BiliResponse<SeasonArchivesData>

    /** 系列内稿件列表（x/series/archives）。 */
    @GET("x/series/archives")
    suspend fun seriesArchives(
        @Query("mid") mid: Long,
        @Query("series_id") seriesId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("sort") sort: String = "desc",
    ): BiliResponse<SeriesArchivesData>

    // ---- 评论（WBI）----
    @GET
    suspend fun comments(@Url url: String): BiliResponse<ReplyData>

    @FormUrlEncoded
    @POST("x/v2/reply/add")
    suspend fun replyAdd(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 评论表情面板（business=reply）。 */
    @GET("x/emote/user/panel/web")
    suspend fun emotePanel(@QueryMap params: Map<String, String>): BiliResponse<EmotePanelData>

    /** 评论配图上传（x/dynamic/feed/draw/upload_bfs）。 */
    @Multipart
    @POST("x/dynamic/feed/draw/upload_bfs")
    suspend fun uploadCommentImage(
        @Part fileUp: MultipartBody.Part,
        @Part("category") category: RequestBody,
        @Part("biz") biz: RequestBody,
        @Part("csrf") csrf: RequestBody,
    ): BiliResponse<UploadImageData>

    /** @ 提及用户搜索。 */
    @GET("x/polymer/web-dynamic/v1/mention/search")
    suspend fun mentionSearch(@Query("keyword") keyword: String?): BiliResponse<MentionSearchData>

    /** 举报评论（x/v2/reply/report）。 */
    @FormUrlEncoded
    @POST("x/v2/reply/report")
    suspend fun replyReport(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 删除自己发的评论（x/v2/reply/del）。 */
    @FormUrlEncoded
    @POST("x/v2/reply/del")
    suspend fun replyDelete(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    @FormUrlEncoded
    @POST("x/v2/dm/post")
    suspend fun danmakuPost(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    @FormUrlEncoded
    @POST("x/v2/reply/action")
    suspend fun replyAction(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    @FormUrlEncoded
    @POST("x/v2/reply/hate")
    suspend fun replyHate(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 楼中楼评论（某条评论下的回复）。 */
    @GET("x/v2/reply/reply")
    suspend fun replyDetail(
        @Query("oid") oid: Long,
        @Query("type") type: Int = 1,
        @Query("root") root: Long,
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 20,
    ): BiliResponse<ReplyDetailData>

    // ---- 收藏夹 ----
    @GET("x/v3/fav/folder/created/list-all")
    suspend fun favFolders(@Query("up_mid") upMid: String): BiliResponse<FavFolderListData>

    /** 是否已收藏该视频（详情页星标初始状态）。 */
    @GET("x/v2/fav/video/favoured")
    suspend fun favoured(@Query("aid") aid: Long): BiliResponse<FavouredData>

    @GET("x/v3/fav/resource/list")
    suspend fun favResources(
        @Query("media_id") mediaId: String,
        @Query("pn") pn: Int,
        @Query("ps") ps: Int = 20,
        @Query("order") order: String = "mtime",
        @Query("type") type: Int = 0,
        @Query("tid") tid: Int = 0,
        @Query("platform") platform: String = "web",
    ): BiliResponse<FavListData>

    // ---- 历史 ----
    // 游标分页：max/view_at/business 来自上一页响应的 data.cursor，原样回传；
    // type：all/archive/live/article（null=不传，即全部）；番剧(PGC)服务端不支持 type，只能取全部后本地过滤。
    @GET("x/web-interface/history/cursor")
    suspend fun history(
        @Query("ps") ps: Int = 30,
        @Query("max") max: Long? = null,
        @Query("view_at") viewAt: Long? = null,
        @Query("business") business: String? = null,
        @Query("type") type: String? = null,
    ): BiliResponse<HistoryData>

    @FormUrlEncoded
    @POST("x/v2/history/report")
    suspend fun historyReport(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    @FormUrlEncoded
    @POST("x/v2/history/clear")
    suspend fun historyClear(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 删除单条历史（kid 形如 archive_<aid>）。 */
    @FormUrlEncoded
    @POST("x/v2/history/delete")
    suspend fun historyDelete(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    // ---- 稍后再看 ----
    /** 加入稍后再看。 */
    @FormUrlEncoded
    @POST("x/v2/history/toview/add")
    suspend fun watchLaterAdd(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 从稍后再看移除。 */
    @FormUrlEncoded
    @POST("x/v2/history/toview/del")
    suspend fun watchLaterDel(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 稍后再看列表（一次返回全部）。 */
    @GET("x/v2/history/toview")
    suspend fun watchLaterList(): BiliResponse<WatchLaterData>

    // ---- 番剧 / PGC ----
    /** 番剧索引：[seasonType] 1=番剧 2=电影 3=纪录片 4=国创 5=电视剧 7=综艺。[page] 从 1 开始。 */
    @GET("pgc/season/index/result")
    suspend fun bangumiIndex(
        @Query("season_type") seasonType: Int = 1,
        @Query("st") st: Int = 1,
        @Query("index_type") indexType: Int = 0,
        @Query("type") type: Int = 1,
        @Query("page") page: Int,
        @Query("pagesize") pageSize: Int = 20,
    ): BiliResponse<BangumiIndexData>

    /**
     * 番剧详情（含分集列表，分集带 bvid，可交给普通播放链路）。
     * 注意：该接口业务数据直接挂在顶层 result 上，需用 [PgcResponse] 解析。
     */
    @GET("pgc/view/web/season")
    suspend fun bangumiSeason(@Query("season_id") seasonId: Long): PgcResponse<BangumiSeasonResult>

    /** 番剧详情：按分集 ep_id 反查所在季度（拿到整季分集列表）。 */
    @GET("pgc/view/web/season")
    suspend fun bangumiSeasonByEp(@Query("ep_id") epId: Long): PgcResponse<BangumiSeasonResult>

    /** 我的追番 / 追剧列表（type：1=追番 2=追剧）。 */
    @GET("x/space/bangumi/follow/list")
    suspend fun myBangumiFollow(
        @Query("vmid") vmid: Long,
        @Query("type") type: Int = 1,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
    ): BiliResponse<MyBangumiData>

    // ---- 直播（独立域名 api.live.bilibili.com）----
    @GET
    suspend fun liveList(@Url url: String): BiliResponse<LiveListData>

    /** 直播一级分区列表（room/v1/area/getList）。 */
    @GET
    suspend fun liveAreaList(@Url url: String): BiliResponse<List<LiveAreaDto>>

    @GET
    suspend fun livePlayUrl(@Url url: String): BiliResponse<PlayUrlData>

    /** 直播播放地址树（xlive/web-room/v2/index/getRoomPlayInfo，WBI 签名）。 */
    @GET
    suspend fun livePlayInfo(@Url url: String): BiliResponse<LiveRoomPlayInfoData>

    /** 直播间最近的历史弹幕（xlive/web-room/v1/dM/gethistory）。 */
    @GET
    suspend fun liveHistoryDanmaku(@Url url: String): BiliResponse<LiveHistoryDanmakuData>

    @GET
    suspend fun liveRoomInfo(@Url url: String): BiliResponse<LiveRoomInfoDto>

    /** 直播间初始化信息（含主播信息 / 封面 / 开播状态）。 */
    @GET
    suspend fun liveRoomInit(@Url url: String): BiliResponse<LiveRoomInitData>

    /** 直播间 H5 轻量信息（getH5InfoByRoom，无需签名，出首屏更快）。 */
    @GET
    suspend fun liveRoomInfoH5(@Url url: String): BiliResponse<LiveRoomH5Data>

    /** 直播弹幕 WebSocket 服务器与鉴权 token（WBI 签名版）。 */
    @GET
    suspend fun liveDanmuInfo(@Url url: String): BiliResponse<LiveDanmuInfoDto>

    /** 发送直播弹幕（msg/send）。 */
    @FormUrlEncoded
    @POST
    suspend fun liveSendDanmaku(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    /** 直播间点赞上报（likeReportV3，WBI 签名 + form）。 */
    @FormUrlEncoded
    @POST
    suspend fun liveLikeReport(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    // ---- 登录（独立域名 passport.bilibili.com）----
    @GET
    suspend fun qrcodeGenerate(@Url url: String): BiliResponse<QrcodeData>

    @GET
    suspend fun qrcodePoll(@Url url: String): BiliResponse<QrcodePollData>

    /** 发送短信验证码（App HD 端，参数带 appkey 签名）。 */
    @FormUrlEncoded
    @POST
    suspend fun smsSend(@Url url: String, @FieldMap params: Map<String, String>): BiliResponse<SmsSendData>

    /** 短信验证码登录（App HD 端，返回 cookie_info）。 */
    @FormUrlEncoded
    @POST
    suspend fun smsLogin(@Url url: String, @FieldMap params: Map<String, String>): BiliResponse<SmsLoginData>

    /** 登录 RSA 公钥。 */
    @GET
    suspend fun webKey(@Url url: String): BiliResponse<WebKeyData>

    // ---- 关注动态 ----
    @GET("x/polymer/web-dynamic/v1/feed/all")
    suspend fun dynamicFeed(
        @Query("type") type: String = "all",
        @Query("offset") offset: String = "",
        @Query("update_baseline") updateBaseline: String = "",
        @Query("features") features: String = DYNAMIC_FEED_FEATURES,
        @Query("timezone_offset") timezoneOffset: Int = -480,
        @Query("platform") platform: String = "web",
        @Query("web_location") webLocation: String = "333.1365",
    ): BiliResponse<DynamicFeedData>

    /** 指定 UP 主的动态（空间页「动态」标签）。 */
    @GET("x/polymer/web-dynamic/v1/feed/space")
    suspend fun spaceDynamicFeed(
        @Query("host_mid") hostMid: Long,
        @Query("offset") offset: String = "",
        @Query("features") features: String = DYNAMIC_FEED_FEATURES,
        @Query("timezone_offset") timezoneOffset: Int = -480,
        @Query("platform") platform: String = "web",
        @Query("web_location") webLocation: String = "333.1387",
    ): BiliResponse<DynamicFeedData>

    /** 话题详情头部（绝对地址：app.bilibili.com）。 */
    @GET
    suspend fun topicDetailTop(@Url url: String): BiliResponse<TopicDetailTopData>

    /** 话题动态流。 */
    @GET("x/polymer/web-dynamic/v1/feed/topic")
    suspend fun topicFeed(
        @Query("topic_id") topicId: Long,
        @Query("sort_by") sortBy: Int = 0,
        @Query("offset") offset: String = "",
        @Query("page_size") pageSize: Int = 20,
        @Query("source") source: String = "Web",
        @Query("features") features: String = DYNAMIC_FEED_FEATURES,
    ): BiliResponse<TopicFeedData>

    // ---- 消息中心 ----
    @GET("x/msgfeed/unread")
    suspend fun msgUnread(
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web",
        @Query("web_location") webLocation: String = "333.1365",
    ): BiliResponse<MsgUnreadData>

    @GET("x/msgfeed/reply")
    suspend fun msgReply(
        @Query("id") id: Long? = null,
        @Query("reply_time") replyTime: Long? = null,
        @Query("platform") platform: String = "web",
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0,
        @Query("web_location") webLocation: String = "333.40164",
    ): BiliResponse<MsgReplyData>

    @GET("x/msgfeed/at")
    suspend fun msgAt(
        @Query("id") id: Long? = null,
        @Query("at_time") atTime: Long? = null,
        @Query("platform") platform: String = "web",
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0,
        @Query("web_location") webLocation: String = "333.40164",
    ): BiliResponse<MsgAtData>

    @GET("x/msgfeed/like")
    suspend fun msgLike(
        @Query("id") id: Long? = null,
        @Query("like_time") likeTime: Long? = null,
        @Query("platform") platform: String = "web",
        @Query("mobi_app") mobiApp: String = "web",
        @Query("build") build: Int = 0,
        @Query("web_location") webLocation: String = "333.40164",
    ): BiliResponse<MsgLikeData>

    /** 系统通知（独立域名 message.bilibili.com，data 直接是数组）。 */
    @GET
    suspend fun sysNotices(@Url url: String): BiliResponse<List<SysNoticeItemDto>>

    /** 点赞详情（「收到的赞」点进某条累计点赞）。 */
    @GET("x/msgfeed/like_detail")
    suspend fun msgLikeDetail(
        @Query("card_id") cardId: Long,
        @Query("pn") pn: Int,
        @Query("last_mid") lastMid: Long = 0,
        @Query("platform") platform: String = "web",
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web",
        @Query("web_location") webLocation: String = "333.40164",
    ): BiliResponse<MsgLikeDetailData>

    /** 删除一条消息。[tp]：0=收到的赞、1=回复我的、2=@我的。 */
    @FormUrlEncoded
    @POST("x/msgfeed/del")
    suspend fun delMsgfeed(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 点赞通知开关（不再通知 / 接收通知）。 */
    @FormUrlEncoded
    @POST("x/msgfeed/notice")
    suspend fun msgSetNotice(@FieldMap body: Map<String, String>): BiliResponse<JsonElement>

    /** 删除系统通知（message.bilibili.com）。 */
    @FormUrlEncoded
    @POST
    suspend fun delSysMsg(
        @Url url: String,
        @Query("mobi_app") mobiApp: String = "android",
        @Query("csrf") csrf: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    /** 系统通知已读上报（message.bilibili.com）。 */
    @GET
    suspend fun msgSysUpdateCursor(
        @Url url: String,
        @Query("csrf") csrf: String,
        @Query("cursor") cursor: Long,
        @Query("has_up") hasUp: Int = 0,
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web",
    ): BiliResponse<JsonElement>

    // ---- 私信（api.vc.bilibili.com，全域名经 @Url 传入）----
    @GET
    suspend fun privateSessions(
        @Url url: String,
        @Query("session_type") sessionType: Int = 4,
        @Query("group_fold") groupFold: Int = 1,
        @Query("unfollow_fold") unfollowFold: Int = 0,
        @Query("sort_rule") sortRule: Int = 2,
        @Query("size") size: Int = 20,
        @Query("build") build: Int = 0,
        @Query("mobi_app") mobiApp: String = "web",
        @Query("web_location") webLocation: String = "333.999",
        @Query("pn") pn: Int = 1,
        @Query("end_ts") endTs: Long = 0,
    ): BiliResponse<SessionListData>

    @GET
    suspend fun privateMessages(
        @Url url: String,
        @Query("talker_id") talkerId: Long,
        @Query("session_type") sessionType: Int = 1,
        @Query("size") size: Int = 20,
        @Query("begin_seqno") beginSeqno: Long = 0,
        @Query("end_seqno") endSeqno: Long = 0,
        @Query("mobi_app") mobiApp: String = "web",
    ): BiliResponse<MessageHistoryData>

    @FormUrlEncoded
    @POST
    suspend fun sendPrivateMsg(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<SendMessageData>

    @FormUrlEncoded
    @POST
    suspend fun updatePrivateAck(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    /** 会话置顶 / 取消置顶。 */
    @FormUrlEncoded
    @POST
    suspend fun privateSetTop(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    /** 删除会话。 */
    @FormUrlEncoded
    @POST
    suspend fun privateRemoveSession(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    /** 开启 / 关闭免打扰。 */
    @FormUrlEncoded
    @POST
    suspend fun privateSetDnd(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    /** 移入拦截 / 移出拦截。 */
    @FormUrlEncoded
    @POST
    suspend fun privateUpdateIntercept(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    /** 拦截区全部已读。 */
    @FormUrlEncoded
    @POST
    suspend fun privateBatchDustbinAck(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    /** 清空拦截列表。 */
    @FormUrlEncoded
    @POST
    suspend fun privateBatchRmDustbin(
        @Url url: String,
        @FieldMap body: Map<String, String>,
    ): BiliResponse<JsonElement>

    // ---- 发布动态 ----
    /** 发布图文 / 富文本动态。 */
    @POST("x/dynamic/feed/create/dyn")
    suspend fun createFeedDynamic(
        @Query("csrf") csrf: String,
        @Query("platform") platform: String = "web",
        @Query("x-bili-device-req-json") deviceRequestJson: String = "{\"platform\":\"web\",\"device\":\"pc\"}",
        @Query("x-bili-web-req-json") webRequestJson: String = "{\"spm_id\":\"333.999\"}",
        @Body body: DynamicCreateFeedRequest,
    ): BiliResponse<DynamicCreateFeedData>

    /** 发布话题搜索。 */
    @GET("x/topic/pub/search")
    suspend fun searchTopics(
        @Query("keywords") keywords: String? = null,
        @Query("page_size") pageSize: Int = 20,
        @Query("page_num") pageNum: Int = 1,
    ): BiliResponse<TopicSearchData>

    /** 创建投票。 */
    @POST("x/vote/create")
    suspend fun createVote(
        @Query("csrf") csrf: String,
        @Body body: DynamicCreateVoteRequest,
    ): BiliResponse<DynamicCreateVoteData>

    // ---- 匿名指纹 ----
    @GET("x/frontend/finger/spi")
    suspend fun fingerSpi(): BiliResponse<FingerData>
}

/** 动态 Feed 特性开关（与 BiliPai 一致，含 opus 风格）。 */
const val DYNAMIC_FEED_FEATURES =
    "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,decorationCard," +
        "onlyfansAssetsV2,onlyfansQaCard,forwardListHidden,ugcDelete"

@Serializable
data class RelationData(val attribute: Int = 0)

/** 已投币数（x/web-interface/archive/coins）。 */
@Serializable
data class CoinCountData(val multiply: Int = 0)

@Serializable
data class RelationStatData(val follower: Long = 0)

/** UP 主获赞 / 播放统计（x/space/upstat）。 */
@Serializable
data class UpStatData(val likes: Long = 0, val archive: UpStatArchiveData? = null)

@Serializable
data class UpStatArchiveData(val view: Long = 0)
