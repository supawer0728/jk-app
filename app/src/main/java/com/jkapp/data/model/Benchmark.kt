package com.jkapp.data.model

import java.math.BigDecimal
import java.math.RoundingMode

data class Benchmark(
    val firestoreId: String? = null,
    val date: String,
    val additionalInvestment: BigDecimal,
    val principal: BigDecimal,
    val currentAmount: BigDecimal,
    val kospi: BigDecimal,
    val snp500: BigDecimal,
    val nasdaq: BigDecimal,
)

// 누적 투입 원금(원금 + 추가투자) 대비 수익률(%). 원금과 추가투자가 모두 0이면 계산할 수 없으므로 null을 반환한다.
fun Benchmark.returnRatePercent(): BigDecimal? {
    val investedTotal = principal + additionalInvestment
    if (investedTotal.signum() == 0) return null
    return (currentAmount - investedTotal)
        .divide(investedTotal, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal(100))
        .setScale(2, RoundingMode.HALF_UP)
}
