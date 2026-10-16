package com.biko.client.data

import com.biko.client.data.model.LiveRoom

/**
 * 首页点击直播卡片时暂存整个对象，供直播详情页展示主播头像/昵称。
 * （原版是通过 Intent extras 传递这些字段的。）
 */
object LiveSelection {
    var current: LiveRoom? = null
}
