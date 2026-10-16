package com.biko.client

import android.app.Application
import com.biko.client.data.AppGraph
import com.biko.client.data.ensureBuvid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BikoApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // 预热：确保匿名指纹存在（不阻塞主线程）
        appScope.launch {
            runCatching { AppGraph.repository.ensureBuvid() }
        }
    }
}
