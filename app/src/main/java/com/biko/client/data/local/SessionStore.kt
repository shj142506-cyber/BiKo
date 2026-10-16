package com.biko.client.data.local

import android.content.Context
import android.content.SharedPreferences
import com.biko.client.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 登录会话 / 匿名指纹，使用 SharedPreferences 持久化。
 * 之所以不用 DataStore：OkHttp 拦截器需要同步读取 Cookie，SharedPreferences 天然同步。
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    @Volatile
    private var cachedCookie: String = buildCookie()

    // ---- Cookie / 身份 ----

    /** 当前 Cookie，供 OkHttp 拦截器同步读取。 */
    fun cookie(): String = cachedCookie

    fun csrf(): String = prefs.getString(KEY_JCT, "").orEmpty()

    fun mid(): String = prefs.getString(KEY_UID, "").orEmpty()

    fun isLogin(): Boolean = prefs.contains(KEY_SESSDATA)

    fun saveCookie(sessdata: String, jct: String, uid: String) {
        prefs.edit()
            .putString(KEY_SESSDATA, sessdata)
            .putString(KEY_JCT, jct)
            .putString(KEY_UID, uid)
            .apply()
        cachedCookie = buildCookie()
        _profile.value = loadProfile()
    }

    fun saveBuvid(b3: String, b4: String) {
        prefs.edit().putString(KEY_B3, b3).putString(KEY_B4, b4).apply()
        cachedCookie = buildCookie()
    }

    /**
     * 从响应头的 Set-Cookie 中捕获会话 Cookie。
     * 扫码登录时 SESSDATA / bili_jct 只在响应头下发的情况必须靠这里兜住，
     * 否则会出现「接口已登录成功但 App 仍是未登录」。
     */
    fun captureCookies(setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        val editor = prefs.edit()
        var changed = false
        setCookies.forEach { header ->
            val first = header.substringBefore(';')
            val index = first.indexOf('=')
            if (index > 0) {
                val name = first.substring(0, index).trim()
                val value = first.substring(index + 1).trim()
                if (value.isNotEmpty()) {
                    when (name) {
                        KEY_SESSDATA -> { editor.putString(KEY_SESSDATA, value); changed = true }
                        KEY_JCT -> { editor.putString(KEY_JCT, value); changed = true }
                        KEY_UID -> { editor.putString(KEY_UID, value); changed = true }
                        KEY_B3 -> { editor.putString(KEY_B3, value); changed = true }
                        KEY_B4 -> { editor.putString(KEY_B4, value); changed = true }
                    }
                }
            }
        }
        if (!changed) return
        editor.apply()
        cachedCookie = buildCookie()
        _profile.value = loadProfile()
    }

    fun saveProfile(
        uname: String,
        face: String,
        level: Int,
        coin: Int,
        bcoin: Int,
        exp: Int,
        nextExp: Int,
    ) {
        prefs.edit()
            .putString(KEY_UNAME, uname)
            .putString(KEY_FACE, face)
            .putInt(KEY_LEVEL, level)
            .putInt(KEY_COIN, coin)
            .putInt(KEY_BCOIN, bcoin)
            .putInt(KEY_EXP, exp)
            .putInt(KEY_NEXT_EXP, nextExp)
            .apply()
        _profile.value = loadProfile()
    }

    fun logout() {
        prefs.edit().clear().apply()
        cachedCookie = buildCookie()
        _profile.value = loadProfile()
    }

    // ---- 读取 ----

    fun buvid3(): String = prefs.getString(KEY_B3, "").orEmpty()

    /**
     * 确保存在 buvid3（设备指纹）。缺失时生成随机值并持久化——B 站风控对
     * 缺少 buvid3 的批量请求极易返回 -352，导致头像/昵称补拉全部失败。
     * 与 BiliPai CookieJar 的兜底行为一致。
     */
    fun ensureBuvid3(): String {
        val existing = buvid3()
        if (existing.isNotEmpty()) return existing
        val generated = UUID.randomUUID().toString() + "infoc"
        prefs.edit().putString(KEY_B3, generated).apply()
        cachedCookie = buildCookie()
        return generated
    }

    fun userProfile(): UserProfile = _profile.value

    private fun loadProfile(): UserProfile = UserProfile(
        isLogin = isLogin(),
        uname = prefs.getString(KEY_UNAME, "").orEmpty(),
        face = prefs.getString(KEY_FACE, "").orEmpty(),
        level = prefs.getInt(KEY_LEVEL, 0),
        coin = prefs.getInt(KEY_COIN, 0),
        bcoin = prefs.getInt(KEY_BCOIN, 0),
        exp = prefs.getInt(KEY_EXP, 0),
        nextExp = prefs.getInt(KEY_NEXT_EXP, 0),
    )

    private fun buildCookie(): String {
        val parts = mutableListOf<String>()
        prefs.getString(KEY_B3, "").orEmpty().takeIf { it.isNotEmpty() }?.let { parts += "buvid3=$it" }
        prefs.getString(KEY_B4, "").orEmpty().takeIf { it.isNotEmpty() }?.let { parts += "buvid4=$it" }
        prefs.getString(KEY_SESSDATA, "").orEmpty().takeIf { it.isNotEmpty() }?.let { parts += "SESSDATA=$it" }
        prefs.getString(KEY_JCT, "").orEmpty().takeIf { it.isNotEmpty() }?.let { parts += "bili_jct=$it" }
        prefs.getString(KEY_UID, "").orEmpty().takeIf { it.isNotEmpty() }?.let { parts += "DedeUserID=$it" }
        return parts.joinToString("; ")
    }

    companion object {
        private const val PREF = "biko_session"
        private const val KEY_SESSDATA = "SESSDATA"
        private const val KEY_JCT = "bili_jct"
        private const val KEY_UID = "DedeUserID"
        private const val KEY_B3 = "buvid3"
        private const val KEY_B4 = "buvid4"
        private const val KEY_UNAME = "uname"
        private const val KEY_FACE = "face"
        private const val KEY_LEVEL = "level"
        private const val KEY_COIN = "coin"
        private const val KEY_BCOIN = "bcoin"
        private const val KEY_EXP = "exp"
        private const val KEY_NEXT_EXP = "next_exp"
    }
}
