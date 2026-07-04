package com.jkapp.data.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenchmarkTest {

    private fun makeBenchmark(
        date: String,
        additionalInvestment: BigDecimal = BigDecimal.ZERO,
        currentAmount: BigDecimal = BigDecimal.ZERO,
        kospi: BigDecimal = BigDecimal("1000"),
        snp500: BigDecimal = BigDecimal("1000"),
        nasdaq: BigDecimal = BigDecimal("1000"),
    ) = Benchmark(
        date = date,
        additionalInvestment = additionalInvestment,
        currentAmount = currentAmount,
        kospi = kospi,
        snp500 = snp500,
        nasdaq = nasdaq,
    )

    @Test
    fun `withRowMetrics는 날짜 오름차순으로 추가투자를 누적한 원금을 계산한다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000")),
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal("200000")),
            makeBenchmark("2026-07-03", additionalInvestment = BigDecimal.ZERO),
        )

        val result = benchmarks.withRowMetrics()

        assertEquals(BigDecimal("1000000"), result[0].principal)
        assertEquals(BigDecimal("1200000"), result[1].principal)
        assertEquals(BigDecimal("1200000"), result[2].principal)
    }

    @Test
    fun `withRowMetrics는 입력 순서와 상관없이 날짜순으로 계산한다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-03", additionalInvestment = BigDecimal.ZERO),
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000")),
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal("200000")),
        )

        val result = benchmarks.withRowMetrics()

        assertEquals(listOf("2026-07-01", "2026-07-02", "2026-07-03"), result.map { it.benchmark.date })
        assertEquals(BigDecimal("1200000"), result.last().principal)
    }

    @Test
    fun `profit과 returnRatePercent는 원금 대비 현재금액으로 계산한다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("1100000"))
        )

        val entry = benchmarks.withRowMetrics().single()

        assertEquals(BigDecimal("100000"), entry.profit)
        assertEquals(BigDecimal("10.00"), entry.returnRatePercent)
    }

    @Test
    fun `returnRatePercent은 손실일 때 음수를 반환한다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("900000"))
        )

        val entry = benchmarks.withRowMetrics().single()

        assertEquals(BigDecimal("-100000"), entry.profit)
        assertEquals(BigDecimal("-10.00"), entry.returnRatePercent)
    }

    @Test
    fun `returnRatePercent은 원금이 0이면 null이다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("100000"))
        )

        val entry = benchmarks.withRowMetrics().single()

        assertNull(entry.returnRatePercent)
        assertNull(entry.returnRateChangePercent)
        assertNull(entry.assetMdd)
    }

    @Test
    fun `returnRateChangePercent은 직전 날짜 수익률과의 퍼센트포인트 차이다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("1100000")),
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("1150000")),
        )

        val result = benchmarks.withRowMetrics()

        assertNull(result[0].returnRateChangePercent)
        // 1차 수익률 10.00%, 2차 수익률 (1150000-1000000)/1000000*100 = 15.00%
        assertEquals(BigDecimal("15.00"), result[1].returnRatePercent)
        assertEquals(BigDecimal("5.00"), result[1].returnRateChangePercent)
    }

    @Test
    fun `assetMdd는 수익률 시리즈의 고점 대비 하락폭을 누적으로 계산한다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("1200000")), // 20.00%
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("1100000")), // 10.00%
            makeBenchmark("2026-07-03", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("1300000")), // 30.00%
        )

        val result = benchmarks.withRowMetrics()

        assertEquals(BigDecimal("0.00"), result[0].assetMdd)
        assertEquals(BigDecimal("-10.00"), result[1].assetMdd)
        assertEquals(BigDecimal("0.00"), result[2].assetMdd)
    }

    @Test
    fun `지수 수익률은 가장 빠른 날짜 값 대비 변화율이다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", kospi = BigDecimal("1000")),
            makeBenchmark("2026-07-02", kospi = BigDecimal("1100")),
        )

        val result = benchmarks.withRowMetrics()

        assertEquals(BigDecimal("0.00"), result[0].kospi.returnRatePercent)
        assertEquals(BigDecimal("10.00"), result[1].kospi.returnRatePercent)
    }

    @Test
    fun `지수 상승률은 직전 날짜 대비 변화율이고 최초 항목은 null이다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", kospi = BigDecimal("1000")),
            makeBenchmark("2026-07-02", kospi = BigDecimal("900")),
        )

        val result = benchmarks.withRowMetrics()

        assertNull(result[0].kospi.changePercent)
        assertEquals(BigDecimal("-10.00"), result[1].kospi.changePercent)
    }

    @Test
    fun `지수 MDD는 원본 값 시리즈의 고점 대비 하락폭을 누적으로 계산한다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", kospi = BigDecimal("1000")),
            makeBenchmark("2026-07-02", kospi = BigDecimal("1200")),
            makeBenchmark("2026-07-03", kospi = BigDecimal("900")),
        )

        val result = benchmarks.withRowMetrics()

        assertEquals(BigDecimal("0.00"), result[0].kospi.mdd)
        assertEquals(BigDecimal("0.00"), result[1].kospi.mdd)
        // (900-1200)/1200*100 = -25.00
        assertEquals(BigDecimal("-25.00"), result[2].kospi.mdd)
    }

    @Test
    fun `S&P500과 나스닥도 동일한 방식으로 독립적으로 계산된다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", snp500 = BigDecimal("5000"), nasdaq = BigDecimal("16000")),
            makeBenchmark("2026-07-02", snp500 = BigDecimal("5500"), nasdaq = BigDecimal("15000")),
        )

        val result = benchmarks.withRowMetrics()

        assertEquals(BigDecimal("10.00"), result[1].snp500.returnRatePercent)
        assertEquals(BigDecimal("10.00"), result[1].snp500.changePercent)
        assertEquals(BigDecimal("0.00"), result[1].snp500.mdd)

        assertEquals(BigDecimal("-6.25"), result[1].nasdaq.returnRatePercent)
        assertEquals(BigDecimal("-6.25"), result[1].nasdaq.changePercent)
        assertEquals(BigDecimal("-6.25"), result[1].nasdaq.mdd)
    }
}
