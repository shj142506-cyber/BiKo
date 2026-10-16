package com.biko.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biko.client.data.AppGraph
import com.biko.client.data.feedRecommend

/**
 * 短视频入口：取推荐流的第一条，直接以「竖屏模式」打开详情页。
 *
 * 竖屏模式本身就能上下滑切换推荐视频（复用详情页的 UI 与播放逻辑），
 * 所以这里只负责挑一个起点视频，不做播放。
 */
@Composable
fun ShortVideoEntry(
    onOpenPortraitVideo: (String) -> Unit,
    onBack: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        error = null
        runCatching { AppGraph.repository.feedRecommend(1) }
            .onSuccess { list ->
                val first = list.firstOrNull { it.bvid.isNotEmpty() }
                if (first != null) {
                    onOpenPortraitVideo(first.bvid)
                } else {
                    error = "没有取到推荐视频"
                }
            }
            .onFailure { error = it.message ?: "加载失败" }
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val text = error
        if (text == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = text, color = Color.White, fontSize = 13.sp)
                Row(Modifier.padding(top = 14.dp)) {
                    Text(
                        text = "重试",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { retryKey++ }
                            .padding(horizontal = 16.dp),
                    )
                    Text(
                        text = "返回",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}
