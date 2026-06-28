package com.jkapp.ui

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

fun String.toComposeColorOrNull(): Color? =
    try { Color(this.toColorInt()) } catch (_: IllegalArgumentException) { null }

fun Long.formatFileSize(): String = when {
    this < 1024L -> "${this}B"
    this < 1024L * 1024 -> "${this / 1024}KB"
    else -> "${"%.1f".format(this / (1024.0 * 1024))}MB"
}
