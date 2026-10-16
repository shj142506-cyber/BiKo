package com.biko.client.data

import com.biko.client.data.remote.SmsSigner
import java.net.URLDecoder

// ---------------------------------------------------------------------------
// 登录（扫码 / 短信）
// ---------------------------------------------------------------------------

suspend fun BiliRepository.qrcodeGenerate(): String =
    api.qrcodeGenerate("${PASSPORT_BASE}x/passport-login/web/qrcode/generate")
        .requireOk("生成二维码")
        .url

suspend fun BiliRepository.qrcodePoll(key: String): Int {
    val resp = api.qrcodePoll("${PASSPORT_BASE}x/passport-login/web/qrcode/poll?qrcode_key=$key")
    val data = resp.data ?: return -1
    if (data.code == 0) {
        // 会话 Cookie 主要在 data.url 里，少数情况下才走响应头（由拦截器兜住）
        val params = runCatching { parseQuery(data.url) }.getOrDefault(emptyMap())
        val sessdata = params["SESSDATA"]
        val jct = params["bili_jct"]
        val uid = params["DedeUserID"] ?: "0"
        if (!sessdata.isNullOrEmpty() && !jct.isNullOrEmpty()) {
            session.saveCookie(sessdata, jct, uid)
        }
    }
    return data.code
}

private fun BiliRepository.parseQuery(url: String): Map<String, String> =
    url.substringAfter('?', "")
        .split('&')
        .mapNotNull { seg ->
            val i = seg.indexOf('=')
            if (i <= 0) null else {
                URLDecoder.decode(seg.substring(0, i), "UTF-8") to
                    URLDecoder.decode(seg.substring(i + 1), "UTF-8")
            }
        }
        .toMap()

// ---- 短信登录（参数与签名对齐 BiliPai 的 Android HD 方案）----

/** App 登录公共参数（android_hd 客户端画像）。 */
private fun BiliRepository.smsBaseParams(): MutableMap<String, String> {
    val buvid = session.buvid3()
    return mutableMapOf(
        "appkey" to SmsSigner.HD_APP_KEY,
        "build" to "2001100",
        "c_locale" to "zh_CN",
        "channel" to "master",
        "disable_rcmd" to "0",
        "mobi_app" to "android_hd",
        "platform" to "android",
        "s_locale" to "zh_CN",
        "statistics" to "{\"appId\":5,\"platform\":3,\"version\":\"2.0.1\",\"abtest\":\"\"}",
        "ts" to (System.currentTimeMillis() / 1000).toString(),
        "buvid" to buvid,
        "local_id" to buvid,
    )
}

/** 发送短信验证码，成功返回 captchaKey；触发安全验证时抛「改用扫码」。 */
suspend fun BiliRepository.sendSmsCode(tel: String): String {
    ensureBuvid()
    val buvid = session.buvid3()
    val ts = System.currentTimeMillis()
    val params = smsBaseParams().apply {
        put("login_session_id", SmsSigner.md5(buvid + ts))
        put("cid", "86")
        put("tel", tel)
    }
    val resp = api.smsSend(
        "${PASSPORT_BASE}x/passport-login/sms/send",
        SmsSigner.signHd(params),
    )
    val data = resp.data
    if (resp.code == 0 && data != null && data.recaptchaUrl.isBlank() && data.captchaKey.isNotBlank()) {
        return data.captchaKey
    }
    if (data != null && data.recaptchaUrl.isNotBlank()) {
        throw BiliException(resp.code, "需要安全验证，请改用扫码登录", "发送短信")
    }
    throw BiliException(resp.code, resp.message, "发送短信")
}

/** 短信验证码登录，成功后把 cookie_info 里的会话 Cookie 落盘并刷新资料。 */
suspend fun BiliRepository.loginBySms(tel: String, code: String, captchaKey: String) {
    ensureBuvid()
    val buvid = session.buvid3()
    val pubKey = api.webKey("${PASSPORT_BASE}x/passport-login/web/key")
        .requireOk("获取登录密钥").key
    // 设备 id 由 buvid 派生，保持同一设备稳定
    val deviceId = SmsSigner.md5("biko$buvid")
    val dt = SmsSigner.rsaEncrypt(SmsSigner.randomString(16), pubKey)
    val params = smsBaseParams().apply {
        put("bili_local_id", deviceId)
        put("device", "phone")
        put("device_id", deviceId)
        put("device_name", "vivo")
        put("device_platform", "Android14vivo")
        put("dt", SmsSigner.percentEncode(dt))
        put("cid", "86")
        put("tel", tel)
        put("code", code)
        put("captcha_key", captchaKey)
        put("from_pv", "main.my-information.my-login.0.click")
        put("from_url", SmsSigner.percentEncode("bilibili://user_center/mine"))
    }
    val resp = api.smsLogin(
        "${PASSPORT_BASE}x/passport-login/login/sms",
        SmsSigner.signHd(params),
    )
    val data = resp.data
    if (resp.code == 0 && data != null) {
        val cookies = data.cookieInfo?.cookies.orEmpty()
        val sess = cookies.firstOrNull { it.name == "SESSDATA" }?.value
        val jct = cookies.firstOrNull { it.name == "bili_jct" }?.value
        val uid = cookies.firstOrNull { it.name == "DedeUserID" }?.value ?: "0"
        if (!sess.isNullOrEmpty() && !jct.isNullOrEmpty()) {
            session.saveCookie(sess, jct, uid)
            refreshProfile()
            return
        }
        throw BiliException(resp.code, "未获取到登录凭证", "短信登录")
    }
    throw BiliException(resp.code, resp.message, "短信登录")
}