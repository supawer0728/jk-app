package com.jkapp.finance.benchmark

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

    // AC-5: 최초 행은 returnRateChangePercent·assetMdd 모두 null
    @Test
    fun `최초 행은 returnRateChangePercent와 assetMdd가 null이다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("1100000")),
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("1150000")),
        )

        val result = benchmarks.withRowMetrics()

        assertNull(result[0].returnRateChangePercent)
        assertNull(result[0].assetMdd)
    }

    // returnRateChangePercent는 이제 TWR 기간수익률 rₜ
    @Test
    fun `returnRateChangePercent는 TWR 기간수익률이다`() {
        val benchmarks = listOf(
            // 1일차: 100만 투자, 현재가 110만
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("1100000")),
            // 2일차: 추가투자 없이 110만 → 115만 (+5만 상승)
            // rₜ = (1150000 - 1100000 - 0) / 1100000 * 100 ≈ 4.55%
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("1150000")),
        )

        val result = benchmarks.withRowMetrics()

        // (1150000 - 1100000 - 0) / 1100000 * 100 = 50000/1100000*100 = 4.5454...% → 4.55%
        assertEquals(BigDecimal("4.55"), result[1].returnRateChangePercent)
    }

    // AC-2: 추가투자만 있고 시장 변동 없는 날은 기간수익률 정확히 0%
    @Test
    fun `추가투자만 있고 시장 변동 없는 날은 returnRateChangePercent가 0이다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("1000000")),
            // currentAmount = 직전(1000000) + additionalInvestment(500000) = 1500000 → 시장 변동 없음
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal("500000"), currentAmount = BigDecimal("1500000")),
        )

        val result = benchmarks.withRowMetrics()

        // rₜ = (1500000 - 1000000 - 500000) / 1000000 * 100 = 0
        assertEquals(BigDecimal("0.00"), result[1].returnRateChangePercent)
    }

    // AC-3: assetMdd는 TWR 성과지수 기준 % 나눗셈으로 계산한다
    @Test
    fun `assetMdd는 TWR 성과지수 고점 대비 하락폭을 누적으로 계산한다`() {
        // 1일차: 100만 투자, 현재 120만 (최초 행 → MDD null)
        // 2일차: 추가투자 없이 120만 → 110만 (하락)
        //   rₜ = (110만 - 120만 - 0) / 120만 * 100 = -8.3333...% → -8.33%
        //   I₁ = 1 * (1 - 0.0833...) ≈ 0.91667
        //   고점 = 0.91667, MDD = 0 → 0.00%
        // 3일차: 추가투자 없이 110만 → 130만 (반등)
        //   rₜ = (130만 - 110만 - 0) / 110만 * 100 = 18.1818...% → 18.18%
        //   I₂ = 0.91667 * 1.1818... ≈ 1.0833
        //   고점 = 1.0833, MDD = 0 → 0.00%
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal("1200000")),
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("1100000")),
            makeBenchmark("2026-07-03", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("1300000")),
        )

        val result = benchmarks.withRowMetrics()

        assertNull(result[0].assetMdd)
        // 2일차: Iₜ가 고점 대비 하락 → MDD 음수
        // rₜ = (1100000-1200000-0)/1200000*100 = -8.3333→ -8.33%
        // I₁ = 1 * (1 + (-8.33/100)) = 0.9167 (내부 고정밀)
        // 고점 = 1.0(초기), I₁ < 1이면 MDD = (I₁-1)/1*100 < 0
        // 실제 MDD = (0.9167-1)/1*100 = -8.33% (대략)
        // 정확히는 factor = 1 + (-8.3333.../100) 로 고정밀 계산
        // MDD ≈ (1100000-1200000)/1200000*100 = -8.33
        assertEquals(BigDecimal("-8.33"), result[1].assetMdd)
        // 3일차: 반등 후 고점 돌파 → MDD 0.00
        assertEquals(BigDecimal("0.00"), result[2].assetMdd)
    }

    // AC-4: 하락 국면 대규모 추가투자가 있어도 시장 낙폭을 왜곡 없이 반영
    @Test
    fun `하락 국면에 대규모 추가투자가 있어도 assetMdd가 실제 시장 낙폭을 반영한다`() {
        // 1일차: 1000만 투자, 현재 1000만 (최초 행)
        // 2일차: 시장이 -10% 하락 → currentAmount = 900만 (추가투자 없음)
        //   rₜ = (900만 - 1000만 - 0) / 1000만 * 100 = -10%
        //   I₁ = 1 * 0.9 = 0.9, 고점 = 1.0, MDD = (0.9-1)/1*100 = -10%
        // 3일차: 대규모 추가투자 1000만 + 시장 변동 없음 → currentAmount = 1900만
        //   rₜ = (1900만 - 900만 - 1000만) / 900만 * 100 = 0%
        //   I₂ = 0.9 * 1.0 = 0.9, 고점 = 1.0, MDD = -10% (추가투자로 왜곡되지 않음)
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("10000000"), currentAmount = BigDecimal("10000000")),
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("9000000")),
            makeBenchmark("2026-07-03", additionalInvestment = BigDecimal("10000000"), currentAmount = BigDecimal("19000000")),
        )

        val result = benchmarks.withRowMetrics()

        assertNull(result[0].assetMdd)
        assertEquals(BigDecimal("-10.00"), result[1].assetMdd)
        // 대규모 추가투자가 있어도 시장 낙폭(-10%)이 MDD에 계속 반영됨
        assertEquals(BigDecimal("-10.00"), result[2].assetMdd)
    }

    // AC-5: 직전 currentAmount가 0인 기간은 rₜ=0%로 간주
    @Test
    fun `직전 currentAmount가 0이면 기간수익률을 0으로 간주한다`() {
        val benchmarks = listOf(
            // 1일차: 추가투자 있지만 현재금액 0 (아직 실제 투자 미실행 등)
            makeBenchmark("2026-07-01", additionalInvestment = BigDecimal("1000000"), currentAmount = BigDecimal.ZERO),
            // 2일차: 100만 유입
            makeBenchmark("2026-07-02", additionalInvestment = BigDecimal.ZERO, currentAmount = BigDecimal("1000000")),
        )

        val result = benchmarks.withRowMetrics()

        // 직전 currentAmount = 0 → rₜ = 0%, 성과지수 연속성 유지
        assertEquals(BigDecimal("0.00"), result[1].returnRateChangePercent)
        // 성과지수 = 1 * 1.0 = 1.0, 고점 = 1.0, MDD = 0
        assertEquals(BigDecimal("0.00"), result[1].assetMdd)
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
    fun `지수 MDD는 누적 고점이 0이면 계산 불가로 null을 반환한다`() {
        val benchmarks = listOf(
            makeBenchmark("2026-07-01", kospi = BigDecimal.ZERO),
            makeBenchmark("2026-07-02", kospi = BigDecimal("-100")),
        )

        val result = benchmarks.withRowMetrics()

        assertNull(result[0].kospi.mdd)
        assertNull(result[1].kospi.mdd)
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
