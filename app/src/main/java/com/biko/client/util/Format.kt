package com.biko.client.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 数值缩写（亿 / 万）。 */
fun formatCount(n: Int): String = when {
    n >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", n / 1.0E8)
    n >= 10_000 -> String.format(Locale.CHINA, "%.1f万", n / 10_000.0)
    else -> n.toString()
}

/** 秒数 → mm:ss；超过 1 小时 → h:mm:ss。负数按 0 处理。 */
fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val mmss = "$m:${s.toString().padStart(2, '0')}"
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else mmss
}

/** Unix 时间戳（秒）→ 相对时间。 */
fun formatTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val diff = System.currentTimeMillis() / 1000 - timestamp
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86400 -> "${diff / 3600}小时前"
        diff < 604800 -> "${diff / 86400}天前"
        else -> SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(timestamp * 1000))
    }
}
