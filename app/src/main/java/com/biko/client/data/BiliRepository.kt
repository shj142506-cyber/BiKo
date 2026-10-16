package com.biko.client.data

import com.biko.client.data.local.HistoryStore
import com.biko.client.data.local.SessionStore
import com.biko.client.data.remote.BiliApi
import com.biko.client.data.remote.BiliResponse
import com.biko.client.data.remote.WbiSigner

/** 直播接口的独立域名（与原版一致）。 */
internal const val LIVE_BASE = "https://api.live.bilibili.com/"

/** 登录二维码接口的独立域名（与原版一致）。 */
internal const val PASSPORT_BASE = "https://passport.bilibili.com/"

/**
 * UP 主投稿接口（space/wbi/arc/search）会做风控校验，
 * 不带浏览器指纹参数会直接返回 -352「风控校验失败」，所以固定带上这几个占位值。
 */
internal val SPACE_FINGERPRINT = mapOf(
    "dm_img_list" to "[]",
    "dm_img_str" to "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ",
    "dm_cover_img_str" to
        "QU5HTEUgKEludGVsLCBJbnRlbChSKSBVSEQgR3JhcGhpY3MgNjMwIChveDAwMDAwMDAwKSBEaXJlY3QzRDExIHZzXzVfMCBwc181XzAsIEQzRDExKQ",
    "dm_img_inter" to "{\"ds\":[],\"wh\":[0,0,0],\"of\":[0,0,0]}",
)

class BiliException(
    val code: Int,
    override val message: String,
    val action: String,
) : Exception("$action 失败($code): $message")

/** 领域层仓库，统一处理 WBI 签名、Cookie、错误检查与 DTO→模型映射。 */
class BiliRepository(
    internal val api: BiliApi,
    internal val session: SessionStore,
    internal val history: HistoryStore,
) {

    internal val wbi = WbiSigner(api)

    internal fun csrf(): String = session.csrf()
}

/** 响应必须 code==0 且 data 非空，否则抛 [BiliException]（全仓库共用）。 */
internal fun <T> BiliResponse<T>.requireOk(action: String): T {
    if (code != 0) throw BiliException(code, message, action)
    return data ?: throw BiliException(code, message, action)
}

/** 响应 code==0 即可，不关心 data（写操作用）。 */
internal fun <T> BiliResponse<T>.checkOk(action: String) {
    if (code != 0) throw BiliException(code, message, action)
}