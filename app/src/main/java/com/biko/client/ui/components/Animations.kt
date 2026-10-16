package com.biko.client.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 触觉反馈类型（对齐 BiliPai 的 HapticType）。
 */
enum class HapticType { LIGHT, MEDIUM, HEAVY, SELECTION }

/**
 * 触发系统触觉反馈。按需求已全局关闭点击振动：返回空实现，
 * 弹性缩放等其余点击动效不受影响。
 */
@Composable
fun rememberHapticFeedback(): (HapticType) -> Unit = remember { { _: HapticType -> } }

/**
 * 弹性点击：按压时缩小 + 弹性回弹，并触发触觉反馈（去除默认涟漪）。
 */
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.95f,
    hapticType: HapticType = HapticType.LIGHT,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = rememberHapticFeedback()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bouncy_scale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
        ) {
            haptic(hapticType)
            onClick()
        }
}

/**
 * 弹性点击（支持长按）：用于视频卡片这类「单击进入 + 长按弹菜单」的控件。
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyCombinedClickable(
    scaleDown: Float = 0.96f,
    hapticType: HapticType = HapticType.LIGHT,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = rememberHapticFeedback()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bouncy_combined_scale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onLongClick = onLongClick?.let { callback ->
                {
                    haptic(HapticType.MEDIUM)
                    callback()
                }
            },
            onClick = {
                haptic(hapticType)
                onClick()
            },
        )
}

/**
 * 数字滚动切换：值变化时旧值上滑淡出、新值上滑淡入（用于点赞/投币/收藏计数）。
 */
@Composable
fun AnimatedCountText(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "animated_count",
        modifier = modifier,
    ) { value ->
        content(value)
    }
}
