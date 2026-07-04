package com.jkapp.data.model

import java.math.BigDecimal
import java.math.RoundingMode

data class Benchmark(
    val firestoreId: String? = null,
    val date: String,
    val additionalInvestment: BigDecimal,
    val currentAmount: BigDecimal,
    val kospi: BigDecimal,
    val snp500: BigDecimal,
    val nasdaq: BigDecimal,
)

// 원금은 별도로 입력받지 않고, 날짜 오름차순으로 지금까지의 추가투자를 누적한 합으로 계산한다.
data class BenchmarkPrincipal(
    val benchmark: Benchmark,
    val principal: BigDecimal,
)

// benchmarks는 어떤 순서로 전달해도 되며, 내부적으로 날짜 오름차순 정렬 후 누적 계산한다.
fun List<Benchmark>.withCumulativePrincipal(): List<BenchmarkPrincipal> {
    var cumulative = BigDecimal.ZERO
    return sortedBy { it.date }.map { benchmark ->
        cumulative += benchmark.additionalInvestment
        BenchmarkPrincipal(benchmark = benchmark, principal = cumulative)
    }
}

// 누적 원금 대비 수익률(%). 원금이 0이면 계산할 수 없으므로 null을 반환한다.
fun BenchmarkPrincipal.returnRatePercent(): BigDecimal? {
    if (principal.signum() == 0) return null
    return (benchmark.currentAmount - principal)
        .divide(principal, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal(100))
        .setScale(2, RoundingMode.HALF_UP)
}
