package com.jkapp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt

fun String.toComposeColorOrNull(): Color? =
    try { Color(this.toColorInt()) } catch (_: IllegalArgumentException) { null }

fun Long.formatFileSize(): String = when {
    this < 1024L -> "${this}B"
    this < 1024L * 1024 -> "${this / 1024}KB"
    else -> "${"%.1f".format(this / (1024.0 * 1024))}MB"
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
) {
    val transition = rememberInfiniteTransition(label = "loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
        ),
        label = "rotation",
    )
    CircularProgressIndicator(
        progress = { 0.25f },
        modifier = modifier.rotate(rotation),
        color = color,
        strokeWidth = strokeWidth,
        trackColor = Color.Transparent,
    )
}
