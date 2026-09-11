package com.demonlab.suikaplayer.ui.utils

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import java.util.Locale

fun Vibrator.triggerLightVibration() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    } else {
        @Suppress("DEPRECATION")
        this.vibrate(20)
    }
}

fun formatDuration(duration: Long): String {
    val minutes = (duration / 1000) / 60
    val seconds = (duration / 1000) % 60
    return "%d:%02d".format(Locale.getDefault(), minutes, seconds)
}

fun formatDurationCompact(durationInMillis: Long): String {
    val totalSeconds = durationInMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
    } else {
        "${minutes}m"
    }
}

fun formatLongDuration(durationInMillis: Long): String {
    val totalSeconds = durationInMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.getDefault(), hours, minutes, seconds)
    } else {
        "%02d:%02d".format(Locale.getDefault(), minutes, seconds)
    }
}

fun Modifier.bounceClick(scaleDown: Float = 0.85f): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                waitForUpOrCancellation()
                isPressed = false
            }
        }
}

fun Modifier.songSwipeGestures(
    enabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit
): Modifier = this.then(
    if (enabled) {
        Modifier.pointerInput(Unit) {
            var totalDragX = 0f
            var gestureConsumed = false
            detectDragGestures(
                onDragStart = {
                    totalDragX = 0f
                    gestureConsumed = false
                },
                onDrag = { _, dragAmount ->
                    if (!gestureConsumed) {
                        totalDragX += dragAmount.x
                        val absX = kotlin.math.abs(totalDragX)
                        val absY = kotlin.math.abs(dragAmount.y)
                        if (absX > 60 && absX > absY * 1.5f) {
                            if (totalDragX < 0) onNext() else onPrevious()
                            gestureConsumed = true
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }
)
