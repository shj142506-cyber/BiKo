package com.biko.client.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** 从 Context 向上查找宿主 Activity（用于控制横竖屏与系统栏）。 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
