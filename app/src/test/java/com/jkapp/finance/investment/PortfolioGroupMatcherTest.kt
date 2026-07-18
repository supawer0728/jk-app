package com.jkapp.finance.investment

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioGroupMatcherTest {

    private fun item(
        assetName: String = "주식계좌",
        category: String = "국내주식",
        investmentName: String = "삼성전자",
        valuationAmount: BigDecimal = BigDecimal("100"),
    ) = InvestmentItem(
        assetName = assetName,
        category = category,
        investmentName = investmentName,
        pricePerShare = BigDecimal("10"),
        valuationAmount = valuationAmount,
        quantity = BigDecimal("10"),
        purchaseAmount = CurrencyAmount(currency = "KRW", amount = BigDecimal("90")),
    )

    // ── matches: 축 내 OR, 축 간 AND, nullable 축 무시 ──

    @Test
    fun `모든 축이 비어 있으면(전체 허용) 모든 종목이 통과한다`() {
        val group = PortfolioGroup(name = "전체")
        assertTrue(PortfolioGroupMatcher.matches(group, "전지훈", item()))
    }

    @Test
    fun `owners 축은 축 내 OR로 매칭한다`() {
        val group = PortfolioGroup(name = "명의", owners = listOf("전지훈", "권유경"))
        assertTrue(PortfolioGroupMatcher.matches(group, "전지훈", item()))
        assertTrue(PortfolioGroupMatcher.matches(group, "권유경", item()))
        assertFalse(PortfolioGroupMatcher.matches(group, "타인", item()))
    }

    @Test
    fun `축 간에는 AND로 매칭한다 - 한 축이라도 불일치면 제외된다`() {
        val group = PortfolioGroup(
            name = "복합",
            owners = listOf("전지훈"),
            accounts = listOf("주식계좌"),
            categories = listOf("국내주식"),
        )
        // 모든 축 통과
        assertTrue(PortfolioGroupMatcher.matches(group, "전지훈", item(assetName = "주식계좌", category = "국내주식")))
        // 계좌 불일치
        assertFalse(PortfolioGroupMatcher.matches(group, "전지훈", item(assetName = "연금계좌", category = "국내주식")))
        // 카테고리 불일치
        assertFalse(PortfolioGroupMatcher.matches(group, "전지훈", item(assetName = "주식계좌", category = "해외주식")))
        // owner 불일치
        assertFalse(PortfolioGroupMatcher.matches(group, "권유경", item(assetName = "주식계좌", category = "국내주식")))
    }

    @Test
    fun `nullable 축(categories, stockNames)이 null이면 제한하지 않는다`() {
        val group = PortfolioGroup(name = "명의만", owners = listOf("전지훈"), categories = null, stockNames = null)
        assertTrue(PortfolioGroupMatcher.matches(group, "전지훈", item(category = "무엇이든", investmentName = "무엇이든")))
    }

    @Test
    fun `nullable 축이 빈 리스트여도 제한하지 않는다`() {
        val group = PortfolioGroup(name = "명의만", owners = listOf("전지훈"), categories = emptyList(), stockNames = emptyList())
        assertTrue(PortfolioGroupMatcher.matches(group, "전지훈", item(category = "무엇이든")))
    }

    @Test
    fun `stockNames 축은 종목명(investmentName)으로 매칭한다`() {
        val group = PortfolioGroup(name = "종목", stockNames = listOf("삼성전자", "네이버"))
        assertTrue(PortfolioGroupMatcher.matches(group, "전지훈", item(investmentName = "삼성전자")))
        assertFalse(PortfolioGroupMatcher.matches(group, "전지훈", item(investmentName = "카카오")))
    }

    // ── computeGroupAmounts: 원화 합산, 겹침 중복 합산, 미분류 제외 ──

    @Test
    fun `computeGroupAmounts는 그룹별 평가금액을 원화로 합산한다`() {
        val portfolio = Portfolio(
            name = "P",
            groups = listOf(
                PortfolioGroup(name = "국내", categories = listOf("국내주식")),
                PortfolioGroup(name = "해외", categories = listOf("해외주식")),
            ),
        )
        val pairs = listOf(
            "전지훈" to item(category = "국내주식", valuationAmount = BigDecimal("100")),
            "전지훈" to item(category = "국내주식", investmentName = "네이버", valuationAmount = BigDecimal("200")),
            "권유경" to item(category = "해외주식", investmentName = "애플", valuationAmount = BigDecimal("300")),
        )

        val amounts = PortfolioGroupMatcher.computeGroupAmounts(portfolio, pairs)

        assertEquals(BigDecimal("300"), amounts["국내"])
        assertEquals(BigDecimal("300"), amounts["해외"])
    }

    @Test
    fun `한 종목이 여러 그룹에 겹치면 각 그룹에 중복 합산된다`() {
        val portfolio = Portfolio(
            name = "P",
            groups = listOf(
                PortfolioGroup(name = "전지훈그룹", owners = listOf("전지훈")),
                PortfolioGroup(name = "국내그룹", categories = listOf("국내주식")),
            ),
        )
        val pairs = listOf(
            // 이 종목은 두 그룹 조건을 모두 만족한다.
            "전지훈" to item(category = "국내주식", valuationAmount = BigDecimal("500")),
        )

        val amounts = PortfolioGroupMatcher.computeGroupAmounts(portfolio, pairs)

        // 각 그룹에 500씩 중복 합산된다.
        assertEquals(BigDecimal("500"), amounts["전지훈그룹"])
        assertEquals(BigDecimal("500"), amounts["국내그룹"])
    }

    @Test
    fun `어느 그룹에도 속하지 않는 종목은 합산에서 제외된다`() {
        val portfolio = Portfolio(
            name = "P",
            groups = listOf(PortfolioGroup(name = "국내", categories = listOf("국내주식"))),
        )
        val pairs = listOf(
            "전지훈" to item(category = "국내주식", valuationAmount = BigDecimal("100")),
            // 미분류: 어느 그룹에도 속하지 않는다.
            "전지훈" to item(category = "채권", investmentName = "국고채", valuationAmount = BigDecimal("999")),
        )

        val amounts = PortfolioGroupMatcher.computeGroupAmounts(portfolio, pairs)

        assertEquals(BigDecimal("100"), amounts["국내"])
    }

    // ── computePieSlices: 미분류 제외한 분모로 실제 비율 계산 ──

    @Test
    fun `computePieSlices는 분류된 종목 합계를 분모로 실제 비율을 계산한다`() {
        val portfolio = Portfolio(
            name = "P",
            groups = listOf(
                PortfolioGroup(name = "국내", categories = listOf("국내주식"), targetRatio = 60),
                PortfolioGroup(name = "해외", categories = listOf("해외주식"), targetRatio = 40),
            ),
        )
        val pairs = listOf(
            "전지훈" to item(category = "국내주식", valuationAmount = BigDecimal("300")),
            "권유경" to item(category = "해외주식", investmentName = "애플", valuationAmount = BigDecimal("100")),
            // 미분류 종목은 분모에 포함되지 않는다.
            "전지훈" to item(category = "채권", investmentName = "국고채", valuationAmount = BigDecimal("1000")),
        )

        val slices = PortfolioGroupMatcher.computePieSlices(portfolio, pairs)

        // 분모 = 300 + 100 = 400 (미분류 1000 제외)
        val domesticSlice = slices.single { it.groupName == "국내" }
        val overseasSlice = slices.single { it.groupName == "해외" }
        assertEquals(BigDecimal("75.0"), domesticSlice.actualRatioPct)
        assertEquals(BigDecimal("25.0"), overseasSlice.actualRatioPct)
        assertEquals(BigDecimal("60"), domesticSlice.targetRatioPct)
        assertEquals(BigDecimal("40"), overseasSlice.targetRatioPct)
    }

    @Test
    fun `분류된 종목이 없으면 실제 비율은 0이다`() {
        val portfolio = Portfolio(
            name = "P",
            groups = listOf(PortfolioGroup(name = "국내", categories = listOf("국내주식"), targetRatio = 100)),
        )
        val pairs = listOf(
            "전지훈" to item(category = "채권", valuationAmount = BigDecimal("100")),
        )

        val slices = PortfolioGroupMatcher.computePieSlices(portfolio, pairs)

        assertEquals(BigDecimal.ZERO, slices.single().actualRatioPct)
    }

    @Test
    fun `두 명의가 값이 동일한 종목을 각각 보유해도 슬라이스 실제 비율 합은 100이다`() {
        // 그룹이 겹치지 않도록 owners로 분리한다. 두 종목은 값 동등성(assetName·category·
        // investmentName·금액)이 완전히 동일하다 — distinct 분모 버그가 있으면 한 pair로 뭉쳐
        // 분모가 절반이 되어 비율 합이 200%가 된다.
        val portfolio = Portfolio(
            name = "P",
            groups = listOf(
                PortfolioGroup(name = "전지훈", owners = listOf("전지훈"), targetRatio = 50),
                PortfolioGroup(name = "권유경", owners = listOf("권유경"), targetRatio = 50),
            ),
        )
        val sameItem = item(valuationAmount = BigDecimal("100"))
        val pairs = listOf(
            "전지훈" to sameItem,
            "권유경" to sameItem,
        )

        val slices = PortfolioGroupMatcher.computePieSlices(portfolio, pairs)

        // 각 그룹 100/200 = 50.0%. 합 = 100.0%.
        assertEquals(BigDecimal("50.0"), slices.single { it.groupName == "전지훈" }.actualRatioPct)
        assertEquals(BigDecimal("50.0"), slices.single { it.groupName == "권유경" }.actualRatioPct)
        assertEquals(BigDecimal("100.0"), slices.sumOf { it.actualRatioPct })
    }
}
