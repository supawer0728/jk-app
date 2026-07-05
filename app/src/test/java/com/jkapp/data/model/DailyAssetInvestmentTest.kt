package com.jkapp.data.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyAssetInvestmentTest {

    private fun makeInvestmentItem(
        valuationAmount: BigDecimal,
        purchaseAmount: BigDecimal,
    ) = InvestmentItem(
        assetName = "주식계좌",
        category = "국내주식",
        investmentName = "삼성전자",
        pricePerShare = BigDecimal("70000"),
        valuationAmount = valuationAmount,
        purchasePrice = PurchasePrice(currency = "KRW", amount = BigDecimal("65000")),
        quantity = BigDecimal("10"),
        purchaseAmount = purchaseAmount,
    )

    @Test
    fun `withProfitMetrics는 평가금액에서 매수금액을 뺀 수익금을 계산한다`() {
        val items = listOf(
            makeInvestmentItem(valuationAmount = BigDecimal("700000"), purchaseAmount = BigDecimal("650000")),
            makeInvestmentItem(valuationAmount = BigDecimal("500000"), purchaseAmount = BigDecimal("650000")),
        )

        val result = items.withProfitMetrics()

        assertEquals(BigDecimal("50000"), result[0].profit)
        assertEquals(BigDecimal("-150000"), result[1].profit)
    }

    @Test
    fun `withProfitMetrics는 빈 목록이면 빈 목록을 반환한다`() {
        assertEquals(emptyList<InvestmentItemMetrics>(), emptyList<InvestmentItem>().withProfitMetrics())
    }
}
