package com.jkapp.haptic

import com.jkapp.common.MAX_HAPTIC_INTENSITY

/**
 * 햅틱 피드백 관련 순수 함수 유틸.
 * HapticController에서 동일한 공식을 재사용하고, JVM 단위테스트에서 검증한다.
 */

/**
 * 슬라이더의 이전 값과 새 값이 서로 다른 정수 단계에 있는지 반환한다.
 * 같은 정수 단계 내 미세 이동(예: 3.1 → 3.9)에서는 false를 반환해 중복 진동을 방지한다.
 */
fun hapticStepChanged(prevValue: Float, newValue: Float): Boolean =
    prevValue.toInt() != newValue.toInt()

/**
 * 햅틱 강도(0~MAX_HAPTIC_INTENSITY)를 VibrationEffect 진폭(1~255)으로 변환한다.
 * intensity <= 0이면 null을 반환해 진동하지 않음을 나타낸다.
 */
fun hapticAmplitude(intensity: Int): Int? {
    if (intensity <= 0) return null
    return (intensity * 255 / MAX_HAPTIC_INTENSITY).coerceIn(1, 255)
}
