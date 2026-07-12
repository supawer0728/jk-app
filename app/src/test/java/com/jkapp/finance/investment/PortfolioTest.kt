package com.jkapp.finance.investment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PortfolioTest {

    @Test
    fun `그룹 목표 비율 합이 정확히 100이면 유효하다(null 반환)`() {
        val groups = listOf(
            PortfolioGroup(name = "A", targetRatio = 30),
            PortfolioGroup(name = "B", targetRatio = 50),
            PortfolioGroup(name = "C", targetRatio = 20),
        )
        assertNull(validatePortfolioGroups(groups))
    }

    @Test
    fun `그룹 목표 비율 합이 100 미만이면 오류 메시지를 반환한다`() {
        val groups = listOf(
            PortfolioGroup(name = "A", targetRatio = 30),
            PortfolioGroup(name = "B", targetRatio = 50),
        )
        val error = validatePortfolioGroups(groups)
        assertNotNull(error)
        assertEquals("그룹 목표 비율 합계가 80%입니다. 정확히 100%가 되어야 저장할 수 있습니다.", error)
    }

    @Test
    fun `그룹 목표 비율 합이 100 초과면 오류 메시지를 반환한다`() {
        val groups = listOf(
            PortfolioGroup(name = "A", targetRatio = 70),
            PortfolioGroup(name = "B", targetRatio = 50),
        )
        assertNotNull(validatePortfolioGroups(groups))
    }

    @Test
    fun `그룹이 비어 있으면 합이 0이라 유효하지 않다`() {
        assertNotNull(validatePortfolioGroups(emptyList()))
    }
}
