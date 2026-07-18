package com.jkapp.haptic

import com.jkapp.common.MAX_HAPTIC_INTENSITY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticFeedbackUtilsTest {

    // ── hapticStepChanged ──────────────────────────────────────────────────────

    @Test
    fun `hapticStepChanged - 다른 정수 단계로 이동하면 true`() {
        assertTrue(hapticStepChanged(2.0f, 3.0f))
    }

    @Test
    fun `hapticStepChanged - 같은 정수 단계 내 미세 이동이면 false`() {
        assertFalse(hapticStepChanged(3.1f, 3.9f))
    }

    @Test
    fun `hapticStepChanged - 정확히 같은 값이면 false`() {
        assertFalse(hapticStepChanged(5.0f, 5.0f))
    }

    @Test
    fun `hapticStepChanged - 0에서 1로 변경되면 true`() {
        assertTrue(hapticStepChanged(0.0f, 1.0f))
    }

    @Test
    fun `hapticStepChanged - 1에서 0으로 변경되면 true`() {
        assertTrue(hapticStepChanged(1.0f, 0.0f))
    }

    @Test
    fun `hapticStepChanged - 같은 단계 내 상한 근처 미세 이동이면 false`() {
        // 9.1f → 9.9f : 둘 다 toInt() == 9, 같은 단계
        assertFalse(hapticStepChanged(9.1f, 9.9f))
    }

    // ── hapticAmplitude ────────────────────────────────────────────────────────

    @Test
    fun `hapticAmplitude - 강도 0이면 null 반환(무진동)`() {
        assertNull(hapticAmplitude(0))
    }

    @Test
    fun `hapticAmplitude - 음수 강도이면 null 반환(무진동)`() {
        assertNull(hapticAmplitude(-1))
    }

    @Test
    fun `hapticAmplitude - 강도 1이면 최소 진폭 1 이상`() {
        val amplitude = hapticAmplitude(1)!!
        assertTrue(amplitude >= 1)
    }

    @Test
    fun `hapticAmplitude - 강도 MAX이면 255`() {
        assertEquals(255, hapticAmplitude(MAX_HAPTIC_INTENSITY))
    }

    @Test
    fun `hapticAmplitude - 강도 5(중간)이면 진폭이 1~255 사이`() {
        val amplitude = hapticAmplitude(5)!!
        assertTrue(amplitude in 1..255)
    }

    @Test
    fun `hapticAmplitude - 강도가 클수록 진폭도 크거나 같다(단조 증가)`() {
        val amplitudes = (1..MAX_HAPTIC_INTENSITY).map { hapticAmplitude(it)!! }
        for (i in 0 until amplitudes.size - 1) {
            assertTrue(
                "강도 ${i + 1} 진폭(${amplitudes[i]})이 강도 ${i + 2} 진폭(${amplitudes[i + 1]})보다 크면 안 됨",
                amplitudes[i] <= amplitudes[i + 1]
            )
        }
    }

    @Test
    fun `hapticAmplitude - 진폭은 항상 1~255 범위`() {
        for (intensity in 1..MAX_HAPTIC_INTENSITY) {
            val amplitude = hapticAmplitude(intensity)!!
            assertTrue("강도 $intensity 의 진폭 $amplitude 이 범위 밖", amplitude in 1..255)
        }
    }

    @Test
    fun `hapticAmplitude - 강도 10이면 진폭 255`() {
        assertEquals(255, hapticAmplitude(10))
    }

    @Test
    fun `hapticAmplitude - 강도 1이면 진폭 25`() {
        assertEquals(25, hapticAmplitude(1))
    }
}
