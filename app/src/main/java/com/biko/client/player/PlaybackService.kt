package com.biko.client.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.biko.client.R

/**
 * 前台媒体服务：向系统暴露 [MediaCard] 持有的 [MediaSession]，
 * 并以「小窗播放」通知把播放状态挂到前台，桌面/锁屏由此渲染媒体控制卡片。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        MediaCard.session

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val notification = MediaCard.currentNotification
        when {
            startInForegroundRequired -> startAsForeground(notification ?: buildFallbackNotification())
            notification != null -> notificationManager().notify(NOTIFICATION_ID, notification)
            else -> stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND ->
                startAsForeground(MediaCard.currentNotification ?: buildFallbackNotification())
            ACTION_STOP_FOREGROUND -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager().cancel(NOTIFICATION_ID)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildFallbackNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_biko)
            .setContentTitle("Biko")
            .setContentText("正在准备播放控件")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "小窗播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "小窗播放控制"
                setShowBadge(false)
                setSound(null, null)
            },
        )
    }

    companion object {
        /** 与 [MediaCard.NOTIFICATION_ID] 必须一致，否则 onUpdateNotification 会推两条通知。 */
        const val CHANNEL_ID = MediaCard.CHANNEL_ID
        const val NOTIFICATION_ID = MediaCard.NOTIFICATION_ID
        const val ACTION_START_FOREGROUND = "com.biko.client.action.START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "com.biko.client.action.STOP_FOREGROUND"
    }
}
