package com.biko.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightScheme = lightColorScheme(
    primary = LightBikoColors.acc,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    background = LightBikoColors.bg,
    onBackground = LightBikoColors.text,
    surface = LightBikoColors.card,
    onSurface = LightBikoColors.text,
    surfaceVariant = LightBikoColors.card2,
    onSurfaceVariant = LightBikoColors.t3,
    outline = LightBikoColors.line,
    // 覆盖 M3 baseline 的 surfaceContainer*（默认淡紫 #F3EDF7），统一弹窗/菜单底色
    surfaceContainerLowest = LightBikoColors.bg,
    surfaceContainerLow = LightBikoColors.card,
    surfaceContainer = LightBikoColors.card,
    surfaceContainerHigh = LightBikoColors.card,
    surfaceContainerHighest = LightBikoColors.card2,
)

private val DarkScheme = darkColorScheme(
    primary = DarkBikoColors.acc,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    background = DarkBikoColors.bg,
    onBackground = DarkBikoColors.text,
    surface = DarkBikoColors.card,
    onSurface = DarkBikoColors.text,
    surfaceVariant = DarkBikoColors.card2,
    onSurfaceVariant = DarkBikoColors.t3,
    outline = DarkBikoColors.line,
    // 覆盖 M3 baseline 的 surfaceContainer*，统一弹窗/菜单底色
    surfaceContainerLowest = DarkBikoColors.bg,
    surfaceContainerLow = DarkBikoColors.card,
    surfaceContainer = DarkBikoColors.card,
    surfaceContainerHigh = DarkBikoColors.card,
    surfaceContainerHighest = DarkBikoColors.card2,
)

@Composable
fun BikoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBikoColors provides if (darkTheme) DarkBikoColors else LightBikoColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** 便捷读取当前语义色板。 */
val biko: BikoColors
    @Composable get() = LocalBikoColors.current
