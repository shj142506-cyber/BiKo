package com.biko.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biko.client.R
import com.biko.client.data.DownloadManager
import com.biko.client.data.model.DownloadTask
import com.biko.client.ui.components.NetworkImage
import com.biko.client.ui.theme.biko

/** 离线缓存管理页：展示下载任务，支持暂停 / 继续 / 删除 / 全部清空，点击已完成的项离线播放。 */
@Composable
fun DownloadScreen(
    onOpenVideo: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = biko
    val tasks by DownloadManager.tasks.collectAsState()

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
            )
            Text(
                text = "离线缓存",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            if (tasks.isNotEmpty()) {
                Text(
                    text = "全部清空",
                    fontSize = 13.sp,
                    color = colors.acc,
                    modifier = Modifier.clickable { DownloadManager.clearAll() }.padding(8.dp),
                )
            }
        }

        LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
            if (tasks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = colors.t3,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "还没有离线缓存\n在视频详情页点「缓存」即可下载",
                            fontSize = 13.sp,
                            color = colors.t3,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            } else {
                items(tasks, key = { it.bvid }) { task ->
                    DownloadRow(
                        task = task,
                        onClick = {
                            if (task.isDone) onOpenVideo(task.bvid)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, onClick: () -> Unit) {
    val colors = biko
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).aspectRatio(16f / 10f)) {
            NetworkImage(
                url = task.cover,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
            )
        }

        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = task.title,
                fontSize = 13.5.sp,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Text(
                text = task.upName,
                fontSize = 11.sp,
                color = colors.t3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )

            // 进度条：未完成的显示百分比，已完成的显示大小
            if (!task.isDone && task.status != DownloadTask.STATUS_FAILED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.line),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(task.progress)
                            .height(3.dp)
                            .background(colors.acc),
                    )
                }
            }

            Text(
                text = statusText(task),
                fontSize = 11.sp,
                color = if (task.status == DownloadTask.STATUS_FAILED) colors.acc else colors.t3,
                modifier = Modifier.padding(top = 5.dp),
            )
        }

        // 操作按钮：下载中可暂停；暂停 / 失败可继续；任意状态可删除
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (task.status) {
                DownloadTask.STATUS_RUNNING, DownloadTask.STATUS_WAITING ->
                    TextAction("暂停") { DownloadManager.pause(task.bvid) }

                DownloadTask.STATUS_PAUSED, DownloadTask.STATUS_FAILED ->
                    TextAction("继续") { DownloadManager.resume(task.bvid) }

                else -> Unit
            }
            TextAction("删除") { DownloadManager.remove(task.bvid) }
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 12.sp,
        color = biko.acc,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun statusText(task: DownloadTask): String = when (task.status) {
    DownloadTask.STATUS_WAITING -> "等待下载"
    DownloadTask.STATUS_RUNNING -> {
        val pct = (task.progress * 100).toInt()
        val size = formatSize(task.downloaded) + if (task.total > 0) " / " + formatSize(task.total) else ""
        "$pct% · $size"
    }
    DownloadTask.STATUS_DONE -> "已完成 · " + formatSize(task.downloaded)
    DownloadTask.STATUS_FAILED -> task.error.ifEmpty { "下载失败" }
    DownloadTask.STATUS_PAUSED -> "已暂停 · " + formatSize(task.downloaded)
    else -> ""
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2fGB".format(bytes / 1024f / 1024f / 1024f)
    bytes >= 1024L * 1024L -> "%.1fMB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "${bytes / 1024}KB"
    else -> "${bytes}B"
}
