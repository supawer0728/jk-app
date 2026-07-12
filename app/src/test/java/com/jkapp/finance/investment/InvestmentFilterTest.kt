package com.jkapp.finance.investment

import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentFilterTest {

    private fun item(
        assetName: String = "주식계좌",
        category: String = "국내주식",
        investmentName: String = "삼성전자",
    ) = InvestmentItem(
        assetName = assetName,
        category = category,
        investmentName = investmentName,
        pricePerShare = BigDecimal("10"),
        valuationAmount = BigDecimal("100"),
        quantity = BigDecimal("10"),
        purchaseAmount = CurrencyAmount(currency = "KRW", amount = BigDecimal("90")),
    )

    @Test
    fun `빈 필터는 isEmpty이고 모든 종목을 통과시킨다`() {
        val filter = InvestmentFilter()
        assertTrue(filter.isEmpty)
        assertTrue(filter.matches("전지훈", item()))
    }

    @Test
    fun `각 축은 축 내 OR로 매칭한다`() {
        val filter = InvestmentFilter(owners = setOf("전지훈", "권유경"))
        assertTrue(filter.matches("전지훈", item()))
        assertTrue(filter.matches("권유경", item()))
        assertFalse(filter.matches("타인", item()))
    }

    @Test
    fun `축 간에는 AND로 매칭한다`() {
        val filter = InvestmentFilter(
            owners = setOf("전지훈"),
            accounts = setOf("주식계좌"),
            categories = setOf("국내주식"),
            stockNames = setOf("삼성전자"),
        )
        assertTrue(filter.matches("전지훈", item()))
        assertFalse(filter.matches("전지훈", item(assetName = "연금계좌")))
        assertFalse(filter.matches("전지훈", item(category = "해외주식")))
        assertFalse(filter.matches("전지훈", item(investmentName = "네이버")))
        assertFalse(filter.matches("권유경", item()))
    }

    @Test
    fun `지정되지 않은(빈 Set) 축은 필터하지 않는다`() {
        // 계좌만 지정하고 나머지는 전체 허용.
        val filter = InvestmentFilter(accounts = setOf("주식계좌"))
        assertFalse(filter.isEmpty)
        assertTrue(filter.matches("아무명의", item(assetName = "주식계좌", category = "무엇이든", investmentName = "무엇이든")))
        assertFalse(filter.matches("아무명의", item(assetName = "연금계좌")))
    }
}
