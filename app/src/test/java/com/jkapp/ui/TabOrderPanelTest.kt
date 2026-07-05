package com.jkapp.ui

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
}
