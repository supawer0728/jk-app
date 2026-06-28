package com.jkapp.ui

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

fun String.toComposeColorOrNull(): Color? =
    try { Color(this.toColorInt()) } catch (_: IllegalArgumentException) { null }
