package com.biko.client.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 接收媒体通知里「播放/暂停」按钮的广播。 */
class MediaControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MediaCard.ACTION_MEDIA_CONTROL) return
        when (intent.getIntExtra(MediaCard.EXTRA_CONTROL_TYPE, -1)) {
            MediaCard.CONTROL_TOGGLE -> MediaCard.togglePlayPause()
        }
    }
}
