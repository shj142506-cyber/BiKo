package com.biko.client.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.biko.client.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 桌面/锁屏「媒体播放卡片」的宿主。
 *
 * 通过暴露一个 [MediaSession]（并配一个前台 [PlaybackService]），
 * 系统就会在桌面、锁屏、下拉栏渲染出可遥控的媒体卡片（封面 / 标题 / UP 主 / 播放暂停 / 进度）。
 *
 * 关键点：Biko 的 DASH 播放源是自定义的本地 MPD（`biko-dash://local/manifest.mpd`），
 * 不能像常规做法那样用 [Player.replaceMediaItem] 重建 MediaItem 来塞元数据（会丢掉自定义数据源）。
 * 因此这里用 [ForwardingPlayer] 包一层，仅覆写 [Player.getMediaMetadata]，把标题/UP 主/封面交给系统。
 */
@OptIn(UnstableApi::class)
object MediaCard {
    const val CHANNEL_ID = "mini_player_channel"
    const val NOTIFICATION_ID = 1002
    const val ACTION_MEDIA_CONTROL = "com.biko.client.MEDIA_CONTROL"
    const val EXTRA_CONTROL_TYPE = "control_type"
    const val CONTROL_TOGGLE = 1

    private const val THEME_COLOR = 0xFF5D9FD6.toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appContext: Context? = null

    var session: MediaSession? = null
        private set
    var currentNotification: Notification? = null
        private set

    private var boundPlayer: Player? = null
    private var currentKey: String? = null
    private var coverUrl: String = ""
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentBitmap: Bitmap? = null
    private var playerListener: Player.Listener? = null

    /** 播放源切换到 [player] 上的 [key] 对应视频时调用；重复调用同一视频会被忽略。 */
    fun attach(
        context: Context,
        player: Player,
        key: String,
        title: String,
        artist: String,
        artworkUrl: String,
    ) {
        val app = context.applicationContext
        if (session != null && boundPlayer === player && currentKey == key) return

        appContext = app
        releaseSession()

        boundPlayer = player
        currentKey = key
        currentTitle = title
        currentArtist = artist
        coverUrl = artworkUrl
        currentBitmap = null

        val sessionPlayer = MetadataPlayer(player) { buildMetadata() }
        val builder = MediaSession.Builder(app, sessionPlayer)
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(
                    app,
                    0,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        session = builder.build()

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = refreshNotification()
            override fun onPlaybackStateChanged(playbackState: Int) = refreshNotification()
        }
        playerListener = listener
        player.addListener(listener)

        refreshNotification()

        // 封面走网络加载，先推一版无封面通知，拿到位图后再刷新。
        scope.launch {
            val bitmap = loadBitmap(app, artworkUrl)
            if (bitmap != null && currentKey == key) {
                currentBitmap = bitmap
                refreshNotification()
            }
        }
    }

    /** 通知里「播放/暂停」按钮回调。 */
    fun togglePlayPause() {
        val player = boundPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    /** 离开播放页时调用：撤掉通知、停掉前台服务并释放会话。 */
    fun release() {
        val context = appContext
        if (context != null) {
            runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIFICATION_ID)
        }
        releaseSession()
        appContext = null
        coverUrl = ""
        currentTitle = ""
        currentArtist = ""
    }

    private fun releaseSession() {
        playerListener?.let { listener -> boundPlayer?.removeListener(listener) }
        playerListener = null
        session?.release()
        session = null
        currentNotification = null
        boundPlayer = null
        currentKey = null
        currentBitmap = null
    }

    private fun buildMetadata(): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(currentTitle)
            .setArtist(currentArtist)
            .setDisplayTitle(currentTitle)
            .setIsPlayable(true)
        if (coverUrl.isNotBlank()) builder.setArtworkUri(Uri.parse(coverUrl))
        return builder.build()
    }

    private fun refreshNotification() {
        val context = appContext ?: return
        if (session == null) return
        val notification = buildNotification(context)
        currentNotification = notification
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
        requestForegroundService(context)
    }

    private fun buildNotification(context: Context): Notification {
        ensureChannel(context)
        val isPlaying = boundPlayer?.isPlaying == true
        val toggle = PendingIntent.getBroadcast(
            context,
            CONTROL_TOGGLE,
            Intent(context, MediaControlReceiver::class.java)
                .setAction(ACTION_MEDIA_CONTROL)
                .putExtra(EXTRA_CONTROL_TYPE, CONTROL_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggleAction = NotificationCompat.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "暂停" else "播放",
            toggle,
        ).build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_biko)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setLargeIcon(currentBitmap)
            .setColor(THEME_COLOR)
            .setColorized(true)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(session?.sessionActivity)
            .addAction(toggleAction)
        session?.let { builder.setStyle(MediaStyleNotificationHelper.MediaStyle(it).setShowActionsInCompactView(0)) }
        return builder.build()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "小窗播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "小窗播放控制"
                setShowBadge(false)
                setSound(null, null)
            },
        )
    }

    private fun requestForegroundService(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_START_FOREGROUND),
            )
        }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        if (url.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .scale(Scale.FILL)
                    .size(512, 512)
                    .build()
                when (val result = context.imageLoader.execute(request)) {
                    is SuccessResult -> result.drawable.toBitmap()
                    else -> null
                }
            }.getOrNull()
        }
    }

    /** 只覆写元数据读取，其余全部委托给真正的 ExoPlayer，避免破坏自定义 DASH 播放源。 */
    private class MetadataPlayer(
        player: Player,
        private val metadataProvider: () -> MediaMetadata,
    ) : ForwardingPlayer(player) {
        override fun getMediaMetadata(): MediaMetadata = metadataProvider()
    }
}
