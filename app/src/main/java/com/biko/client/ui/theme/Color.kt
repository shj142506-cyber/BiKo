package com.biko.client.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 原版内联色值（来自反编译代码 Color.parseColor）。 */
val Accent = Color(0xFF5D9FD6)

/** 未选中态文字/图标灰（原 Ui.java 使用 #5F6771）。 */
private val InactiveGray = Color(0xFF5F6771)

/** 等级徽章色。 */
val Level2 = Color(0xFF4CAF50)
val Level3 = Color(0xFF2196F3)
val Level4 = Color(0xFF9C27B0)
val Level5 = Color(0xFFFF9800)
val Level6 = Color(0xFFF44336)

/** 排行前三名色（原 RankAdapter 使用）。 */
val RankGold = Color(0xFFF04142)
val RankSilver = Color(0xFFF5822E)
val RankBronze = Color(0xFFFF9F19)

/** 三级灰：非高亮的次要信息色。 */
private val Tertiary = Color(0xFF949CA6)

/** 排行榜/热度榜的名次色：前三名用金银铜，其余用三级灰。 */
fun rankColor(rank: Int): Color = when (rank) {
    0 -> RankGold
    1 -> RankSilver
    2 -> RankBronze
    else -> Tertiary
}

/**
 * 原版语义色板：与反编译资源中的 bg / card / card_2 / text / t2 / t3 / line / acc 一一对应。
 */
data class BikoColors(
    val bg: Color,
    val card: Color,
    val card2: Color,
    val text: Color,
    val t2: Color,
    val t3: Color,
    val line: Color,
    val acc: Color,
    val inactive: Color,
)

/** 浅色（原版默认：AppCompatDelegate MODE_NIGHT_NO）。 */
val LightBikoColors = BikoColors(
    bg = Color(0xFFFFFFFF),
    card = Color(0xFFF5F6F8),
    card2 = Color(0xFFEDEFF2),
    text = Color(0xFF18191B),
    t2 = Color(0xFF4E5666),
    t3 = Tertiary,
    line = Color(0xFFE8EAED),
    acc = Accent,
    inactive = InactiveGray,
)

/** 深色（原版顶部月亮图标切换）。 */
val DarkBikoColors = BikoColors(
    bg = Color(0xFF0F1216),
    card = Color(0xFF1A1E24),
    card2 = Color(0xFF22272E),
    text = Color(0xFFF0F0F2),
    t2 = Color(0xFFC0C4CC),
    t3 = Tertiary,
    line = Color(0xFF2A2F37),
    acc = Accent,
    inactive = Color(0xFF7C848E),
)

val LocalBikoColors = staticCompositionLocalOf { LightBikoColors }
