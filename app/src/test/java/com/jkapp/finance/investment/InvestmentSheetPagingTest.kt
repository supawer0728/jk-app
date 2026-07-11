package com.jkapp.finance.investment

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentSheetPagingTest {

    private fun rows(count: Int, from: Int) = (from until from + count).map { listOf(it.toString()) }

    @Test
    fun `첫 페이지가 가득 차지 않으면 한 번만 읽는다`() = runTest {
        var calls = 0
        val result = collectPagedRows(pageSize = 100, firstRow = 2) { start, _ ->
            calls++
            rows(50, start)
        }

        assertEquals(1, calls)
        assertEquals(50, result.size)
    }

    @Test
    fun `첫 페이지가 가득 차면 다음 100행을 이어서 읽는다`() = runTest {
        val startRows = mutableListOf<Int>()
        val result = collectPagedRows(pageSize = 100, firstRow = 2) { start, _ ->
            startRows.add(start)
            when (start) {
                2 -> rows(100, 2)   // 가득 참 → 다음 페이지
                102 -> rows(30, 102) // 부족 → 종료
                else -> emptyList()
            }
        }

        assertEquals(listOf(2, 102), startRows)
        assertEquals(130, result.size)
    }

    @Test
    fun `정확히 페이지 크기의 배수면 빈 페이지를 만나 멈춘다`() = runTest {
        val startRows = mutableListOf<Int>()
        collectPagedRows(pageSize = 100, firstRow = 2) { start, _ ->
            startRows.add(start)
            if (start == 2) rows(100, 2) else emptyList()
        }

        // 2행에서 100개(가득) → 102행 조회 → 0개 → 종료.
        assertEquals(listOf(2, 102), startRows)
    }
}
