package com.jkapp.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderPanelTest {

    @Test
    fun `가로로 한 칸 이동하면 인덱스가 1 증가한다`() {
        assertEquals(3, computeNewIndex(currentIndex = 2, rowShift = 0, colShift = 1, columns = 5, tabCount = 8))
    }

    @Test
    fun `세로로 한 줄 이동하면 인덱스가 columns만큼 증가한다`() {
        assertEquals(7, computeNewIndex(currentIndex = 2, rowShift = 1, colShift = 0, columns = 5, tabCount = 8))
    }

    @Test
    fun `왼쪽 경계를 벗어나면 0으로 고정된다`() {
        assertEquals(0, computeNewIndex(currentIndex = 0, rowShift = 0, colShift = -1, columns = 5, tabCount = 8))
    }

    @Test
    fun `오른쪽 경계(마지막 인덱스)를 벗어나면 마지막 인덱스로 고정된다`() {
        assertEquals(7, computeNewIndex(currentIndex = 7, rowShift = 0, colShift = 1, columns = 5, tabCount = 8))
    }

    @Test
    fun `마지막 줄이 꽉 차지 않은 경우 아래로 이동하면 마지막 인덱스로 고정된다`() {
        // 8개 탭, 5열: 0번째 줄 0~4, 1번째 줄 5~7 (3칸만 존재). index 3에서 한 줄 아래로
        // 이동하면 존재하지 않는 index 8을 가리키므로 마지막 인덱스(7)로 스냅되어야 한다.
        assertEquals(7, computeNewIndex(currentIndex = 3, rowShift = 1, colShift = 0, columns = 5, tabCount = 8))
    }

    @Test
    fun `왼쪽으로 한 칸 이동하면 오프셋 X가 아이템 너비만큼 오른쪽으로 보정된다`() {
        // 회귀 테스트: colShift가 음수일 때 deltaIndex를 columns로 floorMod하면 항상 [0, columns)
        // 범위의 양수로 해석되어 오프셋이 반대 방향(더 음수)으로 보정되는 버그가 있었다.
        val (x, y) = computeOffsetAfterMove(
            offsetX = -64f, offsetY = 0f, rowShift = 0, colShift = -1, itemWidthPx = 64f, itemHeightPx = 64f,
        )
        assertEquals(0f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun `오른쪽으로 한 칸 이동하면 오프셋 X가 아이템 너비만큼 왼쪽으로 보정된다`() {
        val (x, y) = computeOffsetAfterMove(
            offsetX = 64f, offsetY = 0f, rowShift = 0, colShift = 1, itemWidthPx = 64f, itemHeightPx = 64f,
        )
        assertEquals(0f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun `위로 한 줄 이동하면 오프셋 Y가 아이템 높이만큼 아래로 보정된다`() {
        val (x, y) = computeOffsetAfterMove(
            offsetX = 0f, offsetY = -64f, rowShift = -1, colShift = 0, itemWidthPx = 64f, itemHeightPx = 64f,
        )
        assertEquals(0f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun `아래로 한 줄 이동하면 오프셋 Y가 아이템 높이만큼 위로 보정된다`() {
        val (x, y) = computeOffsetAfterMove(
            offsetX = 0f, offsetY = 64f, rowShift = 1, colShift = 0, itemWidthPx = 64f, itemHeightPx = 64f,
        )
        assertEquals(0f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun `왼쪽 위로 대각선 이동하면 X, Y 오프셋이 모두 보정된다`() {
        val (x, y) = computeOffsetAfterMove(
            offsetX = -64f, offsetY = -64f, rowShift = -1, colShift = -1, itemWidthPx = 64f, itemHeightPx = 64f,
        )
        assertEquals(0f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun `마지막 줄이 꽉 차지 않아 인덱스가 클램프되어도 오프셋은 무한히 누적되지 않는다`() {
        // 8개 탭, 5열: index 3에서 아래로 드래그하면 존재하지 않는 8 대신 마지막 인덱스(7)로 클램프된다.
        // 이때 rowShift(1)만큼 오프셋을 보정해도, 이후 최종 coerceIn(-1.5*item, 1.5*item)에 의해
        // 오프셋이 아이템 크기의 1.5배를 넘어 무한히 쌓이지는 않는다.
        val newIndex = computeNewIndex(currentIndex = 3, rowShift = 1, colShift = 0, columns = 5, tabCount = 8)
        assertEquals(7, newIndex)

        val (_, y) = computeOffsetAfterMove(
            offsetX = 0f, offsetY = 64f, rowShift = 1, colShift = 0, itemWidthPx = 64f, itemHeightPx = 64f,
        )
        val coercedY = y.coerceIn(-64f * 1.5f, 64f * 1.5f)
        assertEquals(0f, coercedY, 0.01f)
    }
}
