package com.biko.client.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 夜间模式偏好，键名与原版一致（prefs "biko_night" / key "on"）。
 * 原版默认浅色，由首页顶栏的月亮图标切换。
 */
class NightModeStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun toggle() {
        val value = !_enabled.value
        prefs.edit().putBoolean(KEY, value).apply()
        _enabled.value = value
    }

    private companion object {
        const val PREF = "biko_night"
        const val KEY = "on"
    }
}
