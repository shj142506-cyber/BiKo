package com.biko.client.data.remote

import android.util.Base64
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * 短信登录用的签名与加密工具，参考 BiliPai AppSignUtils / LoginAppAuthPolicy：
 * - Android HD 端 appkey/appsec 签名（key 排序、percent-encode、MD5）
 * - 登录 RSA 公钥加密设备凭据
 */
object SmsSigner {
    const val HD_APP_KEY = "dfca71928277209b"
    private const val HD_APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e"

    fun percentEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** HD 登录签名：percent-encode 后的 query + appsec 取 MD5，返回含 sign 的完整参数。 */
    fun signHd(params: Map<String, String>): Map<String, String> {
        val sorted = params.toSortedMap()
        val query = sorted.entries.joinToString("&") { (k, v) -> "${percentEncode(k)}=${percentEncode(v)}" }
        return sorted + ("sign" to md5(query + HD_APP_SEC))
    }

    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 用登录公钥加密（RSA/ECB/PKCS1Padding），输出 Base64。 */
    fun rsaEncrypt(plain: String, pem: String): String {
        val clean = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.decode(clean, Base64.DEFAULT)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.DEFAULT).trim()
    }

    /** 随机字符串（设备凭据明文）。 */
    fun randomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}
