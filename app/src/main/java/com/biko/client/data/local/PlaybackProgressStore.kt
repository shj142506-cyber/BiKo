package com.biko.client.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.playbackDataStore by preferencesDataStore("biko_playback")

/**
 * 播放记忆：按 cid 记录上次观看进度，用于进入视频时续播。
 * 与 BiliPai 的策略一致：不足 5 秒不记，看过 95% 视为看完并清除。
 */
class PlaybackProgressStore(private val context: Context) {

    suspend fun save(cid: Long, positionMs: Long, durationMs: Long) {
        if (cid <= 0) return
        if (positionMs < MIN_SAVE_MS) return
        if (durationMs > 0 && positionMs >= durationMs * 95 / 100) {
            clear(cid)
            return
        }
        context.playbackDataStore.edit { it[longPreferencesKey(key(cid))] = positionMs }
    }

    suspend fun position(cid: Long): Long {
        if (cid <= 0) return 0L
        return context.playbackDataStore.data.first()[longPreferencesKey(key(cid))] ?: 0L
    }

    suspend fun clear(cid: Long) {
        context.playbackDataStore.edit { it.remove(longPreferencesKey(key(cid))) }
    }

    private fun key(cid: Long) = "cid_$cid"

    private companion object {
        const val MIN_SAVE_MS = 5_000L
    }
}
