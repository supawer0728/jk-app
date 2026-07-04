package com.jkapp.ui

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkSheetPasteTest {

    private fun row(vararg cells: String) = cells.joinToString("\t")

    private val fullHeader = row(
        "날짜", "추가투자", "원금", "수익", "수익률", "계", "", "벤치마킹/날짜",
        "KOSPI", "KOSPI 상승률", "KOSPI전월대비",
        "S&P500", "S&P500 상승률", "S&P500 전월대비",
        "나스닥", "나스닥 상승률", "나스닥 전월대비",
        "자산 수익률", "자산 전월대비",
        "KOSPI MDD", "S&P500 MDD", "나스닥 MDD", "자산 MDD",
    )

    private fun fullDataRow(
        date: String = "2026-07-03",
        additionalInvestment: String = "₩0",
        principal: String = "₩364,500,000",
        currentAmount: String = "₩576,968,966",
        kospi: String = "8930.3",
        snp500: String = "7358.22",
        nasdaq: String = "25476.64",
    ) = row(
        date, additionalInvestment, principal, "₩212,468,966", "58.29%", currentAmount, "", date,
        kospi, "192.89%", "0.00%",
        snp500, "60.84%", "0.00%",
        nasdaq, "67.22%", "0.00%",
        "58.29%", "-0.06%",
        "0.00%", "-1.54%", "-3.29%", "-0.58%",
    )

    @Test
    fun `파생 계산 열이 섞여 있어도 필요한 7개 열만 헤더 이름으로 찾아 파싱한다`() {
        val text = listOf(fullHeader, fullDataRow()).joinToString("\n")

        val result = parseBenchmarkSheetPaste(text)

        assertEquals(1, result.size)
        val benchmark = result.single().benchmark!!
        assertEquals("2026-07-03", benchmark.date)
        assertEquals(BigDecimal("0"), benchmark.additionalInvestment)
        assertEquals(BigDecimal("364500000"), benchmark.principal)
        assertEquals(BigDecimal("576968966"), benchmark.currentAmount)
        assertEquals(BigDecimal("8930.3"), benchmark.kospi)
        assertEquals(BigDecimal("7358.22"), benchmark.snp500)
        assertEquals(BigDecimal("25476.64"), benchmark.nasdaq)
    }

    @Test
    fun `KOSPI와 KOSPI 상승률처럼 접두사가 같은 열을 혼동하지 않는다`() {
        val text = listOf(fullHeader, fullDataRow(kospi = "1111.1")).joinToString("\n")

        val result = parseBenchmarkSheetPaste(text)

        assertEquals(BigDecimal("1111.1"), result.single().benchmark?.kospi)
    }

    @Test
    fun `열 순서가 달라도 헤더 이름만 맞으면 정상 파싱한다`() {
        val header = row("KOSPI", "나스닥", "S&P500", "날짜", "원금", "추가투자", "현재금액")
        val data = row("9000", "26000", "7500", "2026-07-04", "1000000", "0", "1200000")
        val text = listOf(header, data).joinToString("\n")

        val result = parseBenchmarkSheetPaste(text)

        val benchmark = result.single().benchmark!!
        assertEquals("2026-07-04", benchmark.date)
        assertEquals(BigDecimal("1000000"), benchmark.principal)
        assertEquals(BigDecimal("1200000"), benchmark.currentAmount)
        assertEquals(BigDecimal("9000"), benchmark.kospi)
        assertEquals(BigDecimal("7500"), benchmark.snp500)
        assertEquals(BigDecimal("26000"), benchmark.nasdaq)
    }

    @Test
    fun `현재금액 열은 계라는 헤더 이름도 인식한다`() {
        val header = row("날짜", "추가투자", "원금", "계", "KOSPI", "S&P500", "나스닥")
        val data = row("2026-07-04", "0", "1000000", "1200000", "9000", "7500", "26000")
        val text = listOf(header, data).joinToString("\n")

        val result = parseBenchmarkSheetPaste(text)

        assertEquals(BigDecimal("1200000"), result.single().benchmark?.currentAmount)
    }

    @Test
    fun `필요한 열이 헤더에 없으면 모든 데이터 행이 에러로 처리된다`() {
        val header = row("날짜", "원금")
        val data = row("2026-07-04", "1000000")
        val text = listOf(header, data).joinToString("\n")

        val result = parseBenchmarkSheetPaste(text)

        val parsed = result.single()
        assertNull(parsed.benchmark)
        assertTrue(parsed.error!!.contains("헤더에서"))
    }

    @Test
    fun `날짜 형식이 올바르지 않으면 에러로 처리한다`() {
        val text = listOf(fullHeader, fullDataRow(date = "2026.07.03")).joinToString("\n")

        val result = parseBenchmarkSheetPaste(text)

        val parsed = result.single()
        assertNull(parsed.benchmark)
        assertTrue(parsed.error!!.contains("날짜 형식"))
    }

    @Test
    fun `숫자로 변환할 수 없는 값은 에러로 처리한다`() {
        val text = listOf(fullHeader, fullDataRow(principal = "미정")).joinToString("\n")

        val result = parseBenchmarkSheetPaste(text)

        val parsed = result.single()
        assertNull(parsed.benchmark)
        assertTrue(parsed.error!!.contains("원금 값을 숫자로 변환할 수 없습니다"))
    }

    @Test
    fun `같은 붙여넣기 안에 날짜가 중복되면 두 행 모두 에러로 처리한다`() {
        val text = listOf(fullHeader, fullDataRow(date = "2026-07-03"), fullDataRow(date = "2026-07-03")).joinToString("\n")

        val result = parseBenchmarkSheetPaste(text)

        assertEquals(2, result.size)
        assertNull(result[0].benchmark)
        assertTrue(result[0].error!!.contains("중복"))
        assertNull(result[1].benchmark)
        assertTrue(result[1].error!!.contains("중복"))
    }

    @Test
    fun `빈 줄은 무시한다`() {
        val text = listOf(fullHeader, "", fullDataRow(date = "2026-07-01"), "\t\t\t", fullDataRow(date = "2026-07-02")).joinToString("\n")

        val result = parseBenchmarkSheetPaste(text)

        assertEquals(2, result.size)
    }

    @Test
    fun `데이터 행이 없으면 빈 목록을 반환한다`() {
        val result = parseBenchmarkSheetPaste(fullHeader)

        assertEquals(emptyList<ParsedBenchmarkRow>(), result)
    }
}
