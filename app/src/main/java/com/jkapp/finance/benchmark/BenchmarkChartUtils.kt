package com.jkapp.finance.benchmark

import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import java.util.Locale

/**
 * 벤치마크 차트에서 사용하는 순수 유틸리티 함수 모음.
 * UI 컴포저블에 의존하지 않으므로 JVM 단위 테스트가 가능하다.
 */
object BenchmarkChartUtils {

    enum class ChartPeriod { MONTHS_3, MONTHS_6, YEAR_1, ALL }

    /**
     * rowMetrics 목록을 기간 프리셋으로 슬라이싱한다.
     *
     * - 기준일: 목록에서 가장 최신 날짜(오늘이 아님).
     * - rows는 어떤 순서로 전달해도 되며, 반환값은 날짜 오름차순이다.
     * - rows가 비어 있으면 빈 목록을 반환한다.
     * - ALL이면 정렬만 하고 필터 없이 전체를 반환한다.
     */
    fun filterByPeriod(rows: List<BenchmarkRowMetrics>, period: ChartPeriod): List<BenchmarkRowMetrics> {
        if (rows.isEmpty()) return emptyList()
        val sorted = rows.sortedBy { it.benchmark.date }
        if (period == ChartPeriod.ALL) return sorted
        val latestDate = LocalDate.parse(sorted.last().benchmark.date)
        val cutoff = when (period) {
            ChartPeriod.MONTHS_3 -> latestDate.minus(Period.ofMonths(3))
            ChartPeriod.MONTHS_6 -> latestDate.minus(Period.ofMonths(6))
            ChartPeriod.YEAR_1 -> latestDate.minus(Period.ofYears(1))
            ChartPeriod.ALL -> latestDate // 도달 불가(위에서 처리)
        }
        return sorted.filter { !LocalDate.parse(it.benchmark.date).isBefore(cutoff) }
    }

    /**
     * 금액(BigDecimal)을 K/M/G 단위 축약 문자열로 변환한다.
     *
     * - 1,000 미만: 그대로 (소수점 없음)
     * - 1,000 이상 ~ 1,000,000 미만: "NNNk"
     * - 1,000,000 이상 ~ 1,000,000,000 미만: "NNN.Nm" (소수 1자리, 필요 없으면 생략)
     * - 1,000,000,000 이상: "NNN.Ng" (소수 1자리, 필요 없으면 생략)
     */
    fun formatAmount(value: BigDecimal): String {
        val d = value.toDouble()
        return when {
            d >= 1_000_000_000.0 -> formatWithSuffix(d / 1_000_000_000.0, "g")
            d >= 1_000_000.0 -> formatWithSuffix(d / 1_000_000.0, "m")
            d >= 1_000.0 -> formatWithSuffix(d / 1_000.0, "k")
            else -> value.toBigInteger().toString()
        }
    }

    private fun formatWithSuffix(value: Double, suffix: String): String {
        val formatted = if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
        return "$formatted$suffix"
    }
}
