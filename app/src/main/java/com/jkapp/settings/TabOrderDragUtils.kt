package com.jkapp.settings

import kotlin.math.roundToInt

// 세로 리스트에서 누적 드래그 오프셋(Y, px)을 항목 높이로 나눠 목표 인덱스를 계산한다.
// 리스트 경계를 벗어나면 [0, itemCount-1]로 고정된다.
internal fun computeTargetIndex(currentIndex: Int, offsetY: Float, itemHeightPx: Float, itemCount: Int): Int {
    if (itemHeightPx <= 0f) return currentIndex
    val shift = (offsetY / itemHeightPx).roundToInt()
    return (currentIndex + shift).coerceIn(0, itemCount - 1)
}

// 인덱스가 이동한 뒤, 소비된 이동량(indexShift * itemHeight)만큼 누적 오프셋을 되돌린다.
internal fun consumeOffsetAfterMove(offsetY: Float, indexShift: Int, itemHeightPx: Float): Float =
    offsetY - indexShift * itemHeightPx
