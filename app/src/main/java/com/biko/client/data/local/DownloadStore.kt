package com.biko.client.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.biko.client.data.model.DownloadTask
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.downloadDataStore by preferencesDataStore("biko_downloads")

/** 离线缓存任务列表的本地持久化（DataStore + JSON）。 */
class DownloadStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): List<DownloadTask> {
        val raw = context.downloadDataStore.data.first()[KEY] ?: return emptyList()
        return try {
            json.decodeFromString<List<DownloadTask>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(list: List<DownloadTask>) {
        context.downloadDataStore.edit { it[KEY] = json.encodeToString(list) }
    }

    companion object {
        private val KEY = stringPreferencesKey("list")
    }
}
