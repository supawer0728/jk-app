package com.jkapp.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderDragUtilsTest {

    private val itemHeight = 64f
    private val itemCount = 5

    @Test
    fun `아래로 한 칸 드래그하면 인덱스가 1 증가한다`() {
        val result = computeTargetIndex(currentIndex = 1, offsetY = itemHeight, itemHeightPx = itemHeight, itemCount = itemCount)
        assertEquals(2, result)
    }

    @Test
    fun `위로 한 칸 드래그하면 인덱스가 1 감소한다`() {
        val result = computeTargetIndex(currentIndex = 2, offsetY = -itemHeight, itemHeightPx = itemHeight, itemCount = itemCount)
        assertEquals(1, result)
    }

    @Test
    fun `상단 경계에서 위로 드래그하면 0으로 고정된다`() {
        val result = computeTargetIndex(currentIndex = 0, offsetY = -itemHeight, itemHeightPx = itemHeight, itemCount = itemCount)
        assertEquals(0, result)
    }

    @Test
    fun `하단 경계에서 아래로 드래그하면 마지막 인덱스로 고정된다`() {
        val result = computeTargetIndex(currentIndex = itemCount - 1, offsetY = itemHeight, itemHeightPx = itemHeight, itemCount = itemCount)
        assertEquals(itemCount - 1, result)
    }

    @Test
    fun `항목 높이가 0이면 현재 인덱스를 그대로 반환한다`() {
        val result = computeTargetIndex(currentIndex = 3, offsetY = itemHeight, itemHeightPx = 0f, itemCount = itemCount)
        assertEquals(3, result)
    }

    @Test
    fun `consumeOffsetAfterMove는 소비된 이동량만큼 오프셋을 되돌린다`() {
        val result = consumeOffsetAfterMove(offsetY = itemHeight, indexShift = 1, itemHeightPx = itemHeight)
        assertEquals(0f, result, 0f)
    }

    @Test
    fun `consumeOffsetAfterMove는 여러 칸 이동도 되돌린다`() {
        val result = consumeOffsetAfterMove(offsetY = itemHeight * 2 + 10f, indexShift = 2, itemHeightPx = itemHeight)
        assertEquals(10f, result, 0f)
    }
}
