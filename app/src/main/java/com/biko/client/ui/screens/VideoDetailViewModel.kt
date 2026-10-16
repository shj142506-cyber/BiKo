package com.biko.client.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biko.client.data.AppGraph
import com.biko.client.data.addHistory
import com.biko.client.data.BiliException
import com.biko.client.data.bangumiEpisodesOfBvid
import com.biko.client.data.DownloadManager
import com.biko.client.data.coin
import com.biko.client.data.coinBalance
import com.biko.client.data.coinCount
import com.biko.client.data.comments
import com.biko.client.data.danmaku
import com.biko.client.data.deleteReply
import com.biko.client.data.emotePackages
import com.biko.client.data.favourite
import com.biko.client.data.feedRecommend
import com.biko.client.data.follow
import com.biko.client.data.hasLiked
import com.biko.client.data.hateReply
import com.biko.client.data.isFavoured
import com.biko.client.data.isFollowing
import com.biko.client.data.like
import com.biko.client.data.likeReply
import com.biko.client.data.playInfo
import com.biko.client.data.progressivePlayUrl
import com.biko.client.data.refreshProfile
import com.biko.client.data.related
import com.biko.client.data.replyAdd
import com.biko.client.data.replyDetail
import com.biko.client.data.reportHistory
import com.biko.client.data.reportReply
import com.biko.client.data.search
import com.biko.client.data.searchMentionUsers
import com.biko.client.data.sendDanmaku
import com.biko.client.data.SpecialDanmaku
import com.biko.client.data.specialDanmaku
import com.biko.client.data.upFans
import com.biko.client.data.uploadCommentImage
import com.biko.client.data.videoDetailWithCollection
import com.biko.client.data.videoNotes
import com.biko.client.data.watchLaterAdd
import com.biko.client.data.local.PlayerSettings
import com.biko.client.data.model.Comment
import com.biko.client.data.model.CommentPicture
import com.biko.client.data.model.DanmakuItem
import com.biko.client.data.model.EmotePackage
import com.biko.client.data.model.MentionUser
import com.biko.client.data.model.PlayInfo
import com.biko.client.data.model.Video
import com.biko.client.data.model.VideoNote
import com.biko.client.ui.components.DanmakuSettings
import com.biko.client.ui.components.VideoAspect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单个视频最多可投的硬币数。 */
internal const val COIN_LIMIT = 2

/** 硬币余额加载失败的哨兵值。 */
internal const val COIN_BALANCE_FAILED = -1.0

/** 硬币余额：未登录。 */
internal const val COIN_BALANCE_NO_LOGIN = -3.0

/** 竖屏评论半屏弹层占屏幕高度的比例（固定 60%，画面在剩下的 40% 里按比例显示）。 */
internal const val PORTRAIT_SHEET_FRACTION = 0.6f

/** 评论排序：与 B 站接口 mode 对齐（3=按热度，2=按时间）。 */
internal const val COMMENT_SORT_HOT = 3
internal const val COMMENT_SORT_NEWEST = 2

