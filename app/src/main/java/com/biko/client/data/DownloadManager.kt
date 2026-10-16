package com.biko.client.data

import android.content.Context
import android.net.Uri
import com.biko.client.data.local.DownloadStore
import com.biko.client.data.model.DownloadTask
import com.biko.client.data.model.PlayInfo
import com.biko.client.data.model.StreamTrack
import com.biko.client.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 离线缓存：把 B 站 DASH 的视频 / 音频两条 m4s 下载到 App 私有目录。
 *
 * 播放时用本地 `file://` 地址重建 MPD（复用 [com.biko.client.player.buildLocalMpd]），
 * 配合 BikoPlayer 的 DefaultDataSource 即可离线播放，无需 MediaMuxer 合流。
 * 下载使用 Range 断点续传，退出重进可继续。
 */
object DownloadManager {

    private lateinit var appContext: Context
    private lateinit var store: DownloadStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    /** 下载专用客户端：CDN 分片必须用桌面 UA + Referer，不能复用 api 客户端。 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun init(context: Context, store: DownloadStore) {
        appContext = context.applicationContext
        this.store = store
        scope.launch {
            // 进程重启后，之前的「下载中」实际已中断，标记为暂停等待用户恢复
            val restored = store.load().map {
                if (it.status == DownloadTask.STATUS_RUNNING) {
                    it.copy(status = DownloadTask.STATUS_PAUSED)
                } else {
                    it
                }
            }
            _tasks.value = restored
        }
    }

    fun taskOf(bvid: String): DownloadTask? = _tasks.value.firstOrNull { it.bvid == bvid }

    fun observe(bvid: String): Flow<DownloadTask?> =
        _tasks.map { list -> list.firstOrNull { it.bvid == bvid } }

    /** 已完成、可用于离线播放的本地播放信息；不可用返回 null。 */
    fun localPlayInfo(bvid: String): PlayInfo? {
        val task = taskOf(bvid) ?: return null
        if (!task.isDone) return null
        val vFile = File(task.videoFile)
        val aFile = File(task.audioFile)
        if (!vFile.exists() || !aFile.exists()) return null
        return PlayInfo(
            quality = task.qn,
            acceptQuality = listOf(task.qn),
            videoTracks = listOf(
                StreamTrack(
                    id = task.qn,
                    url = Uri.fromFile(vFile).toString(),
                    bandwidth = task.videoBandwidth,
                    codecs = task.videoCodecs,
                    mimeType = task.videoMime.ifEmpty { "video/mp4" },
                    width = task.width,
                    height = task.height,
                    frameRate = task.frameRate,
                    initRange = task.videoInitRange,
                    indexRange = task.videoIndexRange,
                ),
            ),
            audioTracks = listOf(
                StreamTrack(
                    id = 0,
                    url = Uri.fromFile(aFile).toString(),
                    mimeType = task.audioMime.ifEmpty { "audio/mp4" },
                    initRange = task.audioInitRange,
                    indexRange = task.audioIndexRange,
                ),
            ),
            durationMs = task.durationMs,
            durl = null,
        )
    }

    /** 加入离线缓存；已存在未完成任务则继续，已完成的忽略。 */
    fun enqueue(video: Video, qn: Int) {
        if (video.bvid.isEmpty()) return
        val existing = taskOf(video.bvid)
        if (existing != null) {
            if (!existing.isDone) resume(video.bvid)
            return
        }
        val task = DownloadTask(
            bvid = video.bvid,
            aid = video.aid,
            cid = video.cid,
            title = video.title,
            cover = video.pic,
            upName = video.upName,
            durationMs = video.duration.toLong() * 1000L,
            qn = qn,
            status = DownloadTask.STATUS_WAITING,
        )
        _tasks.value = _tasks.value.filterNot { it.bvid == task.bvid } + task
        persist()
        start(video.bvid, video.aid, video.cid, qn)
    }

    fun pause(bvid: String) {
        jobs.remove(bvid)?.cancel()
        updateField(bvid) { it.copy(status = DownloadTask.STATUS_PAUSED) }
        persist()
    }

    fun resume(bvid: String) {
        val task = taskOf(bvid) ?: return
        if (task.isDone || jobs[bvid]?.isActive == true) return
        start(bvid, task.aid, task.cid, if (task.qn > 0) task.qn else 32)
    }

    fun remove(bvid: String) {
        jobs.remove(bvid)?.cancel()
        _tasks.value = _tasks.value.filterNot { it.bvid == bvid }
        persist()
        scope.launch { runCatching { downloadDir(bvid).deleteRecursively() } }
    }

