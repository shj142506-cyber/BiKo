package com.biko.client.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dislikeDataStore by preferencesDataStore("biko_dislike")

/**
 * 本地「不感兴趣」名单。
 *
 * B 站的「不感兴趣」在 App 端走 access_key 签名的接口（本项目只有 Web Cookie，调不通），
 * 所以按 BiliPai 的本地负反馈思路实现：记下被屏蔽视频的 bvid，
 * 之后每次加载列表时过滤掉，保证点过「不感兴趣」的视频不再出现。
 */
class DislikeStore(private val context: Context) {

    /** 被屏蔽的视频 bvid 集合。 */
    suspend fun load(): Set<String> =
        context.dislikeDataStore.data.first()[KEY] ?: emptySet()

    suspend fun add(bvid: String) {
        if (bvid.isEmpty()) return
        context.dislikeDataStore.edit { it[KEY] = (it[KEY] ?: emptySet()) + bvid }
    }

    suspend fun clear() {
        context.dislikeDataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("bvids")
    }
}
