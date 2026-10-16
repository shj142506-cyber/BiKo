package com.biko.client.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore by preferencesDataStore("biko_search_history")

/** 本地搜索历史（关键词列表），使用 DataStore 持久化（JSON 序列化字符串列表）。 */
class SearchHistoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): List<String> {
        val raw = context.searchHistoryDataStore.data.first()[KEY] ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(list: List<String>) {
        context.searchHistoryDataStore.edit { it[KEY] = json.encodeToString(list) }
    }

    /** 记录一次搜索：已存在的词提到最前，超出上限时丢弃最旧的。 */
    suspend fun add(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        val list = load().toMutableList().apply {
            removeAll { it.equals(kw, ignoreCase = true) }
            add(0, kw)
            while (size > MAX_ITEMS) removeAt(size - 1)
        }
        save(list)
    }

    suspend fun remove(keyword: String) {
        save(load().filterNot { it.equals(keyword, ignoreCase = true) })
    }

    suspend fun clear() {
        context.searchHistoryDataStore.edit { it.remove(KEY) }
    }

    companion object {
        private val KEY = stringPreferencesKey("list")
        private const val MAX_ITEMS = 20
    }
}