    /** 清空全部缓存（列表 + 文件）。 */
    fun clearAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _tasks.value = emptyList()
        persist()
        scope.launch { runCatching { downloadRoot().deleteRecursively() } }
    }

    // ---- 内部实现 ----

    private fun start(bvid: String, aid: Long, cid: Long, qn: Int) {
        if (jobs[bvid]?.isActive == true) return
        jobs[bvid] = scope.launch {
            updateField(bvid) { it.copy(status = DownloadTask.STATUS_RUNNING, error = "") }
            persist()
            try {
                var task = taskOf(bvid) ?: return@launch
                // 解析播放地址（首次）
                if (task.videoUrl.isEmpty() || task.audioUrl.isEmpty() ||
                    task.videoInitRange.isEmpty() || task.audioInitRange.isEmpty()
                ) {
                    val info = AppGraph.repository.playInfo(aid, cid, qn)
                    val v = info.videoTracks.maxByOrNull { it.bandwidth }
                    val a = info.audioTracks.maxByOrNull { it.bandwidth }
                    if (!info.isDash || v == null || a == null) {
                        updateField(bvid) {
                            it.copy(status = DownloadTask.STATUS_FAILED, error = "该视频不支持离线缓存")
                        }
                        persist()
                        return@launch
                    }
                    updateField(bvid) {
                        it.copy(
                            videoUrl = v.bestUrl(),
                            audioUrl = a.bestUrl(),
                            videoMime = v.mimeType,
                            audioMime = a.mimeType,
                            videoCodecs = v.codecs,
                            videoBandwidth = v.bandwidth,
                            width = v.width,
                            height = v.height,
                            frameRate = v.frameRate,
                            videoInitRange = v.initRange,
                            videoIndexRange = v.indexRange,
                            audioInitRange = a.initRange,
                            audioIndexRange = a.indexRange,
                            durationMs = info.durationMs.takeIf { d -> d > 0 } ?: it.durationMs,
                            qn = info.quality.takeIf { q -> q > 0 } ?: qn,
                        )
                    }
                    task = taskOf(bvid) ?: return@launch
                }

                val dir = downloadDir(bvid).apply { mkdirs() }
                val vFile = File(dir, "video.m4s")
                val aFile = File(dir, "audio.m4s")
                var videoTotal = 0L
                var audioTotal = 0L
                var lastEmit = 0L

                val vSize = downloadTrack(
                    url = task.videoUrl,
                    dest = vFile,
                    onTotal = { total ->
                        videoTotal = total
                        updateField(bvid) { it.copy(total = videoTotal + audioTotal) }
                    },
                    onProgress = { written ->
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 200) {
                            lastEmit = now
                            updateField(bvid) { it.copy(downloaded = written, total = (videoTotal + audioTotal).coerceAtLeast(written)) }
                        }
                    },
                )
                val aSize = downloadTrack(
                    url = task.audioUrl,
                    dest = aFile,
                    onTotal = { total ->
                        audioTotal = total
                        updateField(bvid) { it.copy(total = videoTotal + audioTotal) }
                    },
                    onProgress = { written ->
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 200) {
                            lastEmit = now
                            updateField(bvid) { it.copy(downloaded = vSize + written, total = (videoTotal + audioTotal).coerceAtLeast(vSize + written)) }
                        }
                    },
                )

                updateField(bvid) {
                    it.copy(
                        status = DownloadTask.STATUS_DONE,
                        videoFile = vFile.absolutePath,
                        audioFile = aFile.absolutePath,
                        downloaded = vSize + aSize,
                        total = vSize + aSize,
                    )
                }
                persist()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (jobs[bvid]?.isActive != true) return@launch
                updateField(bvid) {
                    it.copy(status = DownloadTask.STATUS_FAILED, error = e.message ?: "下载失败")
                }
                persist()
            } finally {
                jobs.remove(bvid)
            }
        }
    }

    /** 下载单个 m4s（Range 断点续传），返回文件最终大小。 */
    private suspend fun downloadTrack(
        url: String,
        dest: File,
        onTotal: (Long) -> Unit,
        onProgress: (Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val existing = if (dest.exists()) dest.length() else 0L
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", BILI_PLAY_USER_AGENT)
            .header("Referer", BILI_REFERER)
        val cookie = AppGraph.session.cookie()
        if (cookie.isNotEmpty()) builder.header("Cookie", cookie)
        if (existing > 0) builder.header("Range", "bytes=$existing-")

        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw BiliException(-1, "下载失败：HTTP ${resp.code}", "离线缓存")
            }
            val body = resp.body ?: throw BiliException(-1, "下载失败：响应为空", "离线缓存")
            // 206 表示服务端接受了续传；200 表示从头返回，需覆盖写入
            val append = resp.code == 206
            val startLen = if (append) existing else 0L
            val contentLength = body.contentLength().takeIf { it > 0 } ?: 0L
            onTotal(startLen + contentLength)

            var written = startLen
            FileOutputStream(dest, append).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(written)
                    }
                }
            }
            written
        }
    }

    /** 优先选非 mcdn / P2P 镜像的直连地址（这些节点直连常 403）。 */
    private fun StreamTrack.bestUrl(): String {
        val candidates = (listOf(url) + backupUrls).filter { it.isNotEmpty() }
        return candidates.firstOrNull { !it.contains("mcdn") && !it.contains("mountaintoys") }
            ?: candidates.firstOrNull()
            ?: url
    }

    private fun updateField(bvid: String, transform: (DownloadTask) -> DownloadTask) {
        val current = _tasks.value
        val index = current.indexOfFirst { it.bvid == bvid }
        if (index < 0) return
        val updated = current.toMutableList()
        updated[index] = transform(updated[index])
        _tasks.value = updated
    }

    private fun persist() {
        val snapshot = _tasks.value
        scope.launch { runCatching { store.save(snapshot) } }
    }

    private fun downloadRoot(): File {
        val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(base, "downloads")
    }

    private fun downloadDir(bvid: String): File = File(downloadRoot(), bvid)
}
