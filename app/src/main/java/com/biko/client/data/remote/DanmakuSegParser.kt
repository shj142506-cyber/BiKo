package com.biko.client.data.remote

import com.biko.client.data.model.DanmakuItem

/**
 * 解析 B 站弹幕 seg（`x/v2/dm/web/seg.so`）的 protobuf 响应。
 *
 * 老接口 `x/v1/dm/list.so`（XML）现在对普通请求直接返回 412 风控，
 * 弹幕全空；官方 Web 端和 BiliPai 都改用了 seg 分段接口。
 *
 * 响应体格式（实测确认）：一段扁平的 protobuf，repeated field 1（wire=LEN）
 * 每个子消息是一个 `DanmakuElem`：
 * ```
 * 1: id(varint)  2: progress_ms(varint)  3: mode(varint)
 * 4: fontsize(varint)  5: color(varint)  7: content(string)
 * ```
 * 每个 `segment_index` 覆盖 6 分钟，超出视频时长的分段返回空。
 */
object DanmakuSegParser {

    /** 每个弹幕分段的时长（秒），与 B 站一致。 */
    const val SEGMENT_SECONDS = 360

    fun parse(bytes: ByteArray): List<DanmakuItem> {
        val result = ArrayList<DanmakuItem>(512)
        var pos = 0
        while (pos < bytes.size) {
            val tag = readVarint(bytes, pos) ?: break
            pos = tag.second
            // 顶层只认识 field 1 + wire type 2（LEN），即 tag = 0x0A
            if (tag.first != 0x0AL) break
            val len = readVarint(bytes, pos) ?: break
            pos = len.second
            val end = (pos + len.first.toInt()).coerceAtMost(bytes.size)
            readElem(bytes, pos, end)?.let { result += it }
            pos = end
        }
        result.sortBy { it.timeMs }
        return result
    }

    /** 解析单条 `DanmakuElem`。 */
    private fun readElem(bytes: ByteArray, start: Int, end: Int): DanmakuItem? {
        var pos = start
        var progress = -1L
        var mode = 1
        var fontSize = 25
        var color = 0xFFFFFF
        var text: String? = null

        while (pos < end) {
            val tag = readVarint(bytes, pos) ?: return null
            pos = tag.second
            val field = (tag.first ushr 3).toInt()
            when ((tag.first and 0x7L).toInt()) {
                0 -> {
                    val v = readVarint(bytes, pos) ?: return null
                    pos = v.second
                    when (field) {
                        2 -> progress = v.first
                        3 -> mode = v.first.toInt()
                        4 -> fontSize = v.first.toInt()
                        5 -> color = v.first.toInt()
                    }
                }
                2 -> {
                    val l = readVarint(bytes, pos) ?: return null
                    pos = l.second
                    val strEnd = (pos + l.first.toInt()).coerceAtMost(end)
                    if (field == 7) {
                        text = String(bytes, pos, strEnd - pos, Charsets.UTF_8)
                    }
                    pos = strEnd
                }
                // 其它线型（32/64 位定长）跳过
                5 -> pos += 4
                1 -> pos += 8
                else -> return null
            }
        }

        val content = text?.takeIf { it.isNotBlank() } ?: return null
        if (progress < 0) return null
        return DanmakuItem(
            timeMs = progress,
            mode = mode,
            fontSize = fontSize,
            color = color,
            text = content,
        )
    }

    /** 读取一个 varint，返回 (值, 下一个位置的索引)；越界或过长返回 null。 */
    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int>? {
        var pos = start
        var shift = 0
        var value = 0L
        while (pos < bytes.size && shift <= 63) {
            val b = bytes[pos].toInt()
            value = value or ((b and 0x7F).toLong() shl shift)
            pos++
            if (b and 0x80 == 0) return value to pos
            shift += 7
        }
        return null
    }
}
