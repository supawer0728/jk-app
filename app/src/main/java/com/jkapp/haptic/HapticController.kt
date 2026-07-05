package com.jkapp.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import com.jkapp.data.MAX_HAPTIC_INTENSITY

class HapticController(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    // 0(끄기)~10 사이의 사용자 설정값. 매번 DataStore를 읽지 않고 MainActivity에서 갱신해 둔다.
    var intensity: Int = MAX_HAPTIC_INTENSITY / 2

    fun tick() {
        if (intensity <= 0) return
        val amplitude = (intensity * 255 / MAX_HAPTIC_INTENSITY).coerceIn(1, 255)
        vibrator.vibrate(VibrationEffect.createOneShot(TICK_DURATION_MS, amplitude))
    }

    companion object {
        private const val TICK_DURATION_MS = 20L
    }
}

val LocalHapticController = staticCompositionLocalOf<HapticController?> { null }
