package com.jkapp.finance.benchmark

import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 벤치마크 차트에서 사용하는 순수 유틸리티 함수 모음.
 * UI 컴포저블에 의존하지 않으므로 JVM 단위 테스트가 가능하다.
 */
object BenchmarkChartUtils {

    enum class ChartPeriod { MONTHS_3, MONTHS_6, YEAR_1, ALL }

    /** 수익률 차트 좌·우 세로축에 공통으로 그릴 눈금(라벨) 개수. */
    const val AXIS_TICK_COUNT = 8

    /** 눈금 개수(AXIS_TICK_COUNT)로 만들어지는 구간 수 = 눈금 - 1. */
    private const val AXIS_INTERVALS = AXIS_TICK_COUNT - 1

    /** 수익률(%) 세로축 단위. 최댓값을 이 단위로 올림한다. */
    private const val PERCENT_UNIT = 50.0

    /**
     * 수익률(%) 세로축 최댓값. 데이터 최대 수익률을 50% 단위로 올림한다(최소 50).
     * 예: 123 → 150, 150 → 150, 0/음수 → 50.
     */
    fun percentAxisMax(maxReturn: Double): Double {
        val rounded = ceil(maxReturn / PERCENT_UNIT) * PERCENT_UNIT
        return if (rounded < PERCENT_UNIT) PERCENT_UNIT else rounded
    }

    /**
     * 수익률(%) 세로축 최솟값. 음수 손실이 잘리지 않도록 50% 단위로 내림하고,
     * 손실이 없으면 0을 바닥으로 쓴다. 예: 30 → 0, -10 → -50, -50 → -50.
     */
    fun percentAxisMin(minReturn: Double): Double {
        if (minReturn >= 0.0) return 0.0
        return floor(minReturn / PERCENT_UNIT) * PERCENT_UNIT
    }

    /**
     * 현재금액 세로축 최댓값. 0을 바닥으로 AXIS_TICK_COUNT개의 눈금을 그릴 때,
     * 각 눈금 간격이 1/2/5×10ⁿ 형태의 "깔끔한" 값이 되도록 올림한 상단값을 돌려준다.
     * 반환값은 항상 데이터 최댓값 이상이므로 막대가 최상단 눈금을 넘지 않는다.
     */
    fun amountAxisMax(maxAmount: Double): Double {
        if (maxAmount <= 0.0) return AXIS_INTERVALS.toDouble()
        val rawStep = maxAmount / AXIS_INTERVALS
        return niceCeilStep(rawStep) * AXIS_INTERVALS
    }

    /** value 이상인 가장 작은 1/2/5×10ⁿ 값을 돌려준다. */
    private fun niceCeilStep(value: Double): Double {
        val magnitude = 10.0.pow(floor(log10(value)))
        val normalized = value / magnitude
        val niceNormalized = when {
            normalized <= 1.0 -> 1.0
            normalized <= 2.0 -> 2.0
            normalized <= 5.0 -> 5.0
            else -> 10.0
        }
        return niceNormalized * magnitude
    }

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
