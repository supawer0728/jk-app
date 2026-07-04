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
    ) = Benchmark(
        date = date,
        additionalInvestment = additionalInvestment,
        currentAmount = currentAmount,
        kospi = BigDecimal.ZERO,
        snp500 = BigDecimal.ZERO,
        nasdaq = BigDecimal.ZERO,
    )

    @Test
    fun `withCumulativePrincipal은 날짜 오름차순으로 추가투자를 누적한 원금을 계산한다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000")),
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal("200000")),
            makeBenchmark("2026-07-03", additionalInvestment = BigDecimal.ZERO),
        )

        val result = benchmarks.withCumulativePrincipal()

        assertEquals(BigDecimal("1000000"), result[0].principal)
        assertEquals(BigDecimal("1200000"), result[1].principal)
        assertEquals(BigDecimal("1200000"), result[2].principal)
    }

    @Test
    fun `withCumulativePrincipal은 입력 순서와 상관없이 날짜순으로 계산한다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-03", additionalInvestment = BigDecimal.ZERO),
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000")),
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal("200000")),
        )

        val result = benchmarks.withCumulativePrincipal()

        assertEquals(listOf("2026-07-01", "2026-07-02", "2026-07-03"), result.map { it.benchmark.date })
        assertEquals(BigDecimal("1200000"), result.last().principal)
    }

    @Test
    fun `returnRatePercent은 누적 원금 대비 수익률을 계산한다`() {
        val benchmarks = listOf(makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("1100000")))

        val entry = benchmarks.withCumulativePrincipal().single()

        assertEquals(BigDecimal("10.00"), entry.returnRatePercent())
    }

    @Test
    fun `returnRatePercent은 손실일 때 음수를 반환한다`() {
        val benchmarks = listOf(makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("900000")))

        val entry = benchmarks.withCumulativePrincipal().single()

        assertEquals(BigDecimal("-10.00"), entry.returnRatePercent())
    }

    @Test
    fun `returnRatePercent은 누적 원금이 0이면 null이다`() {
        val benchmarks = listOf(makeBenchmark("2026-07-01", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("100000")))

        val entry = benchmarks.withCumulativePrincipal().single()

        assertNull(entry.returnRatePercent())
    }
}
