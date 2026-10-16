package com.biko.client.data.remote

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * B 站 WBI 接口签名。
 *
 * 算法：对参数过滤 `[!'()*]` 后按 key 排序、URL 编码拼接，再取 `md5(query + mixinKey)`。
 * mixinKey 由 nav 接口返回的 `wbi_img` 两张图 basename 拼接后按固定表重排得到。
 */
class WbiSigner(private val api: BiliApi) {

    private var mixinKey: String? = null
    private var keyTime = 0L

    /** 对参数签名，返回可直接拼接到 URL 之后的完整 query 串（不含前导 `?`）。 */
    suspend fun sign(params: Map<String, String>): String {
        val key = ensureKey()
        val map = params.toMutableMap()
        map["wts"] = (System.currentTimeMillis() / 1000).toString()
        val query = buildString {
            map.toSortedMap().forEach { (k, v) ->
                val filtered = v.replace(Regex("[!'()*]"), "")
                if (isNotEmpty()) append('&')
                append(URLEncoder.encode(k, "UTF-8")).append('=').append(URLEncoder.encode(filtered, "UTF-8"))
            }
        }
        return "$query&w_rid=${md5(query + key)}"
    }

    private suspend fun ensureKey(): String {
        mixinKey?.let { cached ->
            if (System.currentTimeMillis() - keyTime < 3_600_000) return cached
        }
        val wbi = api.nav().data?.wbiImg
            ?: throw IllegalStateException("无法获取 WBI 密钥")
        val raw = wbi.imgUrl.fileBase() + wbi.subUrl.fileBase()
        val key = buildString {
            MIXIN_TAB.forEach { idx -> if (idx < raw.length) append(raw[idx]) }
        }.take(32)
        mixinKey = key
        keyTime = System.currentTimeMillis()
        return key
    }

    private fun String.fileBase(): String =
        substringAfterLast('/').substringBefore('.')

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    companion object {
        private val MIXIN_TAB = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
        )
    }
}