data class VideoDetailState(
    val video: Video? = null,
    val playInfo: PlayInfo? = null,
    val qn: Int = 80,
    val qualityPicked: Boolean = false,
    val audioIndex: Int = 0,
    val speed: Float = 1f,
    val aspect: VideoAspect = VideoAspect.FIT,
    /** 后台听视频开关：开启后退到后台继续播放声音。 */
    val backgroundAudio: Boolean = false,
    /** 音频模式：只播放音频轨，视频画面替换为封面（省流量、省电）。 */
    val audioOnly: Boolean = false,
    val danmaku: List<DanmakuItem> = emptyList(),
    /** 特殊弹幕：Mode 7 高级弹幕 + Mode 9 BAS 代码弹幕。 */
    val specialDanmaku: SpecialDanmaku = SpecialDanmaku(),
    val danmakuSettings: DanmakuSettings = DanmakuSettings(),
    val resumeMs: Long = 0L,
    val liked: Boolean = false,
    /** 我在该视频已投的硬币数（0/1/2）。 */
    val coinCount: Int = 0,
    /** 投币弹窗是否显示。 */
    val coinDialog: Boolean = false,
    /** 硬币余额：null=加载中，-1=加载失败，-3=未登录。 */
    val coinBalance: Double? = null,
    val faved: Boolean = false,
    val following: Boolean = false,
    val upFans: Long = -1,
    val comments: List<Comment> = emptyList(),
    val commentEnd: Boolean = false,
    /** 评论拉取失败（网络/风控）：列表据此给出「点击重试」，避免永远停在「正在加载评论…」。 */
    val commentFailed: Boolean = false,
    /** 评论排序：3=热门（默认），2=最新。 */
    val commentSort: Int = COMMENT_SORT_HOT,
    /** 正在回复的评论；null 表示普通评论模式。 */
    val replyTarget: Comment? = null,
    /** 回复目标的楼主 rpid（回复主楼时等于 replyTarget.rpid）。 */
    val replyRoot: Long = 0,
    /** 楼中楼弹层：楼主评论 + 回复列表 + 分页状态。 */
    val subRoot: Comment? = null,
    val subReplies: List<Comment> = emptyList(),
    val subEnd: Boolean = false,
    val subLoading: Boolean = false,
    /** 评论表情面板（懒加载）。 */
    val emotePackages: List<EmotePackage> = emptyList(),
    /** 表情面板是否正在加载：用于区分「加载中」与「加载失败 / 为空」。 */
    val emoteLoading: Boolean = false,
    /** 表情面板加载失败（未登录或网络异常）：面板据此给出重试提示，避免无限转圈。 */
    val emoteFailed: Boolean = false,
    /** 评论/回复正在发送（含图片上传）。 */
    val sendingReply: Boolean = false,
    /** 竖屏模式上下滑的视频列表（第 0 个是进来的视频，后面是推荐流）。 */
    val portraitFeed: List<Video> = emptyList(),
    /** 相关推荐视频（简介页底部，信息流可无限翻页）。 */
    val related: List<Video> = emptyList(),
    val relatedEnd: Boolean = false,
    val relatedLoadingMore: Boolean = false,
    /** 番剧「选集」：剧名 + 分集列表（普通视频均为空，不展示选集区块）。 */
    val seasonTitle: String = "",
    val episodes: List<Video> = emptyList(),
    /** UP 主视频合集：合集名 + 合集内稿件（随 view 接口一同下发）。 */
    val collectionTitle: String = "",
    val collectionEpisodes: List<Video> = emptyList(),
    /** 视频笔记（公开笔记列表，来自 x/note/publish/list/archive）。 */
    val notes: List<VideoNote> = emptyList(),
    val tab: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class VideoDetailViewModel : ViewModel() {
    private val repo = AppGraph.repository
    private val session = AppGraph.session
    private val _state = MutableStateFlow(VideoDetailState())
    val state = _state.asStateFlow()

    private var bvid = ""
    private var commentNext = 0L
    private var commentLoading = false

    /** 评论请求代际：切换视频/排序时自增，用来丢弃上一批迟到的响应。 */
    private var commentGen = 0

    /** 相关推荐的关键词与当前页码（信息流翻页用）。 */
    private var relatedKeyword = ""
    private var relatedPage = 1

    /** 楼中楼当前页码。 */
    private var subPage = 1

    /** 上次手动选择的清晰度；0 表示未选过，按登录状态取默认。 */
    private var preferredQuality = 0

    /** 是否已经因为分片 403 降级过一次线路。 */
    private var dashFallbackTried = false

    /** 「不感兴趣」本地名单：命中的相关推荐不再展示。 */
    private val dislike = AppGraph.dislike
    private var disliked: Set<String> = emptySet()

    init {
        // 载入「不感兴趣」名单，并把已命中的相关推荐从列表里去掉
        viewModelScope.launch {
            disliked = runCatching { dislike.load() }.getOrDefault(emptySet())
            if (disliked.isNotEmpty()) {
                _state.update { s -> s.copy(related = s.related.filterNot { it.bvid in disliked }) }
            }
        }
        // 恢复上次保存的播放器设置（速度 / 清晰度 / 画面比例 / 弹幕），跨视频生效
        viewModelScope.launch {
            val saved = runCatching { AppGraph.playerSettings.current() }.getOrNull() ?: return@launch
            preferredQuality = saved.quality
            _state.update {
                it.copy(
                    speed = saved.speed,
                    aspect = VideoAspect.entries.firstOrNull { a -> a.name == saved.aspect }
                        ?: VideoAspect.FIT,
                    backgroundAudio = saved.backgroundAudio,
                    danmakuSettings = DanmakuSettings(
                        enabled = saved.danmakuEnabled,
                        fontScale = saved.danmakuFontScale,
                        alpha = saved.danmakuAlpha,
                        speedFactor = saved.danmakuSpeedFactor,
                        displayArea = saved.danmakuDisplayArea,
                        blockWords = saved.danmakuBlockWords,
                    ),
                )
            }
        }
    }

    /** 把当前设置写回本地，供下一个视频使用。 */
    private fun persistSettings() {
        val s = _state.value
        AppGraph.fireAndForget {
            AppGraph.playerSettings.save(
                PlayerSettings(
                    speed = s.speed,
                    quality = if (s.qualityPicked) s.qn else 0,
                    aspect = s.aspect.name,
                    danmakuEnabled = s.danmakuSettings.enabled,
                    danmakuFontScale = s.danmakuSettings.fontScale,
                    danmakuAlpha = s.danmakuSettings.alpha,
                    danmakuSpeedFactor = s.danmakuSettings.speedFactor,
                    danmakuDisplayArea = s.danmakuSettings.displayArea,
                    danmakuBlockWords = s.danmakuSettings.blockWords,
                    backgroundAudio = s.backgroundAudio,
                ),
            )
        }
    }

    fun load(bvid: String, historyType: String = "video") {
        if (this.bvid == bvid) return
        this.bvid = bvid
        dashFallbackTried = false
        // 换视频时清掉上一个视频的评论，并作废其未完成的请求，避免残留/新首屏请求被丢
        commentGen++
        commentNext = 0L
        commentLoading = false
        _state.update { it.copy(comments = emptyList(), commentEnd = false, commentFailed = false) }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true, error = null, coinCount = 0, coinDialog = false, coinBalance = null,
                    collectionTitle = "", collectionEpisodes = emptyList(), notes = emptyList(),
                )
            }
            // 已有离线缓存时优先用本地播放信息
            val localInfo = runCatching { DownloadManager.localPlayInfo(bvid) }.getOrNull()
            try {
                val (video, collection) = repo.videoDetailWithCollection(bvid)
                val qn = if (preferredQuality > 0) {
                    preferredQuality
                } else if (session.isLogin()) 80 else 32
                val resume = runCatching { AppGraph.playback.position(video.cid) }.getOrDefault(0L)
                _state.update {
                    it.copy(
                        video = video, qn = qn, resumeMs = resume,
                        collectionTitle = collection?.first.orEmpty(),
                        collectionEpisodes = collection?.second.orEmpty(),
                    )
                }
                repo.addHistory(video.copy(type = historyType))
                val info = localInfo ?: resolvePlayInfo(video.aid, video.cid, qn, video.duration)
                _state.update { it.copy(playInfo = info) }
                loadComments(video.aid, 0)
                loadTriple(video)
                loadDanmaku(video)
                loadRelated(video)
                loadEpisodes(video)
                loadNotes(video.aid)
            } catch (e: Exception) {
                // 离线兜底：无网络但有离线缓存时，用缓存里的元数据直接播放
                val task = DownloadManager.taskOf(bvid)
                if (localInfo != null && task != null) {
                    val video = Video(
                        aid = task.aid,
                        bvid = task.bvid,
                        cid = task.cid,
                        title = task.title,
                        pic = task.cover,
                        duration = (task.durationMs / 1000L).toInt(),
                        upName = task.upName,
                    )
                    val resume = runCatching { AppGraph.playback.position(video.cid) }.getOrDefault(0L)
                    _state.update {
                        it.copy(video = video, playInfo = localInfo, qn = task.qn, resumeMs = resume)
                    }
                } else {
                    _state.update { it.copy(error = e.message ?: "视频加载失败") }
                }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    /** 把当前视频加入离线缓存（按当前清晰度）。 */
    fun download() {
        val video = _state.value.video ?: return
        val qn = _state.value.qn
        DownloadManager.enqueue(video, qn)
    }

    private fun loadDanmaku(video: Video) {
        viewModelScope.launch {
            // 弹幕按 6 分钟一段拉取，需要视频时长才知道要拉几段
            runCatching { repo.danmaku(video.cid, video.duration) }
                .onSuccess { list -> _state.update { it.copy(danmaku = list) } }
        }
        viewModelScope.launch {
            // 特殊弹幕（Mode 7 高级 / Mode 9 BAS）：先取 special_dms 文件地址再下载解析
            runCatching { repo.specialDanmaku(video.cid, video.aid) }
                .onSuccess { special -> _state.update { it.copy(specialDanmaku = special) } }
        }
    }

    /** 相关推荐（详情页底部）：和分区一样走关键词搜索，可无限翻页。 */
    private fun loadRelated(video: Video) {
        // 关键词取视频分区名（与分区频道搜索同一套逻辑），分区为空时退而用标题
        relatedKeyword = video.category.ifBlank { video.title }
        relatedPage = 1
        viewModelScope.launch {
            _state.update { it.copy(related = emptyList(), relatedEnd = false) }
            val list = runCatching { repo.search(relatedKeyword, 1) }
                .getOrDefault(emptyList())
                .filter { it.bvid.isNotEmpty() && it.bvid != video.bvid && it.bvid !in disliked }
            if (list.isEmpty()) {
                // 关键词搜不到内容时回退到原相关推荐接口，至少不空白
                val fallback = runCatching { repo.related(video.bvid) }.getOrDefault(emptyList())
                    .filterNot { it.bvid in disliked }
                _state.update { it.copy(related = fallback, relatedEnd = true) }
            } else {
                _state.update { it.copy(related = list, relatedEnd = false) }
            }
        }
    }

    /** 相关推荐翻页（信息流滚到底自动调用，内部防重入）。 */
    fun loadMoreRelated() {
        val video = _state.value.video ?: return
        if (_state.value.relatedEnd || _state.value.relatedLoadingMore) return
        val next = relatedPage + 1
        _state.update { it.copy(relatedLoadingMore = true) }
        viewModelScope.launch {
            val more = runCatching { repo.search(relatedKeyword, next) }.getOrDefault(emptyList())
            if (more.isEmpty()) {
                _state.update { it.copy(relatedLoadingMore = false, relatedEnd = true) }
            } else {
                relatedPage = next
                _state.update { s ->
                    val seen = s.related.map { it.bvid }.toSet()
                    s.copy(
                        related = s.related + more.filter {
                            it.bvid.isNotEmpty() && it.bvid != video.bvid &&
                                it.bvid !in seen && it.bvid !in disliked
                        },
                        relatedLoadingMore = false,
                    )
                }
            }
        }
    }

    /** 相关推荐「稍后再看」。 */
    fun watchLater(video: Video) {
        if (video.aid <= 0) return
        viewModelScope.launch { runCatching { repo.watchLaterAdd(video.aid) } }
    }

    /** 相关推荐「不感兴趣」：本地记入名单并立即从列表移除。 */
    fun notInterested(video: Video) {
        if (video.bvid.isEmpty()) return
        disliked = disliked + video.bvid
        _state.update { s -> s.copy(related = s.related.filterNot { it.bvid == video.bvid }) }
        viewModelScope.launch { runCatching { dislike.add(video.bvid) } }
    }

    /** 番剧「选集」：从 bvid 反查整季分集；非番剧（无 redirect_url）不展示该区块。 */
    private fun loadEpisodes(video: Video) {
        _state.update { it.copy(seasonTitle = "", episodes = emptyList()) }
        viewModelScope.launch {
            val res = runCatching { repo.bangumiEpisodesOfBvid(video.bvid) }.getOrNull() ?: return@launch
            _state.update { it.copy(seasonTitle = res.first, episodes = res.second) }
        }
    }

    /** 视频笔记（公开笔记列表）：无笔记或未开放时为空，不展示该区块。 */
    private fun loadNotes(oid: Long) {
        viewModelScope.launch {
            val list = runCatching { repo.videoNotes(oid) }.getOrDefault(emptyList())
            _state.update { it.copy(notes = list) }
        }
    }

    private suspend fun loadTriple(video: Video) {
        // 粉丝数是公开数据，未登录也应展示
        val fans = if (video.upMid > 0) runCatching { repo.upFans(video.upMid) }.getOrDefault(-1L) else -1L
        if (!session.isLogin()) {
            _state.update { it.copy(upFans = fans) }
            return
        }
        val liked = runCatching { repo.hasLiked(video.aid) }.getOrDefault(false)
        val faved = repo.isFavoured(video.aid)
        // 已投币数取自服务端（archive/coins），保证进入页面时图标状态与 B 站一致
        val coinCount = runCatching { repo.coinCount(video.aid) }.getOrDefault(0)
        val following = video.upMid > 0 && runCatching { repo.isFollowing(video.upMid) }.getOrDefault(false)
        _state.update {
            it.copy(liked = liked, coinCount = coinCount, faved = faved, following = following, upFans = fans)
        }
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(tab = index) }
    }

    // ---- 竖屏模式的上下滑列表 ----

    private var portraitFreshIdx = 0
    private var portraitLoadingMore = false

    /** 准备竖屏上下滑列表：当前视频在第一位，后面接推荐流。 */
    fun startPortraitFeed() {
        if (_state.value.portraitFeed.isNotEmpty()) return
        val current = _state.value.video ?: return
        _state.update { it.copy(portraitFeed = listOf(current)) }
        portraitFreshIdx = 1
        loadMorePortraitFeed()
    }

    /** 滑到接近末尾时继续追加推荐（竖屏模式可以一直往下刷）。 */
    fun loadMorePortraitFeed() {
        if (portraitLoadingMore) return
        portraitLoadingMore = true
        viewModelScope.launch {
            portraitFreshIdx += 1
            runCatching { repo.feedRecommend(portraitFreshIdx) }
                .onSuccess { list ->
                    _state.update { s ->
                        val seen = s.portraitFeed.map { it.bvid }.toSet()
                        s.copy(
                            portraitFeed = s.portraitFeed +
                                list.filter { it.bvid.isNotEmpty() && it.bvid !in seen },
                        )
                    }
                }
            portraitLoadingMore = false
        }
    }

    /** 切换清晰度：重新拉取播放地址，并记住这次选择。 */
    fun setQuality(qn: Int) {
        val video = _state.value.video ?: return
        if (_state.value.qn == qn) return
        preferredQuality = qn
        dashFallbackTried = false
        _state.update { it.copy(qn = qn, qualityPicked = true) }
        persistSettings()
        viewModelScope.launch {
            runCatching { resolvePlayInfo(video.aid, video.cid, qn, video.duration) }
                .onSuccess { info -> _state.update { it.copy(playInfo = info) } }
                .onFailure { _state.update { it.copy(message = it.message) } }
        }
    }

    /** DASH 响应偶尔不带 duration，此时用视频详情里的时长补上，否则 MPD 时长会错。 */
    private suspend fun resolvePlayInfo(aid: Long, cid: Long, qn: Int, durationSec: Int): PlayInfo {
        val info = repo.playInfo(aid, cid, qn)
        return if (info.durationMs > 0) info
        else info.copy(durationMs = durationSec.toLong() * 1000L)
    }

    /**
     * DASH 分片被 CDN 拒（403 / BAD_HTTP_STATUS）时，降级成 html5 整段 MP4，
     * 每个视频只降级一次，避免循环。
     */
    fun onPlaybackError(@Suppress("UNUSED_PARAMETER") codeName: String) {
        if (dashFallbackTried) return
        val video = _state.value.video ?: return
        if (_state.value.playInfo?.isDash != true) return
        dashFallbackTried = true
        viewModelScope.launch {
            val url = repo.progressivePlayUrl(video.aid, video.cid) ?: return@launch
            _state.update {
                it.copy(
                    playInfo = PlayInfo(quality = 32, acceptQuality = listOf(32), durl = url),
                    message = "已切换到兼容线路",
                )
            }
        }
    }

    fun setAudio(index: Int) = _state.update { it.copy(audioIndex = index) }

    fun setDanmakuSettings(settings: DanmakuSettings) {
        _state.update { it.copy(danmakuSettings = settings) }
        persistSettings()
    }

    fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        persistSettings()
    }

    fun setAspect(aspect: VideoAspect) {
        _state.update { it.copy(aspect = aspect) }
        persistSettings()
    }

    /** 设置面板「后台听视频」：开启后退到后台或息屏继续播放声音。 */
    fun setBackgroundAudio(enabled: Boolean) {
        _state.update { it.copy(backgroundAudio = enabled) }
        persistSettings()
    }

    /** 设置面板「音频模式」：只播放音频轨（视频画面替换为封面）。 */
    fun setAudioOnly(enabled: Boolean) {
        _state.update { it.copy(audioOnly = enabled) }
    }

    /** 播放中的周期进度：写入本地播放记忆 + 上报服务端历史。 */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        val video = _state.value.video ?: return
        AppGraph.fireAndForget { AppGraph.playback.save(video.cid, positionMs, durationMs) }
        reportProgress((positionMs / 1000).toInt())
    }

    /** 退出播放页 / 切后台时上报观看进度（对应原版 onPause 的 reportHistory）。 */
    fun reportProgress(seconds: Int) {
        val video = _state.value.video ?: return
        AppGraph.fireAndForget { AppGraph.repository.reportHistory(video.aid, video.cid, seconds) }
    }

    fun toggleLike() {
        val video = _state.value.video ?: return
        if (!session.isLogin()) {
            _state.update { it.copy(message = "请先登录") }
            return
        }
        val target = !_state.value.liked
        viewModelScope.launch {
            runCatching { repo.like(video.aid, target) }
                .onSuccess { _state.update { it.copy(liked = target) } }
                .onFailure { _state.update { it.copy(message = it.message) } }
        }
    }

    /** 点投币：已投满只提示；否则弹「投币」弹窗并异步加载硬币余额（对齐 BiliPai）。 */
    fun openCoinDialog() {
        if (!session.isLogin()) {
            _state.update { it.copy(message = "请先登录") }
            return
        }
        if (_state.value.coinCount >= COIN_LIMIT) {
            _state.update { it.copy(message = "已投满 $COIN_LIMIT 个硬币") }
            return
        }
        _state.update { it.copy(coinDialog = true, coinBalance = null) }
        viewModelScope.launch {
            val balance = runCatching { repo.coinBalance() }.getOrElse { e ->
                if ((e as? BiliException)?.code == -101) COIN_BALANCE_NO_LOGIN else COIN_BALANCE_FAILED
            }
            // 弹窗期间用户可能已关闭，避免又把余额写回去
            _state.update { s -> if (s.coinDialog) s.copy(coinBalance = balance) else s }
        }
    }

    fun dismissCoinDialog() = _state.update { it.copy(coinDialog = false) }

    /** 确认投币：[count] 枚，[alsoLike] 同时点赞。 */
    fun doCoin(count: Int, alsoLike: Boolean) {
        val video = _state.value.video ?: return
        _state.update { it.copy(coinDialog = false) }
        viewModelScope.launch {
            runCatching { repo.coin(video.aid, count, alsoLike) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            coinCount = (s.coinCount + count).coerceAtMost(COIN_LIMIT),
                            liked = s.liked || alsoLike,
                            message = "投币成功",
                        )
                    }
                    // 硬币余额已经变了，刷新资料，否则「我的」页的硬币数还是旧的
                    runCatching { repo.refreshProfile() }
                }
                .onFailure { _state.update { it.copy(message = it.message) } }
        }
    }

    fun toggleFav() {
        val video = _state.value.video ?: return
        if (!session.isLogin()) {
            _state.update { it.copy(message = "请先登录") }
            return
        }
        val target = !_state.value.faved
        // 乐观更新：星标立即变色、计数 ±1，失败回滚
        _state.update { s ->
            s.copy(
                faved = target,
                video = s.video?.copy(
                    favorites = (s.video.favorites + if (target) 1 else -1).coerceAtLeast(0),
                ),
            )
        }
        viewModelScope.launch {
            runCatching { repo.favourite(video.aid, target) }
                .onSuccess {
                    _state.update { it.copy(message = if (target) "已收藏" else "已取消收藏") }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(message = e.message, faved = !target, video = it.video?.copy(favorites = video.favorites))
                    }
                }
        }
    }

    fun toggleFollow() {
        val video = _state.value.video ?: return
        if (!session.isLogin()) {
            _state.update { it.copy(message = "请先登录") }
            return
        }
        if (video.upMid <= 0) return
        val target = !_state.value.following
        viewModelScope.launch {
            runCatching { repo.follow(video.upMid, target) }
                .onSuccess { _state.update { it.copy(following = target) } }
                .onFailure { _state.update { it.copy(message = it.message) } }
        }
    }

    private fun loadComments(oid: Long, next: Long) {
        if (commentLoading) return
        commentLoading = true
        val gen = commentGen
        val upMid = _state.value.video?.upMid ?: 0L
        val sort = _state.value.commentSort
        viewModelScope.launch {
            runCatching { repo.comments(oid, next, sort, upMid) }
                .onSuccess { page ->
                    // 期间换过视频/排序，丢弃这批迟到的响应
                    if (gen != commentGen) return@onSuccess
                    _state.update {
                        it.copy(
                            comments = if (next == 0L) {
                                page.comments
                            } else {
                                (it.comments + page.comments).distinctBy { c -> c.rpid }
                            },
                            commentEnd = page.isEnd,
                            commentFailed = false,
                        )
                    }
                    commentNext = page.next
                }
                .onFailure {
                    if (gen == commentGen) _state.update { it.copy(commentFailed = true) }
                }
            if (gen == commentGen) commentLoading = false
        }
    }

    fun loadMoreComments() {
        val video = _state.value.video ?: return
        // 上一次失败过：先复位标记再重试
        if (_state.value.commentFailed) _state.update { it.copy(commentFailed = false) }
        loadComments(video.aid, commentNext)
    }

    /** 切换评论排序（热门 / 最新），切换后重新拉第一页。 */
    fun setCommentSort(sort: Int) {
        if (_state.value.commentSort == sort) return
        _state.update { it.copy(commentSort = sort, comments = emptyList(), commentEnd = false, commentFailed = false) }
        commentNext = 0
        commentGen++
        commentLoading = false
        val video = _state.value.video ?: return
        loadComments(video.aid, 0)
    }

    /** 举报评论：[reason] 为 B 站举报原因码。 */
    fun reportReply(comment: Comment, reason: Int) {
        val video = _state.value.video ?: return
        viewModelScope.launch {
            runCatching { repo.reportReply(video.aid, comment.rpid, reason) }
                .onSuccess { _state.update { it.copy(message = "举报已提交") } }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    /** 删除自己发的评论（先本地移除，失败再恢复）。 */
    fun deleteReply(comment: Comment) {
        val video = _state.value.video ?: return
        val old = _state.value.comments
        _state.update { it.copy(comments = old.filterNot { c -> c.rpid == comment.rpid }) }
        viewModelScope.launch {
            runCatching { repo.deleteReply(video.aid, comment.rpid) }
                .onSuccess { _state.update { it.copy(message = "评论已删除") } }
                .onFailure { e ->
                    _state.update { it.copy(comments = old, message = e.message) }
                }
        }
    }

    fun sendReply(
        message: String,
        imageUris: List<Uri> = emptyList(),
        syncToDynamic: Boolean = false,
    ) {
        val video = _state.value.video ?: return
        if (message.isBlank() && imageUris.isEmpty()) return
        if (!session.isLogin()) {
            _state.update { it.copy(message = "请先登录") }
            return
        }
        val target = _state.value.replyTarget
        val root = _state.value.replyRoot
        // 回复评论时自动补上 @对方：B 站按正文里的「@名字」解析 mention
        val text = if (target != null && message.isNotBlank() &&
            !message.startsWith("@${target.userName}")
        ) {
            "@${target.userName} $message"
        } else {
            message
        }
        viewModelScope.launch {
            _state.update { it.copy(sendingReply = true) }
            runCatching {
                // 先上传图片（若有），再发评论
                val pictures = uploadPictures(imageUris)
                val rpid = repo.replyAdd(
                    video.aid, text, root, target?.rpid ?: 0,
                    pictures = pictures, syncToDynamic = syncToDynamic && target == null,
                )
                pictures to rpid
            }
                .onSuccess { (pictures, rpid) ->
                    // 新评论按热度排序沉底看不见，直接把自己的评论插到列表顶部
                    val profile = session.userProfile()
                    val mine = Comment(
                        rpid = rpid,
                        mid = session.mid().toLongOrNull() ?: 0,
                        userName = profile.uname,
                        avatar = profile.face,
                        level = profile.level,
                        message = text,
                        ctime = System.currentTimeMillis() / 1000,
                        isMine = true,
                        pictures = pictures,
                    )
                    _state.update { s ->
                        if (target == null) {
                            s.copy(
                                message = if (syncToDynamic) "评论已发布并转发到动态" else "评论已发布",
                                comments = listOf(mine) + s.comments,
                                replyTarget = null, replyRoot = 0,
                            )
                        } else {
                            // 回复评论：楼中楼开着就直接上屏，同时给楼主回复数 +1
                            val bump: (Comment) -> Comment = {
                                if (it.rpid == root) it.copy(rcount = it.rcount + 1) else it
                            }
                            s.copy(
                                message = "回复已发布",
                                replyTarget = null, replyRoot = 0,
                                comments = s.comments.map(bump),
                                subRoot = s.subRoot?.let(bump),
                                subReplies = if (s.subRoot?.rpid == root) {
                                    listOf(mine) + s.subReplies
                                } else {
                                    s.subReplies
                                },
                            )
                        }
                    }
                }
                .onFailure { _state.update { it.copy(message = it.message) } }
            _state.update { it.copy(sendingReply = false) }
        }
    }

    /** 上传待发评论的配图（按顺序逐张上传），返回可用于评论负载的图片信息。 */
    private suspend fun uploadPictures(uris: List<Uri>): List<CommentPicture> {
        if (uris.isEmpty()) return emptyList()
        val resolver = AppGraph.appContext.contentResolver
        return uris.map { uri ->
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val name = queryDisplayName(resolver, uri)
                ?: "comment_${System.currentTimeMillis()}.jpg"
            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw BiliException(-1, "无法读取图片", "上传图片")
            }
            repo.uploadCommentImage(bytes, name, mime)
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()

    /** 懒加载评论表情面板（成功后缓存；失败时置位，交由面板提示重试）。 */
    fun loadEmotes() {
        if (_state.value.emoteLoading || _state.value.emotePackages.isNotEmpty()) return
        _state.update { it.copy(emoteLoading = true, emoteFailed = false) }
        viewModelScope.launch {
            runCatching { repo.emotePackages() }
                .onSuccess { list ->
                    _state.update {
                        it.copy(emotePackages = list, emoteLoading = false, emoteFailed = list.isEmpty())
                    }
                }
                .onFailure {
                    _state.update { it.copy(emoteLoading = false, emoteFailed = true) }
                }
        }
    }

    /** @ 提及用户搜索（供输入框 @ 面板调用）。 */
    suspend fun searchMentions(keyword: String): List<MentionUser> =
        runCatching { repo.searchMentionUsers(keyword) }.getOrDefault(emptyList())

    /** 设置回复目标；[root] 为楼主 rpid（从楼中楼里回复时传入）。 */
    fun setReplyTarget(target: Comment?, root: Long = 0) {
        _state.update {
            it.copy(
                replyTarget = target,
                replyRoot = if (target == null) 0 else if (root > 0) root else target.rpid,
            )
        }
    }

    /** 打开楼中楼弹层并加载第一页。 */
    fun openSubReplies(comment: Comment) {
        _state.update {
            it.copy(subRoot = comment, subReplies = emptyList(), subEnd = false, subLoading = false)
        }
        subPage = 1
        loadSubReplies()
    }

    fun closeSubReplies() {
        _state.update { it.copy(subRoot = null, subReplies = emptyList()) }
    }

    /** 楼中楼翻页（滚到底自动调用，内部防重入）。 */
    fun loadSubReplies() {
        val video = _state.value.video ?: return
        val root = _state.value.subRoot ?: return
        if (_state.value.subLoading || _state.value.subEnd) return
        _state.update { it.copy(subLoading = true) }
        viewModelScope.launch {
            runCatching { repo.replyDetail(video.aid, root.rpid, subPage, video.upMid) }
                .onSuccess { (list, isEnd) ->
                    subPage++
                    _state.update {
                        it.copy(subReplies = it.subReplies + list, subEnd = isEnd, subLoading = false)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(subLoading = false, message = e.message) }
                }
        }
    }

    fun sendDanmaku(message: String, positionMs: Long) {
        val video = _state.value.video ?: return
        if (message.isBlank()) return
        if (!session.isLogin()) {
            _state.update { it.copy(message = "请先登录") }
            return
        }
        viewModelScope.launch {
            runCatching { repo.sendDanmaku(video.cid, message, positionMs) }
                .onSuccess {
                    // 本地立即上屏，不等下一次拉取
                    _state.update { s ->
                        s.copy(
                            message = "弹幕已发布",
                            danmaku = (s.danmaku + DanmakuItem(positionMs, 1, 25, 0xFFFFFF, message))
                                .sortedBy { it.timeMs },
                        )
                    }
                }
                .onFailure { _state.update { it.copy(message = it.message) } }
        }
    }

    /** 点赞 / 取消点赞评论：主列表、楼中楼、楼主三处同步，乐观更新失败回滚。 */
    fun likeReply(comment: Comment) {
        val video = _state.value.video ?: return
        if (!session.isLogin()) {
            _state.update { it.copy(message = "请先登录") }
            return
        }
        val target = !comment.liked
        val updated: (Comment) -> Comment = {
            if (it.rpid != comment.rpid) it
            else it.copy(
                liked = target,
                likes = (it.likes + if (target) 1 else -1).coerceAtLeast(0),
                // 点赞与点踩互斥
                hated = if (target) false else it.hated,
            )
        }
        _state.update {
            it.copy(
                comments = it.comments.map(updated),
                subReplies = it.subReplies.map(updated),
                subRoot = it.subRoot?.let(updated),
            )
        }
        viewModelScope.launch {
            runCatching { repo.likeReply(video.aid, comment.rpid, target) }
                .onFailure { e ->
                    val revert: (Comment) -> Comment = { if (it.rpid == comment.rpid) comment else it }
                    _state.update {
                        it.copy(
                            message = e.message,
                            comments = it.comments.map(revert),
                            subReplies = it.subReplies.map(revert),
                            subRoot = it.subRoot?.let(revert),
                        )
                    }
                }
        }
    }

    /** 点踩 / 取消点踩评论：与点赞互斥，乐观更新失败回滚。 */
    fun hateReply(comment: Comment) {
        val video = _state.value.video ?: return
        if (!session.isLogin()) {
            _state.update { it.copy(message = "请先登录") }
            return
        }
        val target = !comment.hated
        val updated: (Comment) -> Comment = {
            if (it.rpid != comment.rpid) it
            else if (target) it.copy(
                hated = true,
                liked = false,
                likes = if (it.liked) (it.likes - 1).coerceAtLeast(0) else it.likes,
            )
            else it.copy(hated = false)
        }
        _state.update {
            it.copy(
                comments = it.comments.map(updated),
                subReplies = it.subReplies.map(updated),
                subRoot = it.subRoot?.let(updated),
            )
        }
        viewModelScope.launch {
            runCatching { repo.hateReply(video.aid, comment.rpid, target) }
                .onFailure { e ->
                    val revert: (Comment) -> Comment = { if (it.rpid == comment.rpid) comment else it }
                    _state.update {
                        it.copy(
                            message = e.message,
                            comments = it.comments.map(revert),
                            subReplies = it.subReplies.map(revert),
                            subRoot = it.subRoot?.let(revert),
                        )
                    }
                }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
