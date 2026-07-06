package com.jkapp.finance.investment

import java.math.BigDecimal

data class DailyAssetInvestment(
    val firestoreId: String? = null,
    val date: String,
    val owner: String,
    val investments: List<InvestmentItem> = emptyList(),
)

data class InvestmentItem(
    val assetName: String,
    val category: String,
    val investmentName: String,
    val pricePerShare: BigDecimal,
    val valuationAmount: BigDecimal,
    val quantity: BigDecimal,
    // 매수단가는 purchaseAmount / quantity로 계산해낼 수 있는 값이라 별도로 저장하지 않는다.
    val purchaseAmount: CurrencyAmount,
)

data class CurrencyAmount(
    val currency: String,
    val amount: BigDecimal,
)

data class InvestmentItemMetrics(
    val item: InvestmentItem,
    // 평가금액 - 매수금액. 양수면 이익, 음수면 손실.
    val profit: BigDecimal,
)

fun List<InvestmentItem>.withProfitMetrics(): List<InvestmentItemMetrics> =
    map { InvestmentItemMetrics(item = it, profit = it.valuationAmount - it.purchaseAmount.amount) }
