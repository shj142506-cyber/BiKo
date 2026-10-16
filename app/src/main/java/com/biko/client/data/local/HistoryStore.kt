package com.biko.client.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.biko.client.data.model.Video
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.historyDataStore by preferencesDataStore("biko_history")

/** 本地观看历史，使用 DataStore 持久化（JSON 序列化视频列表）。 */
class HistoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): List<Video> {
        val raw = context.historyDataStore.data.first()[KEY] ?: return emptyList()
        return try {
            json.decodeFromString<List<Video>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(list: List<Video>) {
        context.historyDataStore.edit { it[KEY] = json.encodeToString(list) }
    }

    suspend fun add(video: Video) {
        if (video.bvid.isEmpty()) return
        val list = load().toMutableList().apply {
            removeAll { it.bvid == video.bvid }
            add(0, video)
            while (size > MAX_ITEMS) removeAt(size - 1)
        }
        save(list)
    }

    suspend fun remove(bvid: String) {
        if (bvid.isEmpty()) return
        save(load().filterNot { it.bvid == bvid })
    }

    suspend fun clear() {
        context.historyDataStore.edit { it.remove(KEY) }
    }

    suspend fun findProgress(bvid: String): Long =
        load().firstOrNull { it.bvid == bvid }?.progress?.times(1000L) ?: 0L

    companion object {
        private val KEY = stringPreferencesKey("list")
        private const val MAX_ITEMS = 50
    }
}
