package com.jkapp.data.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenchmarkTest {

    private fun makeBenchmark(
        principal: BigDecimal = BigDecimal.ZERO,
        additionalInvestment: BigDecimal = BigDecimal.ZERO,
        currentAmount: BigDecimal = BigDecimal.ZERO,
    ) = Benchmark(
        date = "2026-07-04",
        additionalInvestment = additionalInvestment,
        principal = principal,
        currentAmount = currentAmount,
        kospi = BigDecimal.ZERO,
        snp500 = BigDecimal.ZERO,
        nasdaq = BigDecimal.ZERO,
    )

    @Test
    fun `returnRatePercent은 원금과 추가투자 합계 대비 수익률을 계산한다`() {
        val benchmark = makeBenchmark(
            principal = BigDecimal("1000000"),
            additionalInvestment = BigDecimal("200000"),
            currentAmount = BigDecimal("1320000"),
        )

        assertEquals(BigDecimal("10.00"), benchmark.returnRatePercent())
    }

    @Test
    fun `returnRatePercent은 손실일 때 음수를 반환한다`() {
        val benchmark = makeBenchmark(
            principal = BigDecimal("1000000"),
            additionalInvestment = BigDecimal.ZERO,
            currentAmount = BigDecimal("900000"),
        )

        assertEquals(BigDecimal("-10.00"), benchmark.returnRatePercent())
    }

    @Test
    fun `returnRatePercent은 원금과 추가투자 합계가 0이면 null이다`() {
        val benchmark = makeBenchmark(
            principal = BigDecimal.ZERO,
            additionalInvestment = BigDecimal.ZERO,
            currentAmount = BigDecimal("100000"),
        )

        assertNull(benchmark.returnRatePercent())
    }
}
