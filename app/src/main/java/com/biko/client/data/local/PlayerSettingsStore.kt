package com.biko.client.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerSettingsDataStore by preferencesDataStore("biko_player_settings")

/**
 * 播放器设置。字段保持基础类型，避免数据层依赖 UI 层模型。
 * [quality] 为 0 表示「未手动选择」，此时按登录状态取默认清晰度。
 */
data class PlayerSettings(
    val speed: Float = 1f,
    val quality: Int = 0,
    /** 画面比例的枚举名（FIT / FILL / RATIO_16_9 / RATIO_4_3 / STRETCH）。 */
    val aspect: String = "FIT",
    val danmakuEnabled: Boolean = true,
    val danmakuFontScale: Float = 1f,
    val danmakuAlpha: Float = 1f,
    val danmakuSpeedFactor: Float = 1f,
    val danmakuDisplayArea: Float = 0.75f,
    val danmakuBlockWords: String = "",
    /** 后台听视频：开启后退到后台继续播放声音，不暂停不静音。 */
    val backgroundAudio: Boolean = false,
)

/** 播放器设置的本地持久化（跨视频保留）。 */
class PlayerSettingsStore(private val context: Context) {

    val flow: Flow<PlayerSettings> = context.playerSettingsDataStore.data.map { prefs ->
        PlayerSettings(
            speed = prefs[KEY_SPEED] ?: 1f,
            quality = prefs[KEY_QUALITY] ?: 0,
            aspect = prefs[KEY_ASPECT] ?: "FIT",
            danmakuEnabled = prefs[KEY_DM_ENABLED] ?: true,
            danmakuFontScale = prefs[KEY_DM_FONT] ?: 1f,
            danmakuAlpha = prefs[KEY_DM_ALPHA] ?: 1f,
            danmakuSpeedFactor = prefs[KEY_DM_SPEED] ?: 1f,
            danmakuDisplayArea = prefs[KEY_DM_AREA] ?: 0.75f,
            danmakuBlockWords = prefs[KEY_DM_BLOCK] ?: "",
            backgroundAudio = prefs[KEY_BACKGROUND_AUDIO] ?: false,
        )
    }

    suspend fun current(): PlayerSettings = flow.first()

    suspend fun save(settings: PlayerSettings) {
        context.playerSettingsDataStore.edit { prefs ->
            prefs[KEY_SPEED] = settings.speed
            prefs[KEY_QUALITY] = settings.quality
            prefs[KEY_ASPECT] = settings.aspect
            prefs[KEY_DM_ENABLED] = settings.danmakuEnabled
            prefs[KEY_DM_FONT] = settings.danmakuFontScale
            prefs[KEY_DM_ALPHA] = settings.danmakuAlpha
            prefs[KEY_DM_SPEED] = settings.danmakuSpeedFactor
            prefs[KEY_DM_AREA] = settings.danmakuDisplayArea
            prefs[KEY_DM_BLOCK] = settings.danmakuBlockWords
            prefs[KEY_BACKGROUND_AUDIO] = settings.backgroundAudio
        }
    }

    private companion object {
        val KEY_SPEED = floatPreferencesKey("speed")
        val KEY_QUALITY = intPreferencesKey("quality")
        val KEY_ASPECT = stringPreferencesKey("aspect")
        val KEY_DM_ENABLED = booleanPreferencesKey("danmaku_enabled")
        val KEY_DM_FONT = floatPreferencesKey("danmaku_font_scale")
        val KEY_DM_ALPHA = floatPreferencesKey("danmaku_alpha")
        val KEY_DM_SPEED = floatPreferencesKey("danmaku_speed_factor")
        val KEY_DM_AREA = floatPreferencesKey("danmaku_display_area")
        val KEY_DM_BLOCK = stringPreferencesKey("danmaku_block_words")
        val KEY_BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")
    }
}
