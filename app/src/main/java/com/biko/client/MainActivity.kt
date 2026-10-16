package com.biko.client

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biko.client.data.AppGraph
import com.biko.client.ui.BikoApp
import com.biko.client.ui.theme.BikoTheme
import com.biko.client.ui.theme.biko

class MainActivity : ComponentActivity() {

    // 桌面图标长按快捷方式的目标页（biko://search 等），null 表示普通启动
    private var shortcutTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        shortcutTarget = shortcutFromIntent(intent)
        setContent {
            val night by AppGraph.night.enabled.collectAsStateWithLifecycle()
            BikoTheme(darkTheme = night) {
                // 只在主题真正切换时更新一次：
                // 播放页会把状态栏图标临时改成白色（顶部黑条），不能在每次重组时被重置
                LaunchedEffect(night) {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !night
                }
                Surface(modifier = Modifier.fillMaxSize(), color = biko.bg) {
                    BikoApp(
                        shortcutTarget = shortcutTarget,
                        onShortcutConsumed = { shortcutTarget = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutTarget = shortcutFromIntent(intent)
    }

    /** 解析快捷方式 Intent：biko://search | watch_later | favorite | history */
    private fun shortcutFromIntent(intent: Intent?): String? {
        val data: Uri = intent?.data ?: return null
        if (data.scheme != "biko") return null
        return data.host?.takeIf { it.isNotEmpty() }
    }
}
