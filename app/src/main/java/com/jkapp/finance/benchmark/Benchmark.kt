package com.jkapp.finance.benchmark

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

// KOSPI/S&P500/나스닥처럼 순수 가격 시리즈에 대해 공통으로 쓰이는 파생 지표.
data class IndexMetrics(
    val value: BigDecimal,
    // 가장 빠른 날짜의 값 대비 수익률(%). 최초 항목은 자기 자신과 비교하므로 0이다.
    val returnRatePercent: BigDecimal,
    // 직전 날짜 대비 상승률(%). 직전 항목이 없으면(최초 항목) null.
    val changePercent: BigDecimal?,
    // 지금까지의 고점 대비 하락폭(%, 0 이하). 원본 값 시리즈 기준으로 계산한다.
    // 고점이 0이면 변화율을 정의할 수 없으므로 null(0%로 오인되지 않도록 값을 대체하지 않는다).
    val mdd: BigDecimal?,
)

data class BenchmarkRowMetrics(
    val benchmark: Benchmark,
    // 원금은 별도로 입력받지 않고, 날짜 오름차순으로 지금까지의 추가투자를 누적한 합으로 계산한다.
    val principal: BigDecimal,
    // 수익금 = 현재금액 - 원금.
    val profit: BigDecimal,
    // 누적 원금 대비 수익률(%). 원금이 0이면 계산할 수 없으므로 null.
    val returnRatePercent: BigDecimal?,
    // TWR 기간수익률 rₜ = (오늘 currentAmount − 직전 currentAmount − 오늘 additionalInvestment) / 직전 currentAmount × 100 (%).
    // 최초 행은 직전값이 없으므로 null. 직전 currentAmount가 0이면 0으로 간주한다.
    val returnRateChangePercent: BigDecimal?,
    // TWR 성과지수(Iₜ = ∏(1 + rₜ/100)) 기준 고점 대비 하락폭(%, 0 이하, 나눗셈 기반). 최초 행은 null.
    val assetMdd: BigDecimal?,
    val kospi: IndexMetrics,
    val snp500: IndexMetrics,
    val nasdaq: IndexMetrics,
)

private val PERCENT_SCALE = BigDecimal(100)
private const val TWR_DIVIDE_SCALE = 10

// from 대비 to의 변화율(%). from이 0이면 계산할 수 없으므로 null.
private fun percentChange(from: BigDecimal, to: BigDecimal): BigDecimal? {
    if (from.signum() == 0) return null
    return (to - from)
        .divide(from, 4, RoundingMode.HALF_UP)
        .multiply(PERCENT_SCALE)
        .setScale(2, RoundingMode.HALF_UP)
}

// values는 날짜 오름차순으로 전달되어야 한다.
private fun computeIndexMetrics(values: List<BigDecimal>): List<IndexMetrics> {
    if (values.isEmpty()) return emptyList()
    val first = values.first()
    var peak = first
    var previous: BigDecimal? = null
    return values.map { value ->
        val returnRate = percentChange(first, value) ?: BigDecimal.ZERO
        val change = previous?.let { percentChange(it, value) }
        peak = peak.max(value)
        val mdd = percentChange(peak, value)
        previous = value
        IndexMetrics(value = value, returnRatePercent = returnRate, changePercent = change, mdd = mdd)
    }
}

// benchmarks는 어떤 순서로 전달해도 되며, 내부적으로 날짜 오름차순 정렬 후 계산해 같은 순서로 반환한다.
fun List<Benchmark>.withRowMetrics(): List<BenchmarkRowMetrics> {
    val chronological = sortedBy { it.date }
    val kospiMetrics = computeIndexMetrics(chronological.map { it.kospi })
    val snp500Metrics = computeIndexMetrics(chronological.map { it.snp500 })
    val nasdaqMetrics = computeIndexMetrics(chronological.map { it.nasdaq })

    var cumulativePrincipal = BigDecimal.ZERO
    var previousAmount: BigDecimal? = null
    // TWR 성과지수: I₀ = 1, Iₜ = I₀ × ∏(1 + rₜ/100). 내부 계산 변수.
    var performanceIndex = BigDecimal.ONE
    var performanceIndexPeak = BigDecimal.ONE

    return chronological.mapIndexed { index, benchmark ->
        cumulativePrincipal += benchmark.additionalInvestment
        val principal = cumulativePrincipal
        val profit = benchmark.currentAmount - principal
        val returnRate = percentChange(principal, benchmark.currentAmount)

        // 최초 행(index == 0)은 직전값이 없어 기간수익률·MDD를 정의할 수 없다.
        // previousAmount는 다음 행으로 값을 넘기는 캐리 용도로만 쓴다.
        val prev = previousAmount
        val (returnRateChange, assetMdd) = if (index == 0 || prev == null) {
            Pair(null, null)
        } else {
            // 기간수익률 rₜ: 직전 currentAmount가 0이면 0으로 간주.
            // 표시용 값이므로 percentChange와 동일하게 나눗셈 scale 4 → 최종 2자리로 반올림한다.
            val periodReturn = if (prev.signum() == 0) {
                BigDecimal.ZERO.setScale(2)
            } else {
                (benchmark.currentAmount - prev - benchmark.additionalInvestment)
                    .divide(prev, 4, RoundingMode.HALF_UP)
                    .multiply(PERCENT_SCALE)
                    .setScale(2, RoundingMode.HALF_UP)
            }

            // TWR 성과지수 누적 곱: 팩터에는 2자리로 반올림한 rₜ를 쓰되(화면 표시값과 검산 일치),
            // 곱 연산 자체는 중간 반올림 없이 고정밀도로 수행한다. 누적 곱 정밀도 유지를 위해 나눗셈 scale은 10.
            val factor = BigDecimal.ONE + periodReturn.divide(PERCENT_SCALE, TWR_DIVIDE_SCALE, RoundingMode.HALF_UP)
            performanceIndex = performanceIndex.multiply(factor)
            performanceIndexPeak = performanceIndexPeak.max(performanceIndex)

            // 자산 MDD: (Iₜ − 고점) / 고점 × 100 (나눗셈 기반, 최종 2자리 반올림)
            val mdd = (performanceIndex - performanceIndexPeak)
                .divide(performanceIndexPeak, 4, RoundingMode.HALF_UP)
                .multiply(PERCENT_SCALE)
                .setScale(2, RoundingMode.HALF_UP)

            Pair(periodReturn, mdd)
        }

        previousAmount = benchmark.currentAmount

        BenchmarkRowMetrics(
            benchmark = benchmark,
            principal = principal,
            profit = profit,
            returnRatePercent = returnRate,
            returnRateChangePercent = returnRateChange,
            assetMdd = assetMdd,
            kospi = kospiMetrics[index],
            snp500 = snp500Metrics[index],
            nasdaq = nasdaqMetrics[index],
        )
    }
}
