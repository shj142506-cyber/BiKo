package com.biko.client.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MediaSource
import com.biko.client.data.model.StreamTrack
import java.io.ByteArrayInputStream
import java.util.Locale

/** 本地生成的 MPD 使用私有 scheme，避免与真实网络地址冲突。 */
private const val DASH_SCHEME = "biko-dash"

/**
 * 把 B 站 DASH 的两条码流（视频 + 音频）拼成本地 MPD。
 * B 站只给 `base_url` + `SegmentBase`，Media3 无法直接播放单个 m4s，
 * 因此这里生成标准 MPD 再交给 [DashMediaSource]。
 *
 * 每条码流会把 `base_url` 与 `backup_url` 全部写进多个 `<BaseURL>`：
 * 首选镜像（常见是 mcdn/P2P 节点）直连经常返回 403，Media3 会自动改用下一个候选。
 */
fun buildLocalMpd(
    video: StreamTrack?,
    audio: StreamTrack,
    durationMs: Long,
    /** 纯音频模式：不写入视频 AdaptationSet，只下载音频流。 */
    audioOnly: Boolean = false,
): String {
    val seconds = (durationMs / 1000.0).coerceAtLeast(1.0)
    val duration = "PT${String.format(Locale.US, "%.3f", seconds)}S"
    val videoMime = video?.mimeType?.ifEmpty { "video/mp4" } ?: "video/mp4"
    val audioMime = audio.mimeType.ifEmpty { "audio/mp4" }
    return buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append(
            """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" mediaPresentationDuration="$duration" minBufferTime="PT1.5S">"""
        )
        append("""<Period duration="$duration">""")
        if (!audioOnly && video != null) {
            append("""<AdaptationSet contentType="video" mimeType="$videoMime" segmentAlignment="true" startWithSAP="1">""")
            appendRepresentation(
                id = "v${video.id}",
                bandwidth = video.bandwidth,
                codecs = video.codecs,
                urls = orderedUrls(video),
                width = video.width,
                height = video.height,
                frameRate = video.frameRate,
                initRange = video.initRange,
                indexRange = video.indexRange,
            )
            append("</AdaptationSet>")
        }
        append("""<AdaptationSet contentType="audio" mimeType="$audioMime" segmentAlignment="true" startWithSAP="1">""")
        appendRepresentation(
            id = "a${audio.id}",
            bandwidth = audio.bandwidth,
            codecs = audio.codecs,
            urls = orderedUrls(audio),
            width = 0,
            height = 0,
            frameRate = "",
            initRange = audio.initRange,
            indexRange = audio.indexRange,
        )
        append("</AdaptationSet>")
        append("</Period></MPD>")
    }
}

/**
 * 候选地址排序：mcdn / P2P 镜像对直连请求经常 403，放最后，
 * 优先用 upos 等直连 CDN；组内保持接口返回顺序。
 */
private fun orderedUrls(track: StreamTrack): List<String> =
    (listOf(track.url) + track.backupUrls)
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { if (it.contains("mcdn") || it.contains("mountaintoys")) 1 else 0 }

private fun StringBuilder.appendRepresentation(
    id: String,
    bandwidth: Long,
    codecs: String,
    urls: List<String>,
    width: Int,
    height: Int,
    frameRate: String,
    initRange: String,
    indexRange: String,
) {
    append("""<Representation id="$id" bandwidth="$bandwidth" codecs="${xml(codecs)}"""")
    if (width > 0) append(""" width="$width"""")
    if (height > 0) append(""" height="$height"""")
    if (frameRate.isNotEmpty()) append(""" frameRate="${xml(frameRate)}"""")
    append(">")
    urls.forEach { append("<BaseURL>").append(xml(it)).append("</BaseURL>") }
    append("""<SegmentBase indexRange="${xml(indexRange)}" indexRangeExact="true">""")
    append("""<Initialization range="${xml(initRange)}"/>""")
    append("</SegmentBase></Representation>")
}

/** MPD 中 URL 带 `&`，必须转义，否则解析失败。 */
private fun xml(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

/**
 * 私有 scheme 的清单请求由内存返回，其余（分片）请求转交给真正的网络数据源。
 */
private class ManifestDataSource(
    private val manifest: ByteArray,
    private val upstream: DataSource,
) : DataSource {

    private var stream: ByteArrayInputStream? = null
    private var openedUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        openedUri = dataSpec.uri
        if (dataSpec.uri.scheme == DASH_SCHEME) {
            val input = ByteArrayInputStream(manifest)
            if (dataSpec.position > 0) input.skip(dataSpec.position)
            stream = input
            return manifest.size.toLong() - dataSpec.position
        }
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val input = stream
        if (input != null) {
            val read = input.read(buffer, offset, length)
            return if (read == -1) C.RESULT_END_OF_INPUT else read
        }
        return upstream.read(buffer, offset, length)
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        stream = null
        upstream.close()
    }
}

/** 用内存 MPD 创建一条 DASH 播放源。 */
fun createDashMediaSource(
    manifest: ByteArray,
    upstreamFactory: DataSource.Factory,
): MediaSource {
    val factory = DashMediaSource.Factory(
        DataSource.Factory { ManifestDataSource(manifest, upstreamFactory.createDataSource()) },
    )
    return factory.createMediaSource(
        MediaItem.Builder()
            .setUri("$DASH_SCHEME://local/manifest.mpd")
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .build(),
    )
}
