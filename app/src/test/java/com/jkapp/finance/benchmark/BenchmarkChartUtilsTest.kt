package com.jkapp.finance.benchmark

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkChartUtilsTest {

    // ── 테스트 픽스처 ────────────────────────────────────────────────

    private fun makeBenchmark(date: String) = Benchmark(
        date = date,
        additionalInvestment = BigDecimal.ZERO,
        currentAmount = BigDecimal("1000000"),
        kospi = BigDecimal("1000"),
        snp500 = BigDecimal("1000"),
        nasdaq = BigDecimal("1000"),
    )

    private fun makeRow(date: String): BenchmarkRowMetrics {
        val b = makeBenchmark(date)
        return BenchmarkRowMetrics(
            benchmark = b,
            principal = BigDecimal("1000000"),
            profit = BigDecimal.ZERO,
            returnRatePercent = BigDecimal.ZERO,
            returnRateChangePercent = null,
            assetMdd = null,
            kospi = IndexMetrics(BigDecimal("1000"), BigDecimal.ZERO, null, null),
            snp500 = IndexMetrics(BigDecimal("1000"), BigDecimal.ZERO, null, null),
            nasdaq = IndexMetrics(BigDecimal("1000"), BigDecimal.ZERO, null, null),
        )
    }

    // ── filterByPeriod ───────────────────────────────────────────────

    @Test
    fun `filterByPeriod ALL은 날짜 오름차순으로 전체를 반환한다`() {
        val rows = listOf(
            makeRow("2025-01-01"),
            makeRow("2026-01-01"),
            makeRow("2024-06-01"),
        )
        val result = BenchmarkChartUtils.filterByPeriod(rows, BenchmarkChartUtils.ChartPeriod.ALL)
        assertEquals(3, result.size)
        assertEquals("2024-06-01", result[0].benchmark.date)
        assertEquals("2025-01-01", result[1].benchmark.date)
        assertEquals("2026-01-01", result[2].benchmark.date)
    }

    @Test
    fun `filterByPeriod MONTHS_3는 최신 날짜 기준 3개월 이내만 반환한다`() {
        // 최신 = 2026-07-18, 기준일 = 2026-04-18
        val rows = listOf(
            makeRow("2026-07-18"), // 포함(기준일과 같음)
            makeRow("2026-04-18"), // 포함(기준일 = cutoff, isBefore가 false)
            makeRow("2026-04-17"), // 제외(기준일 이전)
            makeRow("2026-01-01"), // 제외
        )
        val result = BenchmarkChartUtils.filterByPeriod(rows, BenchmarkChartUtils.ChartPeriod.MONTHS_3)
        assertEquals(2, result.size)
        assertTrue(result.all { it.benchmark.date >= "2026-04-18" })
    }

    @Test
    fun `filterByPeriod MONTHS_6는 최신 날짜 기준 6개월 이내만 반환한다`() {
        val rows = listOf(
            makeRow("2026-07-18"),
            makeRow("2026-01-18"), // 포함(cutoff)
            makeRow("2026-01-17"), // 제외
            makeRow("2025-06-01"), // 제외
        )
        val result = BenchmarkChartUtils.filterByPeriod(rows, BenchmarkChartUtils.ChartPeriod.MONTHS_6)
        assertEquals(2, result.size)
    }

    @Test
    fun `filterByPeriod YEAR_1는 최신 날짜 기준 1년 이내만 반환한다`() {
        val rows = listOf(
            makeRow("2026-07-18"),
            makeRow("2025-07-18"), // 포함(cutoff)
            makeRow("2025-07-17"), // 제외
            makeRow("2024-01-01"), // 제외
        )
        val result = BenchmarkChartUtils.filterByPeriod(rows, BenchmarkChartUtils.ChartPeriod.YEAR_1)
        assertEquals(2, result.size)
    }

    @Test
    fun `filterByPeriod 빈 목록이면 빈 목록을 반환한다`() {
        val result = BenchmarkChartUtils.filterByPeriod(emptyList(), BenchmarkChartUtils.ChartPeriod.ALL)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterByPeriod 반환값은 날짜 오름차순이다`() {
        val rows = listOf(
            makeRow("2026-07-01"),
            makeRow("2026-05-01"),
            makeRow("2026-06-01"),
        )
        val result = BenchmarkChartUtils.filterByPeriod(rows, BenchmarkChartUtils.ChartPeriod.ALL)
        assertEquals(listOf("2026-05-01", "2026-06-01", "2026-07-01"), result.map { it.benchmark.date })
    }

    // ── formatAmount ─────────────────────────────────────────────────

    @Test
    fun `formatAmount 999 미만은 그대로 반환한다`() {
        assertEquals("0", BenchmarkChartUtils.formatAmount(BigDecimal.ZERO))
        assertEquals("500", BenchmarkChartUtils.formatAmount(BigDecimal("500")))
        assertEquals("999", BenchmarkChartUtils.formatAmount(BigDecimal("999")))
    }

    @Test
    fun `formatAmount 1000 이상은 k 접미사를 붙인다`() {
        assertEquals("1k", BenchmarkChartUtils.formatAmount(BigDecimal("1000")))
        assertEquals("10k", BenchmarkChartUtils.formatAmount(BigDecimal("10000")))
        assertEquals("999k", BenchmarkChartUtils.formatAmount(BigDecimal("999000")))
    }

    @Test
    fun `formatAmount 소수점이 있을 때 k 접미사와 소수 1자리를 포함한다`() {
        assertEquals("1.5k", BenchmarkChartUtils.formatAmount(BigDecimal("1500")))
        assertEquals("12.3k", BenchmarkChartUtils.formatAmount(BigDecimal("12300")))
    }

    @Test
    fun `formatAmount 1_000_000 이상은 m 접미사를 붙인다`() {
        assertEquals("1m", BenchmarkChartUtils.formatAmount(BigDecimal("1000000")))
        assertEquals("10m", BenchmarkChartUtils.formatAmount(BigDecimal("10000000")))
        assertEquals("1.5m", BenchmarkChartUtils.formatAmount(BigDecimal("1500000")))
    }

    @Test
    fun `formatAmount 1_000_000_000 이상은 g 접미사를 붙인다`() {
        assertEquals("1g", BenchmarkChartUtils.formatAmount(BigDecimal("1000000000")))
        assertEquals("1.2g", BenchmarkChartUtils.formatAmount(BigDecimal("1200000000")))
    }

    // ── percentAxisMax / percentAxisMin ──────────────────────────────

    @Test
    fun `percentAxisMax는 50 단위로 올림한다`() {
        assertEquals(150.0, BenchmarkChartUtils.percentAxisMax(123.0), 0.0)
        assertEquals(150.0, BenchmarkChartUtils.percentAxisMax(150.0), 0.0)
        assertEquals(100.0, BenchmarkChartUtils.percentAxisMax(51.0), 0.0)
    }

    @Test
    fun `percentAxisMax는 0 이하일 때 최소 50을 보장한다`() {
        assertEquals(50.0, BenchmarkChartUtils.percentAxisMax(0.0), 0.0)
        assertEquals(50.0, BenchmarkChartUtils.percentAxisMax(-5.0), 0.0)
        assertEquals(50.0, BenchmarkChartUtils.percentAxisMax(10.0), 0.0)
    }

    @Test
    fun `percentAxisMin은 손실이 없으면 0, 있으면 50 단위로 내림한다`() {
        assertEquals(0.0, BenchmarkChartUtils.percentAxisMin(30.0), 0.0)
        assertEquals(0.0, BenchmarkChartUtils.percentAxisMin(0.0), 0.0)
        assertEquals(-50.0, BenchmarkChartUtils.percentAxisMin(-10.0), 0.0)
        assertEquals(-50.0, BenchmarkChartUtils.percentAxisMin(-50.0), 0.0)
        assertEquals(-100.0, BenchmarkChartUtils.percentAxisMin(-51.0), 0.0)
    }

    // ── amountAxisMax ────────────────────────────────────────────────

    @Test
    fun `amountAxisMax는 데이터 최댓값 이상을 보장한다`() {
        assertTrue(BenchmarkChartUtils.amountAxisMax(1_234_567.0) >= 1_234_567.0)
        assertTrue(BenchmarkChartUtils.amountAxisMax(750_000.0) >= 750_000.0)
        assertTrue(BenchmarkChartUtils.amountAxisMax(99.0) >= 99.0)
    }

    @Test
    fun `amountAxisMax는 7구간 나눗셈이 깔끔한 값이 되도록 상단을 계산한다`() {
        // rawStep = 1_000_000/7 ≈ 142857 → nice step 200000 → top 1_400_000
        assertEquals(1_400_000.0, BenchmarkChartUtils.amountAxisMax(1_000_000.0), 0.0)
        // rawStep = 700_000/7 = 100000 → nice step 100000 → top 700_000 (데이터와 동일)
        assertEquals(700_000.0, BenchmarkChartUtils.amountAxisMax(700_000.0), 0.0)
    }
}
